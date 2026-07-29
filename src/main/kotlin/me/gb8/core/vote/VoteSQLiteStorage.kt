/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.vote

import me.gb8.core.util.GlobalUtils
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.HashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.logging.Level

class VoteSQLiteStorage(private val databaseFile: File) {
    private var connection: Connection? = null
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "8b8t-vote-storage").apply { isDaemon = true }
    }

    init {
        initializeDatabase()
    }

    private fun initializeDatabase() {
        try {
            Class.forName("org.sqlite.JDBC")
            
            val url = "jdbc:sqlite:${databaseFile.absolutePath}"
            connection = DriverManager.getConnection(url)
            
            val createTableSQL = "CREATE TABLE IF NOT EXISTS votes (" +
                "username TEXT PRIMARY KEY, " +
                "times_voted INTEGER NOT NULL DEFAULT 0, " +
                "timestamp INTEGER NOT NULL" +
                ")"
            
            connection?.createStatement()?.use { stmt ->
                stmt.execute(createTableSQL)
            }
        } catch (e: Throwable) {
            executor.shutdownNow()
            throw IllegalStateException("Failed to initialize SQLite vote database", e)
        }
    }

    fun save(voteMap: Map<PlayerName, VoteEntry>): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            val snapshot = voteMap.mapValues { (_, entry) -> VoteEntry(entry.count, entry.timestamp) }
            saveSnapshot(snapshot)
        }, executor).whenComplete { _, error ->
            if (error != null) {
                GlobalUtils.log(Level.SEVERE, "Failed to save votes to SQLite: ${error.cause?.message ?: error.message}")
            }
        }
    }

    fun upsert(name: PlayerName, entry: VoteEntry): CompletableFuture<Void> {
        val snapshot = VoteEntry(entry.count, entry.timestamp)
        return CompletableFuture.runAsync({ upsertSnapshot(name, snapshot) }, executor).whenComplete { _, error ->
            if (error != null) {
                GlobalUtils.log(Level.SEVERE, "Failed to update vote for ${name.value}: ${error.cause?.message ?: error.message}")
            }
        }
    }

    fun delete(name: PlayerName): CompletableFuture<Void> = deleteAll(listOf(name))

    fun deleteAll(names: Collection<PlayerName>): CompletableFuture<Void> {
        val snapshot = names.distinct()
        if (snapshot.isEmpty()) return CompletableFuture.completedFuture(null)
        return CompletableFuture.runAsync({ deleteSnapshots(snapshot) }, executor).whenComplete { _, error ->
            if (error != null) {
                GlobalUtils.log(Level.SEVERE, "Failed to delete vote entries: ${error.cause?.message ?: error.message}")
            }
        }
    }

    private fun upsertSnapshot(name: PlayerName, entry: VoteEntry) {
        val conn = connection ?: throw IllegalStateException("SQLite connection is null, cannot update vote")
        conn.prepareStatement(UPSERT_SQL).use { statement ->
            statement.setString(1, name.value)
            statement.setInt(2, entry.count)
            statement.setLong(3, entry.timestamp)
            statement.executeUpdate()
        }
    }

    private fun deleteSnapshots(names: Collection<PlayerName>) {
        val conn = connection ?: throw IllegalStateException("SQLite connection is null, cannot delete votes")
        try {
            conn.autoCommit = false
            conn.prepareStatement("DELETE FROM votes WHERE username = ?").use { statement ->
                names.forEach { name ->
                    statement.setString(1, name.value)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
            conn.commit()
        } catch (e: Throwable) {
            runCatching { conn.rollback() }.onFailure(e::addSuppressed)
            throw e
        } finally {
            runCatching { conn.autoCommit = true }
        }
    }

    private fun saveSnapshot(voteMap: Map<PlayerName, VoteEntry>) {
        val conn = connection ?: run {
            throw IllegalStateException("SQLite connection is null, cannot save votes")
        }
        try {
            conn.autoCommit = false

            val storedNames = HashSet<String>()
            conn.createStatement().use { statement ->
                statement.executeQuery("SELECT username FROM votes").use { result ->
                    while (result.next()) storedNames.add(result.getString(1).lowercase())
                }
            }

            conn.prepareStatement("DELETE FROM votes WHERE username = ?").use { statement ->
                storedNames.asSequence()
                    .filter { PlayerName(it) !in voteMap }
                    .forEach {
                        statement.setString(1, it)
                        statement.addBatch()
                    }
                statement.executeBatch()
            }

            conn.prepareStatement(UPSERT_SQL).use { stmt ->
                voteMap.forEach { (username, entry) ->
                    stmt.setString(1, username.value)
                    stmt.setInt(2, entry.count)
                    stmt.setLong(3, entry.timestamp)
                    stmt.addBatch()
                }
                
                stmt.executeBatch()
            }
            conn.commit()
        } catch (e: Throwable) {
            try {
                conn.rollback()
            } catch (ex: SQLException) {
                e.addSuppressed(ex)
            }
            throw e
        } finally {
            try {
                conn.autoCommit = true
            } catch (e: SQLException) {
                GlobalUtils.log(Level.WARNING, "Failed to restore vote database auto-commit: ${e.message}")
            }
        }
    }

    fun load(): HashMap<PlayerName, VoteEntry> {
        return CompletableFuture.supplyAsync({ loadSnapshot() }, executor).join()
    }

    private fun loadSnapshot(): HashMap<PlayerName, VoteEntry> {
        val voteMap = HashMap<PlayerName, VoteEntry>()
        
        val conn = connection ?: run {
            GlobalUtils.log(Level.WARNING, "SQLite connection is null, returning empty vote map")
            return voteMap
        }

        val selectSQL = "SELECT username, times_voted, timestamp FROM votes"
        
        try {
            conn.createStatement().use { stmt ->
                stmt.executeQuery(selectSQL).use { rs ->
                    while (rs.next()) {
                        val username = PlayerName(rs.getString("username").lowercase())
                        val timesVoted = rs.getInt("times_voted")
                        val timestamp = rs.getLong("timestamp")
                        
                        voteMap[username] = VoteEntry(timesVoted, timestamp)
                    }
                }
            }
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to load votes from SQLite", e)
        }

        return voteMap
    }

    fun close() {
        try {
            CompletableFuture.runAsync({
                connection?.let { conn ->
                    runCatching {
                        conn.close()
                        GlobalUtils.log(Level.INFO, "SQLite vote database connection closed")
                    }.onFailure { e ->
                        GlobalUtils.log(Level.WARNING, "Error closing SQLite connection: ${e.message}")
                    }
                }
                connection = null
            }, executor).join()
        } finally {
            executor.shutdown()
        }
    }

    private companion object {
        val UPSERT_SQL = """
            INSERT INTO votes (username, times_voted, timestamp) VALUES (?, ?, ?)
            ON CONFLICT(username) DO UPDATE SET
                times_voted = excluded.times_voted,
                timestamp = excluded.timestamp
        """.trimIndent()
    }
}

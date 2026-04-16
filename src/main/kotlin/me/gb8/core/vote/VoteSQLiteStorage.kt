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
import java.util.logging.Level

class VoteSQLiteStorage(private val databaseFile: File) {
    private var connection: Connection? = null

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
            
        } catch (e: Exception) {
            GlobalUtils.log(Level.SEVERE, "Failed to initialize SQLite vote database")
            e.printStackTrace()
        }
    }

    fun save(voteMap: Map<String, VoteEntry>) {
        val conn = connection ?: run {
            GlobalUtils.log(Level.WARNING, "SQLite connection is null, cannot save votes")
            return
        }
        try {
            conn.autoCommit = false
            conn.createStatement().use { clearStmt ->
                clearStmt.execute("DELETE FROM votes")
            }

            val insertSQL = "INSERT INTO votes (username, times_voted, timestamp) VALUES (?, ?, ?)"

            conn.prepareStatement(insertSQL).use { stmt ->
                for ((username, entry) in voteMap) {
                    stmt.setString(1, username)
                    stmt.setInt(2, entry.count)
                    stmt.setLong(3, entry.timestamp)
                    stmt.addBatch()
                }
                
                stmt.executeBatch()
            }
            conn.commit()
        } catch (e: SQLException) {
            GlobalUtils.log(Level.SEVERE, "Failed to save votes to SQLite")
            e.printStackTrace()
            try {
                conn.rollback()
            } catch (ex: SQLException) {
                ex.printStackTrace()
            }
        } finally {
            try {
                conn.autoCommit = true
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
    }

    fun load(): HashMap<String, VoteEntry> {
        val voteMap = HashMap<String, VoteEntry>()
        
        val conn = connection ?: run {
            GlobalUtils.log(Level.WARNING, "SQLite connection is null, returning empty vote map")
            return voteMap
        }

        val selectSQL = "SELECT username, times_voted, timestamp FROM votes"
        
        try {
            conn.createStatement().use { stmt ->
                stmt.executeQuery(selectSQL).use { rs ->
                    while (rs.next()) {
                        val username = rs.getString("username")
                        val timesVoted = rs.getInt("times_voted")
                        val timestamp = rs.getLong("timestamp")
                        
                        voteMap[username] = VoteEntry(timesVoted, timestamp)
                    }
                }
            }
        } catch (e: SQLException) {
            GlobalUtils.log(Level.SEVERE, "Failed to load votes from SQLite")
            e.printStackTrace()
        }

        return voteMap
    }

    fun close() {
        connection?.let { conn ->
            try {
                conn.close()
                GlobalUtils.log(Level.INFO, "SQLite vote database connection closed")
            } catch (e: SQLException) {
                GlobalUtils.log(Level.WARNING, "Error closing SQLite connection")
                e.printStackTrace()
            }
        }
    }
}

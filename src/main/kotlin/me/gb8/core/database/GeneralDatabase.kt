/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.io.File
import java.sql.*
import java.time.Instant
import java.util.concurrent.*
import java.util.logging.Level
import java.util.logging.Logger

class GeneralDatabase : Listener {
    companion object {
        private var instance: GeneralDatabase? = null
        private val LOGGER: Logger = Logger.getLogger("8b8tCore-Database")

        @JvmStatic
        fun initialize(pluginFolderPath: String) {
            if (instance == null) {
                instance = GeneralDatabase(pluginFolderPath)
            }
        }

        @JvmStatic
        fun getInstance(): GeneralDatabase {
            return instance ?: throw IllegalStateException("GeneralDatabase not initialized! Call initialize() first.")
        }
    }

    private val dataSource: HikariDataSource
    private val databaseExecutor: ExecutorService

    private val VALID_COLUMNS: Set<String> = setOf(
        "displayname", "muted", "showJoinMsg", "hidePrefix", "hideDeathMessages",
        "hideAnnouncements", "hideBadges", "selectedRank", "customGradient",
        "hideCustomTab", "useVanillaLeaderboard", "gradient_animation",
        "gradient_speed", "nameDecorations", "preventPhantomSpawn",
        "prefixGradient", "prefix_animation", "prefix_speed", "prefixDecorations"
    )

    private val cache = ConcurrentHashMap<String, PlayerDataCache>()

    class PlayerDataCache {
        private val data = ConcurrentHashMap<String, Any?>()
        fun get(key: String): Any? = data[key]
        fun set(key: String, value: Any?) {
            if (value == null) data.remove(key)
            else data[key] = value
        }
        fun getString(key: String): String? = data[key] as? String
        fun getBoolean(key: String, def: Boolean): Boolean {
            val value = data[key]
            if (value is Int) return value == 1
            if (value is Boolean) return value
            return def
        }
        fun getInt(key: String, def: Int): Int {
            val value = data[key]
            if (value is Number) return value.toInt()
            return def
        }
        fun getLong(key: String, def: Long): Long {
            val value = data[key]
            if (value is Number) return value.toLong()
            return def
        }
    }

    private constructor(pluginFolderPath: String) {
        val databaseDir = File("$pluginFolderPath/Database")
        if (!databaseDir.exists()) {
            databaseDir.mkdirs()
        }

        val databasePath = "$pluginFolderPath/Database/8b8tCorePlayerDB.db"

        val config = HikariConfig()
        config.jdbcUrl = "jdbc:sqlite:$databasePath"
        config.maximumPoolSize = 10
        config.minimumIdle = 2
        config.idleTimeout = 300000
        config.maxLifetime = 600000
        config.connectionTimeout = 30000
        config.poolName = "8b8tCore-SQLite-Pool"
        config.addDataSourceProperty("journal_mode", "WAL")
        config.addDataSourceProperty("synchronous", "NORMAL")
        config.addDataSourceProperty("busy_timeout", "5000")

        this.dataSource = HikariDataSource(config)
        this.databaseExecutor = Executors.newFixedThreadPool(4) { r ->
            val t = Thread(r, "8b8tCore-Database-Thread")
            t.isDaemon = true
            t
        }

        createTables()
    }

    private fun getConnection(): Connection = dataSource.connection

    private fun createTables(): Unit {
        val createTableSQL = """
            CREATE TABLE IF NOT EXISTS playerdata (
                username TEXT PRIMARY KEY NOT NULL,
                displayname TEXT,
                muted INTEGER
            );
            """.trimIndent()

        try {
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute(createTableSQL)

                    addColumnIfNotExists(conn, stmt, "showJoinMsg", "BOOLEAN DEFAULT TRUE")
                    addColumnIfNotExists(conn, stmt, "muted", "INTEGER")
                    addColumnIfNotExists(conn, stmt, "hidePrefix", "BOOLEAN DEFAULT FALSE")
                    addColumnIfNotExists(conn, stmt, "hideDeathMessages", "BOOLEAN DEFAULT FALSE")
                    addColumnIfNotExists(conn, stmt, "hideAnnouncements", "BOOLEAN DEFAULT FALSE")
                    addColumnIfNotExists(conn, stmt, "hideBadges", "BOOLEAN DEFAULT FALSE")
                    addColumnIfNotExists(conn, stmt, "selectedRank", "TEXT")
                    addColumnIfNotExists(conn, stmt, "customGradient", "TEXT")
                    addColumnIfNotExists(conn, stmt, "hideCustomTab", "BOOLEAN DEFAULT FALSE")
                    addColumnIfNotExists(conn, stmt, "useVanillaLeaderboard", "BOOLEAN DEFAULT FALSE")
                    addColumnIfNotExists(conn, stmt, "gradient_animation", "TEXT DEFAULT 'none'")
                    addColumnIfNotExists(conn, stmt, "gradient_speed", "INTEGER DEFAULT 5")
                    addColumnIfNotExists(conn, stmt, "nameDecorations", "TEXT")
                    addColumnIfNotExists(conn, stmt, "preventPhantomSpawn", "BOOLEAN DEFAULT TRUE")
                    addColumnIfNotExists(conn, stmt, "prefixGradient", "TEXT")
                    addColumnIfNotExists(conn, stmt, "prefix_animation", "TEXT DEFAULT 'none'")
                    addColumnIfNotExists(conn, stmt, "prefix_speed", "INTEGER DEFAULT 5")
                    addColumnIfNotExists(conn, stmt, "prefixDecorations", "TEXT")
                }
            }
        } catch (e: SQLException) {
            LOGGER.log(Level.SEVERE, "Database operation failed", e)
        }
    }

    private fun addColumnIfNotExists(conn: Connection, stmt: Statement, columnName: String, columnDef: String) {
        if (!columnExists(conn, "playerdata", columnName)) {
            stmt.execute("ALTER TABLE playerdata ADD COLUMN $columnName $columnDef")
        }
    }

    private fun columnExists(conn: Connection, tableName: String, columnName: String): Boolean {
        return try {
            val rs = conn.metaData.getColumns(null, null, tableName, columnName)
            rs.next()
        } catch (e: SQLException) {
            LOGGER.log(Level.SEVERE, "Database operation failed", e)
            false
        }
    }

    private fun validateColumn(column: String) {
        if (!VALID_COLUMNS.contains(column)) {
            throw IllegalArgumentException("Invalid column name: $column")
        }
    }

    private fun executeUpdate(sql: String, vararg params: Any?): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            try {
                getConnection().use { conn ->
                    conn.prepareStatement(sql).use { pstmt ->
                        for (i in params.indices) {
                            pstmt.setObject(i + 1, params[i])
                        }
                        pstmt.executeUpdate()
                    }
                }
            } catch (e: SQLException) {
                LOGGER.log(Level.SEVERE, "Database operation failed", e)
            }
        }, databaseExecutor)
    }

    private fun <T> executeQueryAsync(sql: String, mapper: (ResultSet) -> T, defaultValue: T, vararg params: Any?): CompletableFuture<T> {
        return CompletableFuture.supplyAsync({
            try {
                getConnection().use { conn ->
                    conn.prepareStatement(sql).use { pstmt ->
                        for (i in params.indices) {
                            pstmt.setObject(i + 1, params[i])
                        }
                        pstmt.executeQuery().use { rs ->
                            if (rs.next()) {
                                return@supplyAsync mapper(rs)
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                LOGGER.log(Level.SEVERE, "Database operation failed", e)
            }
            defaultValue
        }, databaseExecutor)
    }

    fun upsertPlayer(username: String, column: String, value: Any?): CompletableFuture<Void> {
        validateColumn(column)
        val pd = cache[username]
        pd?.set(column, value)
        
        val sql = "INSERT INTO playerdata (username, displayname, $column) VALUES (?, ?, ?) " +
                     "ON CONFLICT(username) DO UPDATE SET $column = excluded.$column"
        return executeUpdate(sql, username, username, value)
    }

    fun loadPlayerDataCache(username: String): CompletableFuture<PlayerDataCache> {
        return CompletableFuture.supplyAsync({
            try {
                getConnection().use { conn ->
                    conn.prepareStatement("SELECT * FROM playerdata WHERE username = ?").use { pstmt ->
                        pstmt.setString(1, username)
                        pstmt.executeQuery().use { rs ->
                            val pd = PlayerDataCache()
                            if (rs.next()) {
                                val meta = rs.metaData
                                for (i in 1..meta.columnCount) {
                                    pd.set(meta.getColumnName(i), rs.getObject(i))
                                }
                            }
                            cache[username] = pd
                            pd
                        }
                    }
                }
            } catch (e: SQLException) {
                LOGGER.log(Level.SEVERE, "Database operation failed", e)
                PlayerDataCache()
            }
        }, databaseExecutor)
    }

    fun unloadPlayerDataCache(username: String) {
        cache.remove(username)
    }

    @EventHandler
    fun onPlayerQuit(event: org.bukkit.event.player.PlayerQuitEvent) {
        unloadPlayerDataCache(event.player.name)
    }

    fun insertNickname(username: String, displayname: String): CompletableFuture<Void> {
        val pd = cache[username]
        pd?.set("displayname", displayname)
        val sql = "INSERT INTO playerdata (username, displayname) VALUES (?, ?) " +
                     "ON CONFLICT(username) DO UPDATE SET displayname = excluded.displayname"
        return executeUpdate(sql, username, displayname)
    }

    fun getNicknameAsync(username: String): CompletableFuture<String?> {
        val pd = cache[username]
        if (pd != null) return CompletableFuture.completedFuture(pd.getString("displayname"))
        return getPlayerDataAsync(username, "displayname")
    }

    fun getPlayerDataAsync(username: String, column: String): CompletableFuture<String?> {
        validateColumn(column)
        val pd = cache[username]
        if (pd != null) return CompletableFuture.completedFuture(pd.getString(column))

        return executeQueryAsync<String?>(
                "SELECT $column FROM playerdata WHERE username = ?",
                { rs -> val `val` = rs.getString(column); if (rs.wasNull()) null else `val` },
                null,
                username
        )
    }

    fun getBooleanAsync(username: String, column: String, defaultValue: Boolean): CompletableFuture<Boolean> {
        validateColumn(column)
        val pd = cache[username]
        if (pd != null) return CompletableFuture.completedFuture(pd.getBoolean(column, defaultValue))
        return executeQueryAsync(
                "SELECT $column FROM playerdata WHERE username = ?",
                { rs -> rs.getInt(column) == 1 },
                defaultValue,
                username
        )
    }

    fun updateShowJoinMsg(username: String, showJoinMsg: Boolean): CompletableFuture<Void> {
        return upsertPlayer(username, "showJoinMsg", showJoinMsg)
    }

    fun getPlayerShowJoinMsgAsync(username: String): CompletableFuture<Boolean> {
        return getBooleanAsync(username, "showJoinMsg", true)
    }

    fun updateHidePrefix(username: String, hidePrefix: Boolean): CompletableFuture<Void> {
        return upsertPlayer(username, "hidePrefix", hidePrefix)
    }

    fun getPlayerHidePrefixAsync(username: String): CompletableFuture<Boolean> {
        return getBooleanAsync(username, "hidePrefix", false)
    }

    fun updateHideDeathMessages(username: String, hide: Boolean): CompletableFuture<Void> {
        return upsertPlayer(username, "hideDeathMessages", hide)
    }

    fun getPlayerHideDeathMessagesAsync(username: String): CompletableFuture<Boolean> {
        return getBooleanAsync(username, "hideDeathMessages", false)
    }

    fun updateHideAnnouncements(username: String, hide: Boolean): CompletableFuture<Void> {
        return upsertPlayer(username, "hideAnnouncements", hide)
    }

    fun getPlayerHideAnnouncementsAsync(username: String): CompletableFuture<Boolean> {
        return getBooleanAsync(username, "hideAnnouncements", false)
    }

    fun updateHideBadges(username: String, hide: Boolean): CompletableFuture<Void> {
        return upsertPlayer(username, "hideBadges", hide)
    }

    fun getPlayerHideBadgesAsync(username: String): CompletableFuture<Boolean> {
        return getBooleanAsync(username, "hideBadges", false)
    }

    fun isMutedAsync(username: String): CompletableFuture<Boolean> {
        val pd = cache[username]
        if (pd != null) {
            val mutedUntil = pd.getLong("muted", 0L)
            if (mutedUntil <= Instant.now().epochSecond) {
                if (mutedUntil != 0L) unmute(username)
                return CompletableFuture.completedFuture(false)
            }
            return CompletableFuture.completedFuture(true)
        }

        return CompletableFuture.supplyAsync({
            try {
                getConnection().use { conn ->
                    conn.prepareStatement("SELECT muted FROM playerdata WHERE username = ?").use { pstmt ->
                        pstmt.setString(1, username)
                        pstmt.executeQuery().use { rs ->
                            if (rs.next()) {
                                val mutedUntil = rs.getLong("muted")
                                if (rs.wasNull()) return@supplyAsync false
                                if (mutedUntil > Instant.now().epochSecond) {
                                    return@supplyAsync true
                                } else {
                                    unmute(username)
                                }
                            }
                            false
                        }
                    }
                }
            } catch (e: SQLException) {
                LOGGER.log(Level.SEVERE, "Database operation failed", e)
                false
            }
        }, databaseExecutor)
    }

    fun getMutedUntilAsync(username: String): CompletableFuture<Long> {
        val pd = cache[username]
        if (pd != null) return CompletableFuture.completedFuture(pd.getLong("muted", 0L))
        return executeQueryAsync(
                "SELECT muted FROM playerdata WHERE username = ?",
                { rs -> val `val` = rs.getLong("muted"); if (rs.wasNull()) 0L else `val` },
                0L,
                username
        )
    }

    fun mute(username: String, timestamp: Long): CompletableFuture<Void> {
        return upsertPlayer(username, "muted", timestamp)
    }

    fun mute(username: String): CompletableFuture<Void> {
        return mute(username, 0L)
    }

    fun unmute(username: String): CompletableFuture<Void> {
        val pd = cache[username]
        if (pd != null) pd.set("muted", null)
        val sql = "UPDATE playerdata SET muted = NULL WHERE username = ?"
        return executeUpdate(sql, username)
    }

    fun updateSelectedRank(username: String, rank: String): CompletableFuture<Void> {
        return upsertPlayer(username, "selectedRank", rank)
    }

    fun getSelectedRankAsync(username: String): CompletableFuture<String?> {
        val pd = cache[username]
        if (pd != null) return CompletableFuture.completedFuture(pd.getString("selectedRank"))
        return executeQueryAsync<String?>(
                "SELECT selectedRank FROM playerdata WHERE username = ?",
                { rs -> val `val` = rs.getString("selectedRank"); if (rs.wasNull()) null else `val` },
                null,
                username
        )
    }

    fun updateCustomGradient(username: String, gradient: String): CompletableFuture<Void> {
        return upsertPlayer(username, "customGradient", gradient)
    }

    fun updateGradient(username: String, gradient: String): CompletableFuture<Void> {
        return updateCustomGradient(username, gradient)
    }

    fun getCustomGradientAsync(username: String): CompletableFuture<String?> {
        val pd = cache[username]
        if (pd != null) return CompletableFuture.completedFuture(pd.getString("customGradient"))
        return executeQueryAsync<String?>(
                "SELECT customGradient FROM playerdata WHERE username = ?",
                { rs -> val `val` = rs.getString("customGradient"); if (rs.wasNull()) null else `val` },
                null,
                username
        )
    }

    fun getGradientAsync(username: String): CompletableFuture<String?> {
        return getCustomGradientAsync(username)
    }

    fun updateHideCustomTab(username: String, hide: Boolean): CompletableFuture<Void> {
        return upsertPlayer(username, "hideCustomTab", hide)
    }

    fun getHideCustomTabAsync(username: String): CompletableFuture<Boolean> {
        return getBooleanAsync(username, "hideCustomTab", false)
    }

    fun setVanillaLeaderboard(username: String, useVanilla: Boolean): CompletableFuture<Void> {
        return upsertPlayer(username, "useVanillaLeaderboard", useVanilla)
    }

    fun isVanillaLeaderboardAsync(username: String): CompletableFuture<Boolean> {
        return getBooleanAsync(username, "useVanillaLeaderboard", false)
    }

    fun updateGradientAnimation(username: String, animation: String): CompletableFuture<Void> {
        return upsertPlayer(username, "gradient_animation", animation)
    }

    fun getGradientAnimationAsync(username: String): CompletableFuture<String> {
        val pd = cache[username]
        if (pd != null) return CompletableFuture.completedFuture(pd.getString("gradient_animation"))
        return executeQueryAsync(
                "SELECT gradient_animation FROM playerdata WHERE username = ?",
                { it.getString("gradient_animation") },
                "none",
                username
        )
    }

    fun updateGradientSpeed(username: String, speed: Int): CompletableFuture<Void> {
        return upsertPlayer(username, "gradient_speed", speed)
    }

    fun getGradientSpeedAsync(username: String): CompletableFuture<Int> {
        val pd = cache[username]
        if (pd != null) return CompletableFuture.completedFuture(pd.getInt("gradient_speed", 5))
        return executeQueryAsync(
                "SELECT gradient_speed FROM playerdata WHERE username = ?",
                { it.getInt("gradient_speed") },
                5,
                username
        )
    }

    fun updatePreventPhantomSpawn(username: String, prevent: Boolean): CompletableFuture<Void> {
        return upsertPlayer(username, "preventPhantomSpawn", prevent)
    }

    fun getPreventPhantomSpawnAsync(username: String): CompletableFuture<Boolean> {
        return getBooleanAsync(username, "preventPhantomSpawn", true)
    }

    fun updatePrefixGradient(username: String, gradient: String): CompletableFuture<Void> {
        return upsertPlayer(username, "prefixGradient", gradient)
    }

    fun getPrefixGradientAsync(username: String): CompletableFuture<String?> {
        val pd = cache[username]
        if (pd != null) return CompletableFuture.completedFuture(pd.getString("prefixGradient"))
        return executeQueryAsync<String?>(
                "SELECT prefixGradient FROM playerdata WHERE username = ?",
                { rs -> val `val` = rs.getString("prefixGradient"); if (rs.wasNull()) null else `val` },
                null,
                username
        )
    }

    fun updatePrefixAnimation(username: String, animation: String): CompletableFuture<Void> {
        return upsertPlayer(username, "prefix_animation", animation)
    }

    fun getPrefixAnimationAsync(username: String): CompletableFuture<String> {
        val pd = cache[username]
        if (pd != null) return CompletableFuture.completedFuture(pd.getString("prefix_animation"))
        return executeQueryAsync(
                "SELECT prefix_animation FROM playerdata WHERE username = ?",
                { it.getString("prefix_animation") },
                "none",
                username
        )
    }

    fun updatePrefixSpeed(username: String, speed: Int): CompletableFuture<Void> {
        return upsertPlayer(username, "prefix_speed", speed)
    }

    fun getPrefixSpeedAsync(username: String): CompletableFuture<Int> {
        val pd = cache[username]
        if (pd != null) return CompletableFuture.completedFuture(pd.getInt("prefix_speed", 5))
        return executeQueryAsync(
                "SELECT prefix_speed FROM playerdata WHERE username = ?",
                { it.getInt("prefix_speed") },
                5,
                username
        )
    }

    fun updatePrefixDecorations(username: String, decorations: String): CompletableFuture<Void> {
        return upsertPlayer(username, "prefixDecorations", decorations)
    }

    fun getPrefixDecorationsAsync(username: String): CompletableFuture<String?> {
        val pd = cache[username]
        if (pd != null) return CompletableFuture.completedFuture(pd.getString("prefixDecorations"))
        return executeQueryAsync<String?>(
                "SELECT prefixDecorations FROM playerdata WHERE username = ?",
                { it.getString("prefixDecorations") },
                null,
                username
        )
    }

    fun updateNameDecorations(username: String, decorations: String): CompletableFuture<Void> {
        return upsertPlayer(username, "nameDecorations", decorations)
    }

    fun close() {
        databaseExecutor.shutdown()
        try {
            if (!databaseExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                databaseExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            databaseExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
        
        if (!dataSource.isClosed) {
            dataSource.close()
        }
    }
}

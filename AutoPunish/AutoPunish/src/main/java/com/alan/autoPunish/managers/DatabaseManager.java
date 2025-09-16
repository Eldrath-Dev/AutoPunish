package com.alan.autoPunish.managers;

import com.alan.autoPunish.AutoPunishPlugin;
import com.alan.autopunish.model.Punishment;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Modern database manager with connection pooling and async operations
 */
public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    private final AutoPunishPlugin plugin;
    private HikariDataSource dataSource;
    private final DatabaseConfig config;
    private boolean initialized = false;

    public DatabaseManager(AutoPunishPlugin plugin, DatabaseConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Initialize database connection pool
     */
    public synchronized void initialize() {
        if (initialized) {
            logger.warn("Database already initialized, skipping...");
            return;
        }

        try {
            HikariConfig hikariConfig = createHikariConfig();
            this.dataSource = new HikariDataSource(hikariConfig);

            // Test connection
            try (Connection conn = dataSource.getConnection()) {
                logger.info("Database connection test successful");
            }

            // Create/verify tables
            createTables();
            runMigrations();

            initialized = true;
            logger.info("DatabaseManager initialized successfully with {} connection pool",
                    config.getType());

        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private HikariConfig createHikariConfig() {
        HikariConfig config = new HikariConfig();

        switch (this.config.getType()) {
            case SQLITE -> {
                config.setJdbcUrl("jdbc:h2:file:" + this.config.getPath() +
                        ";DB_CLOSE_ON_EXIT=FALSE;AUTO_SERVER=TRUE");
                config.setDriverClassName("org.h2.Driver");
            }
            case MYSQL -> {
                config.setJdbcUrl("jdbc:mysql://" + this.config.getHost() + ":" +
                        this.config.getPort() + "/" + this.config.getDatabase() +
                        "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                config.setUsername(this.config.getUsername());
                config.setPassword(this.config.getPassword());
            }
        }

        // Common pool settings
        config.setMaximumPoolSize(this.config.getPoolSize());
        config.setMinimumIdle(this.config.getMinIdle());
        config.setConnectionTimeout(this.config.getConnectionTimeout());
        config.setIdleTimeout(600000); // 10 minutes
        config.setMaxLifetime(1800000); // 30 minutes
        config.setLeakDetectionThreshold(60000); // 1 minute

        return config;
    }

    private void createTables() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Punishments table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS punishments (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(32) NOT NULL,
                    punisher VARCHAR(32) NOT NULL,
                    type VARCHAR(20) NOT NULL,
                    reason TEXT NOT NULL,
                    timestamp BIGINT NOT NULL,
                    expiry BIGINT,
                    active BOOLEAN DEFAULT TRUE,
                    severity INT NOT NULL,
                    evidence_link VARCHAR(500),
                    hidden BOOLEAN DEFAULT FALSE,
                    rule_id VARCHAR(100),
                    tier INT,
                    INDEX idx_player_uuid (player_uuid),
                    INDEX idx_timestamp (timestamp),
                    INDEX idx_active (active, expiry)
                )
            """);

            // Punishment history (violation counts)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_history (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    rule_id VARCHAR(100) NOT NULL,
                    violation_count INT DEFAULT 0,
                    last_violation BIGINT,
                    reset_date BIGINT,
                    INDEX idx_rule_id (rule_id)
                )
            """);

            // Queued punishments
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS queued_punishments (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(32) NOT NULL,
                    suggested_type VARCHAR(20) NOT NULL,
                    suggested_duration VARCHAR(50),
                    reason TEXT NOT NULL,
                    evidence TEXT,
                    queued_by VARCHAR(32) NOT NULL,
                    queued_at BIGINT NOT NULL,
                    status VARCHAR(20) DEFAULT 'PENDING',
                    reviewed_by VARCHAR(32),
                    reviewed_at BIGINT,
                    INDEX idx_status (status),
                    INDEX idx_queued_at (queued_at)
                )
            """);

            logger.info("Database tables verified/created successfully");
        }
    }

    private void runMigrations() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Migration: Add evidence_link column if missing
            try {
                stmt.execute("ALTER TABLE punishments ADD COLUMN evidence_link VARCHAR(500)");
                logger.info("Migration: Added evidence_link column");
            } catch (SQLException e) {
                if (!e.getMessage().contains("already exists")) {
                    logger.warn("Migration check failed (column may already exist): {}", e.getMessage());
                }
            }

            // Migration: Add hidden column if missing
            try {
                stmt.execute("ALTER TABLE punishments ADD COLUMN hidden BOOLEAN DEFAULT FALSE");
                logger.info("Migration: Added hidden column");
            } catch (SQLException e) {
                if (!e.getMessage().contains("already exists")) {
                    logger.warn("Migration check failed (column may already exist): {}", e.getMessage());
                }
            }

            logger.info("Database migrations completed");
        }
    }

    /**
     * Get a connection from the pool
     */
    public Connection getConnection() throws SQLException {
        if (!initialized) {
            throw new IllegalStateException("Database not initialized. Call initialize() first.");
        }
        return dataSource.getConnection();
    }

    /**
     * Async save punishment
     */
    public CompletableFuture<Void> savePunishmentAsync(Punishment punishment) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO punishments (player_uuid, player_name, punisher, type, reason, " +
                                 "timestamp, expiry, active, severity, evidence_link, hidden, rule_id, tier) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

                stmt.setString(1, punishment.playerUuid().toString());
                stmt.setString(2, punishment.playerName());
                stmt.setString(3, punishment.punisher());
                stmt.setString(4, punishment.type().name());
                stmt.setString(5, punishment.reason());
                stmt.setLong(6, punishment.timestamp().getEpochSecond());
                stmt.setLong(7, punishment.expiry() != null ?
                        punishment.expiry().getEpochSecond() : 0);
                stmt.setBoolean(8, punishment.active());
                stmt.setInt(9, punishment.severity());
                stmt.setString(10, punishment.evidenceLink());
                stmt.setBoolean(11, punishment.hidden());
                stmt.setString(12, punishment.ruleId());
                stmt.setInt(13, punishment.tier());

                stmt.executeUpdate();

            } catch (SQLException e) {
                logger.error("Failed to save punishment", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Get recent punishments (async)
     */
    public CompletableFuture<List<Punishment>> getRecentPunishmentsAsync(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> punishments = new ArrayList<>();

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM punishments WHERE active = TRUE ORDER BY timestamp DESC LIMIT ?")) {

                stmt.setInt(1, limit);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    punishments.add(extractPunishment(rs));
                }

            } catch (SQLException e) {
                logger.error("Failed to fetch recent punishments", e);
                throw new RuntimeException(e);
            }

            return punishments;
        });
    }

    /**
     * Get player punishment history
     */
    public CompletableFuture<List<Punishment>> getPlayerHistoryAsync(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> history = new ArrayList<>();

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM punishments WHERE player_uuid = ? ORDER BY timestamp DESC")) {

                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    history.add(extractPunishment(rs));
                }

            } catch (SQLException e) {
                logger.error("Failed to fetch player history", e);
                throw new RuntimeException(e);
            }

            return history;
        });
    }

    private Punishment extractPunishment(ResultSet rs) throws SQLException {
        return new Punishment(
                rs.getLong("id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("player_name"),
                rs.getString("punisher"),
                Punishment.PunishmentType.valueOf(rs.getString("type")),
                rs.getString("reason"),
                Instant.ofEpochSecond(rs.getLong("timestamp")),
                rs.getLong("expiry") > 0 ? Instant.ofEpochSecond(rs.getLong("expiry")) : null,
                rs.getBoolean("active"),
                rs.getInt("severity"),
                rs.getString("evidence_link"),
                rs.getBoolean("hidden"),
                rs.getString("rule_id"),
                rs.getInt("tier")
        );
    }

    /**
     * Shutdown database connections
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool shut down");
        }
        initialized = false;
    }

    public boolean isInitialized() {
        return initialized && dataSource != null && !dataSource.isClosed();
    }
}
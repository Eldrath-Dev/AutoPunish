package com.alan.autoPunish.managers;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.models.Punishment;
import com.alan.autoPunish.models.PunishmentRule;
import com.alan.autoPunish.models.QueuedPunishment;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {
    private final AutoPunish plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private Connection connection;

    public DatabaseManager(AutoPunish plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
        setupDatabase();
    }

    private void setupDatabase() {
        String storageType = configManager.getStorageType();
        logger.info("Setting up database with storage type: " + storageType);

        try {
            if (storageType.equalsIgnoreCase("mysql")) {
                setupMysql();
            } else {
                setupSqlite(); // This now actually sets up H2 but we keep the method name
            }

            createTables();
            logger.info("Database connection established successfully!");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to connect to database: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    private void setupSqlite() throws SQLException {
        try {
            Class.forName("org.h2.Driver");
            logger.info("H2 database driver loaded successfully!");
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "H2 database driver not found", e);
            e.printStackTrace();
            throw new SQLException("H2 database driver not found");
        }

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            logger.info("Creating plugin data folder...");
            boolean created = dataFolder.mkdirs();
            if (!created) {
                logger.warning("Failed to create plugin data folder!");
            }
        }

        File dbFile = new File(dataFolder, "punishments");
        String url = "jdbc:h2:" + dbFile.getAbsolutePath() + ";MODE=MySQL";
        logger.info("Connecting to H2 database at: " + dbFile.getAbsolutePath());

        try {
            connection = DriverManager.getConnection(url);

            // Test the connection with a simple query
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SELECT 1");
            }

            logger.info("H2 connection established successfully!");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to connect to H2 database: " + e.getMessage(), e);
            logger.log(Level.SEVERE, "Database file location: " + dbFile.getAbsolutePath());
            logger.log(Level.SEVERE, "Is parent directory writable: " + dataFolder.canWrite());
            e.printStackTrace();
            throw e;
        }
    }

    private void setupMysql() throws SQLException {
        try {
            // Use the newer driver class name
            Class.forName("com.mysql.cj.jdbc.Driver");
            logger.info("MySQL JDBC driver loaded successfully!");
        } catch (ClassNotFoundException e) {
            try {
                // Try with the older driver class name as fallback
                Class.forName("com.mysql.jdbc.Driver");
                logger.info("MySQL JDBC driver (legacy) loaded successfully!");
            } catch (ClassNotFoundException e2) {
                logger.log(Level.SEVERE, "MySQL JDBC driver not found", e);
                e.printStackTrace();
                throw new SQLException("MySQL JDBC driver not found");
            }
        }

        String host = configManager.getMysqlConfig().get("host");
        String port = configManager.getMysqlConfig().get("port");
        String database = configManager.getMysqlConfig().get("database");
        String username = configManager.getMysqlConfig().get("username");
        String password = configManager.getMysqlConfig().get("password");

        logger.info("Connecting to MySQL database at " + host + ":" + port + "/" + database);

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

        try {
            connection = DriverManager.getConnection(url, username, password);
            logger.info("MySQL connection established successfully!");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to connect to MySQL database: " + e.getMessage(), e);
            e.printStackTrace();
            throw e;
        }
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            logger.info("Creating database tables if they don't exist...");

            // Create punishments table
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS punishments (" +
                            "id VARCHAR(36) PRIMARY KEY, " +
                            "player_uuid VARCHAR(36) NOT NULL, " +
                            "player_name VARCHAR(16) NOT NULL, " +
                            "rule VARCHAR(50) NOT NULL, " +
                            "type VARCHAR(20) NOT NULL, " +
                            "duration VARCHAR(20) NOT NULL, " +
                            "staff_name VARCHAR(16) NOT NULL, " +
                            "staff_uuid VARCHAR(36) NOT NULL, " +
                            "date TIMESTAMP NOT NULL" +
                            ");"
            );

            // Create queued_punishments table
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS queued_punishments (" +
                            "id VARCHAR(36) PRIMARY KEY, " +
                            "player_uuid VARCHAR(36) NOT NULL, " +
                            "player_name VARCHAR(16) NOT NULL, " +
                            "rule VARCHAR(50) NOT NULL, " +
                            "type VARCHAR(20) NOT NULL, " +
                            "duration VARCHAR(20) NOT NULL, " +
                            "staff_name VARCHAR(16) NOT NULL, " +
                            "staff_uuid VARCHAR(36) NOT NULL, " +
                            "queued_date TIMESTAMP NOT NULL, " +
                            "approval_id VARCHAR(36) NOT NULL" +
                            ");"
            );

            // Create rules table
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS rules (" +
                            "rule_name VARCHAR(50) NOT NULL, " +
                            "tier_index INT NOT NULL, " +
                            "type VARCHAR(20) NOT NULL, " +
                            "duration VARCHAR(20) NOT NULL, " +
                            "PRIMARY KEY (rule_name, tier_index)" +
                            ");"
            );

            logger.info("Database tables created successfully!");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create database tables: " + e.getMessage(), e);
            e.printStackTrace();
            throw e;
        }
    }

    // --- Rule Management Methods ---

    public void syncRule(PunishmentRule rule) {
        String ruleName = rule.getName();
        String deleteSql = "DELETE FROM rules WHERE rule_name = ?;";
        String insertSql = "INSERT INTO rules (rule_name, tier_index, type, duration) VALUES (?, ?, ?, ?);";

        try (Connection conn = this.connection;
             PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

            conn.setAutoCommit(false); // Start transaction

            // Delete existing tiers for this rule
            deleteStmt.setString(1, ruleName);
            deleteStmt.executeUpdate();

            // Insert new tiers
            int tierIndex = 0;
            for (Map<String, String> tier : rule.getPunishmentTiers()) {
                insertStmt.setString(1, ruleName);
                insertStmt.setInt(2, tierIndex);
                insertStmt.setString(3, tier.get("type"));
                insertStmt.setString(4, tier.get("duration"));
                insertStmt.addBatch();
                tierIndex++;
            }
            insertStmt.executeBatch();

            conn.commit(); // Commit transaction
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to synchronize rule '" + ruleName + "': " + e.getMessage(), e);
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to rollback transaction: " + ex.getMessage(), ex);
            }
        }
    }

    public void deleteRule(String ruleName) {
        String sql = "DELETE FROM rules WHERE rule_name = ?;";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ruleName);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete rule '" + ruleName + "': " + e.getMessage(), e);
        }
    }

    public void syncAllRules(Map<String, PunishmentRule> rules) {
        String clearSql = "DELETE FROM rules;";
        try (Statement statement = connection.createStatement()) {
            statement.execute(clearSql);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to clear rules table: " + e.getMessage(), e);
            return;
        }

        for (PunishmentRule rule : rules.values()) {
            syncRule(rule);
        }
    }

    // --- Punishment History Methods ---

    public void savePunishment(Punishment punishment) {
        String sql = "INSERT INTO punishments (id, player_uuid, player_name, rule, type, duration, staff_name, staff_uuid, date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, punishment.getId().toString());
            statement.setString(2, punishment.getPlayerUuid().toString());
            statement.setString(3, punishment.getPlayerName());
            statement.setString(4, punishment.getRule());
            statement.setString(5, punishment.getType());
            statement.setString(6, punishment.getDuration());
            statement.setString(7, punishment.getStaffName());
            statement.setString(8, punishment.getStaffUuid().toString());
            statement.setTimestamp(9, new Timestamp(punishment.getDate().getTime()));

            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save punishment: " + e.getMessage(), e);
        }
    }

    public List<Punishment> getPunishmentHistory(UUID playerUuid) {
        List<Punishment> punishments = new ArrayList<>();
        String sql = "SELECT * FROM punishments WHERE player_uuid = ? ORDER BY date ASC;";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    punishments.add(createPunishmentFromResultSet(resultSet));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get punishment history: " + e.getMessage(), e);
        }

        return punishments;
    }

    public List<Punishment> getPunishmentHistoryForRule(UUID playerUuid, String rule) {
        List<Punishment> punishments = new ArrayList<>();
        String sql = "SELECT * FROM punishments WHERE player_uuid = ? AND rule = ? ORDER BY date ASC;";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, rule);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    punishments.add(createPunishmentFromResultSet(resultSet));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get punishment history for rule: " + e.getMessage(), e);
        }

        return punishments;
    }

    // *** NEW: Method to get all punishments for the public panel ***
    public List<Punishment> getAllPunishments() {
        List<Punishment> punishments = new ArrayList<>();
        String sql = "SELECT * FROM punishments ORDER BY date DESC;";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                punishments.add(createPunishmentFromResultSet(resultSet));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get all punishments: " + e.getMessage(), e);
        }
        return punishments;
    }

    // *** NEW: Method to get punishments by a specific type for the public panel ***
    public List<Punishment> getPunishmentsByType(String type) {
        List<Punishment> punishments = new ArrayList<>();
        String sql = "SELECT * FROM punishments WHERE type = ? ORDER BY date DESC;";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    punishments.add(createPunishmentFromResultSet(resultSet));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get punishments by type '" + type + "': " + e.getMessage(), e);
        }
        return punishments;
    }

    // --- Queued Punishment Methods ---

    public void saveQueuedPunishment(QueuedPunishment punishment) {
        String sql = "INSERT INTO queued_punishments (id, player_uuid, player_name, rule, type, duration, " +
                "staff_name, staff_uuid, queued_date, approval_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, punishment.getId().toString());
            statement.setString(2, punishment.getPlayerUuid().toString());
            statement.setString(3, punishment.getPlayerName());
            statement.setString(4, punishment.getRule());
            statement.setString(5, punishment.getType());
            statement.setString(6, punishment.getDuration());
            statement.setString(7, punishment.getStaffName());
            statement.setString(8, punishment.getStaffUuid().toString());
            statement.setTimestamp(9, new Timestamp(punishment.getQueuedDate().getTime()));
            statement.setString(10, punishment.getApprovalId());

            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save queued punishment: " + e.getMessage(), e);
        }
    }

    public void removeQueuedPunishment(String approvalId) {
        String sql = "DELETE FROM queued_punishments WHERE approval_id = ?;";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, approvalId);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to remove queued punishment: " + e.getMessage(), e);
        }
    }

    public List<QueuedPunishment> getQueuedPunishments() {
        List<QueuedPunishment> queuedPunishments = new ArrayList<>();
        String sql = "SELECT * FROM queued_punishments;";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                queuedPunishments.add(createQueuedPunishmentFromResultSet(resultSet));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get queued punishments: " + e.getMessage(), e);
        }

        return queuedPunishments;
    }

    // --- Utility and Maintenance Methods ---

    public boolean resetPlayerHistory(UUID playerUuid) {
        try {
            // Delete all punishments for this player
            String deletePunishmentsSql = "DELETE FROM punishments WHERE player_uuid = ?;";
            try (PreparedStatement statement = connection.prepareStatement(deletePunishmentsSql)) {
                statement.setString(1, playerUuid.toString());
                int deletedPunishments = statement.executeUpdate();
                logger.info("Deleted " + deletedPunishments + " punishments for player " + playerUuid);
            }

            // Delete all queued punishments for this player
            String deleteQueuedSql = "DELETE FROM queued_punishments WHERE player_uuid = ?;";
            try (PreparedStatement statement = connection.prepareStatement(deleteQueuedSql)) {
                statement.setString(1, playerUuid.toString());
                int deletedQueued = statement.executeUpdate();
                logger.info("Deleted " + deletedQueued + " queued punishments for player " + playerUuid);
            }

            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to reset player history: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean testConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                logger.severe("Database connection is null or closed!");
                return false;
            }

            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT 1");
                if (rs.next()) {
                    logger.info("Database connection test successful!");
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database connection test failed: " + e.getMessage(), e);
            e.printStackTrace();
            return false;
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error closing database connection: " + e.getMessage(), e);
                e.printStackTrace();
            }
        }
    }

    // --- Private Helper Methods to reduce code duplication ---

    private Punishment createPunishmentFromResultSet(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        String playerName = rs.getString("player_name");
        String rule = rs.getString("rule");
        String type = rs.getString("type");
        String duration = rs.getString("duration");
        String staffName = rs.getString("staff_name");
        UUID staffUuid = UUID.fromString(rs.getString("staff_uuid"));
        Timestamp date = rs.getTimestamp("date");

        return new Punishment(id, playerUuid, playerName, rule, type, duration, staffName, staffUuid, date);
    }

    private QueuedPunishment createQueuedPunishmentFromResultSet(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        String playerName = rs.getString("player_name");
        String rule = rs.getString("rule");
        String type = rs.getString("type");
        String duration = rs.getString("duration");
        String staffName = rs.getString("staff_name");
        UUID staffUuid = UUID.fromString(rs.getString("staff_uuid"));
        Timestamp queuedDate = rs.getTimestamp("queued_date");
        String approvalId = rs.getString("approval_id");

        return new QueuedPunishment(id, playerUuid, playerName, rule, type, duration,
                staffName, staffUuid, new Date(queuedDate.getTime()), approvalId);
    }
}
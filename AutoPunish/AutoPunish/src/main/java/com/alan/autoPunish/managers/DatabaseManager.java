package com.alan.autoPunish.managers;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.models.Punishment;
import com.alan.autoPunish.models.PunishmentRule;
import com.alan.autoPunish.models.QueuedPunishment;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
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
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "H2 database driver not found", e);
            throw new SQLException("H2 database driver not found");
        }

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File dbFile = new File(dataFolder, "punishments");
        String url = "jdbc:h2:" + dbFile.getAbsolutePath() + ";MODE=MySQL";
        logger.info("Connecting to H2 database at: " + dbFile.getAbsolutePath());

        try {
            connection = DriverManager.getConnection(url);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SELECT 1");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to connect to H2 database: " + e.getMessage(), e);
            throw e;
        }
    }

    private void setupMysql() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("com.mysql.jdbc.Driver");
            } catch (ClassNotFoundException e2) {
                logger.log(Level.SEVERE, "MySQL JDBC driver not found", e2);
                throw new SQLException("MySQL JDBC driver not found");
            }
        }

        String host = configManager.getMysqlConfig().get("host");
        String port = configManager.getMysqlConfig().get("port");
        String database = configManager.getMysqlConfig().get("database");
        String username = configManager.getMysqlConfig().get("username");
        String password = configManager.getMysqlConfig().get("password");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

        try {
            connection = DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to connect to MySQL database: " + e.getMessage(), e);
            throw e;
        }
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            logger.info("Creating database tables if they don't exist...");

            statement.execute(
                    "CREATE TABLE IF NOT EXISTS punishments (" +
                            "id VARCHAR(36) PRIMARY KEY, " +
                            "player_uuid VARCHAR(36) NOT NULL, " +
                            "player_name VARCHAR(16) NOT NULL, " +
                            "rule VARCHAR(50) NOT NULL, " +
                            "type VARCHAR(20) NOT NULL, " +
                            "duration VARCHAR(20) NOT NULL, " +
                            "staff_name VARCHAR(100) NOT NULL, " +
                            "staff_uuid VARCHAR(36) NOT NULL, " +
                            "date TIMESTAMP NOT NULL" +
                            ");"
            );

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
            throw e;
        }
    }

    // --- Rule Management Methods ---

    public void syncRule(PunishmentRule rule) {
        String ruleName = rule.getName();
        String deleteSql = "DELETE FROM rules WHERE rule_name = ?;";
        String insertSql = "INSERT INTO rules (rule_name, tier_index, type, duration) VALUES (?, ?, ?, ?);";

        try (PreparedStatement deleteStmt = connection.prepareStatement(deleteSql);
             PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {

            connection.setAutoCommit(false);

            deleteStmt.setString(1, ruleName);
            deleteStmt.executeUpdate();

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

            connection.commit();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to synchronize rule '" + ruleName + "': " + e.getMessage(), e);
        } finally {
            try {
                if (connection != null) connection.setAutoCommit(true);
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to restore auto-commit: " + ex.getMessage(), ex);
            }
        }
    }

    public void deleteRule(String ruleName) {
        String sql = "DELETE FROM rules WHERE rule_name = ?;";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ruleName);
            statement.executeUpdate();
            logger.info("Successfully deleted rule '" + ruleName + "' from the database.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete rule '" + ruleName + "': " + e.getMessage(), e);
        }
    }

    public void syncAllRules(Map<String, PunishmentRule> rules) {
        String clearSql = "TRUNCATE TABLE rules;"; // Use TRUNCATE for efficiency, falls back to DELETE
        try (Statement statement = connection.createStatement()) {
            statement.execute(clearSql);
            logger.info("Cleared existing rules from database for synchronization.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to clear rules table: " + e.getMessage(), e);
            return;
        }

        for (PunishmentRule rule : rules.values()) {
            syncRule(rule);
        }
        logger.info("Finished synchronizing all rules with the database.");
    }

    // --- Punishment History Methods ---

    public void savePunishment(Punishment punishment) {
        String sql = "INSERT INTO punishments (id, player_uuid, player_name, rule, type, duration, staff_name, staff_uuid, date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";
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
        if (playerUuid == null) return punishments;
        String sql = "SELECT * FROM punishments WHERE player_uuid = ? ORDER BY date DESC;";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    punishments.add(createPunishmentFromResultSet(resultSet));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get punishment history for UUID " + playerUuid + ": " + e.getMessage(), e);
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

    public List<Punishment> getPunishmentsByType(String type) {
        List<Punishment> punishments = new ArrayList<>();
        String sql = "SELECT * FROM punishments WHERE type = ? ORDER BY date DESC;";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.toLowerCase());
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

    // --- Queued Punishment & Utility Methods ---

    public void saveQueuedPunishment(QueuedPunishment punishment) {
        String sql = "INSERT INTO queued_punishments (id, player_uuid, player_name, rule, type, duration, staff_name, staff_uuid, queued_date, approval_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
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

    public boolean resetPlayerHistory(UUID playerUuid) {
        try {
            String deletePunishmentsSql = "DELETE FROM punishments WHERE player_uuid = ?;";
            try (PreparedStatement statement = connection.prepareStatement(deletePunishmentsSql)) {
                statement.setString(1, playerUuid.toString());
                statement.executeUpdate();
            }
            String deleteQueuedSql = "DELETE FROM queued_punishments WHERE player_uuid = ?;";
            try (PreparedStatement statement = connection.prepareStatement(deleteQueuedSql)) {
                statement.setString(1, playerUuid.toString());
                statement.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to reset player history: " + e.getMessage(), e);
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
            }
        }
    }

    // --- Private Helper Methods ---

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
        return new QueuedPunishment(id, playerUuid, playerName, rule, type, duration, staffName, staffUuid, new Date(queuedDate.getTime()), approvalId);
    }
}
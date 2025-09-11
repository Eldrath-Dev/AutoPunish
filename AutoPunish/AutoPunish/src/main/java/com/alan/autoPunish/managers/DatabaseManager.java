package com.alan.autoPunish.managers;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.models.Punishment;
import com.alan.autoPunish.models.PunishmentRule;
import com.alan.autoPunish.models.QueuedPunishment;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.Date;
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

    // --- Setup and Connection ---
    private void setupDatabase() {
        String storageType = configManager.getStorageType();
        logger.info("Setting up database with storage type: " + storageType);

        try {
            if (storageType.equalsIgnoreCase("mysql")) setupMysql();
            else setupSqlite();

            createTables();
            logger.info("Database connection established successfully!");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to connect to database: " + e.getMessage(), e);
        }
    }

    private void setupSqlite() throws SQLException {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("H2 database driver not found", e);
        }

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        File dbFile = new File(dataFolder, "punishments");
        String url = "jdbc:h2:" + dbFile.getAbsolutePath() + ";MODE=MySQL";

        logger.info("Connecting to H2 database at: " + dbFile.getAbsolutePath());
        connection = DriverManager.getConnection(url);
    }

    private void setupMysql() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            try { Class.forName("com.mysql.jdbc.Driver"); }
            catch (ClassNotFoundException e2) { throw new SQLException("MySQL JDBC driver not found"); }
        }

        Map<String, String> cfg = configManager.getMysqlConfig();
        String url = "jdbc:mysql://" + cfg.get("host") + ":" + cfg.get("port") + "/" + cfg.get("database") +
                "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

        connection = DriverManager.getConnection(url, cfg.get("username"), cfg.get("password"));
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            logger.info("Ensuring database tables exist...");

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
                            "staff_name VARCHAR(100) NOT NULL, " +
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
        }
    }

    // --- Rule Management ---
    public void syncRule(PunishmentRule rule) {
        String deleteSql = "DELETE FROM rules WHERE rule_name = ?;";
        String insertSql = "INSERT INTO rules (rule_name, tier_index, type, duration) VALUES (?, ?, ?, ?);";

        try (PreparedStatement deleteStmt = connection.prepareStatement(deleteSql);
             PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {

            connection.setAutoCommit(false);
            deleteStmt.setString(1, rule.getName());
            deleteStmt.executeUpdate();

            int tierIndex = 0;
            for (Map<String, String> tier : rule.getPunishmentTiers()) {
                insertStmt.setString(1, rule.getName());
                insertStmt.setInt(2, tierIndex);
                insertStmt.setString(3, tier.get("type"));
                insertStmt.setString(4, tier.get("duration"));
                insertStmt.addBatch();
                tierIndex++;
            }

            insertStmt.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to sync rule '" + rule.getName() + "': " + e.getMessage(), e);
            try { connection.rollback(); } catch (SQLException ignored) {}
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public void updateRule(String ruleName, int tierIndex, String type, String duration) {
        String sql = "REPLACE INTO rules (rule_name, tier_index, type, duration) VALUES (?, ?, ?, ?);";
        try (PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, ruleName);
            st.setInt(2, tierIndex);
            st.setString(3, type);
            st.setString(4, duration);
            st.executeUpdate();
            logger.info("Updated rule tier " + tierIndex + " for rule " + ruleName);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update rule '" + ruleName + "': " + e.getMessage(), e);
        }
    }

    public void deleteRule(String ruleName) {
        String sql = "DELETE FROM rules WHERE rule_name = ?;";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ruleName);
            statement.executeUpdate();
            logger.info("Deleted rule '" + ruleName + "' from DB.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete rule '" + ruleName + "': " + e.getMessage(), e);
        }
    }

    public void syncAllRules(Map<String, PunishmentRule> rules) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM rules;");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to clear rules table: " + e.getMessage(), e);
            return;
        }
        rules.values().forEach(this::syncRule);
        logger.info("Synchronized all rules with DB.");
    }

    public Map<String, List<Map<String, String>>> loadRulesFromDb() {
        Map<String, List<Map<String, String>>> rules = new HashMap<>();
        String sql = "SELECT * FROM rules ORDER BY rule_name, tier_index;";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String ruleName = rs.getString("rule_name");
                rules.putIfAbsent(ruleName, new ArrayList<>());
                Map<String, String> tier = new HashMap<>();
                tier.put("type", rs.getString("type"));
                tier.put("duration", rs.getString("duration"));
                rules.get(ruleName).add(tier);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load rules from DB", e);
        }
        return rules;
    }

    // --- Punishments ---
    public void savePunishment(Punishment p) {
        String sql = "INSERT INTO punishments (id, player_uuid, player_name, rule, type, duration, staff_name, staff_uuid, date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, p.getId().toString());
            st.setString(2, p.getPlayerUuid().toString());
            st.setString(3, p.getPlayerName());
            st.setString(4, p.getRule());
            st.setString(5, p.getType());
            st.setString(6, p.getDuration());
            st.setString(7, p.getStaffName());
            st.setString(8, p.getStaffUuid().toString());
            st.setTimestamp(9, new Timestamp(p.getDate().getTime()));
            st.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save punishment: " + e.getMessage(), e);
        }
    }

    public List<Punishment> getAllPunishments() {
        return fetchPunishments("SELECT * FROM punishments ORDER BY date DESC;", null);
    }

    public List<Punishment> getPunishmentsByType(String type) {
        return fetchPunishments("SELECT * FROM punishments WHERE type = ? ORDER BY date DESC;", type);
    }

    public List<Punishment> getPunishmentHistory(UUID playerUuid) {
        return fetchPunishments("SELECT * FROM punishments WHERE player_uuid = ? ORDER BY date DESC;", playerUuid.toString());
    }

    public List<Punishment> getPunishmentHistoryForRule(UUID playerUuid, String rule) {
        return fetchPunishments("SELECT * FROM punishments WHERE player_uuid = ? AND rule = ? ORDER BY date ASC;", playerUuid.toString(), rule);
    }

    private List<Punishment> fetchPunishments(String sql, String... params) {
        List<Punishment> list = new ArrayList<>();
        try (PreparedStatement st = connection.prepareStatement(sql)) {
            if (params != null) {
                for (int i = 0; i < params.length; i++) st.setString(i + 1, params[i]);
            }
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) list.add(createPunishmentFromResultSet(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to fetch punishments: " + e.getMessage(), e);
        }
        return list;
    }

    // --- Queued Punishments ---
    public void saveQueuedPunishment(QueuedPunishment p) {
        String sql = "INSERT INTO queued_punishments (id, player_uuid, player_name, rule, type, duration, staff_name, staff_uuid, queued_date, approval_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, p.getId().toString());
            st.setString(2, p.getPlayerUuid().toString());
            st.setString(3, p.getPlayerName());
            st.setString(4, p.getRule());
            st.setString(5, p.getType());
            st.setString(6, p.getDuration());
            st.setString(7, p.getStaffName());
            st.setString(8, p.getStaffUuid().toString());
            st.setTimestamp(9, new Timestamp(p.getQueuedDate().getTime()));
            st.setString(10, p.getApprovalId());
            st.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save queued punishment", e);
        }
    }

    public void removeQueuedPunishment(String approvalId) {
        try (PreparedStatement st = connection.prepareStatement("DELETE FROM queued_punishments WHERE approval_id = ?;")) {
            st.setString(1, approvalId);
            st.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to remove queued punishment", e);
        }
    }

    public List<QueuedPunishment> getQueuedPunishments() {
        List<QueuedPunishment> list = new ArrayList<>();
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM queued_punishments;")) {
            while (rs.next()) list.add(createQueuedPunishmentFromResultSet(rs));
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to fetch queued punishments", e);
        }
        return list;
    }

    // --- Player History Reset ---
    public boolean resetPlayerHistory(UUID playerUuid) {
        try (PreparedStatement st1 = connection.prepareStatement("DELETE FROM punishments WHERE player_uuid = ?;");
             PreparedStatement st2 = connection.prepareStatement("DELETE FROM queued_punishments WHERE player_uuid = ?;")) {

            st1.setString(1, playerUuid.toString());
            st1.executeUpdate();

            st2.setString(1, playerUuid.toString());
            st2.executeUpdate();

            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to reset player history", e);
            return false;
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error closing DB connection", e);
            }
        }
    }

    // --- Helpers ---
    private Punishment createPunishmentFromResultSet(ResultSet rs) throws SQLException {
        return new Punishment(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("player_name"),
                rs.getString("rule"),
                rs.getString("type"),
                rs.getString("duration"),
                rs.getString("staff_name"),
                UUID.fromString(rs.getString("staff_uuid")),
                rs.getTimestamp("date")
        );
    }

    private QueuedPunishment createQueuedPunishmentFromResultSet(ResultSet rs) throws SQLException {
        return new QueuedPunishment(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("player_name"),
                rs.getString("rule"),
                rs.getString("type"),
                rs.getString("duration"),
                rs.getString("staff_name"),
                UUID.fromString(rs.getString("staff_uuid")),
                new Date(rs.getTimestamp("queued_date").getTime()),
                rs.getString("approval_id")
        );
    }
}

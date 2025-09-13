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

// API events
import com.alan.autoPunish.api.events.PunishmentQueuedEvent;
import com.alan.autoPunish.api.events.PunishmentApprovedEvent;
import com.alan.autoPunish.api.events.PunishmentDeniedEvent;
import com.alan.autoPunish.api.events.PlayerHistoryResetEvent;

// For password hashing
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

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
        try { Class.forName("org.h2.Driver"); }
        catch (ClassNotFoundException e) { throw new SQLException("H2 database driver not found", e); }

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        File dbFile = new File(dataFolder, "punishments");
        String url = "jdbc:h2:" + dbFile.getAbsolutePath() + ";MODE=MySQL";

        logger.info("Connecting to H2 database at: " + dbFile.getAbsolutePath());
        connection = DriverManager.getConnection(url);
    }

    private void setupMysql() throws SQLException {
        try { Class.forName("com.mysql.cj.jdbc.Driver"); }
        catch (ClassNotFoundException e) {
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

            // Updated punishments table with evidence_link column
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
                            "date TIMESTAMP NOT NULL, " +
                            "evidence_link VARCHAR(500) NULL" +  // NEW: Evidence link column
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

            // NEW: Staff chat table
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS staff_chat (" +
                            "id VARCHAR(36) PRIMARY KEY, " +
                            "staff_name VARCHAR(100) NOT NULL, " +
                            "staff_uuid VARCHAR(36) NOT NULL, " +
                            "message TEXT NOT NULL, " +
                            "timestamp TIMESTAMP NOT NULL" +
                            ");"
            );

            // NEW: Staff users table
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS staff_users (" +
                            "id VARCHAR(36) PRIMARY KEY, " +
                            "username VARCHAR(50) UNIQUE NOT NULL, " +
                            "password_hash VARCHAR(255) NOT NULL, " +
                            "uuid VARCHAR(36) UNIQUE, " +
                            "role VARCHAR(20) NOT NULL DEFAULT 'staff'" +
                            ");"
            );

            logger.info("Database tables created successfully!");
        }
    }

    // --- Expose Connection for WebPanel ---
    public synchronized Connection getConnection() throws SQLException {
        // Check if connection is still valid
        if (connection == null || connection.isClosed()) {
            setupDatabase(); // Reconnect
        }
        return connection;
    }

    // --- Rule Management ---
    public void syncRule(PunishmentRule rule) {
        String deleteSql = "DELETE FROM rules WHERE rule_name = ?;";
        String insertSql = "INSERT INTO rules (rule_name, tier_index, type, duration) VALUES (?, ?, ?, ?);";

        try (Connection conn = getConnection();
             PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

            conn.setAutoCommit(false);
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
            conn.commit();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to sync rule '" + rule.getName() + "': " + e.getMessage(), e);
            try {
                if (getConnection() != null && !getConnection().isClosed()) {
                    getConnection().rollback();
                }
            } catch (SQLException ignored) {}
        } finally {
            try {
                if (getConnection() != null && !getConnection().isClosed()) {
                    getConnection().setAutoCommit(true);
                }
            } catch (SQLException ignored) {}
        }
    }

    public void updateRule(String ruleName, int tierIndex, String type, String duration) {
        String sql = "REPLACE INTO rules (rule_name, tier_index, type, duration) VALUES (?, ?, ?, ?);";
        try (Connection conn = getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
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
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement("DELETE FROM rules WHERE rule_name = ?;")) {
            statement.setString(1, ruleName);
            statement.executeUpdate();
            logger.info("Deleted rule '" + ruleName + "' from DB.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete rule '" + ruleName + "': " + e.getMessage(), e);
        }
    }

    public void syncAllRules(Map<String, PunishmentRule> rules) {
        try (Connection conn = getConnection();
             Statement statement = conn.createStatement()) {
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
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM rules ORDER BY rule_name, tier_index;")) {
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
        String sql = "INSERT INTO punishments (id, player_uuid, player_name, rule, type, duration, staff_name, staff_uuid, date, evidence_link) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (Connection conn = getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, p.getId().toString());
            st.setString(2, p.getPlayerUuid().toString());
            st.setString(3, p.getPlayerName());
            st.setString(4, p.getRule());
            st.setString(5, p.getType());
            st.setString(6, p.getDuration());
            st.setString(7, p.getStaffName());
            st.setString(8, p.getStaffUuid().toString());
            st.setTimestamp(9, new Timestamp(p.getDate().getTime()));
            st.setString(10, null); // evidence_link is initially null
            st.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save punishment: " + e.getMessage(), e);
        }
    }

    /** --- NEW METHODS --- **/

    // Get all punishments for a player
    public List<Punishment> getPunishmentHistory(UUID playerUuid) {
        return fetchPunishments("SELECT * FROM punishments WHERE player_uuid = ? ORDER BY date DESC;", playerUuid.toString());
    }

    // Get punishments for a player filtered by rule
    public List<Punishment> getPunishmentHistoryForRule(UUID playerUuid, String rule) {
        return fetchPunishments("SELECT * FROM punishments WHERE player_uuid = ? AND rule = ? ORDER BY date ASC;", playerUuid.toString(), rule);
    }

    // Helper for executing queries with parameters
    private List<Punishment> fetchPunishments(String sql, Object... params) {
        List<Punishment> punishments = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) st.setObject(i + 1, params[i]);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) punishments.add(createPunishmentFromResultSet(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to fetch punishments", e);
        }
        return punishments;
    }

    // NEW: Update evidence link for a punishment
    public boolean updateEvidenceLink(String punishmentId, String evidenceLink) {
        String sql = "UPDATE punishments SET evidence_link = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, evidenceLink);
            st.setString(2, punishmentId);
            int rowsAffected = st.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update evidence link: " + e.getMessage(), e);
            return false;
        }
    }

    // NEW: Get punishment by ID with evidence link
    public Punishment getPunishmentById(String punishmentId) {
        String sql = "SELECT * FROM punishments WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, punishmentId);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return createPunishmentFromResultSet(rs);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to fetch punishment by ID: " + e.getMessage(), e);
        }
        return null;
    }

    // --- Queued Punishments ---
    public void saveQueuedPunishment(QueuedPunishment p) {
        String sql = "INSERT INTO queued_punishments (id, player_uuid, player_name, rule, type, duration, staff_name, staff_uuid, queued_date, approval_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (Connection conn = getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
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
        try (Connection conn = getConnection();
             PreparedStatement st = conn.prepareStatement("DELETE FROM queued_punishments WHERE approval_id = ?;")) {
            st.setString(1, approvalId);
            st.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to remove queued punishment", e);
        }
    }

    public List<QueuedPunishment> getQueuedPunishments() {
        List<QueuedPunishment> list = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM queued_punishments;")) {
            while (rs.next()) list.add(createQueuedPunishmentFromResultSet(rs));
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to fetch queued punishments", e);
        }
        return list;
    }

    // --- Player History Reset ---
    public boolean resetPlayerHistory(UUID playerUuid) {
        try (Connection conn = getConnection();
             PreparedStatement st1 = conn.prepareStatement("DELETE FROM punishments WHERE player_uuid = ?;");
             PreparedStatement st2 = conn.prepareStatement("DELETE FROM queued_punishments WHERE player_uuid = ?;")) {

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

    // --- Staff Chat Methods ---
    public boolean saveChatMessage(String staffName, String staffUuid, String message) {
        String sql = "INSERT INTO staff_chat (id, staff_name, staff_uuid, message, timestamp) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, UUID.randomUUID().toString());
            st.setString(2, staffName);
            st.setString(3, staffUuid);
            st.setString(4, message);
            st.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            st.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save chat message: " + e.getMessage(), e);
            return false;
        }
    }

    public List<Map<String, Object>> getChatMessages(int limit) {
        List<Map<String, Object>> messages = new ArrayList<>();
        String sql = "SELECT * FROM staff_chat ORDER BY timestamp DESC LIMIT ?";
        try (Connection conn = getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
            st.setInt(1, limit);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> message = new HashMap<>();
                    message.put("id", rs.getString("id"));
                    message.put("staff_name", rs.getString("staff_name"));
                    message.put("staff_uuid", rs.getString("staff_uuid"));
                    message.put("message", rs.getString("message"));
                    message.put("timestamp", rs.getTimestamp("timestamp"));
                    messages.add(message);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to fetch chat messages: " + e.getMessage(), e);
        }
        // Reverse to show oldest first
        Collections.reverse(messages);
        return messages;
    }

    // --- Staff User Methods ---
    public boolean createStaffUser(String username, String password, String uuid, String role) {
        String sql = "INSERT INTO staff_users (id, username, password_hash, uuid, role) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, UUID.randomUUID().toString());
            st.setString(2, username);
            st.setString(3, hashPassword(password));
            st.setString(4, uuid);
            st.setString(5, role);
            st.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create staff user: " + e.getMessage(), e);
            return false;
        }
    }

    public Map<String, Object> authenticateStaffUser(String username, String password) {
        String sql = "SELECT * FROM staff_users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, username);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    if (verifyPassword(password, storedHash)) {
                        Map<String, Object> user = new HashMap<>();
                        user.put("id", rs.getString("id"));
                        user.put("username", rs.getString("username"));
                        user.put("uuid", rs.getString("uuid"));
                        user.put("role", rs.getString("role"));
                        return user;
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to authenticate staff user: " + e.getMessage(), e);
        }
        return null;
    }

    public boolean isStaffUser(String username) {
        String sql = "SELECT 1 FROM staff_users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, username);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to check staff user: " + e.getMessage(), e);
            return false;
        }
    }

    // --- Password Hashing Utilities ---
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] salt = generateSalt();
            md.update(salt);
            byte[] hashedPassword = md.digest(password.getBytes());

            // Combine salt and hash
            byte[] saltedHash = new byte[salt.length + hashedPassword.length];
            System.arraycopy(salt, 0, saltedHash, 0, salt.length);
            System.arraycopy(hashedPassword, 0, saltedHash, salt.length, hashedPassword.length);

            return Base64.getEncoder().encodeToString(saltedHash);
        } catch (NoSuchAlgorithmException e) {
            logger.log(Level.SEVERE, "Failed to hash password", e);
            return null;
        }
    }

    private boolean verifyPassword(String password, String storedHash) {
        try {
            byte[] saltedHash = Base64.getDecoder().decode(storedHash);
            byte[] salt = new byte[16];
            byte[] hash = new byte[saltedHash.length - 16];

            System.arraycopy(saltedHash, 0, salt, 0, 16);
            System.arraycopy(saltedHash, 16, hash, 0, hash.length);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hashedPassword = md.digest(password.getBytes());

            return Arrays.equals(hash, hashedPassword);
        } catch (NoSuchAlgorithmException e) {
            logger.log(Level.SEVERE, "Failed to verify password", e);
            return false;
        }
    }

    private byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return salt;
    }

    public synchronized void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    logger.info("Database connection closed");
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error closing DB connection", e);
            }
        }
    }

    // --- Helpers ---
    public Punishment createPunishmentFromResultSet(ResultSet rs) throws SQLException {
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
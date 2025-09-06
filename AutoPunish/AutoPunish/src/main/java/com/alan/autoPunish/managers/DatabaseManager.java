package com.alan.autoPunish.managers;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.models.Punishment;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
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

        try {
            if (storageType.equalsIgnoreCase("mysql")) {
                setupMysql();
            } else {
                setupSqlite();
            }

            createTables();
            logger.info("Database connection established successfully!");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to connect to database: " + e.getMessage(), e);
        }
    }

    private void setupSqlite() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "SQLite JDBC driver not found", e);
            throw new SQLException("SQLite JDBC driver not found");
        }

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        String url = "jdbc:sqlite:" + new File(dataFolder, "punishments.db").getAbsolutePath();
        connection = DriverManager.getConnection(url);
    }

    private void setupMysql() throws SQLException {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "MySQL JDBC driver not found", e);
            throw new SQLException("MySQL JDBC driver not found");
        }

        String host = configManager.getMysqlConfig().get("host");
        String port = configManager.getMysqlConfig().get("port");
        String database = configManager.getMysqlConfig().get("database");
        String username = configManager.getMysqlConfig().get("username");
        String password = configManager.getMysqlConfig().get("password");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false";
        connection = DriverManager.getConnection(url, username, password);
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
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
        }
    }

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
            logger.info("Saved punishment for player " + punishment.getPlayerName());
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
                    UUID id = UUID.fromString(resultSet.getString("id"));
                    String playerName = resultSet.getString("player_name");
                    String rule = resultSet.getString("rule");
                    String type = resultSet.getString("type");
                    String duration = resultSet.getString("duration");
                    String staffName = resultSet.getString("staff_name");
                    UUID staffUuid = UUID.fromString(resultSet.getString("staff_uuid"));
                    Timestamp date = resultSet.getTimestamp("date");

                    Punishment punishment = new Punishment(id, playerUuid, playerName, rule, type, duration,
                            staffName, staffUuid, date);
                    punishments.add(punishment);
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
                    UUID id = UUID.fromString(resultSet.getString("id"));
                    String playerName = resultSet.getString("player_name");
                    String type = resultSet.getString("type");
                    String duration = resultSet.getString("duration");
                    String staffName = resultSet.getString("staff_name");
                    UUID staffUuid = UUID.fromString(resultSet.getString("staff_uuid"));
                    Timestamp date = resultSet.getTimestamp("date");

                    Punishment punishment = new Punishment(id, playerUuid, playerName, rule, type, duration,
                            staffName, staffUuid, date);
                    punishments.add(punishment);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get punishment history for rule: " + e.getMessage(), e);
        }

        return punishments;
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
}
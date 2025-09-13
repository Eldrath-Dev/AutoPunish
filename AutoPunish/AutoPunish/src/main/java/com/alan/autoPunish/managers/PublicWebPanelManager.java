package com.alan.autoPunish.managers;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.models.Punishment;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.nio.file.Paths; // Import for Paths.get()

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PublicWebPanelManager {
    private final AutoPunish plugin;
    private final Logger logger;
    private final int port;
    private Javalin app;

    public PublicWebPanelManager(AutoPunish plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.port = plugin.getConfig().getInt("public-web-panel.port", 8081);
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("public-web-panel.enabled", true)) {
            logger.info("Public web panel is disabled in config.yml");
            return;
        }

        try {
            logger.info("Starting public web panel on port " + port);

            app = Javalin.create(config -> {
                // *** FIX for Javalin 6.x CORS configuration ***
                config.plugins.enableCors(cors -> {
                    cors.addRule(rule -> {
                        rule.anyHost(); // Allows all hosts
                    });
                });

                config.staticFiles.add(staticFiles -> {
                    // *** FIX for Javalin 6.x: Use Paths.get() ***
                    staticFiles.directory = Paths.get("/public-web");
                    staticFiles.location = io.javalin.http.staticfiles.Location.CLASSPATH;
                });
            });

            setupRoutes();
            app.start(port);

            logger.info("Public web panel started successfully!");
            logger.info("Access your public punishment directory at: http://your-server-ip:" + port);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start public web panel: " + e.getMessage(), e);
        }
    }

    private void setupRoutes() {
        app.get("/", ctx -> ctx.redirect("/index.html"));

        // Consolidated endpoints with optional filtering
        app.get("/api/punishments", ctx -> getPunishments(ctx, null));
        app.get("/api/punishments/warns", ctx -> getPunishments(ctx, "warn"));
        app.get("/api/punishments/mutes", ctx -> getPunishments(ctx, "mute"));
        app.get("/api/punishments/bans", ctx -> getPunishments(ctx, "ban"));
        app.get("/api/punishments/stats", this::getPunishmentStats);

        app.error(404, ctx -> ctx.json(Map.of("error", "Not found")));
    }

    private void getPunishments(Context ctx, String type) {
        try {
            int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
            int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(20);
            String sortBy = ctx.queryParamAsClass("sort", String.class).getOrDefault("date");
            String sortOrder = ctx.queryParamAsClass("order", String.class).getOrDefault("desc");
            String playerFilter = ctx.queryParam("player");
            String ruleFilter = ctx.queryParam("rule");

            List<Punishment> punishments = new ArrayList<>();
            int total = 0;

            // Build SQL dynamically
            StringBuilder sql = new StringBuilder("SELECT * FROM punishments");
            StringBuilder countSql = new StringBuilder("SELECT COUNT(*) AS total FROM punishments");
            List<Object> params = new ArrayList<>();
            List<Object> countParams = new ArrayList<>();

            List<String> conditions = new ArrayList<>();
            if (type != null) {
                conditions.add("type = ?");
                params.add(type);
                countParams.add(type);
            }
            if (playerFilter != null && !playerFilter.isEmpty()) {
                conditions.add("player_name LIKE ?");
                params.add("%" + playerFilter + "%");
                countParams.add("%" + playerFilter + "%");
            }
            if (ruleFilter != null && !ruleFilter.isEmpty()) {
                conditions.add("rule LIKE ?");
                params.add("%" + ruleFilter + "%");
                countParams.add("%" + ruleFilter + "%");
            }

            if (!conditions.isEmpty()) {
                String where = String.join(" AND ", conditions);
                sql.append(" WHERE ").append(where);
                countSql.append(" WHERE ").append(where);
            }

            // Sorting
            sql.append(" ORDER BY ").append(sanitizeSortField(sortBy))
                    .append(sortOrder.equalsIgnoreCase("asc") ? " ASC" : " DESC");

            // Pagination
            sql.append(" LIMIT ? OFFSET ?");
            params.add(size);
            params.add((page - 1) * size);

            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql.toString());
                 PreparedStatement countStmt = connection.prepareStatement(countSql.toString())) {

                for (int i = 0; i < params.size(); i++) stmt.setObject(i + 1, params.get(i));
                for (int i = 0; i < countParams.size(); i++) countStmt.setObject(i + 1, countParams.get(i));

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) punishments.add(plugin.getDatabaseManager().createPunishmentFromResultSet(rs));
                }

                try (ResultSet rsCount = countStmt.executeQuery()) {
                    if (rsCount.next()) total = rsCount.getInt("total");
                }
            }

            ctx.json(Map.of(
                    "punishments", punishments,
                    "total", total,
                    "page", page,
                    "size", size,
                    "sortBy", sortBy,
                    "sortOrder", sortOrder
            ));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading public punishments" + (type != null ? " [" + type + "]" : "") + ": " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to load punishments" + (type != null ? " [" + type + "]" : "") + ": " + e.getMessage()));
        }
    }

    private String sanitizeSortField(String field) {
        switch (field.toLowerCase()) {
            case "player": return "player_name";
            case "rule": return "rule";
            case "type": return "type";
            case "staff": return "staff_name";
            default: return "date";
        }
    }

    private void getPunishmentStats(Context ctx) {
        try {
            String sql = "SELECT type, COUNT(*) AS count FROM punishments GROUP BY type";
            String recentSql = "SELECT COUNT(*) AS recent FROM punishments WHERE date > ?";
            Map<String, Integer> counts = new HashMap<>();
            int recent = 0;

            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) counts.put(rs.getString("type").toLowerCase(), rs.getInt("count"));
            }

            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = connection.prepareStatement(recentSql)) {
                stmt.setTimestamp(1, new Timestamp(System.currentTimeMillis() - 24 * 60 * 60 * 1000));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) recent = rs.getInt("recent");
                }
            }

            ctx.json(Map.of(
                    "totalPunishments", counts.values().stream().mapToInt(i -> i).sum(),
                    "totalWarns", counts.getOrDefault("warn", 0),
                    "totalMutes", counts.getOrDefault("mute", 0),
                    "totalBans", counts.getOrDefault("ban", 0),
                    "recentPunishments", recent,
                    "generatedAt", new java.util.Date()

            ));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading punishment stats: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to load stats: " + e.getMessage()));
        }
    }

    public void stop() {
        if (app != null) {
            logger.info("Stopping public web panel");
            app.stop();
        }
    }
}
package com.alan.autoPunish.managers;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.models.Punishment;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PublicWebPanelManager {
    private final AutoPunish plugin;
    private final Logger logger;
    private final int port;
    private Javalin app;

    // Simple session storage (in production, use proper session management)
    private final Map<String, Map<String, Object>> sessions = new HashMap<>();

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
                // ✅ FIXED: CORS configuration for Javalin 6.x
                config.registerPlugin(new io.javalin.plugin.bundled.CorsPlugin(cors -> {
                    cors.addRule(it -> it.anyHost());
                }));

                // ✅ FIXED: Static files configuration
                config.staticFiles.add(staticFiles -> {
                    staticFiles.hostedPath = "/";
                    staticFiles.directory = "/public-web";
                    staticFiles.location = Location.CLASSPATH;
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

        // Public endpoints
        app.get("/api/punishments", ctx -> getPunishments(ctx, null));
        app.get("/api/punishments/warns", ctx -> getPunishments(ctx, "warn"));
        app.get("/api/punishments/mutes", ctx -> getPunishments(ctx, "mute"));
        app.get("/api/punishments/bans", ctx -> getPunishments(ctx, "ban"));
        app.get("/api/punishments/stats", this::getPunishmentStats);

        // NEW: Get specific punishment (with evidence link)
        app.get("/api/punishments/{id}", this::getPunishmentById);

        // NEW: Evidence link endpoints
        app.put("/api/punishments/{id}/evidence", this::updateEvidenceLink);

        // NEW: Staff chat endpoints
        app.get("/api/staff/chat", this::getChatMessages);
        app.post("/api/staff/chat", this::postChatMessage);

        // NEW: Authentication endpoints
        app.post("/api/auth/login", this::login);
        app.post("/api/auth/logout", this::logout);
        app.get("/api/auth/session", this::getSessionStatus);

        // NEW: Team management endpoints
        app.post("/api/staff/users", this::createStaffUser);
        app.get("/api/staff/users", this::getAllStaffUsers);
        app.delete("/api/staff/users/{username}", this::deleteStaffUser);

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

            List<Map<String, Object>> punishments = new ArrayList<>();
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

            // Using try-with-resources for PreparedStatement and ResultSet
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql.toString());
                 PreparedStatement countStmt = connection.prepareStatement(countSql.toString())) {

                for (int i = 0; i < params.size(); i++) stmt.setObject(i + 1, params.get(i));
                for (int i = 0; i < countParams.size(); i++) countStmt.setObject(i + 1, countParams.get(i));

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> punishment = new HashMap<>();
                        punishment.put("id", rs.getString("id"));
                        punishment.put("player_uuid", rs.getString("player_uuid"));
                        punishment.put("player_name", rs.getString("player_name"));
                        punishment.put("rule", rs.getString("rule"));
                        punishment.put("type", rs.getString("type"));
                        punishment.put("duration", rs.getString("duration"));
                        punishment.put("staff_name", rs.getString("staff_name"));
                        punishment.put("staff_uuid", rs.getString("staff_uuid"));
                        punishment.put("date", rs.getTimestamp("date"));
                        punishment.put("evidence_link", rs.getString("evidence_link"));
                        punishments.add(punishment);
                    }
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

    // NEW: Get specific punishment by ID
    private void getPunishmentById(Context ctx) {
        try {
            String id = ctx.pathParam("id");

            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = connection.prepareStatement("SELECT * FROM punishments WHERE id = ?")) {

                stmt.setString(1, id);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> punishment = new HashMap<>();
                        punishment.put("id", rs.getString("id"));
                        punishment.put("player_uuid", rs.getString("player_uuid"));
                        punishment.put("player_name", rs.getString("player_name"));
                        punishment.put("rule", rs.getString("rule"));
                        punishment.put("type", rs.getString("type"));
                        punishment.put("duration", rs.getString("duration"));
                        punishment.put("staff_name", rs.getString("staff_name"));
                        punishment.put("staff_uuid", rs.getString("staff_uuid"));
                        punishment.put("date", rs.getTimestamp("date"));
                        punishment.put("evidence_link", rs.getString("evidence_link"));

                        ctx.json(punishment);
                    } else {
                        ctx.status(404);
                        ctx.json(Map.of("error", "Punishment not found"));
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading punishment by ID: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to load punishment: " + e.getMessage()));
        }
    }

    // NEW: Update evidence link for a punishment
    private void updateEvidenceLink(Context ctx) {
        try {
            // Check authentication
            if (!isAuthenticated(ctx)) {
                ctx.status(401);
                ctx.json(Map.of("error", "Unauthorized"));
                return;
            }

            String id = ctx.pathParam("id");
            Map<String, Object> requestBody = ctx.bodyAsClass(Map.class);
            String evidenceLink = (String) requestBody.get("evidence_link");

            if (evidenceLink == null || evidenceLink.trim().isEmpty()) {
                ctx.status(400);
                ctx.json(Map.of("error", "Evidence link is required"));
                return;
            }

            boolean success = plugin.getDatabaseManager().updateEvidenceLink(id, evidenceLink);

            if (success) {
                ctx.json(Map.of("success", true, "message", "Evidence link updated successfully"));
            } else {
                ctx.status(500);
                ctx.json(Map.of("error", "Failed to update evidence link"));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error updating evidence link: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to update evidence link: " + e.getMessage()));
        }
    }

    // NEW: Get chat messages
    private void getChatMessages(Context ctx) {
        try {
            // Check authentication
            if (!isAuthenticated(ctx)) {
                ctx.status(401);
                ctx.json(Map.of("error", "Unauthorized"));
                return;
            }

            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);
            List<Map<String, Object>> messages = plugin.getDatabaseManager().getChatMessages(limit);
            ctx.json(Map.of("messages", messages));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading chat messages: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to load chat messages: " + e.getMessage()));
        }
    }

    // NEW: Post chat message
    private void postChatMessage(Context ctx) {
        try {
            // Check authentication
            if (!isAuthenticated(ctx)) {
                ctx.status(401);
                ctx.json(Map.of("error", "Unauthorized"));
                return;
            }

            Map<String, Object> requestBody = ctx.bodyAsClass(Map.class);
            String message = (String) requestBody.get("message");

            if (message == null || message.trim().isEmpty()) {
                ctx.status(400);
                ctx.json(Map.of("error", "Message is required"));
                return;
            }

            // Get staff info from session
            String sessionId = getSessionId(ctx);
            Map<String, Object> session = sessions.get(sessionId);
            String staffName = (String) session.get("username");
            String staffUuid = (String) session.get("uuid");

            boolean success = plugin.getDatabaseManager().saveChatMessage(staffName, staffUuid, message);

            if (success) {
                ctx.json(Map.of("success", true, "message", "Message sent successfully"));
            } else {
                ctx.status(500);
                ctx.json(Map.of("error", "Failed to send message"));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending chat message: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to send message: " + e.getMessage()));
        }
    }

    // NEW: Login endpoint
    private void login(Context ctx) {
        try {
            Map<String, Object> requestBody = ctx.bodyAsClass(Map.class);
            String username = (String) requestBody.get("username");
            String password = (String) requestBody.get("password");

            if (username == null || password == null) {
                ctx.status(400);
                ctx.json(Map.of("error", "Username and password are required"));
                return;
            }

            Map<String, Object> user = plugin.getDatabaseManager().authenticateStaffUser(username, password);

            if (user != null) {
                // Create session
                String sessionId = UUID.randomUUID().toString();
                sessions.put(sessionId, user);

                // Set cookie
                ctx.cookie("session_id", sessionId, 3600); // 1 hour

                ctx.json(Map.of(
                        "success", true,
                        "message", "Login successful",
                        "user", Map.of(
                                "username", user.get("username"),
                                "role", user.get("role")
                        )
                ));
            } else {
                ctx.status(401);
                ctx.json(Map.of("error", "Invalid username or password"));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during login: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }

    // NEW: Logout endpoint
    private void logout(Context ctx) {
        try {
            String sessionId = getSessionId(ctx);
            if (sessionId != null) {
                sessions.remove(sessionId);
                ctx.removeCookie("session_id");
            }
            ctx.json(Map.of("success", true, "message", "Logged out successfully"));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during logout: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Logout failed: " + e.getMessage()));
        }
    }

    // NEW: Get session status
    private void getSessionStatus(Context ctx) {
        try {
            String sessionId = getSessionId(ctx);
            if (sessionId != null && sessions.containsKey(sessionId)) {
                Map<String, Object> session = sessions.get(sessionId);
                ctx.json(Map.of(
                        "authenticated", true,
                        "user", Map.of(
                                "username", session.get("username"),
                                "role", session.get("role")
                        )
                ));
            } else {
                ctx.json(Map.of("authenticated", false));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error checking session status: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to check session status: " + e.getMessage()));
        }
    }

    // NEW: Create staff user (team management)
    private void createStaffUser(Context ctx) {
        try {
            // Check authentication
            if (!isAuthenticated(ctx)) {
                ctx.status(401);
                ctx.json(Map.of("error", "Unauthorized"));
                return;
            }

            Map<String, Object> requestBody = ctx.bodyAsClass(Map.class);
            String username = (String) requestBody.get("username");
            String password = (String) requestBody.get("password");
            String role = (String) requestBody.get("role");

            if (username == null || password == null) {
                ctx.status(400);
                ctx.json(Map.of("error", "Username and password are required"));
                return;
            }

            // Check if user already exists
            if (plugin.getDatabaseManager().isStaffUser(username)) {
                ctx.status(409);
                ctx.json(Map.of("error", "Username already exists"));
                return;
            }

            boolean success = plugin.getDatabaseManager().createStaffUser(username, password, "console", role != null ? role : "staff");

            if (success) {
                ctx.json(Map.of("success", true, "message", "Staff user created successfully"));
            } else {
                ctx.status(500);
                ctx.json(Map.of("error", "Failed to create staff user"));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error creating staff user: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to create staff user: " + e.getMessage()));
        }
    }

    // NEW: Get all staff users
    private void getAllStaffUsers(Context ctx) {
        try {
            // Check authentication
            if (!isAuthenticated(ctx)) {
                ctx.status(401);
                ctx.json(Map.of("error", "Unauthorized"));
                return;
            }

            List<Map<String, Object>> users = plugin.getDatabaseManager().getAllStaffUsers();
            ctx.json(Map.of("users", users));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error fetching staff users: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to fetch staff users: " + e.getMessage()));
        }
    }

    // NEW: Delete staff user
    private void deleteStaffUser(Context ctx) {
        try {
            // Check authentication
            if (!isAuthenticated(ctx)) {
                ctx.status(401);
                ctx.json(Map.of("error", "Unauthorized"));
                return;
            }

            String username = ctx.pathParam("username");

            // Prevent deleting yourself
            String sessionId = getSessionId(ctx);
            Map<String, Object> session = sessions.get(sessionId);
            if (session != null && username.equals(session.get("username"))) {
                ctx.status(400);
                ctx.json(Map.of("error", "You cannot delete your own account"));
                return;
            }

            boolean success = plugin.getDatabaseManager().deleteStaffUser(username);

            if (success) {
                ctx.json(Map.of("success", true, "message", "Staff user deleted successfully"));
            } else {
                ctx.status(404);
                ctx.json(Map.of("error", "Staff user not found"));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error deleting staff user: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to delete staff user: " + e.getMessage()));
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

    // Helper methods for authentication
    private boolean isAuthenticated(Context ctx) {
        String sessionId = getSessionId(ctx);
        return sessionId != null && sessions.containsKey(sessionId);
    }

    private String getSessionId(Context ctx) {
        return ctx.cookie("session_id");
    }

    public void stop() {
        if (app != null) {
            logger.info("Stopping public web panel");
            app.stop();
        }
    }
}
package com.alan.autoPunish.managers;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.models.Punishment;
import com.alan.autoPunish.models.QueuedPunishment;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.plugin.bundled.CorsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebPanelManager {
    private final AutoPunish plugin;
    private final Logger logger;
    private final int port;
    private Javalin app;
    private final ObjectMapper objectMapper;

    // Default username and password - should be configurable in config.yml
    private final String adminUsername;
    private final String adminPassword;

    public WebPanelManager(AutoPunish plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.port = plugin.getConfig().getInt("web-panel.port", 8080);
        this.adminUsername = plugin.getConfig().getString("web-panel.username", "admin");
        this.adminPassword = plugin.getConfig().getString("web-panel.password", "password");
        this.objectMapper = new ObjectMapper();
    }

    public void start() {
        try {
            logger.info("Starting web panel on port " + port);

            // Create and configure the Javalin app
            app = Javalin.create(config -> {
                // Enable CORS for local development
                config.plugins.enableCors(corsContainer -> {
                    corsContainer.add(it -> it.anyHost());
                });

                // Serve static files from resources/web folder
                config.staticFiles.add(staticFiles -> {
                    staticFiles.directory = "/web";
                    staticFiles.location = io.javalin.http.staticfiles.Location.CLASSPATH;
                });
            });

            // Set up API endpoints with authentication
            setupRoutes();

            // Start the server
            app.start(port);
            logger.info("Web panel started successfully!");
            logger.info("Access your web panel at: http://your-server-ip:" + port);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start web panel: " + e.getMessage(), e);
        }
    }

    private void setupRoutes() {
        // Home page redirects to index.html
        app.get("/", ctx -> ctx.redirect("/index.html"));

        // Authentication
        app.post("/api/login", this::handleLogin);

        // API endpoints - all require authentication
        app.before("/api/*", this::authenticate);

        // Punishments API
        app.get("/api/punishments", this::getPunishments);
        app.get("/api/punishments/{uuid}", this::getPunishmentsForPlayer);

        // Approval API
        app.get("/api/approvals", this::getPendingApprovals);
        app.post("/api/approvals/{id}/approve", this::approveQueuedPunishment);
        app.post("/api/approvals/{id}/deny", this::denyQueuedPunishment);

        // Player API
        app.get("/api/players", this::getPlayers);
        app.get("/api/players/{uuid}/severity", this::getPlayerSeverity);

        // Handle 404s
        app.error(404, ctx -> {
            ctx.json(Map.of("error", "Not found"));
        });
    }

    private void authenticate(Context ctx) {
        // Skip authentication for login endpoint
        if (ctx.path().equals("/api/login")) {
            return;
        }

        // Get the Authorization header
        String auth = ctx.header("Authorization");

        // Check if it's valid
        if (auth == null || !auth.startsWith("Bearer ")) {
            ctx.status(401);
            ctx.json(Map.of("error", "Unauthorized"));
            return; // Use return instead of halt()
        }

        // Validate the token (simple implementation - should use JWT in production)
        String token = auth.substring(7);
        if (!isValidToken(token)) {
            ctx.status(401);
            ctx.json(Map.of("error", "Invalid token"));
            return; // Use return instead of halt()
        }
    }

    private boolean isValidToken(String token) {
        // Simple token validation - in a real implementation, use JWT
        // For this example, we'll use a very simple token: username_timestamp
        // where timestamp is when the token was issued
        // A real implementation would use a proper JWT library

        return token.equals("autoPunishToken_" + adminUsername);
    }

    private void handleLogin(Context ctx) {
        try {
            Map<String, String> credentials = objectMapper.readValue(ctx.body(), Map.class);
            String username = credentials.get("username");
            String password = credentials.get("password");

            if (adminUsername.equals(username) && adminPassword.equals(password)) {
                // Success - return a token
                ctx.json(Map.of(
                        "success", true,
                        "token", "autoPunishToken_" + username,
                        "username", username
                ));
            } else {
                // Failed login
                ctx.status(401);
                ctx.json(Map.of("error", "Invalid credentials"));
            }
        } catch (Exception e) {
            ctx.status(400);
            ctx.json(Map.of("error", "Invalid request"));
        }
    }

    private void getPunishments(Context ctx) {
        // Get all punishments (paginated)
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(20);

        try {
            // Get all punishments from all players
            List<Punishment> allPunishments = new ArrayList<>();

            // Get punishments for all online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                allPunishments.addAll(plugin.getDatabaseManager().getPunishmentHistory(player.getUniqueId()));
            }

            // Sort by date (newest first)
            allPunishments.sort((p1, p2) -> p2.getDate().compareTo(p1.getDate()));

            // Apply pagination
            int start = (page - 1) * size;
            int end = Math.min(start + size, allPunishments.size());
            List<Punishment> paginatedPunishments = start < allPunishments.size() ?
                    allPunishments.subList(start, end) :
                    new ArrayList<>();

            ctx.json(Map.of(
                    "punishments", paginatedPunishments,
                    "total", allPunishments.size(),
                    "page", page,
                    "size", size
            ));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading punishments: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to load punishments: " + e.getMessage()));
        }
    }

    private void getPunishmentsForPlayer(Context ctx) {
        String uuidStr = ctx.pathParam("uuid");
        try {
            UUID playerUuid = UUID.fromString(uuidStr);
            List<Punishment> punishments = plugin.getDatabaseManager().getPunishmentHistory(playerUuid);
            ctx.json(punishments);
        } catch (IllegalArgumentException e) {
            ctx.status(400);
            ctx.json(Map.of("error", "Invalid UUID format"));
        }
    }

    private void getPendingApprovals(Context ctx) {
        List<QueuedPunishment> pendingApprovals = plugin.getPunishmentQueueManager().getQueuedPunishments();
        ctx.json(pendingApprovals);
    }

    private void approveQueuedPunishment(Context ctx) {
        String approvalId = ctx.pathParam("id");
        String adminName = ctx.queryParam("adminName");
        if (adminName == null) adminName = "WebAdmin";

        try {
            // Process the approval directly
            ConsoleCommandSender consoleSender = Bukkit.getConsoleSender();
            boolean success = plugin.getPunishmentQueueManager().processApproval(approvalId, true, consoleSender);

            if (success) {
                logger.info("Web panel: Punishment " + approvalId + " approved successfully by " + adminName);
                ctx.json(Map.of("success", true, "message", "Punishment approved successfully"));
            } else {
                logger.warning("Web panel: Failed to approve punishment " + approvalId);
                ctx.status(404);
                ctx.json(Map.of("success", false, "error", "Punishment not found or already processed"));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error approving punishment: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("success", false, "error", "Server error: " + e.getMessage()));
        }
    }

    private void denyQueuedPunishment(Context ctx) {
        String approvalId = ctx.pathParam("id");
        String adminName = ctx.queryParam("adminName");
        if (adminName == null) adminName = "WebAdmin";

        try {
            // Process the denial directly
            ConsoleCommandSender consoleSender = Bukkit.getConsoleSender();
            boolean success = plugin.getPunishmentQueueManager().processApproval(approvalId, false, consoleSender);

            if (success) {
                logger.info("Web panel: Punishment " + approvalId + " denied successfully by " + adminName);
                ctx.json(Map.of("success", true, "message", "Punishment denied successfully"));
            } else {
                logger.warning("Web panel: Failed to deny punishment " + approvalId);
                ctx.status(404);
                ctx.json(Map.of("success", false, "error", "Punishment not found or already processed"));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error denying punishment: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("success", false, "error", "Server error: " + e.getMessage()));
        }
    }

    private void getPlayers(Context ctx) {
        // In a real implementation, you would return a list of players from your database
        // For now, we'll just return online players
        List<Map<String, Object>> players = new java.util.ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<String, Object> playerData = new HashMap<>();
            playerData.put("uuid", player.getUniqueId().toString());
            playerData.put("name", player.getName());
            playerData.put("online", true);
            players.add(playerData);
        }

        ctx.json(Map.of("players", players));
    }

    private void getPlayerSeverity(Context ctx) {
        String uuidStr = ctx.pathParam("uuid");
        try {
            UUID playerUuid = UUID.fromString(uuidStr);
            int severityScore = plugin.getPunishmentManager().calculateSeverityScore(
                    plugin.getDatabaseManager().getPunishmentHistory(playerUuid)
            );
            int tier = plugin.getPunishmentManager().determineGlobalTier(severityScore);

            ctx.json(Map.of(
                    "uuid", uuidStr,
                    "severityScore", severityScore,
                    "tier", tier
            ));
        } catch (IllegalArgumentException e) {
            ctx.status(400);
            ctx.json(Map.of("error", "Invalid UUID format"));
        }
    }

    public void stop() {
        if (app != null) {
            logger.info("Stopping web panel");
            app.stop();
        }
    }
}
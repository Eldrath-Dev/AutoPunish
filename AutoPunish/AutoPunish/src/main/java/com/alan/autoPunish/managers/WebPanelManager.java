package com.alan.autoPunish.managers;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.models.Punishment;
import com.alan.autoPunish.models.QueuedPunishment;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebPanelManager {
    private final AutoPunish plugin;
    private final Logger logger;
    private final int port;
    private Javalin app;
    private final ObjectMapper objectMapper;

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

            app = Javalin.create(config -> {
                config.plugins.enableCors(corsContainer -> corsContainer.add(it -> it.anyHost()));

                config.staticFiles.add(staticFiles -> {
                    staticFiles.directory = "/web";
                    staticFiles.location = io.javalin.http.staticfiles.Location.CLASSPATH;
                });
            });

            setupRoutes();
            app.start(port);
            logger.info("Web panel started successfully!");
            logger.info("Access your web panel at: http://your-server-ip:" + port);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start web panel: " + e.getMessage(), e);
        }
    }

    private void setupRoutes() {
        app.get("/", ctx -> ctx.redirect("/index.html"));
        app.post("/api/login", this::handleLogin);

        app.before("/api/*", this::authenticate);

        app.get("/api/punishments", this::getPunishments);
        app.get("/api/punishments/{uuid}", this::getPunishmentsForPlayer);

        app.get("/api/approvals", this::getPendingApprovals);
        app.post("/api/approvals/{id}/approve", this::approveQueuedPunishment);
        app.post("/api/approvals/{id}/deny", this::denyQueuedPunishment);

        app.get("/api/players", this::getPlayers);
        app.get("/api/players/{uuid}/severity", this::getPlayerSeverity);

        app.error(404, ctx -> ctx.json(Map.of("error", "Not found")));
    }

    private void authenticate(Context ctx) {
        if (ctx.path().equals("/api/login")) return;

        String auth = ctx.header("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            ctx.status(401).json(Map.of("error", "Unauthorized"));
            return;
        }

        String token = auth.substring(7);
        if (!isValidToken(token)) {
            ctx.status(401).json(Map.of("error", "Invalid token"));
        }
    }

    private boolean isValidToken(String token) {
        return token.equals("autoPunishToken_" + adminUsername);
    }

    private void handleLogin(Context ctx) {
        try {
            Map<String, String> credentials = objectMapper.readValue(ctx.body(), Map.class);
            String username = credentials.get("username");
            String password = credentials.get("password");

            if (adminUsername.equals(username) && adminPassword.equals(password)) {
                ctx.json(Map.of(
                        "success", true,
                        "token", "autoPunishToken_" + username,
                        "username", username
                ));
            } else {
                ctx.status(401).json(Map.of("error", "Invalid credentials"));
            }
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "Invalid request"));
        }
    }

    private void getPunishments(Context ctx) {
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(20);

        try {
            List<Punishment> allPunishments = new ArrayList<>();

            for (Player player : Bukkit.getOnlinePlayers()) {
                allPunishments.addAll(plugin.getDatabaseManager().getPunishmentHistory(player.getUniqueId()));
            }

            allPunishments.sort((p1, p2) -> p2.getDate().compareTo(p1.getDate()));

            int start = (page - 1) * size;
            int end = Math.min(start + size, allPunishments.size());
            List<Punishment> paginated = start < allPunishments.size() ?
                    allPunishments.subList(start, end) :
                    new ArrayList<>();

            ctx.json(Map.of(
                    "punishments", paginated,
                    "total", allPunishments.size(),
                    "page", page,
                    "size", size
            ));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading punishments: " + e.getMessage(), e);
            ctx.status(500).json(Map.of("error", "Failed to load punishments"));
        }
    }

    private void getPunishmentsForPlayer(Context ctx) {
        String uuidStr = ctx.pathParam("uuid");
        try {
            UUID uuid = UUID.fromString(uuidStr);
            List<Punishment> punishments = plugin.getDatabaseManager().getPunishmentHistory(uuid);
            ctx.json(punishments);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", "Invalid UUID format"));
        }
    }

    private void getPendingApprovals(Context ctx) {
        List<QueuedPunishment> pending = plugin.getPunishmentQueueManager().getQueuedPunishments();
        ctx.json(pending);
    }

    private void approveQueuedPunishment(Context ctx) {
        String approvalId = ctx.pathParam("id");
        String adminName = ctx.queryParam("adminName");
        if (adminName == null) adminName = "WebAdmin";

        logger.info("Web panel approve request received for ID: " + approvalId + " by " + adminName);

        String finalAdminName = adminName;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                ConsoleCommandSender console = Bukkit.getConsoleSender();
                boolean success = plugin.getPunishmentQueueManager().processApproval(approvalId, true, console);

                if (success) {
                    logger.info("Punishment " + approvalId + " approved successfully by " + finalAdminName);
                    ctx.json(Map.of("success", true, "message", "Punishment approved successfully"));
                } else {
                    ctx.status(404).json(Map.of("success", false, "error", "Punishment not found or already processed"));
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error approving punishment: " + e.getMessage(), e);
                ctx.status(500).json(Map.of("success", false, "error", "Server error: " + e.getMessage()));
            }
        });
    }

    private void denyQueuedPunishment(Context ctx) {
        String approvalId = ctx.pathParam("id");
        String adminName = ctx.queryParam("adminName");
        if (adminName == null) adminName = "WebAdmin";

        logger.info("Web panel deny request received for ID: " + approvalId + " by " + adminName);

        String finalAdminName = adminName;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                ConsoleCommandSender console = Bukkit.getConsoleSender();
                boolean success = plugin.getPunishmentQueueManager().processApproval(approvalId, false, console);

                if (success) {
                    logger.info("Punishment " + approvalId + " denied successfully by " + finalAdminName);
                    ctx.json(Map.of("success", true, "message", "Punishment denied successfully"));
                } else {
                    ctx.status(404).json(Map.of("success", false, "error", "Punishment not found or already processed"));
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error denying punishment: " + e.getMessage(), e);
                ctx.status(500).json(Map.of("success", false, "error", "Server error: " + e.getMessage()));
            }
        });
    }

    private void getPlayers(Context ctx) {
        List<Map<String, Object>> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<String, Object> data = new HashMap<>();
            data.put("uuid", player.getUniqueId().toString());
            data.put("name", player.getName());
            data.put("online", true);
            players.add(data);
        }
        ctx.json(Map.of("players", players));
    }

    private void getPlayerSeverity(Context ctx) {
        String uuidStr = ctx.pathParam("uuid");
        try {
            UUID uuid = UUID.fromString(uuidStr);
            int severity = plugin.getPunishmentManager().calculateSeverityScore(
                    plugin.getDatabaseManager().getPunishmentHistory(uuid)
            );
            int tier = plugin.getPunishmentManager().determineGlobalTier(severity);

            ctx.json(Map.of(
                    "uuid", uuidStr,
                    "severityScore", severity,
                    "tier", tier
            ));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", "Invalid UUID format"));
        }
    }

    public void stop() {
        if (app != null) {
            logger.info("Stopping web panel");
            app.stop();
        }
    }
}

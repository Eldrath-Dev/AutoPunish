package com.alan.autoPunish.managers;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.models.Punishment;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PublicWebPanelManager {
    private final AutoPunish plugin;
    private final Logger logger;
    private final int port;
    private Javalin app;
    private final ObjectMapper objectMapper;

    public PublicWebPanelManager(AutoPunish plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.port = plugin.getConfig().getInt("public-web-panel.port", 8081);
        this.objectMapper = new ObjectMapper();
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("public-web-panel.enabled", true)) {
            logger.info("Public web panel is disabled in config.yml");
            return;
        }

        try {
            logger.info("Starting public web panel on port " + port);

            // Create and configure the Javalin app
            app = Javalin.create(config -> {
                // Enable CORS for public access
                config.plugins.enableCors(corsContainer -> {
                    corsContainer.add(it -> it.anyHost());
                });

                // Serve static files from resources/public-web folder
                config.staticFiles.add(staticFiles -> {
                    staticFiles.directory = "/public-web";
                    staticFiles.location = io.javalin.http.staticfiles.Location.CLASSPATH;
                });
            });

            // Set up public API endpoints (no authentication required)
            setupRoutes();

            // Start the server
            app.start(port);
            logger.info("Public web panel started successfully!");
            logger.info("Access your public punishment directory at: http://your-server-ip:" + port);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start public web panel: " + e.getMessage(), e);
        }
    }

    private void setupRoutes() {
        // Home page redirects to index.html
        app.get("/", ctx -> ctx.redirect("/index.html"));

        // Public API endpoints - no authentication required
        app.get("/api/punishments", this::getPublicPunishments);
        app.get("/api/punishments/warns", this::getPublicWarns);
        app.get("/api/punishments/mutes", this::getPublicMutes);
        app.get("/api/punishments/bans", this::getPublicBans);
        app.get("/api/punishments/stats", this::getPunishmentStats);

        // Handle 404s
        app.error(404, ctx -> {
            ctx.json(Map.of("error", "Not found"));
        });
    }

    /**
     * Get all public punishments (paginated)
     */
    private void getPublicPunishments(Context ctx) {
        try {
            int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
            int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(20);
            String sortBy = ctx.queryParamAsClass("sort", String.class).getOrDefault("date");
            String sortOrder = ctx.queryParamAsClass("order", String.class).getOrDefault("desc");

            // Get all punishments from database
            List<Punishment> allPunishments = plugin.getDatabaseManager().getAllPunishments();

            // Sort punishments
            sortPunishments(allPunishments, sortBy, sortOrder);

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
                    "size", size,
                    "sortBy", sortBy,
                    "sortOrder", sortOrder
            ));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading public punishments: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to load punishments: " + e.getMessage()));
        }
    }

    /**
     * Get all public warns
     */
    private void getPublicWarns(Context ctx) {
        try {
            int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
            int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(20);
            String sortBy = ctx.queryParamAsClass("sort", String.class).getOrDefault("date");
            String sortOrder = ctx.queryParamAsClass("order", String.class).getOrDefault("desc");

            // Get all warns from database
            List<Punishment> allWarns = plugin.getDatabaseManager().getPunishmentsByType("warn");

            // Sort punishments
            sortPunishments(allWarns, sortBy, sortOrder);

            // Apply pagination
            int start = (page - 1) * size;
            int end = Math.min(start + size, allWarns.size());
            List<Punishment> paginatedWarns = start < allWarns.size() ?
                    allWarns.subList(start, end) :
                    new ArrayList<>();

            ctx.json(Map.of(
                    "punishments", paginatedWarns,
                    "total", allWarns.size(),
                    "page", page,
                    "size", size,
                    "sortBy", sortBy,
                    "sortOrder", sortOrder
            ));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading public warns: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to load warns: " + e.getMessage()));
        }
    }

    /**
     * Get all public mutes
     */
    private void getPublicMutes(Context ctx) {
        try {
            int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
            int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(20);
            String sortBy = ctx.queryParamAsClass("sort", String.class).getOrDefault("date");
            String sortOrder = ctx.queryParamAsClass("order", String.class).getOrDefault("desc");

            // Get all mutes from database
            List<Punishment> allMutes = plugin.getDatabaseManager().getPunishmentsByType("mute");

            // Sort punishments
            sortPunishments(allMutes, sortBy, sortOrder);

            // Apply pagination
            int start = (page - 1) * size;
            int end = Math.min(start + size, allMutes.size());
            List<Punishment> paginatedMutes = start < allMutes.size() ?
                    allMutes.subList(start, end) :
                    new ArrayList<>();

            ctx.json(Map.of(
                    "punishments", paginatedMutes,
                    "total", allMutes.size(),
                    "page", page,
                    "size", size,
                    "sortBy", sortBy,
                    "sortOrder", sortOrder
            ));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading public mutes: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to load mutes: " + e.getMessage()));
        }
    }

    /**
     * Get all public bans
     */
    private void getPublicBans(Context ctx) {
        try {
            int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
            int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(20);
            String sortBy = ctx.queryParamAsClass("sort", String.class).getOrDefault("date");
            String sortOrder = ctx.queryParamAsClass("order", String.class).getOrDefault("desc");

            // Get all bans from database
            List<Punishment> allBans = plugin.getDatabaseManager().getPunishmentsByType("ban");

            // Sort punishments
            sortPunishments(allBans, sortBy, sortOrder);

            // Apply pagination
            int start = (page - 1) * size;
            int end = Math.min(start + size, allBans.size());
            List<Punishment> paginatedBans = start < allBans.size() ?
                    allBans.subList(start, end) :
                    new ArrayList<>();

            ctx.json(Map.of(
                    "punishments", paginatedBans,
                    "total", allBans.size(),
                    "page", page,
                    "size", size,
                    "sortBy", sortBy,
                    "sortOrder", sortOrder
            ));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading public bans: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to load bans: " + e.getMessage()));
        }
    }

    /**
     * Get punishment statistics
     */
    private void getPunishmentStats(Context ctx) {
        try {
            // Get all punishments from database
            List<Punishment> allPunishments = plugin.getDatabaseManager().getAllPunishments();

            // Calculate statistics
            int totalPunishments = allPunishments.size();
            int totalWarns = 0;
            int totalMutes = 0;
            int totalBans = 0;

            // Count by type
            for (Punishment punishment : allPunishments) {
                switch (punishment.getType().toLowerCase()) {
                    case "warn":
                        totalWarns++;
                        break;
                    case "mute":
                        totalMutes++;
                        break;
                    case "ban":
                        totalBans++;
                        break;
                }
            }

            // Get recent punishments (last 24 hours)
            long twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
            int recentPunishments = 0;
            for (Punishment punishment : allPunishments) {
                if (punishment.getDate().getTime() > twentyFourHoursAgo) {
                    recentPunishments++;
                }
            }

            ctx.json(Map.of(
                    "totalPunishments", totalPunishments,
                    "totalWarns", totalWarns,
                    "totalMutes", totalMutes,
                    "totalBans", totalBans,
                    "recentPunishments", recentPunishments,
                    "generatedAt", new Date()
            ));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading punishment stats: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to load stats: " + e.getMessage()));
        }
    }

    /**
     * Sort punishments by field and order
     */
    private void sortPunishments(List<Punishment> punishments, String sortBy, String sortOrder) {
        boolean ascending = sortOrder.equalsIgnoreCase("asc");

        switch (sortBy.toLowerCase()) {
            case "player":
                punishments.sort((p1, p2) -> {
                    int result = p1.getPlayerName().compareToIgnoreCase(p2.getPlayerName());
                    return ascending ? result : -result;
                });
                break;
            case "rule":
                punishments.sort((p1, p2) -> {
                    int result = p1.getRule().compareToIgnoreCase(p2.getRule());
                    return ascending ? result : -result;
                });
                break;
            case "type":
                punishments.sort((p1, p2) -> {
                    int result = p1.getType().compareToIgnoreCase(p2.getType());
                    return ascending ? result : -result;
                });
                break;
            case "staff":
                punishments.sort((p1, p2) -> {
                    int result = p1.getStaffName().compareToIgnoreCase(p2.getStaffName());
                    return ascending ? result : -result;
                });
                break;
            case "date":
            default:
                punishments.sort((p1, p2) -> {
                    int result = p1.getDate().compareTo(p2.getDate());
                    return ascending ? result : -result;
                });
                break;
        }
    }

    public void stop() {
        if (app != null) {
            logger.info("Stopping public web panel");
            app.stop();
        }
    }
}
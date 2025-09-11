package com.alan.autoPunish;

import com.alan.autoPunish.api.AutoPunishAPI;
import com.alan.autoPunish.commands.*;
import com.alan.autoPunish.listeners.ChatListener;
import com.alan.autoPunish.managers.*;
import com.alan.autoPunish.utils.ConfigUtils;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public class AutoPunish extends JavaPlugin {
    private static AutoPunish instance;

    private Logger logger;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private WebhookManager webhookManager;
    private PunishmentQueueManager punishmentQueueManager;
    private PunishmentManager punishmentManager;
    private WebPanelManager webPanelManager;
    private PublicWebPanelManager publicWebPanelManager;

    @Override
    public void onEnable() {
        instance = this;
        this.logger = getLogger();

        // Load default config
        saveDefaultConfig();

        // Initialize ConfigUtils
        ConfigUtils.init(this);
        logger.info("Config utilities initialized successfully!");

        // Initialize ConfigManager first
        this.configManager = new ConfigManager(this);

        // Initialize DatabaseManager
        this.databaseManager = new DatabaseManager(this, configManager);

        // Initialize WebhookManager
        this.webhookManager = new WebhookManager(this, configManager);

        // Initialize PunishmentQueueManager and load queued punishments
        this.punishmentQueueManager = new PunishmentQueueManager(this);
        this.punishmentQueueManager.loadQueuedPunishments();

        // Initialize PunishmentManager
        this.punishmentManager = new PunishmentManager(this, configManager, databaseManager, webhookManager);

        // Initialize API
        AutoPunishAPI.init(this);
        logger.info("AutoPunish API initialized successfully!");

        // Start admin web panel if enabled
        if (configManager.getRules() != null && getConfig().getBoolean("web-panel.enabled", true)) {
            this.webPanelManager = new WebPanelManager(this);
            this.webPanelManager.start();
            logger.info("Admin Web Panel initialized on port " + getConfig().getInt("web-panel.port", 8080));
        }

        // Start public web panel if enabled
        if (getConfig().getBoolean("public-web-panel.enabled", true)) {
            this.publicWebPanelManager = new PublicWebPanelManager(this);
            this.publicWebPanelManager.start();
            logger.info("Public Web Panel initialized on port " + getConfig().getInt("public-web-panel.port", 8081));
        }

        // Register commands and listeners
        registerCommands();
        registerListeners();

        logger.info("AutoPunish plugin has been enabled!");
    }

    private void registerCommands() {
        // Register command executors and tab completers
        registerCommand("punish", new PunishCommand(this));
        registerCommand("punishments", new PunishmentsCommand(this));
        registerCommand("punishreload", new PunishReloadCommand(this));
        registerCommand("severity", new SeverityCommand(this));
        registerCommand("punishadmin", new PunishAdminCommand(this));
        registerCommand("resethistory", new ResetHistoryCommand(this));
        registerCommand("rule", new RuleManagementCommand(this));
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(executor);
            getCommand(name).setTabCompleter((org.bukkit.command.TabCompleter) executor);
        } else {
            logger.warning("Command '" + name + "' not defined in plugin.yml!");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
    }

    @Override
    public void onDisable() {
        // Stop web panels
        if (webPanelManager != null) {
            webPanelManager.stop();
            logger.info("Admin Web Panel stopped");
        }
        if (publicWebPanelManager != null) {
            publicWebPanelManager.stop();
            logger.info("Public Web Panel stopped");
        }

        // Close database
        if (databaseManager != null) {
            databaseManager.close();
        }

        logger.info("AutoPunish plugin has been disabled!");
    }

    // --- Getters ---
    public static AutoPunish getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public WebhookManager getWebhookManager() {
        return webhookManager;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public PunishmentQueueManager getPunishmentQueueManager() {
        return punishmentQueueManager;
    }

    public WebPanelManager getWebPanelManager() {
        return webPanelManager;
    }

    public PublicWebPanelManager getPublicWebPanelManager() {
        return publicWebPanelManager;
    }
}

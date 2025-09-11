package com.alan.autoPunish;

import com.alan.autoPunish.api.AutoPunishAPI;
import com.alan.autoPunish.commands.*;
import com.alan.autoPunish.listeners.ChatListener;
import com.alan.autoPunish.managers.*;
import com.alan.autoPunish.utils.ConfigUtils;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
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
    private PublicWebPanelManager publicWebPanelManager; // NEW

    @Override
    public void onEnable() {
        // Set instance and logger
        instance = this;
        this.logger = getLogger();

        // Load default config
        saveDefaultConfig();

        // Initialize ConfigUtils
        ConfigUtils.init(this);
        logger.info("Config utilities initialized successfully!");

        // Initialize managers
        this.configManager = new ConfigManager(this);
        this.databaseManager = new DatabaseManager(this, configManager);
        this.webhookManager = new WebhookManager(this, configManager);
        this.punishmentQueueManager = new PunishmentQueueManager(this);
        this.punishmentManager = new PunishmentManager(this, configManager, databaseManager, webhookManager);

        // Load queued punishments from database
        this.punishmentQueueManager.loadQueuedPunishments();

        // Initialize API
        AutoPunishAPI.init(this);
        logger.info("AutoPunish API initialized successfully!");

        // Start admin web panel if enabled
        if (getConfig().getBoolean("web-panel.enabled", true)) {
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
        // Register command executors
        getCommand("punish").setExecutor(new PunishCommand(this));
        getCommand("punishments").setExecutor(new PunishmentsCommand(this));
        getCommand("punishreload").setExecutor(new PunishReloadCommand(this));
        getCommand("severity").setExecutor(new SeverityCommand(this));
        getCommand("punishadmin").setExecutor(new PunishAdminCommand(this));
        getCommand("resethistory").setExecutor(new ResetHistoryCommand(this));
        getCommand("rule").setExecutor(new RuleManagementCommand(this));

        // Register tab completers
        getCommand("punish").setTabCompleter(new PunishCommand(this));
        getCommand("punishments").setTabCompleter(new PunishmentsCommand(this));
        getCommand("severity").setTabCompleter(new SeverityCommand(this));
        getCommand("punishadmin").setTabCompleter(new PunishAdminCommand(this));
        getCommand("resethistory").setTabCompleter(new ResetHistoryCommand(this));
        getCommand("rule").setTabCompleter(new RuleManagementCommand(this));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
    }

    @Override
    public void onDisable() {
        // Stop admin web panel
        if (webPanelManager != null) {
            webPanelManager.stop();
            logger.info("Admin Web Panel stopped");
        }

        // Stop public web panel
        if (publicWebPanelManager != null) {
            publicWebPanelManager.stop();
            logger.info("Public Web Panel stopped");
        }

        // Close database connection
        if (databaseManager != null) {
            databaseManager.close();
        }

        logger.info("AutoPunish plugin has been disabled!");
    }

    // Getters
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

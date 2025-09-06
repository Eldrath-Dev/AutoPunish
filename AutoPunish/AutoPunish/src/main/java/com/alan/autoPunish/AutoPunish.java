package com.alan.autoPunish;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.logging.Logger;

public class AutoPunish extends JavaPlugin {
    private static AutoPunish instance;
    private Logger logger;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        // Set instance and logger
        instance = this;
        this.logger = getLogger();

        // Load configuration
        saveDefaultConfig();
        config = getConfig();

        logger.info("AutoPunish plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        logger.info("AutoPunish plugin has been disabled!");
    }

    public static AutoPunish getInstance() {
        return instance;
    }
}
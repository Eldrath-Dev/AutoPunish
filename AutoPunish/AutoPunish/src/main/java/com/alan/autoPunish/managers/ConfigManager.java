package com.alan.autoPunish.managers;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.models.PunishmentRule;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ConfigManager {
    private final AutoPunish plugin;
    private final Logger logger;
    private FileConfiguration config;
    private String discordWebhook;
    private String storageType;
    private Map<String, String> mysqlConfig;
    private Map<String, PunishmentRule> rules;

    public ConfigManager(AutoPunish plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.rules = new HashMap<>();
        this.mysqlConfig = new HashMap<>();
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        // Load Discord webhook
        discordWebhook = config.getString("discord-webhook");

        // Load storage configuration
        storageType = config.getString("storage.type", "sqlite");
        if (storageType.equalsIgnoreCase("mysql")) {
            mysqlConfig.put("host", config.getString("storage.mysql.host", "localhost"));
            mysqlConfig.put("port", config.getString("storage.mysql.port", "3306"));
            mysqlConfig.put("database", config.getString("storage.mysql.database", "punishments"));
            mysqlConfig.put("username", config.getString("storage.mysql.username", "root"));
            mysqlConfig.put("password", config.getString("storage.mysql.password", "password"));
        }

        // Load rules
        ConfigurationSection rulesSection = config.getConfigurationSection("rules");
        if (rulesSection != null) {
            for (String ruleName : rulesSection.getKeys(false)) {
                List<Map<String, String>> tiers = new ArrayList<>();
                List<?> tiersList = rulesSection.getList(ruleName);

                if (tiersList != null) {
                    for (Object tierObj : tiersList) {
                        if (tierObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> tierMap = (Map<String, Object>) tierObj;
                            Map<String, String> tier = new HashMap<>();

                            // Convert Object values to String
                            for (Map.Entry<String, Object> entry : tierMap.entrySet()) {
                                tier.put(entry.getKey(), String.valueOf(entry.getValue()));
                            }

                            tiers.add(tier);
                        }
                    }
                }

                PunishmentRule rule = new PunishmentRule(ruleName, tiers);
                rules.put(ruleName, rule);
                logger.info("Loaded rule: " + ruleName + " with " + tiers.size() + " tiers");
            }
        }

        logger.info("Configuration loaded successfully!");
    }

    public String getDiscordWebhook() {
        return discordWebhook;
    }

    public String getStorageType() {
        return storageType;
    }

    public Map<String, String> getMysqlConfig() {
        return mysqlConfig;
    }

    public Map<String, PunishmentRule> getRules() {
        return rules;
    }

    public PunishmentRule getRule(String ruleName) {
        return rules.get(ruleName);
    }
}
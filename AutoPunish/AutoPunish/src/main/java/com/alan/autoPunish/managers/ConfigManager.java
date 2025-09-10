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
        rules.clear(); // Clear existing rules before loading
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

        // Sync rules with the database after loading
        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().syncAllRules(rules);
        }

        logger.info("Configuration loaded successfully!");
    }

    // *** NEW: Method to save the current state of rules to config.yml ***
    public void saveRulesToConfig() {
        ConfigurationSection rulesSection = config.createSection("rules");
        for (Map.Entry<String, PunishmentRule> entry : rules.entrySet()) {
            String ruleName = entry.getKey();
            PunishmentRule rule = entry.getValue();
            List<Map<String, String>> tiers = rule.getPunishmentTiers();
            rulesSection.set(ruleName, tiers);
        }
        plugin.saveConfig();
    }

    // *** NEW: Method to add a rule both in memory and to the config file ***
    public void addRule(String ruleName) {
        if (!rules.containsKey(ruleName)) {
            PunishmentRule newRule = new PunishmentRule(ruleName, new ArrayList<>());
            rules.put(ruleName, newRule);
            saveRulesToConfig();
            plugin.getDatabaseManager().syncRule(newRule); // Sync new empty rule to DB
        }
    }

    // *** NEW: Method to delete a rule from memory, config, and database ***
    public void deleteRule(String ruleName) {
        if (rules.containsKey(ruleName)) {
            rules.remove(ruleName);
            config.set("rules." + ruleName, null); // Remove from config object
            plugin.saveConfig();
            plugin.getDatabaseManager().deleteRule(ruleName); // Delete from DB
        }
    }

    // *** NEW: Method to add a tier to a rule and save it ***
    public void addTierToRule(String ruleName, Map<String, String> tier) {
        PunishmentRule rule = rules.get(ruleName);
        if (rule != null) {
            rule.addTier(tier);
            saveRulesToConfig();
            plugin.getDatabaseManager().syncRule(rule); // Re-sync the updated rule
        }
    }

    // *** NEW: Method to remove a tier from a rule and save it ***
    public boolean removeTierFromRule(String ruleName, int tierIndex) {
        PunishmentRule rule = rules.get(ruleName);
        if (rule != null && tierIndex >= 0 && tierIndex < rule.getPunishmentTiers().size()) {
            rule.removeTier(tierIndex);
            saveRulesToConfig();
            plugin.getDatabaseManager().syncRule(rule); // Re-sync the updated rule
            return true;
        }
        return false;
    }

    // *** NEW: Method to edit a tier in a rule and save it ***
    public boolean editTierInRule(String ruleName, int tierIndex, Map<String, String> newTier) {
        PunishmentRule rule = rules.get(ruleName);
        if (rule != null && tierIndex >= 0 && tierIndex < rule.getPunishmentTiers().size()) {
            rule.modifyTier(tierIndex, newTier);
            saveRulesToConfig();
            plugin.getDatabaseManager().syncRule(rule); // Re-sync the updated rule
            return true;
        }
        return false;
    }

    // --- Existing methods below ---

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
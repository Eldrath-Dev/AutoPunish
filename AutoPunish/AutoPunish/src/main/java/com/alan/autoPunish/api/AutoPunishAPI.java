package com.alan.autoPunish.api;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.models.Punishment;
import com.alan.autoPunish.models.PunishmentRule;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The main API class for AutoPunish
 * This allows other plugins to interact with the AutoPunish system
 */
public class AutoPunishAPI {
    private static AutoPunish plugin;

    /**
     * Initialize the API with the plugin instance
     * @param instance The AutoPunish plugin instance
     */
    public static void init(AutoPunish instance) {
        plugin = instance;
    }

    /**
     * Get a player's punishment history
     * @param playerUuid The UUID of the player
     * @return A list of punishments for this player
     */
    public static List<Punishment> getPunishmentHistory(UUID playerUuid) {
        return plugin.getDatabaseManager().getPunishmentHistory(playerUuid);
    }

    /**
     * Get a player's punishment history for a specific rule
     * @param playerUuid The UUID of the player
     * @param rule The rule name
     * @return A list of punishments for this player and rule
     */
    public static List<Punishment> getPunishmentHistoryForRule(UUID playerUuid, String rule) {
        return plugin.getDatabaseManager().getPunishmentHistoryForRule(playerUuid, rule);
    }

    /**
     * Calculate a player's severity score based on their punishment history
     * @param playerUuid The UUID of the player
     * @return The severity score
     */
    public static int calculateSeverityScore(UUID playerUuid) {
        List<Punishment> history = getPunishmentHistory(playerUuid);
        return plugin.getPunishmentManager().calculateSeverityScore(history);
    }

    /**
     * Get the punishment tier for a player based on their severity score
     * @param playerUuid The UUID of the player
     * @return The punishment tier (1-5)
     */
    public static int getPunishmentTier(UUID playerUuid) {
        int score = calculateSeverityScore(playerUuid);
        return plugin.getPunishmentManager().determineGlobalTier(score);
    }

    /**
     * Get all available punishment rules
     * @return A map of rule names to rule objects
     */
    public static Map<String, PunishmentRule> getRules() {
        return plugin.getConfigManager().getRules();
    }

    /**
     * Apply a punishment to a player programmatically
     * @param player The player to punish
     * @param rule The rule name
     * @param issuer The name of who is issuing the punishment
     * @param issuerUuid The UUID of who is issuing the punishment
     * @return true if successful, false otherwise
     */
    public static boolean punishPlayer(OfflinePlayer player, String rule, String issuer, UUID issuerUuid) {
        // Create a custom punishment
        PunishmentRule punishmentRule = plugin.getConfigManager().getRule(rule);
        if (punishmentRule == null) {
            return false;
        }

        // Get the tier based on history
        List<Punishment> history = getPunishmentHistoryForRule(player.getUniqueId(), rule);
        int tier = history.size() + 1;

        Map<String, String> punishment = punishmentRule.getTier(tier);
        if (punishment == null) {
            return false;
        }

        // Create punishment record
        Punishment record = new Punishment(
                player.getUniqueId(),
                player.getName() != null ? player.getName() : "Unknown",
                rule,
                punishment.get("type"),
                punishment.get("duration"),
                issuer,
                issuerUuid
        );

        // Save to database
        plugin.getDatabaseManager().savePunishment(record);

        // Send webhook notification
        plugin.getWebhookManager().sendPunishmentWebhook(record);

        return true;
    }
}
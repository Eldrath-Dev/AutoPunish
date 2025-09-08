package com.alan.autoPunish.api;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.models.Punishment;
import com.alan.autoPunish.models.PunishmentRule;
import com.alan.autoPunish.models.QueuedPunishment;
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
        // Get the rule
        PunishmentRule punishmentRule = plugin.getConfigManager().getRule(rule);
        if (punishmentRule == null) {
            return false;
        }

        // Get previous punishments for escalation
        List<Punishment> ruleHistory = getPunishmentHistoryForRule(player.getUniqueId(), rule);
        int tier = ruleHistory.size() + 1;

        Map<String, String> punishment = punishmentRule.getTier(tier);
        if (punishment == null) {
            return false;
        }

        // Apply the punishment through the punishment manager
        return plugin.getPunishmentManager().punishPlayer(
                plugin.getServer().getConsoleSender(), // Using console as sender
                player,
                rule
        );
    }

    /**
     * Get all queued punishments awaiting approval
     * @return List of queued punishments
     */
    public static List<QueuedPunishment> getQueuedPunishments() {
        return plugin.getPunishmentQueueManager().getQueuedPunishments();
    }

    /**
     * Get a specific queued punishment by approval ID
     * @param approvalId The approval ID
     * @return The queued punishment or null if not found
     */
    public static QueuedPunishment getQueuedPunishment(String approvalId) {
        return plugin.getPunishmentQueueManager().getQueuedPunishment(approvalId);
    }

    /**
     * Approve a queued punishment
     * @param approvalId The approval ID of the punishment
     * @param approver The player or entity approving the punishment
     * @return true if successful, false otherwise
     */
    public static boolean approvePunishment(String approvalId, Player approver) {
        return plugin.getPunishmentQueueManager().processApproval(approvalId, true, approver);
    }

    /**
     * Deny a queued punishment
     * @param approvalId The approval ID of the punishment
     * @param denier The player or entity denying the punishment
     * @return true if successful, false otherwise
     */
    public static boolean denyPunishment(String approvalId, Player denier) {
        return plugin.getPunishmentQueueManager().processApproval(approvalId, false, denier);
    }

    /**
     * Check if a punishment needs admin approval
     * @param type The punishment type
     * @param duration The punishment duration
     * @param sender The command sender
     * @return true if approval is needed, false otherwise
     */
    public static boolean needsApproval(String type, String duration, Player sender) {
        return plugin.getPunishmentQueueManager().needsApproval(type, duration, sender);
    }

    /**
     * Reset a player's punishment history
     * @param playerUuid The UUID of the player
     * @return true if successful, false otherwise
     */
    public static boolean resetPlayerHistory(UUID playerUuid) {
        return plugin.getPunishmentQueueManager().resetPlayerHistory(playerUuid);
    }

    /**
     * Check if a staff member can bypass punishment approval
     * @param sender The command sender
     * @return true if they can bypass, false otherwise
     */
    public static boolean canBypassApproval(Player sender) {
        return plugin.getPunishmentQueueManager().canBypassApproval(sender);
    }
}
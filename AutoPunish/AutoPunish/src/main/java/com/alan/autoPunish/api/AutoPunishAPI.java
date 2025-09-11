package com.alan.autoPunish.api;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.models.Punishment;
import com.alan.autoPunish.models.PunishmentRule;
import com.alan.autoPunish.models.QueuedPunishment;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * The main API class for AutoPunish
 * This allows other plugins to interact with the AutoPunish system safely
 */
public class AutoPunishAPI {
    private static AutoPunish plugin;

    public static void init(AutoPunish instance) {
        plugin = instance;
    }

    private static boolean isPluginReady() {
        return plugin != null
                && plugin.getDatabaseManager() != null
                && plugin.getPunishmentManager() != null
                && plugin.getPunishmentQueueManager() != null
                && plugin.getConfigManager() != null;
    }

    public static List<Punishment> getPunishmentHistory(UUID playerUuid) {
        if (!isPluginReady()) return Collections.emptyList();
        return plugin.getDatabaseManager().getPunishmentHistory(playerUuid);
    }

    public static List<Punishment> getPunishmentHistoryForRule(UUID playerUuid, String rule) {
        if (!isPluginReady()) return Collections.emptyList();
        return plugin.getDatabaseManager().getPunishmentHistoryForRule(playerUuid, rule);
    }

    public static int calculateSeverityScore(UUID playerUuid) {
        if (!isPluginReady()) return 0;
        List<Punishment> history = getPunishmentHistory(playerUuid);
        return plugin.getPunishmentManager().calculateSeverityScore(history);
    }

    public static int getPunishmentTier(UUID playerUuid) {
        if (!isPluginReady()) return 0;
        int score = calculateSeverityScore(playerUuid);
        return plugin.getPunishmentManager().determineGlobalTier(score);
    }

    public static Map<String, PunishmentRule> getRules() {
        if (!isPluginReady()) return Collections.emptyMap();
        return plugin.getConfigManager().getRules();
    }

    public static boolean punishPlayer(OfflinePlayer player, String rule, String issuer, UUID issuerUuid) {
        if (!isPluginReady()) return false;

        PunishmentRule punishmentRule = plugin.getConfigManager().getRule(rule);
        if (punishmentRule == null) return false;

        List<Punishment> ruleHistory = getPunishmentHistoryForRule(player.getUniqueId(), rule);
        int tier = ruleHistory.size() + 1;

        Map<String, String> punishment = punishmentRule.getTier(tier);
        if (punishment == null) return false;

        return plugin.getPunishmentManager().punishPlayer(
                plugin.getServer().getConsoleSender(),
                player,
                rule
        );
    }

    public static List<QueuedPunishment> getQueuedPunishments() {
        if (!isPluginReady()) return Collections.emptyList();
        return plugin.getPunishmentQueueManager().getQueuedPunishments();
    }

    public static QueuedPunishment getQueuedPunishment(String approvalId) {
        if (!isPluginReady()) return null;
        return plugin.getPunishmentQueueManager().getQueuedPunishment(approvalId);
    }

    public static boolean approvePunishment(String approvalId, Player approver) {
        if (!isPluginReady()) return false;
        return plugin.getPunishmentQueueManager().processApproval(approvalId, true, approver);
    }

    public static boolean denyPunishment(String approvalId, Player denier) {
        if (!isPluginReady()) return false;
        return plugin.getPunishmentQueueManager().processApproval(approvalId, false, denier);
    }

    public static boolean needsApproval(String type, String duration, Player sender) {
        if (!isPluginReady()) return false;
        return plugin.getPunishmentQueueManager().needsApproval(type, duration, sender);
    }

    public static boolean resetPlayerHistory(UUID playerUuid) {
        if (!isPluginReady()) return false;
        return plugin.getPunishmentQueueManager().resetPlayerHistory(playerUuid);
    }

    public static boolean canBypassApproval(Player sender) {
        if (!isPluginReady()) return false;
        return plugin.getPunishmentQueueManager().canBypassApproval(sender);
    }
}

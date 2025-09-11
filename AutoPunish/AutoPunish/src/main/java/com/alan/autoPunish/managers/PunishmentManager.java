package com.alan.autoPunish.managers;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.api.events.PrePunishmentEvent;
import com.alan.autoPunish.api.events.PunishmentAppliedEvent;
import com.alan.autoPunish.models.Punishment;
import com.alan.autoPunish.models.PunishmentRule;
import com.alan.autoPunish.utils.TimeUtil;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PunishmentManager {
    private final AutoPunish plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final WebhookManager webhookManager;

    public PunishmentManager(AutoPunish plugin, ConfigManager configManager,
                             DatabaseManager databaseManager, WebhookManager webhookManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.webhookManager = webhookManager;
    }

    /**
     * Main entry: punish a player based on rule
     */
    public boolean punishPlayer(CommandSender sender, OfflinePlayer target, String ruleName) {
        PunishmentRule rule = validateRule(ruleName, sender);
        if (rule == null) return false;

        List<Punishment> ruleHistory = databaseManager.getPunishmentHistoryForRule(target.getUniqueId(), ruleName);
        List<Punishment> allHistory = databaseManager.getPunishmentHistory(target.getUniqueId());

        int totalOffenses = allHistory.size();
        int severityScore = calculateSeverityScore(allHistory);
        int ruleTier = ruleHistory.size() + 1;
        int severityTier = Math.min(determineGlobalTier(severityScore), rule.getPunishmentTiers().size());
        int tier = Math.max(ruleTier, severityTier);

        Map<String, String> punishment = rule.getTier(tier);
        if (punishment == null) {
            sender.sendMessage("§cNo punishment tier defined for offense #" + tier);
            return false;
        }

        String type = punishment.get("type");
        String duration = punishment.get("duration");
        logSelectedPunishment(target, ruleName, type, duration, totalOffenses, severityScore, ruleTier, severityTier);

        // Approval check
        PunishmentQueueManager queueManager = plugin.getPunishmentQueueManager();
        if (queueManager != null && queueManager.needsApproval(type, duration, sender)) {
            queueManager.queuePunishment(target, ruleName, type, duration, sender, severityScore);
            return true;
        }

        // PrePunishmentEvent
        PrePunishmentEvent preEvent = firePrePunishmentEvent(sender, target, ruleName, type, duration);
        if (preEvent != null && preEvent.isCancelled()) return false;
        if (preEvent != null) {
            type = preEvent.getType();
            duration = preEvent.getDuration();
        }

        // Apply punishment
        Punishment record = createPunishmentRecord(sender, target, ruleName, type, duration);
        boolean success = applyAndSavePunishment(record, type, duration, ruleName, tier, ruleHistory, allHistory, severityScore);

        if (success) {
            sender.sendMessage("§aPunished " + target.getName() + " for " + ruleName +
                    " (" + type + ", " + (duration.equals("0") ? "Permanent" : duration) +
                    ", Rule offense #" + ruleTier + ", Severity " + severityScore + ")");
        } else {
            sender.sendMessage("§cFailed to punish " + target.getName());
        }
        return success;
    }

    /**
     * Execute punishment after admin approval
     */
    public boolean executeApprovedPunishment(OfflinePlayer target, String rule, String type, String duration,
                                             String staffName, UUID staffUuid, String adminName) {
        List<Punishment> ruleHistory = databaseManager.getPunishmentHistoryForRule(target.getUniqueId(), rule);
        List<Punishment> allHistory = databaseManager.getPunishmentHistory(target.getUniqueId());

        int tier = ruleHistory.size() + 1;
        int severityScore = calculateSeverityScore(allHistory);

        Punishment record = new Punishment(
                target.getUniqueId(),
                target.getName() != null ? target.getName() : "Unknown",
                rule, type, duration,
                staffName + " (Approved by " + adminName + ")", staffUuid
        );

        return applyAndSavePunishment(record, type, duration, rule, tier, ruleHistory, allHistory, severityScore);
    }

    /**
     * Calculate severity score (decays over time)
     */
    public int calculateSeverityScore(List<Punishment> punishments) {
        int score = 0;
        long now = System.currentTimeMillis();

        for (Punishment p : punishments) {
            int points = getBasePoints(p.getType(), p.getDuration());
            long age = (now - p.getDate().getTime()) / (1000 * 60 * 60 * 24);
            double decay = Math.pow(0.5, age / 30.0);
            score += Math.max(1, (int) (points * decay));
        }
        return score;
    }

    public int determineGlobalTier(int severityScore) {
        if (severityScore <= 2) return 1;
        if (severityScore <= 5) return 2;
        if (severityScore <= 10) return 3;
        if (severityScore <= 20) return 4;
        return 5;
    }

    public List<Punishment> getPunishmentHistory(UUID playerUuid) {
        return databaseManager.getPunishmentHistory(playerUuid);
    }

    // -------------------- Helpers --------------------

    private PunishmentRule validateRule(String ruleName, CommandSender sender) {
        PunishmentRule rule = configManager.getRule(ruleName);
        if (rule == null) {
            sender.sendMessage("§cRule not found: " + ruleName);
            logger.warning("Attempted to punish player with non-existent rule: " + ruleName);
        }
        return rule;
    }

    private void logSelectedPunishment(OfflinePlayer target, String ruleName, String type, String duration,
                                       int offenses, int severity, int ruleTier, int severityTier) {
        logger.info("Punishing " + target.getName() + ": " + offenses + " offenses, severity " + severity);
        logger.info("Selected punishment: type=" + type + ", duration=" + duration +
                " (rule tier=" + ruleTier + ", severity tier=" + severityTier + ")");
    }

    private PrePunishmentEvent firePrePunishmentEvent(CommandSender sender, OfflinePlayer target,
                                                      String ruleName, String type, String duration) {
        if (!(sender instanceof Player)) return null;
        PrePunishmentEvent preEvent = new PrePunishmentEvent(target, ruleName, type, duration, (Player) sender);
        Bukkit.getPluginManager().callEvent(preEvent);
        return preEvent;
    }

    private Punishment createPunishmentRecord(CommandSender sender, OfflinePlayer target,
                                              String rule, String type, String duration) {
        String staffName = sender instanceof Player ? sender.getName() : "Console";
        UUID staffUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : new UUID(0, 0);
        return new Punishment(
                target.getUniqueId(),
                target.getName() != null ? target.getName() : "Unknown",
                rule, type, duration, staffName, staffUuid
        );
    }

    private boolean applyAndSavePunishment(Punishment record, String type, String duration, String reason,
                                           int tier, List<Punishment> ruleHistory, List<Punishment> allHistory,
                                           int severityScore) {
        boolean success = applyPunishment(record.getPlayerUuid(), type, duration, reason);
        if (!success) return false;

        try {
            databaseManager.savePunishment(record);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to save punishment", e);
        }

        Bukkit.getPluginManager().callEvent(new PunishmentAppliedEvent(record));

        try {
            webhookManager.sendPunishmentWebhook(record, tier, ruleHistory, allHistory, severityScore);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to send punishment webhook", e);
        }
        return true;
    }

    private int getBasePoints(String type, String duration) {
        switch (type.toLowerCase()) {
            case "warn": return 1;
            case "mute":
            case "kick": return 2;
            case "demotion": return 3;
            case "ban": return duration.equals("0") ? 5 : 3;
            default: return 1;
        }
    }

    private boolean applyPunishment(UUID uuid, String type, String duration, String reason) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
        try {
            switch (type.toLowerCase()) {
                case "warn": return applyWarn(target, reason);
                case "mute": return applyMute(target, duration, reason);
                case "ban": return applyBan(target, duration, reason);
                case "kick": return applyKick(target, reason);
                case "demotion": return applyDemotion(target, reason);
                default:
                    logger.warning("Unknown punishment type: " + type);
                    return false;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error applying punishment: " + e.getMessage(), e);
            return false;
        }
    }

    // -------------------- Individual punishment handlers --------------------

    private boolean applyWarn(OfflinePlayer target, String reason) {
        if (target.isOnline()) target.getPlayer().sendMessage("§c[Warning] " + reason);
        logger.info("Warned " + target.getName() + " for " + reason);
        return true;
    }

    private boolean applyMute(OfflinePlayer target, String duration, String reason) {
        if (duration.equals("0")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + target.getName() + " permission set autopunish.muted true");
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + target.getName() + " permission settemp autopunish.muted true " + duration);
        }
        if (target.isOnline()) {
            target.getPlayer().sendMessage(duration.equals("0")
                    ? "§cYou have been permanently muted: " + reason
                    : "§cMuted for " + duration + ": " + reason);
        }
        return true;
    }

    private boolean applyBan(OfflinePlayer target, String duration, String reason) {
        long millis = TimeUtil.parseDuration(duration);
        if (millis <= 0) {
            Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), "Permanent ban: " + reason, null, null);
        } else {
            Date expiry = new Date(System.currentTimeMillis() + millis);
            Bukkit.getBanList(BanList.Type.NAME).addBan(
                    target.getName(), "Banned for " + TimeUtil.formatDuration(millis) + ": " + reason, expiry, null);
        }
        if (target.isOnline()) {
            target.getPlayer().kickPlayer(millis <= 0
                    ? "Permanently banned: " + reason
                    : "Banned for " + TimeUtil.formatDuration(millis) + ": " + reason);
        }
        return true;
    }

    private boolean applyKick(OfflinePlayer target, String reason) {
        if (target.isOnline()) {
            target.getPlayer().kickPlayer("Kicked: " + reason);
            return true;
        }
        return false;
    }

    private boolean applyDemotion(OfflinePlayer target, String reason) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) return false;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + target.getName() + " track demote moderation");
        if (target.isOnline()) target.getPlayer().sendMessage("§cDemoted: " + reason);
        return true;
    }
}

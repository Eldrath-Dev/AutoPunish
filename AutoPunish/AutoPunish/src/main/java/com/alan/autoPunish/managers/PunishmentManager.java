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

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    public boolean punishPlayer(CommandSender sender, OfflinePlayer target, String ruleName) {
        // Check if rule exists
        PunishmentRule rule = configManager.getRule(ruleName);
        if (rule == null) {
            sender.sendMessage("§cRule not found: " + ruleName);
            logger.warning("Attempted to punish player with non-existent rule: " + ruleName);
            return false;
        }

        // Get punishment history
        List<Punishment> ruleSpecificPunishments =
                databaseManager.getPunishmentHistoryForRule(target.getUniqueId(), ruleName);
        List<Punishment> allPunishments = databaseManager.getPunishmentHistory(target.getUniqueId());

        int totalOffenses = allPunishments.size();
        int severityScore = calculateSeverityScore(allPunishments);

        logger.info("Punishing player " + target.getName() + ": " + totalOffenses + " total offenses, severity " + severityScore);
        logger.info("For rule " + ruleName + ": " + ruleSpecificPunishments.size() + " prior offenses");

        int ruleTier = ruleSpecificPunishments.size() + 1;
        int severityTier = Math.min(determineGlobalTier(severityScore), rule.getPunishmentTiers().size());
        int tier = Math.max(ruleTier, severityTier);

        Map<String, String> punishment = rule.getTier(tier);
        if (punishment == null) {
            sender.sendMessage("§cNo punishment tier defined for offense #" + tier);
            logger.warning("Missing punishment tier for " + ruleName + " tier " + tier);
            return false;
        }

        String type = punishment.get("type");
        String duration = punishment.get("duration");

        logger.info("Selected punishment: type=" + type + ", duration=" + duration +
                " (rule tier=" + ruleTier + ", severity tier=" + severityTier + ")");

        // Approval check
        PunishmentQueueManager queueManager = plugin.getPunishmentQueueManager();
        if (queueManager != null && queueManager.needsApproval(type, duration, sender)) {
            queueManager.queuePunishment(target, ruleName, type, duration, sender, severityScore);
            return true;
        }

        // PrePunishmentEvent
        PrePunishmentEvent preEvent = null;
        if (sender instanceof Player) {
            preEvent = new PrePunishmentEvent(target, ruleName, type, duration, (Player) sender);
            Bukkit.getPluginManager().callEvent(preEvent);

            if (preEvent.isCancelled()) {
                logger.info("Punishment cancelled by plugin for " + target.getName());
                return false;
            }
            type = preEvent.getType();
            duration = preEvent.getDuration();
        }

        // Create punishment record
        String staffName = sender instanceof Player ? sender.getName() : "Console";
        UUID staffUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : new UUID(0, 0);
        Punishment record = new Punishment(
                target.getUniqueId(),
                target.getName() != null ? target.getName() : "Unknown",
                ruleName,
                type,
                duration,
                staffName,
                staffUuid
        );

        // Apply punishment
        boolean success = applyPunishment(target, type, duration, ruleName);
        if (success) {
            try {
                databaseManager.savePunishment(record);
                logger.info("Punishment saved: " + record);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to save punishment", e);
                sender.sendMessage("§eWarning: Punishment applied but failed to save to database.");
            }

            Bukkit.getPluginManager().callEvent(new PunishmentAppliedEvent(record));

            try {
                webhookManager.sendPunishmentWebhook(record, tier, ruleSpecificPunishments, allPunishments, severityScore);
                logger.info("Webhook sent for punishment of " + target.getName());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to send punishment webhook", e);
                sender.sendMessage("§eWarning: Punishment applied but failed to send Discord notification.");
            }

            sender.sendMessage("§aPunished " + target.getName() + " for " + ruleName +
                    " (" + type + ", " +
                    (duration.equals("0") ? "Permanent" : duration) +
                    ", Rule offense #" + ruleTier +
                    ", Severity " + severityScore + ")");
        } else {
            sender.sendMessage("§cFailed to punish " + target.getName());
        }

        return success;
    }

    public boolean executeApprovedPunishment(OfflinePlayer target, String rule, String type, String duration,
                                             String staffName, UUID staffUuid, String adminName) {
        List<Punishment> ruleHistory = databaseManager.getPunishmentHistoryForRule(target.getUniqueId(), rule);
        List<Punishment> allHistory = databaseManager.getPunishmentHistory(target.getUniqueId());

        int tier = ruleHistory.size() + 1;
        int severityScore = calculateSeverityScore(allHistory);

        Punishment record = new Punishment(
                target.getUniqueId(),
                target.getName() != null ? target.getName() : "Unknown",
                rule,
                type,
                duration,
                staffName + " (Approved by " + adminName + ")",
                staffUuid
        );

        boolean success = applyPunishment(target, type, duration, rule);
        if (success) {
            try {
                databaseManager.savePunishment(record);
                logger.info("Approved punishment saved: " + record);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to save approved punishment", e);
                return false;
            }

            Bukkit.getPluginManager().callEvent(new PunishmentAppliedEvent(record));

            try {
                webhookManager.sendPunishmentWebhook(record, tier, ruleHistory, allHistory, severityScore);
                logger.info("Webhook sent for approved punishment of " + target.getName());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to send approved punishment webhook", e);
            }
        }
        return success;
    }

    public int calculateSeverityScore(List<Punishment> punishments) {
        int score = 0;
        long now = System.currentTimeMillis();

        for (Punishment p : punishments) {
            int points;
            switch (p.getType().toLowerCase()) {
                case "warn": points = 1; break;
                case "mute":
                case "kick": points = 2; break;
                case "demotion": points = 3; break;
                case "ban": points = p.getDuration().equals("0") ? 5 : 3; break;
                default: points = 1;
            }

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

    private boolean applyPunishment(OfflinePlayer target, String type, String duration, String reason) {
        try {
            switch (type.toLowerCase()) {
                case "warn":
                    if (target.isOnline() && target.getPlayer() != null) {
                        target.getPlayer().sendMessage("§c[Warning] You have been warned for: " + reason);
                    }
                    logger.info("Warned " + target.getName() + " for " + reason);
                    return true;

                case "mute":
                    if (duration.equals("0")) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                "lp user " + target.getName() + " permission set autopunish.muted true");
                        logger.info("Permanent mute applied to " + target.getName());
                    } else {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                "lp user " + target.getName() + " permission settemp autopunish.muted true " + duration);
                        logger.info("Temporary mute (" + duration + ") applied to " + target.getName());
                    }
                    if (target.isOnline() && target.getPlayer() != null) {
                        target.getPlayer().sendMessage(duration.equals("0")
                                ? "§cYou have been permanently muted for: " + reason
                                : "§cYou have been muted for " + duration + " for: " + reason);
                    }
                    return true;

                case "ban":
                    long millis = TimeUtil.parseDuration(duration);
                    if (millis <= 0) {
                        Bukkit.getBanList(BanList.Type.NAME).addBan(
                                target.getName(),
                                "Permanently banned for: " + reason,
                                null,
                                null
                        );
                        logger.info("Permanent ban applied to " + target.getName());
                    } else {
                        Date expiry = new Date(System.currentTimeMillis() + millis);
                        Bukkit.getBanList(BanList.Type.NAME).addBan(
                                target.getName(),
                                "Banned for " + TimeUtil.formatDuration(millis) + " for: " + reason,
                                expiry,
                                null
                        );
                        logger.info("Temporary ban (" + duration + ") applied to " + target.getName());
                    }
                    if (target.isOnline() && target.getPlayer() != null) {
                        target.getPlayer().kickPlayer(millis <= 0
                                ? "You have been permanently banned for: " + reason
                                : "You have been banned for " + TimeUtil.formatDuration(millis) + " for: " + reason);
                    }
                    return true;

                case "kick":
                    if (target.isOnline() && target.getPlayer() != null) {
                        target.getPlayer().kickPlayer("You have been kicked for: " + reason);
                        logger.info("Kick applied to " + target.getName());
                        return true;
                    }
                    logger.warning("Kick failed, " + target.getName() + " not online");
                    return false;

                case "demotion":
                    if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                "lp user " + target.getName() + " track demote moderation");
                        logger.info("Demotion applied to " + target.getName());
                        if (target.isOnline() && target.getPlayer() != null) {
                            target.getPlayer().sendMessage("§cYou have been demoted for: " + reason);
                        }
                        return true;
                    }
                    logger.warning("Demotion requires LuckPerms!");
                    return false;

                default:
                    logger.warning("Unknown punishment type: " + type);
                    return false;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error applying punishment: " + e.getMessage(), e);
            return false;
        }
    }

    public List<Punishment> getPunishmentHistory(UUID playerUuid) {
        return databaseManager.getPunishmentHistory(playerUuid);
    }
}

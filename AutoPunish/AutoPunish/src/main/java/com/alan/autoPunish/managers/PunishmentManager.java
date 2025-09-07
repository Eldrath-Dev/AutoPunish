package com.alan.autoPunish.managers;

import com.alan.autoPunish.AutoPunish;
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

        // Get previous punishments for this rule
        List<Punishment> previousPunishments =
                databaseManager.getPunishmentHistoryForRule(target.getUniqueId(), ruleName);

        logger.info("Player " + target.getName() + " has " + previousPunishments.size() +
                " previous punishments for rule: " + ruleName);

        // Determine the appropriate tier based on previous punishments
        int tier = previousPunishments.size() + 1;
        Map<String, String> punishment = rule.getTier(tier);

        if (punishment == null) {
            sender.sendMessage("§cNo punishment tier defined for offense #" + tier);
            logger.warning("No punishment tier defined for rule " + ruleName + " at tier " + tier);
            return false;
        }

        String type = punishment.get("type");
        String duration = punishment.get("duration");

        logger.info("Selected punishment: Type=" + type + ", Duration=" + duration);

        // Create punishment record
        String staffName = sender instanceof Player ? sender.getName() : "Console";
        UUID staffUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : new UUID(0, 0);

        Punishment punishmentRecord = new Punishment(
                target.getUniqueId(),
                target.getName() != null ? target.getName() : "Unknown",
                ruleName,
                type,
                duration,
                staffName,
                staffUuid
        );

        // Apply the punishment
        boolean success = applyPunishment(target, type, duration, ruleName);

        if (success) {
            // Save to database
            try {
                databaseManager.savePunishment(punishmentRecord);
                logger.info("Punishment record saved to database successfully");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to save punishment to database", e);
                sender.sendMessage("§eWarning: Punishment applied but failed to save to database.");
            }

            // Send webhook notification
            try {
                webhookManager.sendPunishmentWebhook(punishmentRecord);
                logger.info("Punishment webhook notification sent successfully");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to send webhook notification", e);
                sender.sendMessage("§eWarning: Punishment applied but failed to send Discord notification.");
            }

            // Notify the staff member
            sender.sendMessage("§aSuccessfully punished " + target.getName() + " for " + ruleName +
                    " (Punishment: " + type + ", Duration: " +
                    (duration.equals("0") ? "Permanent" : duration) + ")");
        } else {
            sender.sendMessage("§cFailed to apply punishment to " + target.getName());
        }

        return success;
    }

    private boolean applyPunishment(OfflinePlayer target, String type, String duration, String reason) {
        try {
            switch (type.toLowerCase()) {
                case "warn":
                    Player onlineTarget = target.getPlayer();
                    if (onlineTarget != null) {
                        onlineTarget.sendMessage("§c[Warning] You have been warned for: " + reason);
                    }
                    logger.info("Warning applied to " + target.getName() + " for " + reason);
                    return true;

                case "mute":
                    // Assuming you have a permission plugin like LuckPerms
                    if (duration.equals("0")) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                "lp user " + target.getName() + " permission set autopunish.muted true");
                        logger.info("Permanent mute applied to " + target.getName() + " for " + reason);
                    } else {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                "lp user " + target.getName() + " permission settemp autopunish.muted true " + duration);
                        logger.info("Temporary mute (" + duration + ") applied to " + target.getName() + " for " + reason);
                    }

                    // Notify the player if they're online
                    if (target.isOnline() && target.getPlayer() != null) {
                        String muteMessage = duration.equals("0")
                                ? "§cYou have been permanently muted for: " + reason
                                : "§cYou have been muted for " + duration + " for: " + reason;
                        target.getPlayer().sendMessage(muteMessage);
                    }
                    return true;

                case "ban":
                    long durationMillis = TimeUtil.parseDuration(duration);
                    if (durationMillis <= 0) {
                        // Permanent ban
                        Bukkit.getBanList(BanList.Type.NAME).addBan(
                                target.getName(),
                                "You have been permanently banned for: " + reason,
                                null,
                                null
                        );
                        logger.info("Permanent ban applied to " + target.getName() + " for " + reason);
                    } else {
                        // Temporary ban
                        Date expiry = new Date(System.currentTimeMillis() + durationMillis);
                        Bukkit.getBanList(BanList.Type.NAME).addBan(
                                target.getName(),
                                "You have been banned for " + TimeUtil.formatDuration(durationMillis) + " for: " + reason,
                                expiry,
                                null
                        );
                        logger.info("Temporary ban (" + duration + ") applied to " + target.getName() + " for " + reason);
                    }

                    // Kick the player if they're online
                    if (target.isOnline() && target.getPlayer() != null) {
                        String kickMessage = durationMillis <= 0
                                ? "You have been permanently banned for: " + reason
                                : "You have been banned for " + TimeUtil.formatDuration(durationMillis) + " for: " + reason;
                        target.getPlayer().kickPlayer(kickMessage);
                    }
                    return true;

                case "kick":
                    if (target.isOnline() && target.getPlayer() != null) {
                        target.getPlayer().kickPlayer("You have been kicked for: " + reason);
                        logger.info("Kick applied to " + target.getName() + " for " + reason);
                        return true;
                    }
                    logger.warning("Failed to kick " + target.getName() + " (player not online)");
                    return false;

                case "demotion":
                    // Using LuckPerms for group management
                    if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
                        // For this example, we'll implement a basic demotion system
                        // In a real implementation, you might want to define group hierarchies in config

                        // This is just a demonstration - you would need to implement proper group handling
                        // based on your server's permission structure
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                "lp user " + target.getName() + " track demote moderation");

                        logger.info("Demotion applied to " + target.getName() + " for " + reason);

                        // Notify the player
                        if (target.isOnline() && target.getPlayer() != null) {
                            target.getPlayer().sendMessage("§cYou have been demoted for: " + reason);
                        }

                        return true;
                    }
                    logger.warning("Demotion requires LuckPerms plugin!");
                    return false;

                default:
                    logger.warning("Unknown punishment type: " + type);
                    return false;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error applying punishment: " + e.getMessage(), e);
            e.printStackTrace();
            return false;
        }
    }

    public List<Punishment> getPunishmentHistory(UUID playerUuid) {
        return databaseManager.getPunishmentHistory(playerUuid);
    }
}
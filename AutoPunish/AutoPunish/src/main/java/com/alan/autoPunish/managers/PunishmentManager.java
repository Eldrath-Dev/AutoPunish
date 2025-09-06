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
            return false;
        }

        // Get previous punishments for this rule
        List<Punishment> previousPunishments =
                databaseManager.getPunishmentHistoryForRule(target.getUniqueId(), ruleName);

        // Determine the appropriate tier based on previous punishments
        int tier = previousPunishments.size() + 1;
        Map<String, String> punishment = rule.getTier(tier);

        if (punishment == null) {
            sender.sendMessage("§cNo punishment tier defined for offense #" + tier);
            return false;
        }

        String type = punishment.get("type");
        String duration = punishment.get("duration");

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
            databaseManager.savePunishment(punishmentRecord);

            // Send webhook notification
            webhookManager.sendPunishmentWebhook(punishmentRecord);

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
                    return true;

                case "mute":
                    // Assuming you have a permission plugin like LuckPerms
                    // We'll implement a basic mute using a permission flag
                    if (duration.equals("0")) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                "lp user " + target.getName() + " permission set autopunish.muted true");
                    } else {
                        long durationMillis = TimeUtil.parseDuration(duration);
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                "lp user " + target.getName() + " permission settemp autopunish.muted true " + duration);
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
                    } else {
                        // Temporary ban
                        Date expiry = new Date(System.currentTimeMillis() + durationMillis);
                        Bukkit.getBanList(BanList.Type.NAME).addBan(
                                target.getName(),
                                "You have been banned for: " + reason,
                                expiry,
                                null
                        );
                    }

                    // Kick the player if they're online
                    if (target.isOnline() && target.getPlayer() != null) {
                        target.getPlayer().kickPlayer("You have been banned for: " + reason);
                    }
                    return true;

                case "kick":
                    if (target.isOnline() && target.getPlayer() != null) {
                        target.getPlayer().kickPlayer("You have been kicked for: " + reason);
                        return true;
                    }
                    return false;

                default:
                    logger.warning("Unknown punishment type: " + type);
                    return false;
            }
        } catch (Exception e) {
            logger.severe("Error applying punishment: " + e.getMessage());
            return false;
        }
    }

    public List<Punishment> getPunishmentHistory(UUID playerUuid) {
        return databaseManager.getPunishmentHistory(playerUuid);
    }
}
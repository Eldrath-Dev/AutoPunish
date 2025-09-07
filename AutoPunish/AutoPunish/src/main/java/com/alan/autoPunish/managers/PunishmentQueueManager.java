package com.alan.autoPunish.managers;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.models.Punishment;
import com.alan.autoPunish.models.QueuedPunishment;
import com.alan.autoPunish.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PunishmentQueueManager {
    private final AutoPunish plugin;
    private final Logger logger;
    private final Map<String, QueuedPunishment> queuedPunishments = new HashMap<>();

    public PunishmentQueueManager(AutoPunish plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Queue a punishment for admin approval
     */
    public void queuePunishment(OfflinePlayer target, String rule, String type, String duration,
                                CommandSender sender, int severityScore) {
        String staffName = sender instanceof Player ? sender.getName() : "Console";
        UUID staffUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : new UUID(0, 0);

        QueuedPunishment queuedPunishment = new QueuedPunishment(
                target.getUniqueId(),
                target.getName() != null ? target.getName() : "Unknown",
                rule,
                type,
                duration,
                staffName,
                staffUuid
        );

        // Store in our queue
        queuedPunishments.put(queuedPunishment.getApprovalId(), queuedPunishment);

        // Notify staff member
        sender.sendMessage("§eSevere punishment has been queued for admin approval. Approval ID: §f" +
                queuedPunishment.getApprovalId());

        // Notify all online admins
        notifyAdmins("§6[AutoPunish] §eNew punishment queued for approval: §f" +
                queuedPunishment.getPlayerName() + " §e(ID: §f" + queuedPunishment.getApprovalId() + "§e)");

        logger.info("Punishment queued for approval: " + type + " " + duration + " for player " +
                target.getName() + " (Approval ID: " + queuedPunishment.getApprovalId() + ")");
    }

    /**
     * Process an approval response
     */
    public boolean processApproval(String approvalId, boolean approved, CommandSender admin) {
        QueuedPunishment queued = queuedPunishments.get(approvalId);
        if (queued == null) {
            admin.sendMessage("§cNo pending punishment found with ID: " + approvalId);
            return false;
        }

        PunishmentManager punishmentManager = plugin.getPunishmentManager();

        if (approved) {
            // Execute the punishment
            OfflinePlayer target = Bukkit.getOfflinePlayer(queued.getPlayerUuid());
            boolean success = punishmentManager.executeApprovedPunishment(
                    target,
                    queued.getRule(),
                    queued.getType(),
                    queued.getDuration(),
                    queued.getStaffName(),
                    queued.getStaffUuid(),
                    admin.getName()
            );

            if (success) {
                admin.sendMessage("§aPunishment approved and executed successfully.");
                logger.info("Approved punishment executed: " + approvalId);

                // Notify all admins
                notifyAdmins("§6[AutoPunish] §aPunishment for §f" + queued.getPlayerName() +
                        " §aapproved by §f" + admin.getName());
            } else {
                admin.sendMessage("§cFailed to execute approved punishment.");
                logger.warning("Failed to execute approved punishment: " + approvalId);
                return false;
            }
        } else {
            // Notify staff that punishment was denied
            Player staffMember = Bukkit.getPlayer(queued.getStaffUuid());
            if (staffMember != null) {
                staffMember.sendMessage("§cYour punishment request for " + queued.getPlayerName() +
                        " was denied by admin " + admin.getName());
            }

            // Notify all admins
            notifyAdmins("§6[AutoPunish] §cPunishment for §f" + queued.getPlayerName() +
                    " §cdenied by §f" + admin.getName());

            admin.sendMessage("§cPunishment denied successfully.");
            logger.info("Punishment denied by admin " + admin.getName() + ": " + approvalId);
        }

        // Remove from queue
        queuedPunishments.remove(approvalId);
        return true;
    }

    /**
     * Check if a punishment needs admin approval based on severity
     */
    public boolean needsApproval(String type, String duration) {
        // Check for severe punishments (ban > 7 days)
        if (type.equalsIgnoreCase("ban")) {
            // If permanent ban
            if (duration.equals("0")) {
                return true;
            }

            // If ban duration > 7 days
            long durationMillis = TimeUtil.parseDuration(duration);
            if (durationMillis > 7 * 24 * 60 * 60 * 1000L) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all queued punishments
     */
    public List<QueuedPunishment> getQueuedPunishments() {
        return new ArrayList<>(queuedPunishments.values());
    }

    /**
     * Get a queued punishment by ID
     */
    public QueuedPunishment getQueuedPunishment(String approvalId) {
        return queuedPunishments.get(approvalId);
    }

    /**
     * Notify all online admins with a message
     */
    private void notifyAdmins(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("autopunish.admin")) {
                player.sendMessage(message);
            }
        }
    }
}
package com.alan.autoPunish.managers;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.models.QueuedPunishment;
import com.alan.autoPunish.utils.ConfigUtils;
import com.alan.autoPunish.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

// API events
import com.alan.autoPunish.api.events.PunishmentQueuedEvent;
import com.alan.autoPunish.api.events.PunishmentApprovedEvent;
import com.alan.autoPunish.api.events.PunishmentDeniedEvent;
import com.alan.autoPunish.api.events.PlayerHistoryResetEvent;

public class PunishmentQueueManager {
    private final AutoPunish plugin;
    private final Logger logger;
    private final Map<String, QueuedPunishment> queuedPunishments = new HashMap<>();

    public PunishmentQueueManager(AutoPunish plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /** Load queued punishments from the database on startup */
    public void loadQueuedPunishments() {
        List<QueuedPunishment> loaded = plugin.getDatabaseManager().getQueuedPunishments();
        synchronized(queuedPunishments) {
            for (QueuedPunishment punishment : loaded) {
                queuedPunishments.put(punishment.getApprovalId(), punishment);
            }
        }
        logger.info("Loaded " + loaded.size() + " queued punishments from database");
    }

    /** Ensure a runnable executes on the main thread */
    private void runSync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    /** Check if punishment requires approval */
    public boolean needsApproval(String type, String duration, CommandSender sender) {
        if (!ConfigUtils.isApprovalSystemEnabled()) return false;
        if (canBypassApproval(sender)) return false;

        if (type.equalsIgnoreCase("ban")) {
            if (duration.equals("0")) return true; // permanent ban

            long durationMillis = TimeUtil.parseDuration(duration);
            int approvalAfterDays = ConfigUtils.getRequireApprovalAfterDays();
            return durationMillis > approvalAfterDays * 24L * 60L * 60L * 1000L;
        }
        return false;
    }

    /** Check if staff bypass approval */
    public boolean canBypassApproval(CommandSender sender) {
        if (!(sender instanceof Player)) return true; // console bypass
        for (String permission : ConfigUtils.getBypassApprovalPermissions()) {
            if (sender.hasPermission(permission)) return true;
        }
        return false;
    }

    /** Auto-approve punishment if staff rank allows it */
    public boolean processAutoApproved(OfflinePlayer target, String rule, String type, String duration,
                                       CommandSender sender, int severityScore) {
        String staffName = sender.getName();
        UUID staffUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : new UUID(0, 0);

        final boolean[] result = new boolean[1];
        runSync(() -> {
            result[0] = plugin.getPunishmentManager().executeApprovedPunishment(
                    target, rule, type, duration, staffName, staffUuid,
                    staffName + " (Auto-approved by rank)"
            );

            if (result[0]) {
                if (sender instanceof Player) {
                    sender.sendMessage("§aYour punishment was auto-approved due to your staff rank.");
                }
                if (ConfigUtils.shouldNotifyAdminOnAutoApproved()) {
                    notifyAdmins("§6[AutoPunish] §e" + staffName + " issued a " + type +
                            " (" + (duration.equals("0") ? "Permanent" : duration) + ") to " +
                            target.getName() + " (auto-approved)");
                }
                logger.info("Auto-approved punishment executed: " + type + " " + duration +
                        " for player " + target.getName() + " by " + staffName);
            }
        });
        return result[0];
    }

    /** Queue punishment for approval */
    public void queuePunishment(OfflinePlayer target, String rule, String type, String duration,
                                CommandSender sender, int severityScore) {
        if (canBypassApproval(sender)) {
            processAutoApproved(target, rule, type, duration, sender, severityScore);
            return;
        }

        String staffName = sender instanceof Player ? sender.getName() : "Console";
        UUID staffUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : new UUID(0, 0);

        QueuedPunishment queuedPunishment = new QueuedPunishment(
                target.getUniqueId(),
                target.getName() != null ? target.getName() : "Unknown",
                rule, type, duration, staffName, staffUuid
        );

        synchronized(queuedPunishments) {
            queuedPunishments.put(queuedPunishment.getApprovalId(), queuedPunishment);
        }
        plugin.getDatabaseManager().saveQueuedPunishment(queuedPunishment);

        // Async webhook
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getWebhookManager().sendQueuedPunishmentWebhook(queuedPunishment, severityScore);
                logger.info("Queued punishment webhook sent");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to send webhook for queued punishment", e);
            }
        });

        runSync(() -> {
            Bukkit.getPluginManager().callEvent(new PunishmentQueuedEvent(queuedPunishment, sender, severityScore));
            if (sender instanceof Player) {
                sender.sendMessage("§eSevere punishment has been queued. Approval ID: §f" +
                        queuedPunishment.getApprovalId());
            }
            notifyAdmins("§6[AutoPunish] §eNew punishment queued: §f" +
                    queuedPunishment.getPlayerName() + " §e(ID: §f" + queuedPunishment.getApprovalId() + "§e)");
        });

        logger.info("Queued punishment for approval: " + type + " " + duration + " for " +
                target.getName() + " (ID: " + queuedPunishment.getApprovalId() + ")");
    }

    /** Process approval or denial */
    public boolean processApproval(String approvalId, boolean approved, CommandSender admin) {
        QueuedPunishment queued;
        synchronized(queuedPunishments) {
            queued = queuedPunishments.get(approvalId);
        }

        if (queued == null) {
            if (admin instanceof Player) {
                runSync(() -> admin.sendMessage("§cNo pending punishment found with ID: " + approvalId));
            }
            return false;
        }

        if (approved) {
            final boolean[] success = new boolean[1];
            runSync(() -> {
                OfflinePlayer target = Bukkit.getOfflinePlayer(queued.getPlayerUuid());
                success[0] = plugin.getPunishmentManager().executeApprovedPunishment(
                        target, queued.getRule(), queued.getType(), queued.getDuration(),
                        queued.getStaffName(), queued.getStaffUuid(), admin.getName()
                );

                if (success[0]) {
                    if (admin instanceof Player) admin.sendMessage("§aPunishment approved and executed.");
                    notifyAdmins("§6[AutoPunish] §aPunishment for §f" + queued.getPlayerName() +
                            " §aapproved by §f" + admin.getName());
                    Bukkit.getPluginManager().callEvent(new PunishmentApprovedEvent(queued, admin));
                } else {
                    if (admin instanceof Player) admin.sendMessage("§cFailed to execute punishment.");
                }
            });
            if (!success[0]) return false;
        } else {
            runSync(() -> {
                Player staffMember = Bukkit.getPlayer(queued.getStaffUuid());
                if (staffMember != null) {
                    staffMember.sendMessage("§cYour punishment request for " + queued.getPlayerName() +
                            " was denied by " + admin.getName());
                }
                notifyAdmins("§6[AutoPunish] §cPunishment for §f" + queued.getPlayerName() +
                        " §cdenied by §f" + admin.getName());

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        plugin.getWebhookManager().sendDeniedPunishmentWebhook(queued, admin.getName());
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to send webhook for denied punishment", e);
                    }
                });

                Bukkit.getPluginManager().callEvent(new PunishmentDeniedEvent(queued, admin));
                if (admin instanceof Player) admin.sendMessage("§cPunishment denied.");
            });
        }

        runSync(() -> {
            synchronized(queuedPunishments) {
                queuedPunishments.remove(approvalId);
            }
            plugin.getDatabaseManager().removeQueuedPunishment(approvalId);
            logger.info("Removed queued punishment ID: " + approvalId);
        });
        return true;
    }

    /** Get all queued punishments */
    public List<QueuedPunishment> getQueuedPunishments() {
        synchronized(queuedPunishments) {
            return new ArrayList<>(queuedPunishments.values());
        }
    }

    /** Get queued punishment by ID */
    public QueuedPunishment getQueuedPunishment(String approvalId) {
        synchronized(queuedPunishments) {
            return queuedPunishments.get(approvalId);
        }
    }

    /** Reset a player's punishment history */
    public boolean resetPlayerHistory(UUID playerUuid) {
        synchronized(queuedPunishments) {
            queuedPunishments.entrySet().removeIf(entry -> {
                if (entry.getValue().getPlayerUuid().equals(playerUuid)) {
                    plugin.getDatabaseManager().removeQueuedPunishment(entry.getValue().getApprovalId());
                    return true;
                }
                return false;
            });
        }

        String playerName = Optional.ofNullable(Bukkit.getOfflinePlayer(playerUuid).getName()).orElse("Unknown");
        runSync(() -> Bukkit.getPluginManager().callEvent(
                new PlayerHistoryResetEvent(playerUuid, playerName, Bukkit.getConsoleSender())
        ));
        return plugin.getDatabaseManager().resetPlayerHistory(playerUuid);
    }

    /** Notify admins */
    private void notifyAdmins(String message) {
        runSync(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("autopunish.admin.approve")) {
                    player.sendMessage(message);
                }
            }
        });
    }
}
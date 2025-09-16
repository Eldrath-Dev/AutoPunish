package com.alan.autoPunish.commands;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.managers.PunishmentQueueManager;
import com.alan.autoPunish.models.QueuedPunishment;
import com.alan.autoPunish.utils.TimeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PunishAdminCommand implements CommandExecutor, TabCompleter {
    private final AutoPunish plugin;
    private final PunishmentQueueManager queueManager;
    private final SimpleDateFormat dateFormat;

    public PunishAdminCommand(AutoPunish plugin) {
        this.plugin = plugin;
        this.queueManager = plugin.getPunishmentQueueManager();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("autopunish.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cUsage: /punishadmin <list|approve|deny> [approvalId]");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
                return listPendingPunishments(sender);

            case "approve":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /punishadmin approve <approvalId>");
                    return true;
                }
                return approvePunishment(sender, args[1]);

            case "deny":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /punishadmin deny <approvalId>");
                    return true;
                }
                return denyPunishment(sender, args[1]);

            default:
                sender.sendMessage("§cUnknown subcommand. Use 'list', 'approve', or 'deny'.");
                return true;
        }
    }

    private boolean listPendingPunishments(CommandSender sender) {
        List<QueuedPunishment> queuedPunishments = queueManager.getQueuedPunishments();

        if (queuedPunishments.isEmpty()) {
            sender.sendMessage("§aThere are no punishments pending approval.");
            return true;
        }

        sender.sendMessage("§6Punishments Pending Approval: §f" + queuedPunishments.size());

        for (QueuedPunishment punishment : queuedPunishments) {
            String formattedDuration;
            if (punishment.getDuration().equals("0")) {
                formattedDuration = "Permanent";
            } else {
                formattedDuration = punishment.getDuration();
            }

            sender.sendMessage("§7--------------------------------");
            sender.sendMessage("§fID: §e" + punishment.getApprovalId());
            sender.sendMessage("§fPlayer: §e" + punishment.getPlayerName());
            sender.sendMessage("§fRule: §e" + punishment.getRule());
            sender.sendMessage("§fPunishment: §e" + punishment.getType() + " (" + formattedDuration + ")");
            sender.sendMessage("§fRequested by: §e" + punishment.getStaffName());
            sender.sendMessage("§fDate: §e" + dateFormat.format(punishment.getQueuedDate()));
            sender.sendMessage("§7/punishadmin approve " + punishment.getApprovalId() + " §8- §aApprove");
            sender.sendMessage("§7/punishadmin deny " + punishment.getApprovalId() + " §8- §cDeny");
        }

        return true;
    }

    private boolean approvePunishment(CommandSender sender, String approvalId) {
        boolean result = queueManager.processApproval(approvalId, true, sender);
        if (!result) {
            sender.sendMessage("§cFailed to approve punishment. Make sure the ID is correct.");
        }
        return true;
    }

    private boolean denyPunishment(CommandSender sender, String approvalId) {
        boolean result = queueManager.processApproval(approvalId, false, sender);
        if (!result) {
            sender.sendMessage("§cFailed to deny punishment. Make sure the ID is correct.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("autopunish.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("list", "approve", "deny").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("approve") || args[0].equalsIgnoreCase("deny"))) {
            // Suggest approval IDs
            String input = args[1].toLowerCase();
            return queueManager.getQueuedPunishments().stream()
                    .map(QueuedPunishment::getApprovalId)
                    .filter(id -> id.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
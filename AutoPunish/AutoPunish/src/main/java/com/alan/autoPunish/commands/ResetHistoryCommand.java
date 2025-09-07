package com.alan.autoPunish.commands;

import com.alan.autoPunish.AutoPunish;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ResetHistoryCommand implements CommandExecutor, TabCompleter {
    private final AutoPunish plugin;

    public ResetHistoryCommand(AutoPunish plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("autopunish.admin.reset")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cUsage: /resethistory <player>");
            return true;
        }

        String playerName = args[0];

        // Get the target player
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(playerName);
        if (target == null || !target.hasPlayedBefore()) {
            // Try to find online player
            Player onlinePlayer = Bukkit.getPlayer(playerName);
            if (onlinePlayer != null) {
                target = onlinePlayer;
            } else {
                sender.sendMessage("§cPlayer not found: " + playerName);
                return true;
            }
        }

        // Reset the player's history
        boolean success = plugin.getPunishmentQueueManager().resetPlayerHistory(target.getUniqueId());

        if (success) {
            sender.sendMessage("§aSuccessfully reset violation history for player §f" + target.getName());
            plugin.getLogger().info(sender.getName() + " reset violation history for player " + target.getName());
        } else {
            sender.sendMessage("§cFailed to reset violation history for player §f" + target.getName());
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("autopunish.admin.reset") || args.length != 1) {
            return new ArrayList<>();
        }

        // Suggest online players
        String input = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
    }
}
package com.alan.autoPunish.commands;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.managers.PunishmentManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PunishCommand implements CommandExecutor, TabCompleter {
    private final AutoPunish plugin;
    private final PunishmentManager punishmentManager;

    public PunishCommand(AutoPunish plugin) {
        this.plugin = plugin;
        this.punishmentManager = plugin.getPunishmentManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("autopunish.punish")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /punish <player> <rule>");
            return true;
        }

        String playerName = args[0];
        String ruleName = args[1];

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

        // Apply the punishment
        punishmentManager.punishPlayer(sender, target, ruleName);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("autopunish.punish")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            // Suggest online players
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            // Suggest available rules
            String input = args[1].toLowerCase();
            return plugin.getConfigManager().getRules().keySet().stream()
                    .filter(rule -> rule.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
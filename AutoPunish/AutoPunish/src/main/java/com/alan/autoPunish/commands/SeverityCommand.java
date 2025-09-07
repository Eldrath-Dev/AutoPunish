package com.alan.autoPunish.commands;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.managers.PunishmentManager;
import com.alan.autoPunish.models.Punishment;
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

public class SeverityCommand implements CommandExecutor, TabCompleter {
    private final AutoPunish plugin;
    private final PunishmentManager punishmentManager;

    public SeverityCommand(AutoPunish plugin) {
        this.plugin = plugin;
        this.punishmentManager = plugin.getPunishmentManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("autopunish.severity")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cUsage: /severity <player>");
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

        // Get punishment history
        List<Punishment> punishments = plugin.getPunishmentManager().getPunishmentHistory(target.getUniqueId());

        if (punishments.isEmpty()) {
            sender.sendMessage("§aPlayer §f" + target.getName() + "§a has no punishment history. Severity score: §f0");
            return true;
        }

        // Calculate severity score
        int severityScore = punishmentManager.calculateSeverityScore(punishments);
        int tier = punishmentManager.determineGlobalTier(severityScore);

        sender.sendMessage("§aPunishment severity for §f" + target.getName() + "§a:");
        sender.sendMessage("§7Total violations: §f" + punishments.size());
        sender.sendMessage("§7Severity score: §f" + severityScore);
        sender.sendMessage("§7Current tier: §f" + tier);

        // Show the most recent punishments
        sender.sendMessage("§7Recent violations:");
        punishments.sort((p1, p2) -> p2.getDate().compareTo(p1.getDate()));
        int displayCount = Math.min(punishments.size(), 5);

        for (int i = 0; i < displayCount; i++) {
            Punishment p = punishments.get(i);
            sender.sendMessage(String.format("§7- §f%s §7for §f%s §7(%s)",
                    p.getType(), p.getRule(),
                    p.getDuration().equals("0") ? "Permanent" : p.getDuration()));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("autopunish.severity") || args.length != 1) {
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
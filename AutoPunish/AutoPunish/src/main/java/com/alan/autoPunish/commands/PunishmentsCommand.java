package com.alan.autoPunish.commands;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.models.Punishment;
import com.alan.autoPunish.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PunishmentsCommand implements CommandExecutor, TabCompleter {
    private final AutoPunish plugin;
    private final SimpleDateFormat dateFormat;

    public PunishmentsCommand(AutoPunish plugin) {
        this.plugin = plugin;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("autopunish.punishments")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cUsage: /punishments <player>");
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
            sender.sendMessage("§aPunishment history for §f" + target.getName() + "§a: No punishments found.");
            return true;
        }

        sender.sendMessage("§aPunishment history for §f" + target.getName() + "§a:");

        for (int i = 0; i < punishments.size(); i++) {
            Punishment punishment = punishments.get(i);
            String formattedDuration;

            if (punishment.getDuration().equals("0")) {
                formattedDuration = "Permanent";
            } else {
                formattedDuration = punishment.getDuration();
            }

            sender.sendMessage(String.format("§7%d. §f%s §7- §f%s §7(§f%s§7) by §f%s §7on §f%s",
                    i + 1,
                    punishment.getRule(),
                    punishment.getType(),
                    formattedDuration,
                    punishment.getStaffName(),
                    dateFormat.format(punishment.getDate())
            ));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("autopunish.punishments") || args.length != 1) {
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
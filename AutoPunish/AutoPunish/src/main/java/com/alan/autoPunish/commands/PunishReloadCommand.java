package com.alan.autoPunish.commands;

import com.alan.autoPunish.AutoPunish;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class PunishReloadCommand implements CommandExecutor {
    private final AutoPunish plugin;

    public PunishReloadCommand(AutoPunish plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("autopunish.reload")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        // Reload the configuration
        plugin.getConfigManager().loadConfig();
        sender.sendMessage("§aAutoPunish configuration reloaded successfully!");

        return true;
    }
}
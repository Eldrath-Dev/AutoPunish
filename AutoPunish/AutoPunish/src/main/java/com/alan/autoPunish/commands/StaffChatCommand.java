package com.alan.autoPunish.commands;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.listeners.ChatListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class StaffChatCommand implements CommandExecutor, TabCompleter {
    private final AutoPunish plugin;
    private final ChatListener chatListener;

    public StaffChatCommand(AutoPunish plugin, ChatListener chatListener) {
        this.plugin = plugin;
        this.chatListener = chatListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!player.hasPermission("autopunish.staff.chat")) {
            player.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§cUsage: /" + label + " <message>");
            player.sendMessage("§cExample: /" + label + " Player is breaking rules in spawn");
            return true;
        }

        // Join all arguments into a single message
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            messageBuilder.append(args[i]);
            if (i < args.length - 1) {
                messageBuilder.append(" ");
            }
        }

        String message = messageBuilder.toString();

        // Handle the staff chat message
        chatListener.handleStaffChatCommand(player, message);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // No tab completion needed for this command
        return new ArrayList<>();
    }
}
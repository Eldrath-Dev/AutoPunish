package com.alan.autoPunish.listeners;

import com.alan.autoPunish.AutoPunish;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {
    private final AutoPunish plugin;

    public ChatListener(AutoPunish plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Check if player is muted
        if (player.hasPermission("autopunish.muted")) {
            event.setCancelled(true);
            player.sendMessage("§cYou are currently muted and cannot chat.");
        }
    }
}
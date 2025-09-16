package com.alan.autoPunish.listeners;

import com.alan.autoPunish.AutoPunish;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.logging.Level;

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
            return;
        }

        // NEW: Broadcast chat message to web panel chat system
        try {
            String message = "[" + player.getWorld().getName() + "] " + player.getName() + ": " + event.getMessage();
            boolean success = plugin.getDatabaseManager().saveChatMessage(
                    "[In-Game] " + player.getName(),
                    player.getUniqueId().toString(),
                    event.getMessage()
            );

            if (success) {
                plugin.getLogger().info("Broadcasting chat message to web panel: " + message);
                // Optionally broadcast to staff in-game
                // plugin.getServer().broadcastMessage("§6[In-Game Chat] §f" + message);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save chat message to web panel: " + e.getMessage(), e);
        }
    }

    // NEW: Handle player join events
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Announce player join to web chat
        try {
            boolean success = plugin.getDatabaseManager().saveChatMessage(
                    "[System]",
                    "system",
                    player.getName() + " joined the game"
            );

            if (success) {
                plugin.getLogger().info("Announced player join to web chat: " + player.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to announce player join to web chat: " + e.getMessage(), e);
        }
    }

    // NEW: Handle player quit events
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Announce player quit to web chat
        try {
            boolean success = plugin.getDatabaseManager().saveChatMessage(
                    "[System]",
                    "system",
                    player.getName() + " left the game"
            );

            if (success) {
                plugin.getLogger().info("Announced player quit to web chat: " + player.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to announce player quit to web chat: " + e.getMessage(), e);
        }
    }

    // NEW: Staff chat command handler
    public void handleStaffChatCommand(Player player, String message) {
        if (message == null || message.trim().isEmpty()) {
            player.sendMessage("§cUsage: /staffchat <message>");
            return;
        }

        // Save message to web panel chat
        try {
            boolean success = plugin.getDatabaseManager().saveChatMessage(
                    player.getName(),
                    player.getUniqueId().toString(),
                    message
            );

            if (success) {
                // Broadcast to web panel
                plugin.getLogger().info("Staff chat message sent to web panel: " + player.getName() + ": " + message);

                // Broadcast to in-game staff
                broadcastToStaff("§6[Staff Chat] §e" + player.getName() + ": §f" + message);

                player.sendMessage("§a[Staff Chat] §fMessage sent successfully!");
            } else {
                player.sendMessage("§cFailed to send staff chat message. Please try again.");
                plugin.getLogger().warning("Failed to save staff chat message to web panel");
            }
        } catch (Exception e) {
            player.sendMessage("§cFailed to send staff chat message: " + e.getMessage());
            plugin.getLogger().log(Level.SEVERE, "Error saving staff chat message: " + e.getMessage(), e);
        }
    }

    // NEW: Broadcast message to online staff members
    private void broadcastToStaff(String message) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.hasPermission("autopunish.staff.chat")) {
                player.sendMessage(message);
            }
        }
    }
}
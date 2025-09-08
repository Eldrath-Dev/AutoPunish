package com.alan.autoPunish.api.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Event triggered when a player's punishment history is reset
 */
public class PlayerHistoryResetEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerUuid;
    private final String playerName;
    private final CommandSender resetter;

    public PlayerHistoryResetEvent(UUID playerUuid, String playerName, CommandSender resetter) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.resetter = resetter;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public CommandSender getResetter() {
        return resetter;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
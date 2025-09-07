package com.alan.autoPunish.api.events;

import com.alan.autoPunish.models.Punishment;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event triggered before a punishment is applied to a player
 */
public class PrePunishmentEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final OfflinePlayer target;
    private final String rule;
    private String type;
    private String duration;
    private final Player issuer;
    private boolean cancelled;

    public PrePunishmentEvent(OfflinePlayer target, String rule, String type, String duration, Player issuer) {
        this.target = target;
        this.rule = rule;
        this.type = type;
        this.duration = duration;
        this.issuer = issuer;
        this.cancelled = false;
    }

    public OfflinePlayer getTarget() {
        return target;
    }

    public String getRule() {
        return rule;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public Player getIssuer() {
        return issuer;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
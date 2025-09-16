package com.alan.autoPunish.api.events;

import com.alan.autoPunish.models.Punishment;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event triggered after a punishment has been applied
 */
public class PunishmentAppliedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Punishment punishment;

    public PunishmentAppliedEvent(Punishment punishment) {
        this.punishment = punishment;
    }

    public Punishment getPunishment() {
        return punishment;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
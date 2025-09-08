package com.alan.autoPunish.api.events;

import com.alan.autoPunish.models.QueuedPunishment;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event triggered when a queued punishment is denied
 */
public class PunishmentDeniedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final QueuedPunishment deniedPunishment;
    private final CommandSender denier;

    public PunishmentDeniedEvent(QueuedPunishment deniedPunishment, CommandSender denier) {
        this.deniedPunishment = deniedPunishment;
        this.denier = denier;
    }

    public QueuedPunishment getDeniedPunishment() {
        return deniedPunishment;
    }

    public CommandSender getDenier() {
        return denier;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
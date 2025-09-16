package com.alan.autoPunish.api.events;

import com.alan.autoPunish.models.QueuedPunishment;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event triggered when a queued punishment is approved
 */
public class PunishmentApprovedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final QueuedPunishment approvedPunishment;
    private final CommandSender approver;

    public PunishmentApprovedEvent(QueuedPunishment approvedPunishment, CommandSender approver) {
        this.approvedPunishment = approvedPunishment;
        this.approver = approver;
    }

    public QueuedPunishment getApprovedPunishment() {
        return approvedPunishment;
    }

    public CommandSender getApprover() {
        return approver;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
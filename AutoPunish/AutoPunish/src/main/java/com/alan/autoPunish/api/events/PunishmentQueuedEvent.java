package com.alan.autoPunish.api.events;

import com.alan.autoPunish.models.QueuedPunishment;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event triggered when a punishment is queued for admin approval
 */
public class PunishmentQueuedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final QueuedPunishment queuedPunishment;
    private final CommandSender requester;
    private final int severityScore;

    public PunishmentQueuedEvent(QueuedPunishment queuedPunishment, CommandSender requester, int severityScore) {
        this.queuedPunishment = queuedPunishment;
        this.requester = requester;
        this.severityScore = severityScore;
    }

    public QueuedPunishment getQueuedPunishment() {
        return queuedPunishment;
    }

    public CommandSender getRequester() {
        return requester;
    }

    public int getSeverityScore() {
        return severityScore;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
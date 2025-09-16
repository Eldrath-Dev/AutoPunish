package com.alan.autoPunish.models;

import java.util.Date;
import java.util.UUID;

public class QueuedPunishment {
    private final UUID id;
    private final UUID playerUuid;
    private final String playerName;
    private final String rule;
    private final String type;
    private final String duration;
    private final String staffName;
    private final UUID staffUuid;
    private final Date queuedDate;
    private final String approvalId;

    public QueuedPunishment(UUID playerUuid, String playerName, String rule, String type, String duration,
                            String staffName, UUID staffUuid) {
        this.id = UUID.randomUUID();
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.rule = rule;
        this.type = type;
        this.duration = duration;
        this.staffName = staffName;
        this.staffUuid = staffUuid;
        this.queuedDate = new Date();
        this.approvalId = UUID.randomUUID().toString().substring(0, 8); // Generate a short approval ID
    }

    // New constructor for loading from database
    public QueuedPunishment(UUID id, UUID playerUuid, String playerName, String rule, String type, String duration,
                            String staffName, UUID staffUuid, Date queuedDate, String approvalId) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.rule = rule;
        this.type = type;
        this.duration = duration;
        this.staffName = staffName;
        this.staffUuid = staffUuid;
        this.queuedDate = queuedDate;
        this.approvalId = approvalId;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getRule() {
        return rule;
    }

    public String getType() {
        return type;
    }

    public String getDuration() {
        return duration;
    }

    public String getStaffName() {
        return staffName;
    }

    public UUID getStaffUuid() {
        return staffUuid;
    }

    public Date getQueuedDate() {
        return queuedDate;
    }

    public String getApprovalId() {
        return approvalId;
    }
}
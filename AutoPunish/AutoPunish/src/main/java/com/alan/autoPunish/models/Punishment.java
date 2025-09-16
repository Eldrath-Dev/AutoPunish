package com.alan.autoPunish.models;

import java.util.Date;
import java.util.UUID;

public class Punishment {
    private UUID id;
    private UUID playerUuid;
    private String playerName;
    private String rule;
    private String type;
    private String duration;
    private String staffName;
    private UUID staffUuid;
    private Date date;

    public Punishment(UUID playerUuid, String playerName, String rule, String type, String duration,
                      String staffName, UUID staffUuid) {
        this.id = UUID.randomUUID();
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.rule = rule;
        this.type = type;
        this.duration = duration;
        this.staffName = staffName;
        this.staffUuid = staffUuid;
        this.date = new Date();
    }

    // Constructor for loading from database
    public Punishment(UUID id, UUID playerUuid, String playerName, String rule, String type, String duration,
                      String staffName, UUID staffUuid, Date date) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.rule = rule;
        this.type = type;
        this.duration = duration;
        this.staffName = staffName;
        this.staffUuid = staffUuid;
        this.date = date;
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

    public Date getDate() {
        return date;
    }
}
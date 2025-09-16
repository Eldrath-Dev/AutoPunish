package com.alan.autoPunish.model;

import com.alan.autoPunish.utils.TimeUtil;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single punishment applied to a player
 *
 * @author AlanTheDev
 */
public record Punishment(
        long id,
        UUID playerUuid,
        String playerName,
        String punisher,
        PunishmentType type,
        String reason,
        Instant timestamp,
        Instant expiry,
        boolean active,
        int severity,
        String evidenceLink,
        boolean hidden,
        String ruleId,
        int tier
) {

    /**
     * Check if this punishment is permanent (no expiry)
     */
    public boolean isPermanent() {
        return expiry == null || expiry.isAfter(Instant.MAX);
    }

    /**
     * Check if this punishment is currently active
     */
    public boolean isActive() {
        if (!active) return false;
        if (isPermanent()) return true;
        return Instant.now().isBefore(expiry);
    }

    /**
     * Get remaining time in milliseconds (-1 if permanent or expired)
     */
    public long getRemainingTime() {
        if (!isActive() || isPermanent()) return -1;
        return expiry.toEpochMilli() - Instant.now().toEpochMilli();
    }

    /**
     * Get formatted duration string
     */
    public String getFormattedDuration() {
        if (isPermanent()) return "Permanent";
        if (!isActive()) return "Expired";

        return TimeUtil.formatDuration(getRemainingTime());
    }

    /**
     * Supported punishment types
     */
    public enum PunishmentType {
        WARNING("warning"),
        MUTE("mute"),
        KICK("kick"),
        TEMPBAN("tempban"),
        BAN("ban"),
        IPBAN("ipban"),
        TEMP_IPBAN("tempipban");

        private final String key;

        PunishmentType(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public static PunishmentType fromKey(String key) {
            for (PunishmentType type : values()) {
                if (type.key.equalsIgnoreCase(key)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown punishment type: " + key);
        }
    }

    /**
     * Severity levels for punishments
     */
    public static class Severity {
        public static final int LOW = 1;
        public static final int MEDIUM = 2;
        public static final int HIGH = 3;
        public static final int VERY_HIGH = 4;
        public static final int EXTREME = 5;

        public static String getName(int level) {
            return switch (level) {
                case 1 -> "Low";
                case 2 -> "Medium";
                case 3 -> "High";
                case 4 -> "Very High";
                case 5 -> "Extreme";
                default -> "Unknown";
            };
        }
    }
}
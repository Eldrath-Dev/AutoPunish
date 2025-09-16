package com.alan.autoPunish.model;

import java.time.Instant;
import java.util.List;

/**
 * Defines a punishment rule with multiple severity tiers
 *
 * @author AlanTheDev
 */
public class PunishmentRule {

    private final String id;
    private final String name;
    private final String description;
    private final List<RuleTier> tiers;
    private final boolean enabled;

    /**
     * Constructor
     */
    public PunishmentRule(String id, String name, String description,
                          List<RuleTier> tiers, boolean enabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.tiers = List.copyOf(tiers);
        this.enabled = enabled;
    }

    /**
     * Get the appropriate tier for the given number of violations
     */
    public RuleTier getTierForViolations(int violationCount) {
        if (!enabled || tiers.isEmpty()) {
            return null;
        }

        return tiers.stream()
                .filter(tier -> violationCount >= tier.threshold())
                .max((t1, t2) -> Integer.compare(t1.threshold(), t2.threshold()))
                .orElse(tiers.get(0)); // Return lowest tier as fallback
    }

    /**
     * Check if this rule should trigger a queue review
     */
    public boolean requiresQueueReview(int violationCount, int maxTier) {
        RuleTier tier = getTierForViolations(violationCount);
        if (tier == null) return false;

        int tierLevel = tiers.indexOf(tier) + 1;
        return tierLevel >= maxTier - 1; // Last two tiers require review
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<RuleTier> getTiers() { return tiers; }
    public boolean isEnabled() { return enabled; }

    /**
     * Represents a single tier within a rule
     */
    public record RuleTier(
            int threshold,
            String duration,
            Punishment.PunishmentType type,
            int severity,
            String reason
    ) {
        /**
         * Parse duration string to milliseconds
         */
        public long parseDuration() {
            return TimeUtil.parseDuration(duration);
        }

        /**
         * Calculate expiry timestamp
         */
        public Instant calculateExpiry(Instant startTime) {
            long durationMs = parseDuration();
            if (durationMs == -1) { // Permanent
                return Instant.MAX;
            }
            return startTime.plusMillis(durationMs);
        }
    }
}
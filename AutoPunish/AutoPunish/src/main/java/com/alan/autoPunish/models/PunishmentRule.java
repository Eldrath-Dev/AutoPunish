package com.alan.autoPunish.models;

import java.util.List;
import java.util.Map;

public class PunishmentRule {
    private final String name;
    private final List<Map<String, String>> punishmentTiers;

    public PunishmentRule(String name, List<Map<String, String>> punishmentTiers) {
        this.name = name;
        this.punishmentTiers = punishmentTiers;
    }

    public String getName() {
        return name;
    }

    public List<Map<String, String>> getPunishmentTiers() {
        return punishmentTiers;
    }

    public Map<String, String> getTier(int offenseNumber) {
        if (offenseNumber <= 0) {
            return null;
        }

        // If the offense number exceeds the number of tiers, return the last tier
        if (offenseNumber > punishmentTiers.size()) {
            return punishmentTiers.get(punishmentTiers.size() - 1);
        }

        return punishmentTiers.get(offenseNumber - 1);
    }
}
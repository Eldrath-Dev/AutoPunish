package com.alan.autoPunish.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PunishmentRule {
    private final String name;
    private final List<Map<String, String>> punishmentTiers;

    public PunishmentRule(String name, List<Map<String, String>> punishmentTiers) {
        this.name = name;
        this.punishmentTiers = punishmentTiers != null ? new ArrayList<>(punishmentTiers) : new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public List<Map<String, String>> getPunishmentTiers() {
        return punishmentTiers;
    }

    public Map<String, String> getTier(int offenseNumber) {
        if (punishmentTiers.isEmpty()) {
            return null; // No tiers defined
        }
        if (offenseNumber <= 0) {
            offenseNumber = 1; // Default to first tier if input is invalid
        }

        // If the offense number exceeds the number of tiers, return the last tier
        if (offenseNumber > punishmentTiers.size()) {
            return punishmentTiers.get(punishmentTiers.size() - 1);
        }

        return punishmentTiers.get(offenseNumber - 1);
    }

    // *** NEW: Method to add a new tier ***
    public void addTier(Map<String, String> tier) {
        this.punishmentTiers.add(tier);
    }

    // *** NEW: Method to remove a tier by its index ***
    public void removeTier(int index) {
        if (index >= 0 && index < this.punishmentTiers.size()) {
            this.punishmentTiers.remove(index);
        }
    }

    // *** NEW: Method to modify an existing tier ***
    public void modifyTier(int index, Map<String, String> newTier) {
        if (index >= 0 && index < this.punishmentTiers.size()) {
            this.punishmentTiers.set(index, newTier);
        }
    }
}
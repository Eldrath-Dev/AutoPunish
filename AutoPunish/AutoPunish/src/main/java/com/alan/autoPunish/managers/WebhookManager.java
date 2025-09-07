package com.alan.autoPunish.managers;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.models.Punishment;
import com.alan.autoPunish.utils.TimeUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebhookManager {
    private final AutoPunish plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final SimpleDateFormat dateFormat;

    public WebhookManager(AutoPunish plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    // Overload the method to maintain backward compatibility
    public void sendPunishmentWebhook(Punishment punishment) {
        sendPunishmentWebhook(punishment, 1, null);
    }

    // Overload for rule-specific punishments only
    public void sendPunishmentWebhook(Punishment punishment, int tier, List<Punishment> rulePunishments) {
        sendPunishmentWebhook(punishment, tier, rulePunishments, null, 0);
    }

    // Main method with all punishment information
    public void sendPunishmentWebhook(Punishment punishment, int tier, List<Punishment> rulePunishments,
                                      List<Punishment> allPunishments, int severityScore) {
        String webhookUrl = configManager.getDiscordWebhook();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            logger.warning("Discord webhook URL is not configured.");
            return;
        }

        String jsonPayload = formatPunishmentForDiscord(punishment, tier, rulePunishments, allPunishments, severityScore);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "AutoPunish/1.0");
                connection.setDoOutput(true);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    logger.info("Punishment webhook sent successfully");
                } else {
                    logger.warning("Failed to send webhook. Response code: " + responseCode);
                }

                connection.disconnect();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error sending punishment webhook: " + e.getMessage(), e);
            }
        });
    }

    private String formatPunishmentForDiscord(Punishment punishment, int tier,
                                              List<Punishment> rulePunishments,
                                              List<Punishment> allPunishments,
                                              int severityScore) {
        String formattedDuration;
        if (punishment.getDuration().equals("0")) {
            formattedDuration = "Permanent";
        } else {
            long durationMillis = TimeUtil.parseDuration(punishment.getDuration());
            formattedDuration = TimeUtil.formatDuration(durationMillis);
        }

        // Format rule-specific violations
        StringBuilder ruleViolationsBuilder = new StringBuilder();
        if (rulePunishments != null && !rulePunishments.isEmpty()) {
            ruleViolationsBuilder.append("\\n\\n**Previous Violations for ").append(punishment.getRule()).append(":**");
            int count = 1;
            for (Punishment prev : rulePunishments) {
                String prevDuration = prev.getDuration().equals("0") ?
                        "Permanent" : prev.getDuration();

                ruleViolationsBuilder.append("\\n")
                        .append(count).append(". ")
                        .append(prev.getType().substring(0, 1).toUpperCase())
                        .append(prev.getType().substring(1))
                        .append(" (").append(prevDuration).append(")")
                        .append(" - ").append(dateFormat.format(prev.getDate()));
                count++;
            }
        }

        // Format recent violations from ALL rules (max 5)
        StringBuilder recentViolationsBuilder = new StringBuilder();
        if (allPunishments != null && allPunishments.size() > 0) {
            recentViolationsBuilder.append("\\n\\n**Recent Violations (All Rules):**");

            // Sort by date (most recent first) and limit to last 5
            allPunishments.sort((p1, p2) -> p2.getDate().compareTo(p1.getDate()));
            int displayCount = Math.min(allPunishments.size(), 5);

            for (int i = 0; i < displayCount; i++) {
                Punishment prev = allPunishments.get(i);

                // Skip the current punishment if it's in the list
                if (prev.getId().equals(punishment.getId())) {
                    continue;
                }

                String prevDuration = prev.getDuration().equals("0") ?
                        "Permanent" : prev.getDuration();

                recentViolationsBuilder.append("\\n")
                        .append(i+1).append(". ")
                        .append(prev.getRule())
                        .append(" - ")
                        .append(prev.getType().substring(0, 1).toUpperCase())
                        .append(prev.getType().substring(1))
                        .append(" (").append(prevDuration).append(")")
                        .append(" - ").append(dateFormat.format(prev.getDate()));
            }
        }

        String content = "**Punishment Issued**\\n" +
                "Player: " + punishment.getPlayerName() + "\\n" +
                "Rule: " + punishment.getRule() + "\\n" +
                "Offense #: " + tier + "\\n" +
                "Punishment: " + punishment.getType().substring(0, 1).toUpperCase() +
                punishment.getType().substring(1) +
                (formattedDuration.equals("Permanent") ? " (Permanent)" : " (" + punishment.getDuration() + ")") + "\\n" +
                "Staff: " + punishment.getStaffName() + "\\n" +
                "Date: " + dateFormat.format(punishment.getDate());

        // Add severity score if available
        if (severityScore > 0) {
            content += "\\nTotal Severity Score: " + severityScore;
        }

        // Add rule-specific history
        if (rulePunishments == null || rulePunishments.isEmpty()) {
            content += "\\n\\n*First offense for this rule.*";
        } else {
            content += ruleViolationsBuilder.toString();
        }

        // Add recent violations from all rules
        if (allPunishments != null && allPunishments.size() > 0) {
            content += recentViolationsBuilder.toString();
        }

        content += "\\n\\n*Automated punishment determined by escalation system.*";

        // Format as JSON for Discord webhook
        return "{\"content\":\"" + content + "\"}";
    }
}
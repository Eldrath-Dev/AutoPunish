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

    public void sendPunishmentWebhook(Punishment punishment) {
        String webhookUrl = configManager.getDiscordWebhook();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            logger.warning("Discord webhook URL is not configured.");
            return;
        }

        String jsonPayload = formatPunishmentForDiscord(punishment);

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

    private String formatPunishmentForDiscord(Punishment punishment) {
        String formattedDuration;
        if (punishment.getDuration().equals("0")) {
            formattedDuration = "Permanent";
        } else {
            long durationMillis = TimeUtil.parseDuration(punishment.getDuration());
            formattedDuration = TimeUtil.formatDuration(durationMillis);
        }

        String content = "**Punishment Issued**\\n" +
                "Player: " + punishment.getPlayerName() + "\\n" +
                "Rule: " + punishment.getRule() + "\\n" +
                "Punishment: " + punishment.getType().substring(0, 1).toUpperCase() +
                punishment.getType().substring(1) +
                (formattedDuration.equals("Permanent") ? " (Permanent)" : " (" + punishment.getDuration() + ")") + "\\n" +
                "Staff: " + punishment.getStaffName() + "\\n" +
                "Date: " + dateFormat.format(punishment.getDate());

        // Format as JSON for Discord webhook
        return "{\"content\":\"" + content + "\"}";
    }
}
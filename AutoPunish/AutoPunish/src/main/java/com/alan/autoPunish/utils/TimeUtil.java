package com.alan.autoPunish.utils;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtil {
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([mhdw])");

    /**
     * Convert a duration string (e.g., "1d", "30m") to milliseconds.
     *
     * @param duration The duration string
     * @return The duration in milliseconds, or 0 for permanent/invalid duration
     */
    public static long parseDuration(String duration) {
        if (duration == null || duration.equals("0") || duration.isEmpty()) {
            return 0; // Permanent/no duration
        }

        Matcher matcher = TIME_PATTERN.matcher(duration);
        if (!matcher.matches()) {
            return 0; // Invalid format
        }

        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        switch (unit) {
            case "m":
                return TimeUnit.MINUTES.toMillis(amount);
            case "h":
                return TimeUnit.HOURS.toMillis(amount);
            case "d":
                return TimeUnit.DAYS.toMillis(amount);
            case "w":
                return TimeUnit.DAYS.toMillis(amount * 7);
            default:
                return 0;
        }
    }

    /**
     * Format milliseconds to a human-readable duration string.
     *
     * @param millis The duration in milliseconds
     * @return A formatted duration string (e.g., "1 day, 2 hours")
     */
    public static String formatDuration(long millis) {
        if (millis <= 0) {
            return "Permanent";
        }

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(days == 1 ? " day" : " days");
        }
        if (hours > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(hours).append(hours == 1 ? " hour" : " hours");
        }
        if (minutes > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(minutes).append(minutes == 1 ? " minute" : " minutes");
        }

        return sb.toString();
    }
}
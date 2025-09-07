package com.alan.autoPunish.utils;

import com.alan.autoPunish.AutoPunish;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public class ConfigUtils {
    private static AutoPunish plugin;

    public static void init(AutoPunish instance) {
        plugin = instance;
        ensureDefaults();
    }

    private static void ensureDefaults() {
        FileConfiguration config = plugin.getConfig();

        // Set defaults if they don't exist
        if (!config.contains("approval-system.enabled")) {
            config.set("approval-system.enabled", true);
        }

        if (!config.contains("approval-system.require-approval-after")) {
            config.set("approval-system.require-approval-after", 7);
        }

        if (!config.contains("approval-system.bypass-approval-permissions")) {
            List<String> defaultBypass = new ArrayList<>();
            defaultBypass.add("autopunish.bypass.approval");
            defaultBypass.add("autopunish.admin.senior");
            config.set("approval-system.bypass-approval-permissions", defaultBypass);
        }

        if (!config.contains("approval-system.notify-admin-on-auto-approved")) {
            config.set("approval-system.notify-admin-on-auto-approved", true);
        }

        plugin.saveConfig();
    }

    public static boolean isApprovalSystemEnabled() {
        return plugin.getConfig().getBoolean("approval-system.enabled", true);
    }

    public static int getRequireApprovalAfterDays() {
        return plugin.getConfig().getInt("approval-system.require-approval-after", 7);
    }

    public static List<String> getBypassApprovalPermissions() {
        return plugin.getConfig().getStringList("approval-system.bypass-approval-permissions");
    }

    public static boolean shouldNotifyAdminOnAutoApproved() {
        return plugin.getConfig().getBoolean("approval-system.notify-admin-on-auto-approved", true);
    }
}
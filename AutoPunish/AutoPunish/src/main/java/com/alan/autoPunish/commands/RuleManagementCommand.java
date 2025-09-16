package com.alan.autoPunish.commands;

import com.alan.autoPunish.AutoPunish;
import com.alan.autoPunish.managers.ConfigManager;
import com.alan.autoPunish.models.PunishmentRule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RuleManagementCommand implements CommandExecutor, TabCompleter {
    private final AutoPunish plugin;
    private final ConfigManager configManager;

    public RuleManagementCommand(AutoPunish plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("autopunish.admin.rules")) {
            sender.sendMessage("§cYou don't have permission to manage rules.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "list":
                listRules(sender);
                break;
            case "info":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /rule listtiers <ruleName>");
                    return true;
                }
                listTiers(sender, args[1]);
                break;
            case "create":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /rule create <ruleName>");
                    return true;
                }
                createRule(sender, args[1]);
                break;
            case "delete":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /rule delete <ruleName>");
                    return true;
                }
                deleteRule(sender, args[1]);
                break;
            case "addtier":
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /rule addtier <ruleName> <type> <duration>");
                    return true;
                }
                addTier(sender, args[1], args[2], args[3]);
                break;
            case "removetier":
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /rule removetier <ruleName> <tierNumber>");
                    return true;
                }
                removeTier(sender, args[1], args[2]);
                break;
            case "edittier":
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: /rule edittier <ruleName> <tierNumber> <newType> <newDuration>");
                    return true;
                }
                editTier(sender, args[1], args[2], args[3], args[4]);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6--- AutoPunish Rule Management ---");
        sender.sendMessage("§e/rule list §7- Lists all punishment rules.");
        sender.sendMessage("§e/rule info <ruleName> §7- Shows tiers for a rule.");
        sender.sendMessage("§e/rule create <ruleName> §7- Creates a new empty rule.");
        sender.sendMessage("§e/rule delete <ruleName> §7- Deletes a rule and its tiers.");
        sender.sendMessage("§e/rule addtier <ruleName> <type> <duration> §7- Adds a new tier to a rule.");
        sender.sendMessage("§e/rule removetier <ruleName> <tierNumber> §7- Removes a tier from a rule.");
        sender.sendMessage("§e/rule edittier <ruleName> <tierNumber> <newType> <newDuration> §7- Edits an existing tier.");
    }

    private void listRules(CommandSender sender) {
        Map<String, PunishmentRule> rules = configManager.getRules();
        if (rules.isEmpty()) {
            sender.sendMessage("§cNo rules are configured.");
            return;
        }
        sender.sendMessage("§aAvailable Rules:");
        for (String ruleName : rules.keySet()) {
            sender.sendMessage("§7- §f" + ruleName);
        }
    }

    private void listTiers(CommandSender sender, String ruleName) {
        PunishmentRule rule = configManager.getRule(ruleName);
        if (rule == null) {
            sender.sendMessage("§cRule '" + ruleName + "' does not exist.");
            return;
        }
        sender.sendMessage("§aTiers for rule '" + ruleName + "':");
        List<Map<String, String>> tiers = rule.getPunishmentTiers();
        if (tiers.isEmpty()) {
            sender.sendMessage("§7No tiers defined for this rule.");
            return;
        }
        for (int i = 0; i < tiers.size(); i++) {
            Map<String, String> tier = tiers.get(i);
            sender.sendMessage(String.format("§7%d. Type: §f%s§7, Duration: §f%s",
                    i + 1, tier.get("type"), tier.get("duration")));
        }
    }

    private void createRule(CommandSender sender, String ruleName) {
        if (configManager.getRule(ruleName) != null) {
            sender.sendMessage("§cRule '" + ruleName + "' already exists.");
            return;
        }
        configManager.addRule(ruleName);
        sender.sendMessage("§aSuccessfully created new rule: " + ruleName);
    }

    private void deleteRule(CommandSender sender, String ruleName) {
        if (configManager.getRule(ruleName) == null) {
            sender.sendMessage("§cRule '" + ruleName + "' does not exist.");
            return;
        }
        configManager.deleteRule(ruleName);
        sender.sendMessage("§aSuccessfully deleted rule: " + ruleName);
    }

    private void addTier(CommandSender sender, String ruleName, String type, String duration) {
        if (configManager.getRule(ruleName) == null) {
            sender.sendMessage("§cRule '" + ruleName + "' does not exist. Create it first with /rule create.");
            return;
        }
        Map<String, String> newTier = new HashMap<>();
        newTier.put("type", type);
        newTier.put("duration", duration);
        configManager.addTierToRule(ruleName, newTier);
        sender.sendMessage("§aSuccessfully added new tier to rule: " + ruleName);
        listTiers(sender, ruleName);
    }

    private void removeTier(CommandSender sender, String ruleName, String tierStr) {
        if (configManager.getRule(ruleName) == null) {
            sender.sendMessage("§cRule '" + ruleName + "' does not exist.");
            return;
        }
        try {
            int tierIndex = Integer.parseInt(tierStr) - 1; // User provides 1-based index
            if (configManager.removeTierFromRule(ruleName, tierIndex)) {
                sender.sendMessage("§aSuccessfully removed tier " + tierStr + " from rule: " + ruleName);
                listTiers(sender, ruleName);
            } else {
                sender.sendMessage("§cInvalid tier number. Please check '/rule info " + ruleName + "'.");
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cTier must be a number.");
        }
    }

    private void editTier(CommandSender sender, String ruleName, String tierStr, String newType, String newDuration) {
        if (configManager.getRule(ruleName) == null) {
            sender.sendMessage("§cRule '" + ruleName + "' does not exist.");
            return;
        }
        try {
            int tierIndex = Integer.parseInt(tierStr) - 1; // User provides 1-based index
            Map<String, String> newTier = new HashMap<>();
            newTier.put("type", newType);
            newTier.put("duration", newDuration);
            if (configManager.editTierInRule(ruleName, tierIndex, newTier)) {
                sender.sendMessage("§aSuccessfully edited tier " + tierStr + " in rule: " + ruleName);
                listTiers(sender, ruleName);
            } else {
                sender.sendMessage("§cInvalid tier number. Please check '/rule info " + ruleName + "'.");
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cTier must be a number.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("autopunish.admin.rules")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("list", "info", "create", "delete", "addtier", "removetier", "edittier").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && !args[0].equalsIgnoreCase("create")) {
            return configManager.getRules().keySet().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
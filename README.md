

AutoPunish is a powerful Minecraft punishment management system that automatically handles player infractions with a configurable escalation system. This plugin allows server administrators to enforce rules consistently while maintaining detailed logs of all player violations.

Features
Automatic Punishment Escalation: Punishments automatically increase in severity based on a player's history with each rule
Configurable Rules and Punishments: Fully customizable rules and tiered punishment system
Multiple Punishment Types: Support for warnings, mutes, temporary bans, and permanent bans
Detailed Punishment History: View complete punishment histories for any player
Database Storage: Save all punishment data to SQLite or MySQL
Discord Integration: Send punishment notifications to a Discord channel via webhook
Simple Commands: Easy-to-use commands for staff members
Installation
Download the latest version of AutoPunish from the releases page
Place the JAR file in your server's plugins folder
Start or restart your server
Configure the config.yml file to your preferences
Use /punishreload to apply changes without restarting
Configuration
The plugin uses a configuration file (config.yml) which is created on first run. Here's an explanation of the key settings:

YAML

discord-webhook: "https://discord.com/api/webhooks/your-webhook-url"

storage:
  type: "sqlite"  # or "mysql"
  mysql:
    host: "localhost"
    port: 3306
    database: "punishments"
    username: "root"
    password: "password"

rules:
  rule_name:
    - type: "warn"  # Type of punishment
      duration: "0"  # Duration (0 for permanent or no duration)
    - type: "mute"
      duration: "1h"  # Format: number + unit (m/h/d/w)
    # Additional tiers...
Duration Format
Durations use a simple format:

0 - Permanent/no duration
[number]m - Minutes (e.g., 10m = 10 minutes)
[number]h - Hours (e.g., 2h = 2 hours)
[number]d - Days (e.g., 3d = 3 days)
[number]w - Weeks (e.g., 1w = 1 week)
Default Rules
The plugin comes with several pre-configured rules:

minor_chat_violations
spam
impersonation
advertising
hacking
griefing
harassment
Each rule has its own escalation path defined in the configuration.

Commands
Command	Description	Permission
/punish <player> <rule>	Punish a player for breaking a rule	autopunish.punish
/punishments <player>	View a player's punishment history	autopunish.punishments
/punishreload	Reload the plugin configuration	autopunish.reload
Permissions
Permission	Description	Default
autopunish.punish	Allows using the punish command	op
autopunish.punishments	Allows viewing punishment history	op
autopunish.reload	Allows reloading the configuration	op
autopunish.muted	Flag for muted players (do not assign manually)	false
Dependencies
Required: Paper/Spigot 1.20+
Recommended: LuckPerms (for mute functionality)
Examples
Example Punishment Escalation
A player who violates the "spam" rule repeatedly might face:

First offense: Warning
Second offense: 10-minute mute
Third offense: 30-minute mute
Fourth offense: 2-hour mute
Fifth offense: 1-day mute
Subsequent offenses: 1-day mute (repeating)
Discord Notification Format
text

**Punishment Issued**
Player: PlayerName
Rule: spam
Punishment: Mute (10m)
Staff: AdminName
Date: 2023-09-06 15:30:45
Database Schema
The plugin stores punishment data in a table with the following structure:

Column	Type	Description
id	VARCHAR(36)	Unique identifier for the punishment
player_uuid	VARCHAR(36)	UUID of the punished player
player_name	VARCHAR(16)	Name of the punished player
rule	VARCHAR(50)	Rule that was violated
type	VARCHAR(20)	Type of punishment (warn, mute, ban)
duration	VARCHAR(20)	Duration of the punishment
staff_name	VARCHAR(16)	Name of the staff member who issued the punishment
staff_uuid	VARCHAR(36)	UUID of the staff member
date	TIMESTAMP	Date and time when the punishment was issued
License
This project is licensed under the MIT License - see the LICENSE file for details.

Support
If you encounter any issues or have suggestions, please create an issue on GitHub.

Made with ❤️ by AlanDev

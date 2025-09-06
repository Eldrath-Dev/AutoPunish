AutoPunish
Version: 1.0.0
API Version: 1.21

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


Examples
Example Punishment Escalation
A player who violates the "spam" rule repeatedly might face:

First offense: Warning
Second offense: 10-minute mute
Third offense: 30-minute mute
Fourth offense: 2-hour mute
Fifth offense: 1-day mute
Subsequent offenses: 1-day mute (repeating)


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

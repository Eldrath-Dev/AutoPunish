# AutoPunish - Advanced Minecraft Punishment System


================================================

AutoPunish is a comprehensive punishment management system for Minecraft servers that automatically handles player infractions with a configurable escalation system. This plugin allows server administrators to enforce rules consistently while maintaining detailed logs of all player violations.

Features
--------
- Automatic Punishment Escalation
  * Configurable Rules: Define custom punishment rules with tiered escalation
  * Severity Scoring: Players accumulate severity points based on violation types
  * Progressive Punishments: Warnings → Mutes → Bans with increasing severity
  * Cross-Rule Tracking: Considers all violations when determining punishment tier

- Admin Approval System
  * Severe Punishment Approval: Requires admin approval for permanent bans or long-term punishments
  * Staff Rank Bypass: Higher-ranking staff can bypass approval requirements
  * Web Panel Integration: Approve/deny punishments through an intuitive web interface
  * Discord Webhooks: Real-time notifications for pending approvals

- Web Management Panel
  * Responsive Design: Works on desktop and mobile devices
  * Real-time Updates: Auto-refreshes every minute without manual refresh
  * Approval Management: Approve or deny pending punishments with one click
  * Punishment History: View all recent punishments and player records
  * Player Search: Quickly find player information and violation history

- Advanced Tracking & Analytics
  * Persistent History: SQLite/MySQL database storage for punishment records
  * Severity Scoring: Weighted scoring system based on punishment types and recency
  * Time Decay: Older violations become less impactful over time
  * Comprehensive Logging: Detailed logs of all punishment actions

- Developer API
  * Event-Driven Architecture: Listen for punishment events in other plugins
  * Programmatic Control: Apply punishments and check history via API
  * Extensible Design: Easy integration with custom punishment systems
  * Documentation: Comprehensive API documentation for developers

Installation
------------
1. Download the latest release from the releases page
2. Place the JAR file in your server's plugins folder
3. Start or restart your server
4. Configure the config.yml file to your preferences
5. Use /punishreload to apply changes without restarting

Configuration
-------------
See `config.yml` for full configuration. Example:

```
# Discord webhook for punishment notifications
discord-webhook: "https://discord.com/api/webhooks/your-webhook-url"

# Database configuration
storage:
  type: "sqlite"  # or "mysql"
  mysql:
    host: "localhost"
    port: 3306
    database: "punishments"
    username: "root"
    password: "password"
```

Commands
--------
- /punish <player> <rule> : Punish a player for breaking a rule (Permission: autopunish.punish)
- /punishments <player> : View a player's punishment history (Permission: autopunish.view.history)
- /severity <player> : Check a player's severity score and tier (Permission: autopunish.view.severity)
- /punishadmin <list|approve|deny> [approvalId] : Manage pending punishments (Permission: autopunish.admin.approve)
- /punishreload : Reload the configuration (Permission: autopunish.admin.reload)
- /resethistory <player> : Reset a player's violation history (Permission: autopunish.admin.reset)

Permissions
-----------
- autopunish.punish : Allows using the punish command
- autopunish.view.history : Allows viewing punishment history
- autopunish.view.severity : Allows checking punishment severity scores
- autopunish.admin.reload : Allows reloading the configuration
- autopunish.admin.approve : Allows approving or denying punishments
- autopunish.admin.reset : Allows resetting player violation history
- autopunish.admin.senior : Senior admin permission with bypass capabilities
- autopunish.bypass.approval : Allows bypassing the punishment approval system
- autopunish.staff : Basic staff permission set
- autopunish.admin.* : Grants all admin permissions
- autopunish.* : Grants all AutoPunish permissions

Web Panel
---------
- Access at: http://your-server-ip:8080
- Login system with configurable credentials
- Manage approvals, punishment history, and player search
- Responsive design with auto-refresh

Developer API
-------------
- Events: PunishmentQueuedEvent, PunishmentApprovedEvent, PunishmentDeniedEvent, PlayerHistoryResetEvent
- Methods: Get history, calculate severity score, get punishment tier, apply punishment, manage queued punishments

Database Schema
---------------
Punishments Table:
- id (UUID)
- player_uuid
- player_name
- rule
- type
- duration
- staff_name
- staff_uuid
- date

Queued Punishments Table:
- id (UUID)
- player_uuid
- player_name
- rule
- type
- duration
- staff_name
- staff_uuid
- queued_date
- approval_id

Dependencies
------------
- Required: Paper/Spigot 1.21+
- Optional: LuckPerms (for mute/demote functionality)
- Optional: MySQL Server

License
-------
This project is licensed under the MIT License - see the LICENSE file for details.

Authors
-------
- AlanDev - Initial work - yourusername
- Contributors listed in the GitHub repo

Acknowledgments
---------------
- Thanks to the PaperMC team
- Inspiration from various punishment plugins
- The open-source community

--------------------------------------------------
Made with love by the Minecraft Community

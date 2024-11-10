# ShameSkin Plugin

**ShameSkin** is a Minecraft plugin designed to discipline players by temporarily changing their skin to one from a configurable list. This plugin automatically restores the player’s original skin once the punishment expires, making it ideal for server moderation and encouraging rule-following behavior.

## Features

- Change the skin of players who break server rules to a random skin from the configuration list.
- Automatically remove punishment and restore the player’s original skin when the time expires.
- Integrates with [SkinsRestorer](https://www.spigotmc.org/resources/skinsrestorer.2124/) for skin management.
- Integrates with [LuckPerms](https://www.spigotmc.org/resources/luckperms.28140/) for permission management.
- Option to block skin-changing commands for punished players.
- Kick a player if a proxy is used (Configurable in settings).

## Installation

1. Download and install [LuckPerms](https://www.spigotmc.org/resources/luckperms.28140/) and [SkinsRestorer](https://www.spigotmc.org/resources/skinsrestorer.2124/).
2. Download the ShameSkin plugin and place it in your server’s `plugins` folder.
3. Start the server to generate configuration files.
4. Customize the `config.yml` file to fit your server's needs.

## Commands

- **`/shameskin reload`** — Reloads the plugin configuration.
- **`/shameskin remove [player]`** — Removes punishment from a specified player and restores their original skin.
- **`/shameskin [player] [time] [reason]`** — Applies punishment to a player for the specified time with an optional reason.

### Example

```plaintext
/shameskin Steve 1d Unsportsmanlike behavior
```

This command punishes the player **Steve** for 1 day with the reason “Unsportsmanlike behavior.”

## Permissions

- **`shameskin.mod`** — Allows use of the `/shameskin` commands.
- **`shameskin.reload`** — Allows reloading of the plugin configuration.
- **`shameskin.remove`** — Allows removal of player punishments.

### Example Permissions (LuckPerms)

```plaintext
/lp user [Player] permission set shameskin.mod true
/lp user [Player] permission set shameskin.reload true
/lp user [Player] permission set shameskin.remove true
```

## Configuration (config.yml)

In `config.yml`, you can set up the list of punishment skins, message formats, time formats, and other parameters.

### Key Parameters:

- **skins** — List of URLs for the skins that will be applied as punishment.
- **messages** — Customizable messages sent to players and admins.
- **useLegacySkinChange** — Option to enable a fallback skin change method if the API is unavailable.

## Important

If the SkinsRestorer API is unavailable, the plugin will automatically switch to an alternative skin change method to ensure that punishments are still enforced.

---

With **ShameSkin**, you can maintain order on your server with a playful but effective form of punishment, helping to improve player behavior!

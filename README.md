# SoftBan

SoftBan is a Paper plugin for Minecraft 1.21.11 that adds soft-ban commands backed by independent JSON ban lists.

Unlike vanilla bans, a soft-banned player is allowed to join the server. After joining, ProtocolLib cancels most server-to-client Play packets for that player, so the client behaves like it is stuck on a bad connection: world data does not arrive normally, the player appears to fall in the void, and the connection is expected to time out naturally.

## Requirements

- Paper 1.21.11
- Java 21
- ProtocolLib

ProtocolLib must be installed on the server because SoftBan declares it as a hard dependency.

## Build

```bash
gradle build
```

The plugin jar is written to:

```text
build/libs/softban-1.0.0.jar
```

## Installation

1. Install ProtocolLib in the server's `plugins/` directory.
2. Copy `build/libs/softban-1.0.0.jar` into the server's `plugins/` directory.
3. Restart the server.

SoftBan creates its own data files under:

```text
plugins/SoftBan/softbanned-players.json
plugins/SoftBan/softbanned-ips.json
```

These files use the same general JSON shape as vanilla ban files, but they are independent from `banned-players.json` and `banned-ips.json`.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/softban <player> [reason...]` | Soft-ban a player by UUID/name. | `softban.command.softban` |
| `/softban-ip <ip\|player> [reason...]` | Soft-ban an IP address, or an online player's current IP. | `softban.command.softbanip` |
| `/softbanlist [players\|ips]` | List active player or IP soft bans. | `softban.command.softbanlist` |
| `/softpardon <player>` | Remove a player soft ban. | `softban.command.softpardon` |
| `/softpardon-ip <ip\|player>` | Remove an IP soft ban. | `softban.command.softpardonip` |

All command permissions default to `op`. The wildcard permission is:

```text
softban.command.*
```

## Behavior Notes

- Soft-banned players are not rejected during login.
- Join and quit messages are not hidden.
- The player is still considered online by the server while connected.
- Packet suppression is applied only to players that match a soft-banned UUID/name or IP.
- Unbanning a currently connected player removes the runtime soft-ban mark, but the client may need to reconnect to recover cleanly.

## Storage Format

Player records contain fields compatible with vanilla-style ban entries:

```json
{
  "uuid": "00000000-0000-0000-0000-000000000000",
  "name": "PlayerName",
  "created": "2026-07-04 00:00:00 +0000",
  "source": "Console",
  "expires": "forever",
  "reason": "Banned by an operator."
}
```

IP records use the same structure with an `ip` field instead of `uuid` and `name`.

## License

GPL-3.0. See [LICENSE](LICENSE).

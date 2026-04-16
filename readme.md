# 8b8tCore

Kotlin Folia plugin for 8b8t and anarchy servers.

Built with JDK 21 and Kotlin 1.9.24.

## Features

**Core Commands**
- /home, /sethome, /delhome - Home management
- /tpa, /tpahere, /tpayes, /tpano, /tpacancel, /tpatoggle - TPA system
- /back - Return to last death/location
- /hotspot - Create and teleport to warps
- /msg, /reply - Private messaging
- /ignore, /unignore - Ignore players

**Moderation**
- /vanish - Vanish/unvanish
- /kill, /suicide
- /clearentities - Remove entities in chunks
- /shadowmute - Shadow mute players
- /ban, /kick - Via server commands
- /op whitelist - Only allow specific players to be op
- Anonymous disconnect messages

**Anti-Cheat**
- Anti-Illegal items (bedrock, barriers, spawners, etc.)
- Illegal data/NBT validation
- Overstacked items check
- Illegal enchantments/potions
- Item lore/name validation
- Book content scanning
- Bundle crash protection
- Elytra flight monitoring
- Durability checks
- Attribute modifiers
- Prefilled container detection
- Player effect validation

**Chat System**
- Chat cooldown
- Link blocking (TLD-based)
- Custom prefixes with gradients and animations
- Tab complete filtering
- Shadow mute
- Toggle chat lock

**Death Messages**
- Custom death message system
- Per-cause configurable messages
- PVP death messages
- Entity kill messages
- Cooldown system

**Server Protection**
- Nether roof protection
- World border enforcement
- Entity per chunk limits
- Chest interaction limits
- Map art restrictions
- NBT exploit patch
- Phantom spawn control
- End portal builders
- PVP patches (8b8t Spear PvP meta)
- Boundary monitoring

**Tablist**
- Custom player list display
- Ping display
- Player heads
- TPS info
- Custom header/footer

**Player Settings**
- Toggle join messages
- Toggle prefix visibility
- Toggle announcements
- Toggle death messages
- Toggle leaderboard
- Toggle achievements
- Custom nickname colors
- View distance per permission
- Simulation distance per permission

**Quality of Life**
- /speed - Set fly/walk speed
- /gm - Game mode commands
- /spawn - Teleport to spawn
- /world - World switcher
- /rename, /sign - Item modifications
- /joindate, /lastseen - Player info
- /uptime - Server uptime
- /tpsinfo - TPS and MSPT
- /discord - Discord link
- /cosmetics - Cosmetics menu
- /leaderboard - Toggle vanilla/custom tablist
- /dps - Disable phantom spawning

**Voting**
- VotifierPlus integration
- Vote rewards
- Voter role management
- Legacy player migration

**Utilities**
- Auto announcements
- Violation tracking
- Database storage (SQLite)
- Localization (12 languages)

## Contributing

Issue requests and pull requests will be closed instantly.

Security vulnerabilities: contact@8b8t.me

## Build

```bash
mvn clean package
```

Output: `target/8b8tCore-1.0.0.jar`

## Config

Edit `config.yml` after first run. All settings are documented inline.

## Languages

English, Spanish, French, German, Italian, Portuguese, Russian, Chinese, Japanese, Dutch, Hindi, Arabic, Turkish

## License

Zero warranty. Zero support.

See LICENSE file for details.

# CircuitBreaker v2.4-Aegis

A chunk-based performance sentinel for Minecraft servers.

[Official Website](https://ajaretro.dev/circuitbreaker.html) | [Modrinth Project](https://modrinth.com/project/circuitbreaker) | [Hangar Project](https://hangar.papermc.io/AJA_RETRO/CircuitBreaker) | [GitHub Releases](https://github.com/AJARETRO/CircuitBreaker/releases)

---

CircuitBreaker is an anti-lag plugin for modern Minecraft (Paper 1.21+). Instead of running crude global cleanups that clear all entities, it targets the specific chunk causing physics load, entity build-ups, or packet exploits, letting you isolate lag machines without disrupting legitimate players.

---

## Core Features

* **Chunk Isolation:** Actions are applied only to the chunk causing the issue. All other chunks remain unaffected.
* **Tiered Response:** Uses a multi-strike system to differentiate between temporary resource spikes and malicious lag machines.
* **Non-Destructive Physics Controls:** Does not break blocks or delete player builds. It temporarily pauses physics updates in the affected area.
* **Smart Entity Culling:** An optional module that removes excess entities above a threshold while automatically protecting named mobs, tamed animals, and vehicles.
* **Adaptive Scaling:** Adjusts detection thresholds dynamically depending on live TPS and MSPT metrics.

---

## What's New in v2.4-Aegis

* **AEGIS (Async Event Gate and Isolation Sentinel) System:** Implemented player notifications. When a chunk is frozen, all players inside the 3x3 frozen perimeter receive a chat warning.
* **Configurable Alert Message:** Added `chunk-frozen-message` to `config.yml` to customize the warning format.
* **Proprietary License:** Updated the project under a custom All Rights Reserved Software License Agreement.

### Previous Updates (v2.3)
* **GUI Dashboard:** Open a visual overview via `/cb gui` to monitor active frozen chunks, check server health, and manage whitelists visually.
* **Discord Integration:** Sends rich webhook alerts containing chunk coordinates, defused event counts, and quick actions when lag machines are isolated.
* **TPS Sentinel:** Background tasks log detailed performance reports (loaded chunks, entity counts, TPS/MSPT) to console, in-game chat, or Discord.

---

## Technical Details

### 1. Physics Lag (The 3-Strike System)
This module targets Block Physics Lag (pistons, redstone loops, fluid updates).
1. **Counting:** Tracks the number of `BlockPhysicsEvent` occurrences per chunk per second.
2. **Soft Reset (Strikes 1 & 2):** If a chunk exceeds the configured `lag-threshold`, it is temporarily unloaded and reloaded to break simple clock loops.
3. **Hard Freeze (Strike 3):** If the loop persists, the chunk and its 3x3 neighbors are put in a frozen state, canceling physics events in those chunks.
4. **Resets:** A background timer clears strike lists periodically based on `strike-reset-minutes`.

### 2. Smart Entity Culling (Optional)
Must be enabled in `config.yml`.
1. **Scanning:** A background task checks entity counts per loaded chunk at defined intervals.
2. **Culling:** If total entities in a chunk exceed `entity-culling.threshold`, excess entities are removed.
3. **Exceptions:** The culler preserves players, whitelisted types, custom-named entities, tamed pets, and vehicles (boats/minecarts).

---

## Admin Commands & Permissions

Settings and ignores are saved to `data.yml` and persist across restarts.

### Permissions
* `circuitbreaker.admin`: Allows executing `/cb` commands (Default: op).
* `antilag.notify`: Allows receiving warning alerts (Default: op).

### Commands
| Command | Alias | Description |
| :--- | :--- | :--- |
| `/cb gui` | `/cb panel` | Opens the visual chest-menu control panel. |
| `/cb status` | `/cb status` | Checks state of current chunk (`NORMAL`, `WATCHED`, `FROZEN`, `IGNORED`). |
| `/cb unfreeze [radius]` | `/cb unfreeze` | Unfreezes chunks within a radius (defaults to 10x10 block area). |
| `/cb top` | `/cb top` | Lists the top 5 chunks with highest block physics activity. |
| `/cb ignore` | `/cb ignore` | Adds current chunk to whitelist (ignores physics resets and culling). |
| `/cb unignore` | `/cb unignore` | Removes current chunk from the whitelist. |
| `/cb reload` | `/cb reload` | Reloads configuration settings. |

---

## Configuration Reference (`config.yml`)

```yaml
# CircuitBreaker Configuration File
# Upgraded: v2.4-Aegis

# Toggle physics lag checks
enabled: true

# Physics threshold settings
lag-threshold: 20000
strike-limit: 3
strike-reset-minutes: 15

# Action Durations (Ticks)
soft-reset-duration-ticks: 200
freeze-duration-ticks: 6000

# Alerts
notify-admins: true
chunk-frozen-message: "&cThis chunk has been frozen due to excessive lag detection!"

# Entity Culling (Optional)
entity-culling:
  enabled: false
  scan-interval-seconds: 15
  threshold: 500
  whitelist:
    - "PLAYER"
    - "VILLAGER"
    - "IRON_GOLEM"
    - "ARMOR_STAND"
    - "ITEM_FRAME"
    - "PAINTING"

# Network Sentinel
packet-sentinel:
  enabled: true
  threshold-per-second: 600

# Updates
auto-update:
  enabled: true

# Discord Integration
discord-webhook:
  enabled: false
  url: ""

# TPS Monitor Sentinel
tps-sentinel:
  enabled: true
  threshold: 18.0
  mspt-threshold: 48.0
  send-to-webhook: true
```

---

## Requirements & Compatibility
* **Server Version:** Paper 1.21+ (including Purpur, Pufferfish).
* **Folia:** Not supported. The plugin will automatically disable itself on Folia platforms to prevent scheduling conflicts.

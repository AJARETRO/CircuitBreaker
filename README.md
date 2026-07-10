# ⚙️ CircuitBreaker v2.4 | The Ultimate Performance Sentinel

![CircuitBreaker Banner](https://github.com/AJARETRO/CircuitBreaker/raw/master/banner.png)

[![Official Website](https://img.shields.io/badge/Official-Website-red?style=for-the-badge&logo=googlechrome)](https://ajaretro.dev/circuitbreaker.html)
[![Modrinth Download](https://img.shields.io/badge/Modrinth-Download-00AD5C?style=for-the-badge&logo=modrinth)](https://modrinth.com/project/circuitbreaker)
[![Hangar Download](https://img.shields.io/badge/Hangar-Download-007acc?style=for-the-badge&logo=papermc)](https://hangar.papermc.io/AJA_RETRO/CircuitBreaker)
[![GitHub Releases](https://img.shields.io/badge/GitHub-Releases-222222?style=for-the-badge&logo=github)](https://github.com/AJARETRO/CircuitBreaker/releases)

## 📊 Live Statistics
[![bStats](https://bstats.org/signatures/bukkit/CircuitBreaker.svg)](https://bstats.org/plugin/bukkit/CircuitBreaker/32242)

**CircuitBreaker** is a powerful, high-performance anti-lag plugin for modern Minecraft (Paper 1.21+). It moves beyond basic entity killing by providing a **multi-system, automated response** to physics lag, entity build-ups, and packet exploits.

This plugin ensures your server maintains high **TPS** by surgically neutralizing lag sources without punishing legitimate players.

---

## 🎯 Core Philosophy

* **Surgical:** Targets the *exact* chunk causing lag, leaving all other chunks unaffected.
* **Intelligent:** Uses a "tiered response" to differentiate between a temporary spike and a malicious, persistent machine.
* **Non-Destructive (Physics):** The physics lag system *never* breaks blocks or destroys player property; it only pauses the laggy process.
* **Smart Culling (Entities):** The *optional* entity lag system intelligently removes excess entities while protecting important ones (pets, named mobs, villagers, etc.).
* **Dynamic Scaling:** Automatically adapts its detection thresholds to match the server's live performance.

---

## ✨ What's New?

### 📢 v2.4: Announcement Alert Update
* **Chunk Frozen Chat Announcement:** All players inside a frozen chunk area receive a warning message letting them know the chunk has been isolated, labeled with author attributions.
* **Configurable Warning Message:** Define a custom warning message using `chunk-frozen-message` in `config.yml`.
* **All Rights Reserved License:** Transitioned under a custom All Rights Reserved Software License Agreement.

### ⚙️ v2.3: The Control Panel & Webhook Update
* **In-Game GUI Control Panel (`/cb gui`):** Opens a fully interactive chest-menu dashboard to view server health, list/teleport/unfreeze frozen chunks, and manage whitelists visually.
* **Discord Webhook Alerts:** Sends rich, stylized Discord embeds notifying you when a lag source has been detected and neutralized (includes /tp command, stats, world info).
* **Server TPS Sentinel:** Background task monitors general performance and logs detailed lag reports (entities, loaded chunks, TPS/MSPT) to in-game chat and Discord.
* **Countdown Unfreeze Timers:** Displays real-time auto-unfreeze timers dynamically inside the GUI panel.
* **Auto-Updater:** Automatically downloads new plugin releases directly into the server's update folder for seamless, crash-free restarts.
* **Configuration Reloading (`/cb reload`):** Apply any configuration adjustments (including webhook URLs and alert options) instantly without server restarts.

### 🛡️ v2.2: Advanced Sentinel Release
* **Dynamic Threshold Scaling:** Automatically adjusts physics event thresholds based on live TPS and MSPT load averages (stricter under load, lenient when healthy).
* **3x3 Chunk Freezing:** When a chunk triggers Strike 3 (Hard Freeze), a 3x3 grid centered around that chunk is frozen to catch machines spanning chunk borders.
* **Clickable Chat Alerts:** Broadcasts interactive admin alerts with built-in `[TP]`, `[UNFREEZE]`, and `[IGNORE]` click actions.
* **Improved `/cb unfreeze` Command:** Supports optional radius (e.g. `/cb unfreeze 3`) and falls back to a 10x10 block area unfreeze if omitted.
* **`/cb top` Subcommand:** Real-time diagnostics command showing top 5 chunks with highest block physics activity.
* **Packet Spam Sentinel:** Dynamically injects into player Netty connection pipeline to throttle packet-spam crash exploit clients.

---

## 💡 System 1: Physics Lag (The 3-Strike System)

This is the core of the plugin. It *only* detects **Block Physics Lag**.

1. **Detection:** The plugin counts every `BlockPhysicsEvent` (from pistons, redstone, water, etc.) per chunk, every second.
2. **Strike 1 & 2 (Soft Reset):** If a chunk exceeds the `lag-threshold`, it performs a "Soft Reset"—unloading and reloading the chunk to break simple loops.
3. **Strike 3 (Hard Freeze):** If the lag persists, the plugin performs a "Hard Freeze," adding the 3x3 grid of chunks centered on the source to a "jail" and **canceling all future physics events** from them.
4. **The "Forgiveness" Timer:** A global timer (`strike-reset-minutes`) clears all strikes every 15 minutes to ensure fairness.

---

## 💡 System 2: Entity Lag (The Culler)

This is the **optional** system. It must be enabled in `config.yml`.

1. **Detection:** A separate, slower ticker (`scan-interval-seconds`) runs to check the total number of entities in each loaded chunk.
2. **Threshold Check:** If `chunk.getEntities().length` is greater than the `entity-culling.threshold` (e.g., 500), it triggers a cull.
3. **Smart Culling:** The plugin loops through all entities in that chunk and **removes** them *unless* they are "important."

### What is an "Important" Entity? (Will NOT be culled)
* Anything on the `entity-culling.whitelist` in the config (e.g., "PLAYER", "VILLAGER", "IRON_GOLEM").
* Any entity with a **custom name**.
* Any **tamed pet** (dogs, cats, parrots).
* Any **vehicle** (boats, minecarts).

---

## 🛡️ Admin Guide: Commands & Permissions

You have 100% control. All administrative actions (like ignoring chunks) are **saved to `data.yml`** and persist through server restarts.

### 🔑 Permissions
| Permission | Description | Default |
| :--- | :--- | :--- |
| `circuitbreaker.admin` | Grants access to all `/cb` commands. | `op` |
| `antilag.notify` | Receives alerts when a chunk is frozen, culled, or TPS falls. | `op` |

### 📟 Commands
| Command | Alias | Description |
| :--- | :--- | :--- |
| `/cb gui` | `/cb panel` | Opens the visual chest-menu control panel dashboard. |
| `/cb status` | `/cb status` | Checks the status of your current chunk (`NORMAL`, `WATCHED`, `FROZEN`, `IGNORED`). |
| `/cb unfreeze [radius]` | `/cb unfreeze` | Unfreezes chunks within a radius (default: 10x10 block area around position). |
| `/cb top` | `/cb top` | Shows the top 5 chunks with highest block physics activity in the last second. |
| `/cb ignore` | `/cb ignore` | Whitelists your current chunk. It will be ignored by both the physics lag and entity culling systems. |
| `/cb unignore` | `/cb unignore` | Removes your current chunk from the permanent ignore list. |
| `/cb reload` | `/cb reload` | Instantly reloads the configuration from config.yml. |

---

## 🔧 Full Configuration (`config.yml` v2.4)

Tune the plugin to perfectly match your server's needs.

```yaml
# ------------------------------
# CircuitBreaker Config v2.4
# ------------------------------

# --- v1.0: Physics Lag Detector ---
# Set to false to disable the 3-strike physics lag system.
enabled: true

# How many block physics events in 1 second (20 ticks)
# will trigger a "lag" warning?
lag-threshold: 20000

# How many "strikes" a chunk gets before it is frozen.
strike-limit: 3

# How many minutes of no lag before a chunk's strike count is reset.
strike-reset-minutes: 15

# How long (in ticks) to "soft reset" a chunk for.
# 200 ticks = 10 seconds
soft-reset-duration-ticks: 200

# How long (in ticks) to "hard freeze" a chunk for.
# 6000 ticks = 5 minutes
# Set to -1 to freeze chunks permanently (requires admin /cb unfreeze).
freeze-duration-ticks: 6000

# Send a broadcast message to admins when a chunk is frozen OR culled?
notify-admins: true

# Message sent directly to players inside a chunk when it is frozen.
chunk-frozen-message: "&cThis chunk has been frozen due to excessive lag detection!"

# ------------------------------
# v2.0: Entity Culling Settings
# ------------------------------
entity-culling:
  # Set to true to enable this entity-culling feature.
  # This is disabled by default.
  enabled: false

  # How often (in seconds) to scan all loaded chunks.
  # This is a HEAVY task. Do not set this too low!
  scan-interval-seconds: 15

  # How many entities must be in a *single chunk* to trigger a cull.
  threshold: 500

  # A list of entity types to *NEVER* kill (case-insensitive).
  whitelist:
    - "PLAYER"
    - "VILLAGER"
    - "IRON_GOLEM"
    - "ARMOR_STAND"
    - "ITEM_FRAME"
    - "PAINTING"

# ------------------------------
# v2.2: Packet Sentinel Settings
# ------------------------------
packet-sentinel:
  # Set to true to enable packet spam detection and throttle crash exploit clients.
  enabled: true
  
  # How many packets a player is allowed to send per second.
  threshold-per-second: 600

# ------------------------------
# Auto-Updater Settings (v2.2)
# ------------------------------
auto-update:
  # Set to true to automatically download new versions when available.
  # If false, it will only notify you in the console.
  enabled: true

# ------------------------------
# Discord Webhook Alerts (v2.2)
# ------------------------------
discord-webhook:
  # Set to true to broadcast sentinel alerts to your Discord channel.
  enabled: false
  
  # Paste your Discord Channel Webhook Integration URL below.
  url: ""

# ------------------------------
# Server TPS Sentinel (v2.3)
# ------------------------------
tps-sentinel:
  # Set to true to broadcast performance alerts when TPS drops.
  enabled: true
  
  # The TPS threshold below which the sentinel will trigger alerts.
  threshold: 18.0
  
  # The MSPT threshold above which the sentinel will trigger alerts.
  mspt-threshold: 48.0
  
  # Send the performance report to the Discord Webhook too?
  send-to-webhook: true
```

### 🔗 Compatibility
* **Requires:** Paper 1.21+ (or forks like Purpur, Pufferfish).
* **Folia:** This plugin is **NOT** compatible with Folia. It includes a safety check and will disable itself if Folia is detected, logging a clear message to your console.

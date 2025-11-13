# ⚙️ CircuitBreaker v2.0 | The All-in-One Performance Suite

![CircuitBreaker Banner](httpsS://github.com/AJARETRO/CircuitBreaker/raw/master/banner.png)

**CircuitBreaker** is a powerful, high-performance anti-lag plugin for modern Minecraft (Paper 1.21+). It moves beyond basic entity killing by providing a **dual-system, automated response** to both **physics lag** and **entity lag**.

This plugin ensures your server maintains high **TPS** by surgically neutralizing lag sources without punishing legitimate players.

---

## 🎯 Core Philosophy

* **Surgical:** Targets the *exact* chunk causing lag, leaving all other chunks unaffected.
* **Intelligent:** Uses a "tiered response" to differentiate between a temporary spike and a malicious, persistent machine.
* **Non-Destructive (Physics):** The physics lag system *never* breaks blocks or destroys player property; it only pauses the laggy process.
* **Smart Culling (Entities):** The *optional* entity lag system intelligently removes excess entities while protecting important ones (pets, named mobs, villagers, etc.).

---

## ✨ v2.0: What's New?

This release adds the **Entity Culling System**, a new, optional feature that runs alongside the original physics detector. You now have two layers of defense:

1.  **Physics Lag Detector (v1.0):** The 3-Strike "Hard Freeze" system that stops redstone/piston lag machines.
2.  **Entity Lag Culler (v2.0):** An optional scanner that removes excessive, unimportant entities (e.g., from massive mob farms) to prevent entity cramming lag.

---

## 💡 System 1: Physics Lag (The 3-Strike System)

This is the core of the plugin. It *only* detects **Block Physics Lag**.

1.  **Detection:** The plugin counts every `BlockPhysicsEvent` (from pistons, redstone, water, etc.) per chunk, every second.
2.  **Strike 1 & 2 (Soft Reset):** If a chunk exceeds the `lag-threshold`, it performs a "Soft Reset"—unloading and reloading the chunk to break simple loops.
3.  **Strike 3 (Hard Freeze):** If the lag persists, the plugin performs a "Hard Freeze," adding the chunk to a "jail" and **canceling all future physics events** from it.
4.  **The "Forgiveness" Timer:** A global timer (`strike-reset-minutes`) clears all strikes every 15 minutes to ensure fairness.

![CircuitBreaker Demo GIF](httpsS://github.com/AJARETRO/CircuitBreaker/raw/master/demo.gif)

---

## 💡 System 2: Entity Lag (The Culler)

This is the new **optional** v2.0 system. It must be enabled in `config.yml`.

1.  **Detection:** A separate, slower ticker (`scan-interval-seconds`) runs to check the total number of entities in each loaded chunk.
2.  **Threshold Check:** If `chunk.getEntities().length` is greater than the `entity-culling.threshold` (e.g., 500), it triggers a cull.
3.  **Smart Culling:** The plugin loops through all entities in that chunk and **removes** them *unless* they are "important."

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
| `antilag.notify` | Receives alerts when a chunk is frozen *or* culled. | `op` |

### 📟 Commands
| Command | Alias | Description |
| :--- | :--- | :--- |
| `/cb status` | `/cb status` | Checks the status of your current chunk (`NORMAL`, `WATCHED`, `FROZEN`, `IGNORED`). |
| `/cb unfreeze` | `/cb unfreeze` | Manually unfreezes a (physics-lag) frozen chunk. |
| `/cb ignore` | `/cb ignore` | **(Most Important!)** Whitelists your current chunk. It will be ignored by *both* the physics lag and entity lag systems. |
| `/cb unignore` | `/cb unignore` | Removes your current chunk from the permanent ignore list. |

---

## 🔧 Full Configuration (`config.yml` v2.0)

Tune the plugin to perfectly match your server's needs.

```yaml
# ------------------------------
# CircuitBreaker Config v2.0
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

# ------------------------------
# v2.0: Entity Culling Settings
# ------------------------------
entity-culling:
  # Set to true to enable this new entity-culling feature.
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
```

### Compatibility
Requires: Paper 1.21+ (or forks like Purpur, Pufferfish).

Folia: This plugin is NOT compatible with Folia. It includes a safety check and will disable itself if Folia is detected, logging a clear message to your console.
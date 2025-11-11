# ⚙️ CircuitBreaker | The Surgical Anti-Lag Solution

## I. Project Overview and Vision

**CircuitBreaker** is a powerful, high-performance anti-lag plugin for modern Minecraft (Paper 1.21+). Designed by AJARETRO, this tool moves beyond the antiquated methods of simple entity deletion (like ClearLagg) or broad server-side block limits.

Our mission is to maintain a perfect **20 TPS (Ticks Per Second)** by identifying and neutralizing the precise source of lag: physics-based contraptions and rapid block events. CircuitBreaker provides a dynamic, self-tuning defense that guarantees a lag-free environment without punishing legitimate players or breaking vanilla game mechanics.

### 💡 The Problem with Traditional Solutions

Server owners currently face a difficult choice when dealing with lag machines:

* **The "Blunt Tool" (ClearLagg):** These plugins indiscriminately delete entities and drops across the entire server, causing temporary lag spikes and frustrating players by destroying their progress.
* **The "Veto" (Limiter Plugins):** These stop players from building certain structures entirely (e.g., piston or hopper limits), fundamentally breaking the vanilla game experience for everyone, including legitimate builders.
* **The "Passive Fix" (Paper Configs):** While helpful, built-in server limits are passive and often break specific redstone timings globally, affecting legitimate farms.

**CircuitBreaker is different.** We provide a **Surgical Response** by focusing exclusively on real-time event frequency within a single, problematic chunk.

---

## II. The Core Logic: The Tiered Response System

CircuitBreaker's strength lies in its ability to differentiate between a momentary spike and a persistent, malicious lag machine using a configurable **3-Strike System**.

### 🔬 Technical Deep Dive: Event Monitoring

The plugin's "Ears" (`LagListener.java`) listen for the **`BlockPhysicsEvent`**. This event is the direct symptom of any active redstone, piston, gravity block, or complex fluid mechanism.

* A dedicated **1-second Ticker** in `LagManager.java` checks how many `BlockPhysicsEvent`s fired per chunk.
* The check is against the **`lag-threshold`** value (default: 20,000).

### 🥇 Strike 1 & 2: The Soft Reset (The Warning)

If a chunk exceeds the threshold, the plugin assumes a transient issue (like a momentary update storm or a small design flaw).

| Action | Mechanic | Effect |
| :--- | :--- | :--- |
| **Log Strike** | Strike count increments (`strikeList.put(chunk, strikes)`). | Notifies the console of the warning level. |
| **Soft Reset** | Chunk is unloaded via `chunk.unload()`. | Immediately halts all redstone/piston logic within the chunk. |
| **Scheduled Reload** | A delayed task (`runTaskLater`) reloads the chunk after `soft-reset-duration-ticks`. | The machine restarts, giving the player a chance to see the issue and fix it. |

### 🥉 Strike 3: The Hard Freeze (The Circuit Breaker)

If the chunk triggers the threshold for the third time, the system escalates to full lockdown.

1.  The chunk is added to the `frozenChunks` set in `LagManager.java`.
2.  The `LagListener` detects the chunk is frozen and calls `event.setCancelled(true)` for all subsequent physics updates. **The machine is permanently disabled** until unfrozen.
3.  The plugin calls `notifyAdmins()`.

### Strike Reset Timer (Self-Correction)

* A separate, global timer (`startStrikeResetter`) runs every `strike-reset-minutes` (default: 15 min).
* This task calls `strikeList.clear()`, wiping the slate clean for all monitored chunks. This prevents a chunk from being permanently stuck at "Strike 2" forever, ensuring fairness.

---

## III. Configuration, Persistence, and Safety

The plugin is designed to be highly flexible for any environment, from vanilla survival to full-blown anarchy.

### 🛠️ Data Persistence (Admin Memory)

All administrative actions are saved to **`data.yml`** to ensure persistence across server restarts.

* **Saving Ignored Chunks:** The plugin stores chunks as a unique string (`world_uuid:x:z`) rather than volatile `Chunk` objects, ensuring the whitelist is never lost.
* **Safety:** The `LagManager` loads this file during startup, prioritizing the admin's whitelist above all else.

### 📋 `config.yml` Detailed Breakdown

| Setting | Default | Impact | Notes |
| :--- | :--- | :--- | :--- |
| `enabled` | `true` | Master switch for the entire system. | Set to false to temporarily disable without removing the JAR. |
| `lag-threshold` | **20000** | Max events allowed per second. | **Recommended value for robust servers.** Lower this for testing or small servers. |
| `strike-limit` | 3 | How many soft resets before the final hard freeze. | |
| `strike-reset-minutes`| 15 | **CRITICAL:** Global cooldown (in minutes) for resetting strike counts. | |
| `soft-reset-duration-ticks`| 200 | Time (10 seconds) the chunk remains unloaded during the soft reset. | Must be long enough to break the lag cycle. |
| `freeze-duration-ticks` | 6000 | Time (5 minutes) before a frozen chunk is automatically unfrozen. | Use `-1` for permanent freeze (requires manual admin intervention). |
| `notify-admins` | `true` | Broadcasts the freeze alert to players with the `antilag.notify` permission. | |

### 🛡️ Admin Safety & Controls

| Command | Action | Notes |
| :--- | :--- | :--- |
| `/cb status` | Checks the status (NORMAL, WATCHED, FROZEN, IGNORED) of the chunk you are standing in. | Essential for debugging and verifying the system is working. |
| `/cb unfreeze` | Manually removes a chunk from the frozen list. | The "panic button" if a chunk is permanently frozen. |
| `/cb ignore` | Adds the chunk you are standing in to the **permanent ignore list** (`data.yml`). | Perfect for intentionally laggy but beneficial builds (e.g., large-scale redstone). |
| `/cb unignore` | Removes the chunk from the ignore list. | |
| **Permission:** `circuitbreaker.admin` | Required for all `/cb` commands. | |

---

## IV. Technical & Deployment

### 🚧 Compatibility & Safety Checks

* **API:** Requires **Paper/Spigot 1.21+** (compiled with Java 17/21).
* **Supported Forks:** Tested and stable on Paper, Purpur, and Pufferfish.
* **Folia Safety:** The plugin includes a mandatory **Folia detection check** that disables the plugin on startup. This is required because the Bukkit Global Schedulers used for Soft Reset and Auto-Unfreeze are fundamentally incompatible with Folia's regionized threading model.

### ❓ Troubleshooting & FAQ

**Q: My plugin is running, but nothing happens when I make a lag machine.**
A: Check your `config.yml`! You likely need to lower the `lag-threshold` from `20000` (which is very high) for testing purposes, or your machine is not using `BlockPhysicsEvent`s (e.g., it's entity-lag, which requires a different solution).

**Q: Why does the chunk lag briefly, then stop, then lag again?**
A: That is the **Soft Reset** working perfectly! It proves the machine is persistent. It should enter Hard Freeze (Strike 3) after the `strike-limit` is reached.

**Q: Why do my console messages show ugly codes like `§c`?**
A: This is a rare console issue. The plugin uses the most reliable method for color logging. If this persists, ensure your server terminal supports ANSI escape codes.

### 🤝 Contributing

This project is built on the principles of open-source development. If you are a developer interested in:
* Adding support for entity-based lag detection (monitoring high-frequency `EntityMoveEvent`s).
* Creating a graphical interface for the `/cb status` command.
* Building a separate, fully Folia-compatible version of the `LagManager`.

Please feel free to fork the repository, open an issue, or submit a Pull Request.

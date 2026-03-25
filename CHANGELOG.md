# Changelog for EverFurnace 1.20.1

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.0.0] - 2026-03-24

### 🎉 Highlights

#### Catch-Up Mechanic — Core Overhaul
- Furnaces now correctly simulate fuel consumption and cooking progress for time elapsed
  while the chunk was unloaded or the server was offline.
- Results are delivered instantly when the chunk reloads — no waiting for each item to
  cook individually.
- Applies to all three vanilla furnace types: Furnace, Smoker, and Blast Furnace.

#### Forge Config API
- Key catch-up parameters are now tunable via config files without touching code.
- `everfurnace-common.toml` — server-side settings: master toggle, catch-up tick cap,
  delta threshold, and player notification toggle.
- `everfurnace-client.toml` — client-side settings: particle burst toggle.

#### Offline Progress Notification
- When a player opens a furnace that cooked items while they were away, a chat message
  is sent showing the number of items processed.
- Example: `[EverFurnace] Your furnace cooked 32 items while you were away.`
- Can be disabled per-server via `notifyPlayerOnCatchup` in `everfurnace-common.toml`.

#### Visual Flame Particle Burst
- A brief burst of flame and smoke particles spawns at the furnace position when
  catch-up completes and at least one item was cooked.
- Client-side only — no server impact.
- Can be disabled via `particleBurstEnabled` in `everfurnace-client.toml`.

### 🐛 Fixed

- **Operator bug** — `=+` / `=-` used throughout the tick method instead of `+=` / `-=`,
  causing wildly incorrect `litTime` and `cookingProgress` values on every catch-up pass.
- **Cook time reset** — `cookingTotalTime -= cookingTotalTime` always resolved to `0`,
  silently zeroing out the cook time instead of resetting only the progress counter.
- **Double fuel shrink** — After consuming whole fuel items, a second unconditional
  `shrink(1)` fired and could corrupt the fuel stack item count.
- **Spurious catch-up on first light** — `lastGameTime` initialised to `0` caused
  `deltaTime` to equal the entire world age on the first tick, triggering a massive
  unintended catch-up burst.
- **Unbounded catch-up loop** — No upper limit on simulated ticks meant long offline
  periods could cause a server lag spike on chunk load.
- **Missing `setChanged`** — Inventory changes and cooking progress updates during
  catch-up were not marked dirty, causing them to not sync to clients or persist reliably.

### ⚙️ Changed

- **Delta threshold** — Catch-up now only fires when the tick gap meets or exceeds the
  configured `minDeltaThreshold` (default 20 ticks / 1 second). Below this the furnace
  is considered actively ticking and vanilla handles smelting normally.
- **Catch-up cap** — Simulated time is capped at `maxCatchupTicks` (default 24 000 /
  1 in-game day) per load event to prevent lag spikes after extended offline periods.

### 🔧 Technical / Developer Notes

- `onTick` refactored into two private static helpers: `applyFuelTime()` and
  `applyCookTime()`. Each concern is independently readable and testable.
- `applyCookTime()` return type is `int` (items cooked count) rather than `boolean`,
  used for both the notification NBT tag and the particle packet gate.
- NBT versioning added: `everfurnace_version` written on every save. Currently at
  version `1`. Future releases can key migrations off this value.
- `everfurnace_pendingNotification` (int) stored on furnace persistent NBT data;
  cleared on first open by a player after catch-up.

---

## [1.0.0] - 2024-12-10

### 🎉 Highlights

#### Catch-Up Mechanic — Initial Release
- Initial implementation of the furnace catch-up mechanic via Mixin on
  `AbstractFurnaceBlockEntity`.
- Records `lastGameTime` on each server tick and simulates elapsed fuel and cooking
  on next load.
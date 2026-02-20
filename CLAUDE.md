# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build (default goal)
mvn clean package

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=JoinListenerTest

# Run a single test method
mvn test -Dtest=JoinListenerTest#testOnPlayerJoin

# Skip tests during build
mvn clean package -DskipTests
```

Java 17 is required. The build produces a shaded jar at `target/Limits-<version>-LOCAL.jar`.

## Architecture Overview

Limits is a **BentoBox addon** (not a standalone Spigot plugin). It depends on BentoBox being present and only activates for game modes listed in `config.yml` under `gamemodes` (e.g., BSkyBlock, AcidIsland).

### Entry Point

`Limits.java` extends `world.bentobox.bentobox.api.addons.Addon`. On enable it:
1. Loads `Settings` from config
2. Filters active `GameModeAddon` instances from BentoBox's addon manager
3. Registers admin and player commands under each game mode's command tree
4. Registers three listeners: `BlockLimitsListener`, `JoinListener`, `EntityLimitListener`
5. Registers PlaceholderAPI placeholders for each material and entity type

### Three-Tier Limit System

Limits are resolved in a priority hierarchy (highest wins):

1. **Island-specific** (`IslandBlockCount.blockLimits` / `entityLimits`): set by player permissions, checked at join
2. **World-specific** (`BlockLimitsListener.worldLimitMap`): from `config.yml` `worlds:` section
3. **Default** (`BlockLimitsListener.defaultLimitMap`): from `config.yml` `blocklimits:` section

**Offsets** (`blockLimitsOffset`, `entityLimitsOffset`) are added on top of any limit found — used by the `offset` admin command to give per-island bonus allowances.

### Key Classes

| Class | Role |
|---|---|
| `Limits` | Main addon; wires everything together, registers placeholders |
| `Settings` | Parses `config.yml`; holds entity limits, entity group limits, game mode list |
| `IslandBlockCount` | Per-island data object stored in BentoBox's database (`@Table("IslandBlockCount")`); tracks block counts, per-island block/entity limits, and offsets |
| `BlockLimitsListener` | Core block tracking listener; handles all block place/break/grow/explode events; maintains the `islandCountMap` in memory; persists via BentoBox `Database<IslandBlockCount>` |
| `JoinListener` | Applies permission-based limits on player join, island creation/reset, and ownership change; fires `LimitsPermCheckEvent` / `LimitsJoinPermCheckEvent` for external cancellation |
| `EntityLimitListener` | Cancels entity spawn/breed/vehicle-create events when entity or group limits are exceeded; handles async golem/snowman spawning edge cases |
| `RecountCalculator` | Async chunk scanner used by the admin `calc` command to rebuild block counts from scratch; uses chunk snapshots and BentoBox's `Pipeliner` |

### Permission-Based Limits

Permission format: `<gamemode>.island.limit.<MATERIAL_OR_ENTITY_OR_GROUP>.<NUMBER>`

Example: `bskyblock.island.limit.HOPPER.20`

`JoinListener.checkPerms()` clears all permission-based limits then re-applies them from scratch on each join. The highest value wins if multiple permissions grant the same limit. Wildcards are not supported.

### Block Material Normalization

`BlockLimitsListener.fixMaterial()` normalizes variant materials to their canonical form (e.g., `CHIPPED_ANVIL` → `ANVIL`, `REDSTONE_WALL_TORCH` → `REDSTONE_TORCH`, `PISTON_HEAD` → `PISTON`/`STICKY_PISTON`). Block config keys can use Bukkit Material names or Minecraft tag names (e.g., `minecraft:logs`).

### Events API

Two cancellable events are fired so other plugins can intercept limit application:
- `LimitsJoinPermCheckEvent` — fired once per island on join; can cancel all perm processing or supply a custom `IslandBlockCount`
- `LimitsPermCheckEvent` — fired for each individual permission; can modify the limit value or cancel that single permission

### Data Persistence

`IslandBlockCount` is persisted via BentoBox's `Database<IslandBlockCount>` (JSON flat-file by default, configurable in BentoBox). Saves are batched: every 10 block changes triggers an async save (`CHANGE_LIMIT = 9`). A full save of all changed islands occurs on addon disable.

### Testing

Tests use JUnit 4 + Mockito + PowerMock. The `ServerMocks` utility class in `src/test/java/.../mocks/` sets up Bukkit server mocks. Tests are named `*Test.java` and mirror the main source package structure.

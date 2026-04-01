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

Tests use **JUnit 5 + Mockito 5 + MockBukkit**. Tests are named `*Test.java` and mirror the main source package structure.

#### Test Infrastructure Setup

Both `BlockLimitsListenerTest` and `EntityLimitListenerTest` use `@ExtendWith(MockitoExtension.class)` with `@MockitoSettings(strictness = Strictness.LENIENT)`.

**MockBukkit**: `MockBukkit.mock()` in `@BeforeEach`, `MockBukkit.unmock()` in `@AfterEach`. Provides a mock Bukkit server environment.

**BentoBox mocks required for block event tests** (notification path when limits are hit):
- `User.setPlugin(plugin)` in setUp, `User.clearUsers()` in tearDown
- `LocalesManager` → `plugin.getLocalesManager()`
- `PlaceholdersManager` → `plugin.getPlaceholdersManager()`
- `Notifier` → `plugin.getNotifier()`

**BentoBox mocks required for entity event tests** (permission checks):
- `BentoBox` → `addon.getPlugin()`
- `IslandWorldManager` → `bentoBox.getIWM()` with `getPermissionPrefix(world)` returning e.g. `"bskyblock."`
- Same User/Locales/Placeholders/Notifier chain as block tests

**Island infrastructure** (needed for any event handler that calls `process()`):
- `IslandsManager` → `addon.getIslands()`
- `islandsManager.getIslandAt(any(Location.class))` → `Optional.of(island)`
- `islandsManager.getProtectedIslandAt(any(Location.class))` → `Optional.of(island)` (for vehicle events)
- `island.getUniqueId()` → fixed string like `"test-island-id"`
- `addon.inGameModeWorld(world)` → `true` (override the default `false`)
- `addon.getGameModeName(world)` → `"BSkyBlock"`
- `island.getCenter()` → a location distinct from test block locations (center-block check)

**Database mock**: `Mockito.mockConstruction(Database.class, ...)` to intercept `new Database<>()` in the `BlockLimitsListener` constructor.

#### Key Test Helpers

`BlockLimitsListenerTest.mockBlock(Material, Location)` — creates a fully-stubbed `Block` mock with:
- `getType()`, `getLocation()`, `getWorld()`, `getBlockData()` (with `getMaterial()`)
- `hasMetadata("blockbreakevent-ignore")` → `false`
- `getRelative(any(BlockFace.class))` → an AIR block (override specific faces as needed)

`EntityLimitListenerTest.mockEntity(EntityType, Location)` — creates a `LivingEntity` mock with type, location, world, and random UUID.

#### Paper API Gotcha: `BlockData.clone()`

Paper API's `EntityChangeBlockEvent.getBlockData()` internally calls `BlockData.clone()`. When creating `EntityChangeBlockEvent` with a mock `BlockData`, you **must** stub `clone()`:
```java
when(toBlockData.clone()).thenReturn(toBlockData);
```
Without this, `getBlockData()` returns null and `fixMaterial()` throws NPE.

#### Mock Location World

The `@Mock Location location` field must have `when(location.getWorld()).thenReturn(world)` stubbed. Event handlers like `onCreatureSpawn` call `event.getLocation().getWorld()` which delegates to `entity.getLocation().getWorld()`. Without this stub, `addon.inGameModeWorld(null)` returns `false` and the handler exits early.

#### Testing Limit Hierarchy

`BlockLimitsListener`'s private `worldLimitMap` and `defaultLimitMap` can be accessed via reflection to set up world-specific and default limits for tests:
```java
Field worldLimitField = BlockLimitsListener.class.getDeclaredField("worldLimitMap");
worldLimitField.setAccessible(true);
```

`Settings.getGroupLimits()` returns a mutable map — entity group limits can be added directly for tests.

#### Testing Debounce (`justSpawned`)

`EntityLimitListener`'s private `justSpawned` list (deduplicates entity spawns) can be accessed via reflection to test debounce behavior.

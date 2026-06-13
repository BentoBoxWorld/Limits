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

### Per-Environment Tracking (since #43)

Block counts, entity counts, limits, and offsets are all tracked **per `World.Environment`** (overworld, nether, end). The data model in `IslandBlockCount` keys every map by `Environment` first, then by material/entity. Pre-env data on disk is migrated into `Environment.NORMAL` lazily on first access.

This means:
- Counts in nether and end are persistent — they don't go to zero when chunks unload (the original bug in #43).
- A single config-defined limit applies independently to each env (`HOPPER: 10` allows 10 in overworld, 10 in nether, 10 in end).
- Entity portal teleports decrement the source-env count and increment the destination-env count via `EntityPortalEvent`.

### Limit Resolution Order

For a block placement (or entity spawn) in environment `env`, the limit is resolved in priority order:

1. **Island-specific env limit** (`IslandBlockCount.envBlockLimits[env]`) — set by player permissions, checked at join.
2. **World-specific limit** (`BlockLimitsListener.worldLimitMap[world]`) — from the `worlds:` section. Each world is one env so this is implicitly env-scoped.
3. **Env default** (`BlockLimitsListener.envDefaultLimitMap[env]`) — from `blocklimits` (seeded into all envs) plus `blocklimits-nether` / `blocklimits-end` overrides.

**Offsets** (`envBlockLimitsOffset[env]`, `envEntityLimitsOffset[env]`) are added on top of any resolved limit. The legacy `/offset` admin command sets the same offset across all envs via the `setBlockLimitsOffsetAllEnvs` / `setEntityLimitsOffsetAllEnvs` helpers.

### Persistent Entity Counts

`EntityLimitListener` no longer counts entities via `World.getNearbyEntities(island.getBoundingBox())`. Instead it maintains the count in `IslandBlockCount.envEntityCounts`:

- Increment on MONITOR-priority handlers for `CreatureSpawnEvent`, `VehicleCreateEvent`, `HangingPlaceEvent` (after our LOW-priority limit check has had its say).
- Decrement on `EntityRemoveEvent` whenever the cause is **not** `UNLOAD` (so death, despawn, drop, plugin removal, etc. all decrement; chunk unload does not).
- `EntityPortalEvent` (MONITOR) handles env-to-env teleport: decrement source env, increment destination env.

This is Paper-only (the addon dropped Spigot support; `EntityRemoveEvent` is on Bukkit's API now but `EntityRemoveEvent.Cause` is needed).

### Key Classes

| Class | Role |
|---|---|
| `Limits` | Main addon; wires everything together, registers placeholders |
| `Settings` | Parses `config.yml`; holds entity limits, entity group limits, game mode list |
| `IslandBlockCount` | Per-island data object stored in BentoBox's database (`@Table("IslandBlockCount")`); tracks block counts, entity counts, limits, and offsets — all keyed by `Environment` |
| `EnvNamespacedKeyMapAdapter` | Gson type adapter for `Map<Environment, Map<NamespacedKey, Integer>>` — needed because `NamespacedKey` is not enum-keyed and Gson can't handle it natively |
| `BlockLimitsListener` | Core block tracking listener; handles all block place/break/grow/explode events; maintains the `islandCountMap` in memory; persists via BentoBox `Database<IslandBlockCount>` |
| `JoinListener` | Applies permission-based limits on player join, island creation/reset, and ownership change; fires `LimitsPermCheckEvent` / `LimitsJoinPermCheckEvent` for external cancellation |
| `EntityLimitListener` | Cancels entity spawn/breed/vehicle-create events when entity or group limits are exceeded; handles async golem/snowman spawning edge cases |
| `RecountCalculator` | Async chunk scanner used by the admin `calc` command to rebuild block counts from scratch; uses chunk snapshots and BentoBox's `Pipeliner` |

### Permission-Based Limits

Two formats:
- 5-segment, all-env: `<gamemode>.island.limit.<KEY>.<NUMBER>` — applied independently to overworld, nether, and end.
- 6-segment, env-scoped: `<gamemode>.island.limit.<env>.<KEY>.<NUMBER>` where `<env>` ∈ `{overworld, nether, end}`.

`JoinListener.checkPerms()` clears all permission-based limits then re-applies them from scratch on each join. The highest value wins if multiple permissions grant the same limit for the same env. Wildcards are not supported.

### Block Material Normalization

`BlockLimitsListener.fixMaterial()` normalizes variant materials to their canonical form (e.g., `CHIPPED_ANVIL` → `ANVIL`, `REDSTONE_WALL_TORCH` → `REDSTONE_TORCH`, `PISTON_HEAD` → `PISTON`/`STICKY_PISTON`). Block config keys can use Bukkit Material names or Minecraft tag names (e.g., `minecraft:logs`).

### Block State Transitions

When one block becomes another in place — `BlockFormEvent`, `EntityBlockFormEvent`, `BlockSpreadEvent` (grass/mycelium/fire spread), `BlockGrowEvent` (crops, sugar cane, etc.) — the handlers must keep the count balanced: **decrement the replaced block, then add the new one with a limit check, and revert on cancel.** The canonical shape (see `onBlock(BlockFormEvent)`):

```java
process(e.getBlock(), false);                                   // remove old
if (process(e.getBlock(), e.getNewState().getBlockData(), true) > -1) {
    e.setCancelled(true);
    process(e.getBlock(), true);                                // limit hit → restore old
}
```

Key invariant when editing these handlers: `process(block, true)` returns the limit **before** incrementing when the limit is hit, so it only ever adds to the count on the success path (`-1` return). Do **not** add a compensating `process(..., false)` in the cancel branch — nothing was added, so it would decrement a real block's count below its true value and let the next transition slip past the limit. `BlockLimitsListenerTest` guards this with the `*StateTransition`, `*AtLimitCancelsAndRestoresOld`, and `testBlockGrowAtNonZeroLimitDoesNotDecrement` cases.

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

## Dependency Source Lookup

When you need to inspect source code for a dependency (e.g., BentoBox, addons):

1. **Check local Maven repo first**: `~/.m2/repository/` — sources jars are named `*-sources.jar`
2. **Check the workspace**: Look for sibling directories or Git submodules that may contain the dependency as a local project (e.g., `../bentoBox`, `../addon-*`)
3. **Check Maven local cache for already-extracted sources** before downloading anything
4. Only download a jar or fetch from the internet if the above steps yield nothing useful

Prefer reading `.java` source files directly from a local Git clone over decompiling or extracting a jar.

In general, the latest version of BentoBox should be targeted.

## Project Layout

Related projects are checked out as siblings under `~/git/`:

**Core:**
- `bentobox/` — core BentoBox framework

**Game modes:**
- `addon-acidisland/` — AcidIsland game mode
- `addon-bskyblock/` — BSkyBlock game mode
- `Boxed/` — Boxed game mode (expandable box area)
- `CaveBlock/` — CaveBlock game mode
- `OneBlock/` — AOneBlock game mode
- `SkyGrid/` — SkyGrid game mode
- `RaftMode/` — Raft survival game mode
- `StrangerRealms/` — StrangerRealms game mode
- `Brix/` — plot game mode
- `parkour/` — Parkour game mode
- `poseidon/` — Poseidon game mode
- `gg/` — gg game mode

**Addons:**
- `addon-level/` — island level calculation
- `addon-challenges/` — challenges system
- `addon-welcomewarpsigns/` — warp signs
- `addon-limits/` — block/entity limits
- `addon-invSwitcher/` / `invSwitcher/` — inventory switcher
- `addon-biomes/` / `Biomes/` — biomes management
- `Bank/` — island bank
- `Border/` — world border for islands
- `Chat/` — island chat
- `CheckMeOut/` — island submission/voting
- `ControlPanel/` — game mode control panel
- `Converter/` — ASkyBlock to BSkyBlock converter
- `DimensionalTrees/` — dimension-specific trees
- `discordwebhook/` — Discord integration
- `Downloads/` — BentoBox downloads site
- `DragonFights/` — per-island ender dragon fights
- `ExtraMobs/` — additional mob spawning rules
- `FarmersDance/` — twerking crop growth
- `GravityFlux/` — gravity addon
- `Greenhouses-addon/` — greenhouse biomes
- `IslandFly/` — island flight permission
- `IslandRankup/` — island rankup system
- `Likes/` — island likes/dislikes
- `Limits/` — block/entity limits
- `lost-sheep/` — lost sheep adventure
- `MagicCobblestoneGenerator/` — custom cobblestone generator
- `PortalStart/` — portal-based island start
- `pp/` — pp addon
- `Regionerator/` — region management
- `Residence/` — residence addon
- `TopBlock/` — top ten for OneBlock
- `TwerkingForTrees/` — twerking tree growth
- `Upgrades/` — island upgrades (Vault)
- `Visit/` — island visiting
- `weblink/` — web link addon
- `CrowdBound/` — CrowdBound addon

**Data packs:**
- `BoxedDataPack/` — advancement datapack for Boxed

**Documentation & tools:**
- `docs/` — main documentation site
- `docs-chinese/` — Chinese documentation
- `docs-french/` — French documentation
- `BentoBoxWorld.github.io/` — GitHub Pages site
- `website/` — website
- `translation-tool/` — translation tool

Check these for source before any network fetch.

## Key Dependencies (source locations)

- `world.bentobox:bentobox` → `~/git/bentobox/src/`

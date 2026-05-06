# Limits
[![Build Status](https://ci.codemc.org/buildStatus/icon?job=BentoBoxWorld/Limits)](https://ci.codemc.org/job/BentoBoxWorld/job/Limits/)

Add-on for BentoBox to limit island blocks and entities in GameModes like  BSkyBlock and AcidIsland. This add-on will work
in any game mode world.

## How to use

1. Place the Limits addon jar in the addons folder of the BentoBox plugin
2. Restart the server
3. The addon will create a data folder and inside the folder will be a config.yml
4. Edit the config.yml how you want.
5. Restart the server if you make a change

## Commands
There is a user command and an admin command called "limits". Admins can check the limits of a specific island owner. Both show a GUI panel with the limits and the current count.

## Setup - Config.yml

The config.yml has the following sections:

* `blocklimits`, `blocklimits-nether`, `blocklimits-end`
* `worlds`
* `entitylimits`, `entitylimits-nether`, `entitylimits-end`
* `entitygrouplimits`, `entitygrouplimits-nether`, `entitygrouplimits-end`

### Per-environment separation

Limits are tracked independently in the overworld, nether, and end. A `HOPPER: 10` limit means the player can place up to 10 hoppers in **each** of overworld, nether, and end — 30 hoppers total across the island. Likewise for entities and entity groups.

If you want a different limit in nether or end, add a corresponding `*-nether` or `*-end` section that overrides the env-default for those entries only.

### blocklimits

The base block-limit section. Values here apply to every environment unless overridden by `blocklimits-nether` / `blocklimits-end` or by a `worlds.<worldname>` entry.

### worlds

Per-world overrides. Name the world specifically (e.g. `AcidIsland_world`) and list the materials and limits. A world entry overrides the env-default for that exact world.

### entitylimits

Default entity-type limits applied independently per environment. Override per env via `entitylimits-nether` / `entitylimits-end`.

### entitygrouplimits

Define named entity groups (icon, member set, default limit). Override the limit value per env with `entitygrouplimits-nether` / `entitygrouplimits-end`. The icon and member set are fixed in the base section — only the numeric limit can be overridden per env.

## Permissions

Island owners can have exclusive permissions that override the default or world settings. Two formats are supported:

* All-environment: `<gamemode>.island.limit.<KEY>.<NUMBER>` — applies the limit independently to every environment.
* Per-environment: `<gamemode>.island.limit.<env>.<KEY>.<NUMBER>` — applies only to one environment, where `<env>` is `overworld`, `nether`, or `end`.

Examples:

```
bskyblock.island.limit.hopper.10            # 10 hoppers in each env
bskyblock.island.limit.nether.hopper.5      # only 5 hoppers in nether
bskyblock.island.limit.end.enderman.50      # only 50 endermen in the end
```

Permissions activate when the player logs in.

### Migrating an existing server

When upgrading from a pre-env version of Limits, existing per-island block counts are migrated to the overworld slot on first load. Counts in the nether/end will start at zero and be populated as entities and blocks change. Run `/<gamemode> admin limits calc <player>` to fully recount any island and redistribute its counts across environments.

Usage permissions are (put the gamemode name, e.g. acidisland at the front):

```
  GAMEMODE_NAME.limits.player.limits:
    description: Player can use limits command
    default: true
  GAMEMODE_NAME.limits.admin.limits:
    description: Player can use admin limits command
    default: op
```

## Items that cannot be limited
Some items cannot be limited (right now). The reasons are usually because there are too many ways to remove the item without it being tracked. If you are a programmer and can work out how to fix these, then please submit a PR!

* Primed TNT
* Evoker Fangs
* Llama Spit
* Dragon Fireball
* Area Effect Cloud
* Ender signal
* Small fireball
* Fireball
* Thrown Exp Bottle
* Shulker Bullet
* Wither Skull
* Tridents
* Arrows
* Spectral Arrows
* Snowballs
* Eggs
* Leashes
* Ender crystals
* Ender pearls
* Ender dragon
* Item frames
* Paintings

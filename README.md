# Limits
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

* blocklimits
* worlds
* entitylimits

### blocklimits

This section lists the maximum number of blocks allowed for each block material. Do not use non-block materials because they will not work. The limits apply to all game worlds.

### worlds

This section lists block limits for specific worlds. You must name the world specifically, e.g. AcidIsland_world and then list the materials and the limit.

### entitylimits

Coming soon!

## Permissions

Island owners can have exclusive permissions that override the default or world settings. The format is:

Format is `GAME-MODE-NAME.island.limit.MATERIAL.LIMIT`

example: `bskyblock.island.limit.hopper.10`

Permissions activate when the player logs in.

Usage permissions are:

```
  limits.player.limits:
    description: Player can use limits command
    default: true
  limits.admin.limits:
    description: Player can use admin limits command
    default: op
```



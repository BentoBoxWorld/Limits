package bentobox.addon.limits.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.vehicle.VehicleCreateEvent;

import bentobox.addon.limits.Limits;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;

public class EntityLimitListener implements Listener {
    private final Limits addon;

    /**
     * Handles entity and natural limitations
     * @param addon - Limits object
     */
    public EntityLimitListener(Limits addon) {
        this.addon = addon;
    }

    /**
     * Handles minecart placing
     * @param e - event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMinecart(VehicleCreateEvent e) {
        // Return if not in a known world
        if (!addon.getPlugin().getIWM().inWorld(e.getVehicle().getWorld())) {
            return;
        }
        if (addon.getSettings().getLimits().containsKey(e.getVehicle().getType())) {
            // If someone in that area has the bypass permission, allow the spawning
            for (Entity entity : e.getVehicle().getLocation().getWorld().getNearbyEntities(e.getVehicle().getLocation(), 5, 5, 5)) {
                if (entity instanceof Player) {
                    Player player = (Player)entity;
                    boolean bypass = (player.isOp() || player.hasPermission(addon.getPlugin().getIWM().getPermissionPrefix(e.getVehicle().getWorld()) + "mod.bypass"));
                    // Check island
                    addon.getIslands().getProtectedIslandAt(e.getVehicle().getLocation()).ifPresent(island -> {
                        // Ignore spawn
                        if (island.isSpawn()) {
                            return;
                        }
                        // Check if the player is at the limit
                        if (atLimit(island, bypass, e.getVehicle())) {
                            e.setCancelled(true);
                            for (Entity ent : e.getVehicle().getLocation().getWorld().getNearbyEntities(e.getVehicle().getLocation(), 5, 5, 5)) {
                                if (ent instanceof Player) {
                                    ((Player) ent).updateInventory();
                                    User.getInstance(ent).sendMessage("limits.hit-limit", "[material]",
                                            Util.prettifyText(e.getVehicle().getType().toString())
                                            ,"[number]", String.valueOf(addon.getSettings().getLimits().get(e.getVehicle().getType())));
                                }
                            }
                        }
                    });
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCreatureSpawn(final CreatureSpawnEvent e) {
        // Return if not in a known world
        if (!addon.getPlugin().getIWM().inWorld(e.getLocation())) {
            return;
        }
        if (!addon.getSettings().getLimits().containsKey(e.getEntityType())) {
            // Unknown entity limit or unlimited
            return;
        }
        boolean bypass = false;
        // Check why it was spawned
        switch (e.getSpawnReason()) {
        // These reasons are due to a player being involved (usually) so there may be a bypass
        case BREEDING:
        case BUILD_IRONGOLEM:
        case BUILD_SNOWMAN:
        case BUILD_WITHER:
        case CURED:
        case EGG:
        case SPAWNER_EGG:
            // If someone in that area has the bypass permission, allow the spawning
            for (Entity entity : e.getLocation().getWorld().getNearbyEntities(e.getLocation(), 5, 5, 5)) {
                if (entity instanceof Player) {
                    Player player = (Player)entity;
                    if (player.isOp() || player.hasPermission(addon.getPlugin().getIWM().getPermissionPrefix(e.getEntity().getWorld()) + "mod.bypass")) {
                        bypass = true;
                        break;
                    }
                }
            }
            break;
        default:
            // Other natural reasons
            break;
        }
        // Tag the entity with the island spawn location
        checkLimit(e, bypass);

    }

    private void checkLimit(CreatureSpawnEvent e, boolean bypass) {
        addon.getIslands().getIslandAt(e.getLocation()).ifPresent(island -> {
            // Check if creature is allowed to spawn or not
            if (!island.isSpawn() && atLimit(island, bypass, e.getEntity())) {
                // Not allowed
                e.setCancelled(true);
                // If the reason is anything but because of a spawner then tell players within range
                if (!e.getSpawnReason().equals(SpawnReason.SPAWNER)) {
                    for (Entity ent : e.getLocation().getWorld().getNearbyEntities(e.getLocation(), 5, 5, 5)) {
                        if (ent instanceof Player) {
                            User.getInstance(ent).sendMessage("limits.hit-limit", "[material]",
                                    Util.prettifyText(e.getEntityType().toString()),
                                    "[number]", String.valueOf(addon.getSettings().getLimits().get(e.getEntityType())));
                        }
                    }
                }

            }
        });

    }

    /**
     * Checks if new entities can be added to island
     * @param island - island
     * @param bypass - true if this is being done by a player with authorization to bypass limits
     * @param ent - the entity
     * @return true if at the limit, false if not
     */
    private boolean atLimit(Island island, boolean bypass, Entity ent) {
        return addon.getSettings().getLimits().getOrDefault(ent.getType(), -1) <= ent.getWorld().getEntities().stream()
                .filter(e -> e.getType().equals(ent.getType()))
                .filter(e -> island.inIslandSpace(e.getLocation())).count();
    }
}



package bentobox.addon.limits.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.hanging.HangingPlaceEvent;
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
                                    User.getInstance(ent).sendMessage("entity-limits.hit-limit", "[entity]",
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
            bypass = checkByPass(e.getLocation());
            break;
        default:
            // Other natural reasons
            break;
        }
        // Tag the entity with the island spawn location
        checkLimit(e, bypass);

    }

    private boolean checkByPass(Location l) {
        // If someone in that area has the bypass permission, allow the spawning
        for (Entity entity : l.getWorld().getNearbyEntities(l, 5, 5, 5)) {
            if (entity instanceof Player) {
                Player player = (Player)entity;
                if (player.isOp() || player.hasPermission(addon.getPlugin().getIWM().getPermissionPrefix(l.getWorld()) + "mod.bypass")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * handles paintings and item frames
     * @param e - event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlock(HangingPlaceEvent e) {
        Player player = e.getPlayer();
        addon.getIslands().getIslandAt(e.getEntity().getLocation()).ifPresent(island -> {
            boolean bypass = player.isOp() || player.hasPermission(addon.getPlugin().getIWM().getPermissionPrefix(e.getEntity().getWorld()) + "mod.bypass");
            // Check if entity can be hung
            if (!island.isSpawn() && atLimit(island, bypass, e.getEntity())) {
                // Not allowed
                e.setCancelled(true);
                User.getInstance(player).sendMessage("block-limits.hit-limit", "[material]",
                        Util.prettifyText(e.getEntity().getType().toString()),
                        "[number]", String.valueOf(addon.getSettings().getLimits().getOrDefault(e.getEntity().getType(), -1)));

            }
        });
    }

    private void checkLimit(CreatureSpawnEvent e, boolean bypass) {
        addon.getIslands().getIslandAt(e.getLocation()).ifPresent(island -> {
            // Check if creature is allowed to spawn or not
            if (!island.isSpawn() && atLimit(island, bypass, e.getEntity())) {
                // Not allowed
                e.setCancelled(true);
                // If the reason is anything but because of a spawner then tell players within range
                if (!e.getSpawnReason().equals(SpawnReason.SPAWNER) && !e.getSpawnReason().equals(SpawnReason.NATURAL) && !e.getSpawnReason().equals(SpawnReason.INFECTION) && !e.getSpawnReason().equals(SpawnReason.NETHER_PORTAL) && !e.getSpawnReason().equals(SpawnReason.REINFORCEMENTS) && !e.getSpawnReason().equals(SpawnReason.SLIME_SPLIT)) {
                    for (Entity ent : e.getLocation().getWorld().getNearbyEntities(e.getLocation(), 5, 5, 5)) {
                        if (ent instanceof Player) {
                            User.getInstance(ent).sendMessage("entity-limits.hit-limit", "[entity]",
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
        long count = ent.getWorld().getEntities().stream()
                .filter(e -> e.getType().equals(ent.getType()))
                .filter(e -> island.inIslandSpace(e.getLocation())).count();
        return addon.getSettings().getLimits().containsKey(ent.getType()) && count >= addon.getSettings().getLimits().get(ent.getType());
    }
}



package world.bentobox.limits.listeners;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;

import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.limits.Limits;

public class EntityLimitListener implements Listener {
    private static final String MOD_BYPASS = "mod.bypass";
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
        // If someone in that area has the bypass permission, allow the spawning
        for (Entity entity : Objects.requireNonNull(e.getVehicle().getLocation().getWorld()).getNearbyEntities(e.getVehicle().getLocation(), 5, 5, 5)) {
            if (entity instanceof Player) {
                Player player = (Player)entity;
                boolean bypass = (player.isOp() || player.hasPermission(addon.getPlugin().getIWM().getPermissionPrefix(e.getVehicle().getWorld()) + MOD_BYPASS));
                // Check island
                addon.getIslands().getProtectedIslandAt(e.getVehicle().getLocation()).ifPresent(island -> {
                    // Ignore spawn
                    if (island.isSpawn()) {
                        return;
                    }
                    // Check if the player is at the limit
                    if (!bypass && atLimit(island, e.getVehicle())) {
                        e.setCancelled(true);
                        for (Entity ent : e.getVehicle().getLocation().getWorld().getNearbyEntities(e.getVehicle().getLocation(), 5, 5, 5)) {
                            if (ent instanceof Player) {
                                ((Player) ent).updateInventory();
                                User.getInstance(ent).sendMessage("entity-limits.hit-limit", "[entity]",
                                        Util.prettifyText(e.getVehicle().getType().toString())
                                        , TextVariables.NUMBER, String.valueOf(addon.getSettings().getLimits().get(e.getVehicle().getType())));
                            }
                        }
                    }
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCreatureSpawn(final CreatureSpawnEvent e) {
        // Return if not in a known world
        if (!addon.getPlugin().getIWM().inWorld(e.getLocation())) {
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
        for (Entity entity : Objects.requireNonNull(l.getWorld()).getNearbyEntities(l, 5, 5, 5)) {
            if (entity instanceof Player) {
                Player player = (Player)entity;
                if (player.isOp() || player.hasPermission(addon.getPlugin().getIWM().getPermissionPrefix(l.getWorld()) + MOD_BYPASS)) {
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
        if (player == null) return;
        addon.getIslands().getIslandAt(e.getEntity().getLocation()).ifPresent(island -> {
            boolean bypass = Objects.requireNonNull(player).isOp() || player.hasPermission(addon.getPlugin().getIWM().getPermissionPrefix(e.getEntity().getWorld()) + MOD_BYPASS);
            // Check if entity can be hung
            if (!bypass && !island.isSpawn() && atLimit(island, e.getEntity())) {
                // Not allowed
                e.setCancelled(true);
                User.getInstance(player).sendMessage("block-limits.hit-limit", "[material]",
                        Util.prettifyText(e.getEntity().getType().toString()),
                        TextVariables.NUMBER, String.valueOf(addon.getSettings().getLimits().getOrDefault(e.getEntity().getType(), -1)));

            }
        });
    }

    private void checkLimit(CreatureSpawnEvent e, boolean bypass) {
        addon.getIslands().getIslandAt(e.getLocation()).ifPresent(island -> {
            // Check if creature is allowed to spawn or not
            if (!bypass && !island.isSpawn() && atLimit(island, e.getEntity())) {
                // Not allowed
                e.setCancelled(true);
                // If the reason is anything but because of a spawner then tell players within range
                if (!e.getSpawnReason().equals(SpawnReason.SPAWNER) && !e.getSpawnReason().equals(SpawnReason.NATURAL) && !e.getSpawnReason().equals(SpawnReason.INFECTION) && !e.getSpawnReason().equals(SpawnReason.NETHER_PORTAL) && !e.getSpawnReason().equals(SpawnReason.REINFORCEMENTS) && !e.getSpawnReason().equals(SpawnReason.SLIME_SPLIT)) {
                    World w = e.getLocation().getWorld();
                    if (w == null) return;
                    for (Entity ent : w.getNearbyEntities(e.getLocation(), 5, 5, 5)) {
                        if (ent instanceof Player) {
                            User.getInstance(ent).sendMessage("entity-limits.hit-limit", "[entity]",
                                    Util.prettifyText(e.getEntityType().toString()),
                                    TextVariables.NUMBER, String.valueOf(addon.getSettings().getLimits().get(e.getEntityType())));
                        }
                    }
                }

            }
        });

    }

    /**
     * Checks if new entities can be added to island
     * @param island - island
     * @param ent - the entity
     * @return true if at the limit, false if not
     */
    private boolean atLimit(Island island, Entity ent) {
        long count = ent.getWorld().getEntities().stream()
                .filter(e -> e.getType().equals(ent.getType()))
                .filter(e -> island.inIslandSpace(e.getLocation())).count();
        // Check island settings first
        int limitAmount = -1;
        if (addon.getBlockLimitListener().getIsland(island.getUniqueId()) != null) {
            limitAmount = addon.getBlockLimitListener().getIsland(island.getUniqueId()).getEntityLimit(ent.getType());
        }
        // If no island settings then try global settings
        if (limitAmount < 0 && addon.getSettings().getLimits().containsKey(ent.getType())) {
            limitAmount = addon.getSettings().getLimits().get(ent.getType());
        }
        if (limitAmount == -1) return false;
        return count >= limitAmount;
    }
}



package bentobox.addon.limits.listeners;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import bentobox.addon.limits.Limits;
import bentobox.addon.limits.objects.EntityLimitsDO;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.Database;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;

public class EntityLimitsListener implements Listener {
    private final Limits addon;

    private Database<EntityLimitsDO> handler;

    /**
     * Handles entity and natural limitations
     * @param addon - Limits object
     */
    public EntityLimitsListener(Limits addon) {
        this.addon = addon;
        handler = new Database<>(addon, EntityLimitsDO.class);
    }

    /**
     * Add meta data to entities in the chunk
     * @param e - event
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent e) {
        // Return if not in a known world
        if (!addon.getPlugin().getIWM().inWorld(e.getWorld())) {
            return;
        }
        String uniqueId = e.getWorld().getName() + "." + e.getChunk().getX() + "." + e.getChunk().getZ();
        if (handler.objectExists(uniqueId)) {
            EntityLimitsDO eld = handler.loadObject(uniqueId);
            if (eld != null) {
                for (Entity entity : e.getChunk().getEntities()) {
                    if (eld.getSpawnLoc().containsKey(entity.getUniqueId())) {
                        entity.setMetadata("spawnLoc", new FixedMetadataValue(addon.getPlugin(), eld.getSpawnLoc().get(entity.getUniqueId())));
                    }
                }
                // Delete chunk
                handler.deleteObject(eld);
            }
        }
    }

    /**
     * Save meta data on entities in the chunk
     * @param e - event
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent e) {
        // Return if not in a known world
        if (!addon.getPlugin().getIWM().inWorld(e.getWorld())) {
            return;
        }
        // Save chunk entities spawn loc meta data
        EntityLimitsDO eld = new EntityLimitsDO(e.getWorld().getName() + "." + e.getChunk().getX() + "." + e.getChunk().getZ());
        Map<UUID, String> spawnLoc = new HashMap<>();
        Arrays.stream(e.getChunk().getEntities()).filter(x -> x.hasMetadata("spawnLoc")).forEach(entity -> {
            // Get the meta data
            entity.getMetadata("spawnLoc").stream().filter(y -> y.getOwningPlugin().equals(addon.getPlugin())).forEach(v -> {
                spawnLoc.put(entity.getUniqueId(), v.asString());
            });
        });
        if (!spawnLoc.isEmpty()) {
            eld.setSpawnLoc(spawnLoc);
            handler.saveObject(eld);
        }
    }

    /**
     * Save all the entity meta data for world
     * @param world - world being saved
     */
    public void disable(World world) {
        HashMap<String, EntityLimitsDO> chunkMeta = new HashMap<>();
        world.getEntities().stream().filter(x -> x.hasMetadata("spawnLoc")).forEach(entity -> {
            // Get the meta data
            entity.getMetadata("spawnLoc").stream().filter(y -> y.getOwningPlugin().equals(addon.getPlugin())).forEach(v -> {
                String uniqueId = entity.getWorld().getName() + "." + entity.getLocation().getChunk().getX() + "." + entity.getLocation().getChunk().getZ();
                chunkMeta.putIfAbsent(uniqueId, new EntityLimitsDO(uniqueId));
                chunkMeta.get(uniqueId).getSpawnLoc().put(entity.getUniqueId(), v.asString());
            });
        });
        // Save all the chunks
        chunkMeta.values().forEach(handler::saveObject);
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
                                    User.getInstance(ent).sendMessage("entityLimitReached", "[entity]",
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
        tag(e, bypass);

    }

    private void tag(CreatureSpawnEvent e, boolean bypass) {
        addon.getIslands().getIslandAt(e.getLocation()).ifPresent(island -> {
            // Check if creature is allowed to spawn or not
            if (!island.isSpawn() && atLimit(island, bypass, e.getEntity())) {
                // Not allowed
                e.setCancelled(true);
                // If the reason is anything but because of a spawner then tell players within range
                if (!e.getSpawnReason().equals(SpawnReason.SPAWNER)) {
                    for (Entity ent : e.getLocation().getWorld().getNearbyEntities(e.getLocation(), 5, 5, 5)) {
                        if (ent instanceof Player) {
                            User.getInstance(ent).sendMessage("entityLimitReached", "[entity]",
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
     * @param island
     * @param bypass - true if this is being done by a player with authorization to bypass limits
     * @param ent - the entity
     * @return true if at the limit, false if not
     */
    private boolean atLimit(Island island, boolean bypass, Entity ent) {
        int count = 0;
        checkLimits:
            if (bypass || addon.getSettings().getLimits().get(ent.getType()) > 0) {
                // If bypass, just tag the creature. If not, then we need to count creatures
                if (!bypass) {
                    // Run through all the current entities on this world
                    for (Entity entity: ent.getWorld().getEntities()) {
                        // If it is the right one
                        if (entity.getType().equals(ent.getType())) {
                            // Check spawn location
                            if (entity.hasMetadata("spawnLoc")) {
                                // Get the meta data
                                List<MetadataValue> values = entity.getMetadata("spawnLoc");
                                for (MetadataValue v : values) {
                                    // There is a chance another plugin also uses the meta data spawnLoc
                                    if (v.getOwningPlugin().equals(addon.getPlugin())) {
                                        if (island.getUniqueId().equals(v.asString())) {
                                            // Entity is on this island
                                            count++;
                                            if (count >= addon.getSettings().getLimits().get(ent.getType())) {
                                                // No more allowed!
                                                break checkLimits;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Okay to spawn, but tag it
                ent.setMetadata("spawnLoc", new FixedMetadataValue(addon.getPlugin(), island.getUniqueId()));
                return false;
            }
        // Cancel - no spawning - tell nearby players
        return true;
    }
}

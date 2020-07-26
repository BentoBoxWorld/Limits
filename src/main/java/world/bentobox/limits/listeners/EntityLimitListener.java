package world.bentobox.limits.listeners;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.inventory.ItemStack;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.limits.Limits;
import world.bentobox.limits.Settings;
import world.bentobox.limits.Settings.EntityGroup;

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
                    AtLimitResult res;
                    if (!bypass && (res = atLimit(island, e.getVehicle())).hit()) {
                        e.setCancelled(true);
                        for (Entity ent : e.getVehicle().getLocation().getWorld().getNearbyEntities(e.getVehicle().getLocation(), 5, 5, 5)) {
                            if (ent instanceof Player) {
                                ((Player) ent).updateInventory();
                                if (res.getTypelimit() != null) {
                                    User.getInstance(ent).notify("entity-limits.hit-limit", "[entity]",
                                            Util.prettifyText(e.getVehicle().getType().toString()),
                                            TextVariables.NUMBER, String.valueOf(res.getTypelimit().getValue()));
                                } else {
                                    User.getInstance(ent).notify("entity-limits.hit-limit", "[entity]",
                                            res.getGrouplimit().getKey().getName() + " (" + res.getGrouplimit().getKey().getTypes().stream().map(x -> Util.prettifyText(x.toString())).collect(Collectors.joining(", ")) + ")",
                                            TextVariables.NUMBER, String.valueOf(res.getGrouplimit().getValue()));
                                }
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
        // BentoBox.getInstance().logDebug("Bypass check took " + getTime(timer));
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
            AtLimitResult res;
            if (!bypass && !island.isSpawn() && (res = atLimit(island, e.getEntity())).hit()) {
                // Not allowed
                e.setCancelled(true);
                if (res.getTypelimit() != null) {
                    User.getInstance(player).notify("block-limits.hit-limit", "[material]",
                            Util.prettifyText(e.getEntity().getType().toString()),
                            TextVariables.NUMBER, String.valueOf(res.getTypelimit().getValue()));
                } else {
                    User.getInstance(player).notify("block-limits.hit-limit", "[material]",
                            res.getGrouplimit().getKey().getName() + " (" + res.getGrouplimit().getKey().getTypes().stream().map(x -> Util.prettifyText(x.toString())).collect(Collectors.joining(", ")) + ")",
                            TextVariables.NUMBER, String.valueOf(res.getGrouplimit().getValue()));
                }
            }
        });
    }

    private void checkLimit(CreatureSpawnEvent e, boolean bypass) {
        Bukkit.getScheduler().runTaskAsynchronously(BentoBox.getInstance(), () -> {
            addon.getIslands().getIslandAt(e.getLocation()).ifPresent(island -> {
                // Check if creature is allowed to spawn or not
                AtLimitResult res;
                if (!bypass && !island.isSpawn() && (res = atLimit(island, e.getEntity())).hit()) {
                    //// BentoBox.getInstance().logDebug("Entity limit hit");
                    // Not allowed
                    Bukkit.getScheduler().runTask(BentoBox.getInstance(), () -> {
                        e.getEntity().remove();
                        // If the entity was build, drop the building materials
                        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> replaceEntity(e));
                        // If the reason is anything but because of a spawner then tell players within range
                        tellPlayers(e, res);
                    });
                }

            });
        });

    }

    private void replaceEntity(CreatureSpawnEvent e) {
        World world = e.getEntity().getWorld();
        Location l = e.getLocation();
        switch (e.getSpawnReason()) {
        case BUILD_IRONGOLEM:
            world.dropItem(l, new ItemStack(Material.IRON_BLOCK,3));
            world.dropItem(l, new ItemStack(Material.CARVED_PUMPKIN));
            break;
        case BUILD_SNOWMAN:
            world.dropItem(l, new ItemStack(Material.SNOW_BLOCK,2));
            world.dropItem(l, new ItemStack(Material.CARVED_PUMPKIN));
            l.getBlock().setType(Material.AIR);
            break;
        case BUILD_WITHER:
            world.dropItem(l, new ItemStack(Material.SOUL_SAND,3));
            world.dropItem(l, new ItemStack(Material.WITHER_SKELETON_SKULL, 3));
            break;
        case CURED:
            world.spawnEntity(e.getLocation(), EntityType.ZOMBIE_VILLAGER);
            break;
        case DISPENSE_EGG:
            break;
        case EGG:
            world.dropItem(l, new ItemStack(Material.EGG));
            break;
        case SPAWNER_EGG:
            break;
        default:
            break;

        }
    }

    private void tellPlayers(CreatureSpawnEvent e, AtLimitResult res) {
        if (!e.getSpawnReason().equals(SpawnReason.SPAWNER) && !e.getSpawnReason().equals(SpawnReason.NATURAL)
                && !e.getSpawnReason().equals(SpawnReason.INFECTION) && !e.getSpawnReason().equals(SpawnReason.NETHER_PORTAL)
                && !e.getSpawnReason().equals(SpawnReason.REINFORCEMENTS) && !e.getSpawnReason().equals(SpawnReason.SLIME_SPLIT)) {
            World w = e.getLocation().getWorld();
            if (w == null) return;
            for (Entity ent : w.getNearbyEntities(e.getLocation(), 5, 5, 5)) {
                if (ent instanceof Player) {
                    if (res.getTypelimit() != null) {
                        User.getInstance(ent).notify("entity-limits.hit-limit", "[entity]",
                                Util.prettifyText(e.getEntityType().toString()),
                                TextVariables.NUMBER, String.valueOf(res.getTypelimit().getValue()));
                    } else {
                        User.getInstance(ent).notify("entity-limits.hit-limit", "[entity]",
                                res.getGrouplimit().getKey().getName() + " (" + res.getGrouplimit().getKey().getTypes().stream().map(x -> Util.prettifyText(x.toString())).collect(Collectors.joining(", ")) + ")",
                                TextVariables.NUMBER, String.valueOf(res.getGrouplimit().getValue()));
                    }
                }
            }
        }

    }

    /**
     * Checks if new entities can be added to island
     * @param island - island
     * @param ent - the entity
     * @return true if at the limit, false if not
     */
    private AtLimitResult atLimit(Island island, Entity ent) {
        // Check island settings first
        int limitAmount = -1;
        Map<Settings.EntityGroup, Integer> groupsLimits = new HashMap<>();
        if (addon.getBlockLimitListener().getIsland(island.getUniqueId()) != null) {
            limitAmount = addon.getBlockLimitListener().getIsland(island.getUniqueId()).getEntityLimit(ent.getType());
            List<Settings.EntityGroup> groupdefs = addon.getSettings().getGroupLimits().getOrDefault(ent.getType(), new ArrayList<>());
            groupdefs.forEach(def -> {
                int limit = addon.getBlockLimitListener().getIsland(island.getUniqueId()).getEntityGroupLimit(def.getName());
                if (limit >= 0)
                    groupsLimits.put(def, limit);
            });
        }
        // If no island settings then try global settings
        if (limitAmount < 0 && addon.getSettings().getLimits().containsKey(ent.getType())) {
            limitAmount = addon.getSettings().getLimits().get(ent.getType());
        }
        if (addon.getSettings().getGroupLimits().containsKey(ent.getType())) {
            addon.getSettings().getGroupLimits().getOrDefault(ent.getType(), new ArrayList<>()).stream()
            .filter(group -> !groupsLimits.containsKey(group) || groupsLimits.get(group) > group.getLimit())
            .forEach(group -> groupsLimits.put(group, group.getLimit()));
        }
        if (limitAmount < 0 && groupsLimits.isEmpty()) return new AtLimitResult();
        // We have to count the entities
        if (limitAmount >= 0)
        {
            int count = (int) ent.getWorld().getEntitiesByClasses(ent.getClass()).stream()
                    .filter(e -> island.inIslandSpace(e.getLocation()))
                    .count();
            if (count > limitAmount)
                return new AtLimitResult(ent.getType(), limitAmount);
        }

        // Now do the group limits
        for (Map.Entry<Settings.EntityGroup, Integer> group : groupsLimits.entrySet()) { //do not use lambda
            if (group.getValue() < 0)
                continue;
            int count = (int) ent.getWorld().getEntities().stream()
                    .filter(e -> group.getKey().contains(e.getType()))
                    .filter(e -> island.inIslandSpace(e.getLocation())).count();
            if (count > group.getValue())
                return new AtLimitResult(group.getKey(), group.getValue());
        }
        return new AtLimitResult();
    }

    private class AtLimitResult {
        private Map.Entry<EntityType, Integer> typelimit;
        private Map.Entry<EntityGroup, Integer> grouplimit;

        public AtLimitResult() {}

        public AtLimitResult(EntityType type, int limit) {
            typelimit = new AbstractMap.SimpleEntry<>(type, limit);
        }

        public AtLimitResult(EntityGroup type, int limit) {
            grouplimit = new AbstractMap.SimpleEntry<>(type, limit);
        }

        public boolean hit() {
            return typelimit != null || grouplimit != null;
        }

        public Map.Entry<EntityType, Integer> getTypelimit() {
            return typelimit;
        }

        public Map.Entry<EntityGroup, Integer> getGrouplimit() {
            return grouplimit;
        }
    }
}



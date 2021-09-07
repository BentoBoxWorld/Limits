package world.bentobox.limits.listeners;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;

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
    private final List<UUID> justSpawned = new ArrayList<>();
    private static final List<BlockFace> CARDINALS;
    static {
        List<BlockFace> cardinals = new ArrayList<>();
        cardinals.add(BlockFace.UP);
        cardinals.add(BlockFace.NORTH);
        cardinals.add(BlockFace.SOUTH);
        cardinals.add(BlockFace.EAST);
        cardinals.add(BlockFace.WEST);
        cardinals.add(BlockFace.DOWN);
        CARDINALS = Collections.unmodifiableList(cardinals);
    }

    /**
     * Handles entity and natural limitations
     * @param addon - Limits object
     */
    public EntityLimitListener(Limits addon) {
        this.addon = addon;
        justSpawned.clear();
    }

    /**
     * Handles minecart placing
     * @param e - event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMinecart(VehicleCreateEvent e) {
        // Return if not in a known world

        if (!addon.inGameModeWorld(e.getVehicle().getWorld())) {
            return;
        }
        if (justSpawned.contains(e.getVehicle().getUniqueId())) {
            justSpawned.remove(e.getVehicle().getUniqueId());
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
        if (!addon.inGameModeWorld(e.getLocation().getWorld())) {
            return;
        }
        if (justSpawned.contains(e.getEntity().getUniqueId())) {
            justSpawned.remove(e.getEntity().getUniqueId());
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
        case SHOULDER_ENTITY:
            // Special case - do nothing - jumping around spawns parrots as they drop off player's shoulder
            return;
        default:
            // Other natural reasons
            break;
        }
        // Some checks can be done async, some not
        switch (e.getSpawnReason()) {
        case BUILD_SNOWMAN:
        case BUILD_IRONGOLEM:
            checkLimit(e, e.getEntity(), e.getSpawnReason(), bypass, addon.getSettings().isAsyncGolums());
        default:
            // Check limit sync
            checkLimit(e, e.getEntity(), e.getSpawnReason(), bypass, false);
            break;

        }

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
        if (!addon.inGameModeWorld(e.getBlock().getWorld())) {
            return;
        }
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

    /**
     * Check if a creature is allowed to spawn or not
     * @param e - CreatureSpawnEvent
     * @param bypass - true if the player involved can bypass the checks
     * @param async - true if check can be done async, false if not
     */
    private void checkLimit(Cancellable c, LivingEntity e, SpawnReason reason, boolean bypass, boolean async) {
        Location l = e.getLocation();
        if (async) {
            c.setCancelled(true);
        }
        processIsland(c, e, l, reason, bypass, async);
    }

    private void processIsland(Cancellable c, LivingEntity e, Location l, SpawnReason reason, boolean bypass, boolean async) {
        addon.getIslands().getIslandAt(e.getLocation()).ifPresent(island -> {
            // Check if creature is allowed to spawn or not
            AtLimitResult res = atLimit(island, e);
            if (bypass || island.isSpawn() || !res.hit()) {
                // Allowed
                if (async) {
                    Bukkit.getScheduler().runTask(BentoBox.getInstance(), () -> {
                        l.getWorld().spawn(l, e.getClass(), entity -> preSpawn(entity, reason, l));
                    });
                } // else do nothing
            } else {
                if (async) {
                    e.remove();
                } else {
                    c.setCancelled(true);
                }
                // If the reason is anything but because of a spawner then tell players within range
                tellPlayers(c, l, e, reason, res);
            }

        });
    }

    private void preSpawn(Entity entity, SpawnReason reason, Location l) {
        justSpawned.add(entity.getUniqueId());
        // Check for entities that need cleanup
        switch (reason) {
        case BUILD_IRONGOLEM:
            detectIronGolem(l);
            break;
        case BUILD_SNOWMAN:
            detectSnowman(l);
            break;
        case BUILD_WITHER:
            detectWither(l);
            // Create explosion
            l.getWorld().createExplosion(l, 7F, true, true, entity);
            break;
        default:
            break;


        }
    }

    private void detectIronGolem(Location l) {
        Block legs = l.getBlock();
        // Erase legs
        addon.getBlockLimitListener().removeBlock(legs);
        legs.setType(Material.AIR);
        // Look around for possible constructions
        for (BlockFace bf : CARDINALS) {
            Block body = legs.getRelative(bf);
            if (body.getType().equals(Material.IRON_BLOCK)) {
                // Check for head
                Block head = body.getRelative(bf);
                if (head.getType().equals(Material.CARVED_PUMPKIN)) {
                    // Check for arms the rule is that they must be opposite and have nothing "beneath" them
                    for (BlockFace bf2 : CARDINALS) {
                        Block arm1 = body.getRelative(bf2);
                        Block arm2 = body.getRelative(bf2.getOppositeFace());
                        if (arm1.getType() == Material.IRON_BLOCK && arm2.getType() == Material.IRON_BLOCK
                                && arm1.getRelative(bf.getOppositeFace()).isEmpty()
                                && arm2.getRelative(bf.getOppositeFace()).isEmpty()) {
                            // Erase!
                            addon.getBlockLimitListener().removeBlock(body);
                            addon.getBlockLimitListener().removeBlock(arm1);
                            addon.getBlockLimitListener().removeBlock(arm2);
                            addon.getBlockLimitListener().removeBlock(head);
                            body.setType(Material.AIR);
                            arm1.setType(Material.AIR);
                            arm2.setType(Material.AIR);
                            head.setType(Material.AIR);
                            return;
                        }
                    }
                }
            }
        }

    }

    private void detectSnowman(Location l) {
        Block legs = l.getBlock();
        // Erase legs
        addon.getBlockLimitListener().removeBlock(legs);
        legs.setType(Material.AIR);
        // Look around for possible constructions
        for (BlockFace bf : CARDINALS) {
            Block body = legs.getRelative(bf);
            if (body.getType().equals(Material.SNOW_BLOCK)) {
                // Check for head
                Block head = body.getRelative(bf);
                if (head.getType().equals(Material.CARVED_PUMPKIN)) {
                    // Erase
                    addon.getBlockLimitListener().removeBlock(body);
                    addon.getBlockLimitListener().removeBlock(head);

                    body.setType(Material.AIR);
                    head.setType(Material.AIR);
                    return;
                }
            }
        }

    }

    private void detectWither(Location l) {
        Block legs = l.getBlock();
        // Erase legs
        addon.getBlockLimitListener().removeBlock(legs);
        legs.setType(Material.AIR);
        // Look around for possible constructions
        for (BlockFace bf : CARDINALS) {
            Block body = legs.getRelative(bf);
            if (isWither(body)) {
                // Check for head
                Block head = body.getRelative(bf);
                if (head.getType().equals(Material.WITHER_SKELETON_SKULL) || head.getType().equals(Material.WITHER_SKELETON_WALL_SKULL)) {
                    // Check for arms the rule is that they must be opposite and have nothing "beneath" them
                    for (BlockFace bf2 : CARDINALS) {
                        Block arm1 = body.getRelative(bf2);
                        Block arm2 = body.getRelative(bf2.getOppositeFace());
                        Block head2 = arm1.getRelative(bf);
                        Block head3 = arm2.getRelative(bf);
                        if (isWither(arm1)
                                && isWither(arm2)
                                && arm1.getRelative(bf.getOppositeFace()).isEmpty()
                                && arm2.getRelative(bf.getOppositeFace()).isEmpty()
                                && (head2.getType().equals(Material.WITHER_SKELETON_SKULL) || head2.getType().equals(Material.WITHER_SKELETON_WALL_SKULL))
                                && (head3.getType().equals(Material.WITHER_SKELETON_SKULL) || head3.getType().equals(Material.WITHER_SKELETON_WALL_SKULL))
                                ) {
                            // Erase!
                            addon.getBlockLimitListener().removeBlock(body);
                            addon.getBlockLimitListener().removeBlock(arm1);
                            addon.getBlockLimitListener().removeBlock(arm2);
                            addon.getBlockLimitListener().removeBlock(head);
                            addon.getBlockLimitListener().removeBlock(head2);
                            addon.getBlockLimitListener().removeBlock(head3);
                            body.setType(Material.AIR);
                            arm1.setType(Material.AIR);
                            arm2.setType(Material.AIR);
                            head.setType(Material.AIR);
                            head2.setType(Material.AIR);
                            head3.setType(Material.AIR);
                            return;
                        }
                    }
                }
            }
        }
    }


    private boolean isWither(Block body) {
        if (Util.getMinecraftVersion() < 16) {
            return body.getType().equals(Material.SOUL_SAND);
        }
        return Tag.WITHER_SUMMON_BASE_BLOCKS.isTagged(body.getType());
    }

    private void tellPlayers(Cancellable e, Location l, LivingEntity entity, SpawnReason reason, AtLimitResult res) {
        if (!reason.equals(SpawnReason.SPAWNER) && !reason.equals(SpawnReason.NATURAL)
                && !reason.equals(SpawnReason.INFECTION) && !reason.equals(SpawnReason.NETHER_PORTAL)
                && !reason.equals(SpawnReason.REINFORCEMENTS) && !reason.equals(SpawnReason.SLIME_SPLIT)) {
            World w = l.getWorld();
            if (w == null) return;
            Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                for (Entity ent : w.getNearbyEntities(l, 5, 5, 5)) {
                    if (ent instanceof Player) {
                        if (res.getTypelimit() != null) {
                            User.getInstance(ent).notify("entity-limits.hit-limit", "[entity]",
                                    Util.prettifyText(entity.getType().toString()),
                                    TextVariables.NUMBER, String.valueOf(res.getTypelimit().getValue()));
                        } else {
                            User.getInstance(ent).notify("entity-limits.hit-limit", "[entity]",
                                    res.getGrouplimit().getKey().getName() + " (" + res.getGrouplimit().getKey().getTypes().stream().map(x -> Util.prettifyText(x.toString())).collect(Collectors.joining(", ")) + ")",
                                    TextVariables.NUMBER, String.valueOf(res.getGrouplimit().getValue()));
                        }
                    }
                }
            });
        }

    }

    /**
     * Checks if new entities can be added to island
     * @param island - island
     * @param ent - the entity
     * @return true if at the limit, false if not
     */
    AtLimitResult atLimit(Island island, Entity ent) {
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
            int count = (int) ent.getWorld().getEntities().stream()
                    .filter(e -> e.getType().equals(ent.getType()))
                    .filter(e -> island.inIslandSpace(e.getLocation()))
                    .count();
            if (count >= limitAmount) {
                return new AtLimitResult(ent.getType(), limitAmount);
            }
        }
        // Merge in any permission-based limits
        if (addon.getBlockLimitListener().getIsland(island.getUniqueId()) != null) {
            Map<String, EntityGroup> groupbyname = groupsLimits.keySet().stream().collect(Collectors.toMap(e -> e.getName(), e -> e));
            addon.getBlockLimitListener().getIsland(island.getUniqueId()).getEntityGroupLimits().entrySet().stream()
            .filter(e -> groupbyname.containsKey(e.getKey()))
            .forEach(e -> groupsLimits.put(groupbyname.get(e.getKey()), e.getValue()));
        }
        // Now do the group limits
        for (Map.Entry<Settings.EntityGroup, Integer> group : groupsLimits.entrySet()) { //do not use lambda
            if (group.getValue() < 0)
                continue;
            int count = (int) ent.getWorld().getEntities().stream()
                    .filter(e -> group.getKey().contains(e.getType()))
                    .filter(e -> island.inIslandSpace(e.getLocation())).count();
            if (count >= group.getValue())
                return new AtLimitResult(group.getKey(), group.getValue());
        }
        return new AtLimitResult();
    }

    class AtLimitResult {
        private Map.Entry<EntityType, Integer> typelimit;
        private Map.Entry<EntityGroup, Integer> grouplimit;

        public AtLimitResult() {}

        public AtLimitResult(EntityType type, int limit) {
            typelimit = new AbstractMap.SimpleEntry<>(type, limit);
        }

        public AtLimitResult(EntityGroup type, int limit) {
            grouplimit = new AbstractMap.SimpleEntry<>(type, limit);
        }

        /**
         * @return true if at limit
         */
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



package world.bentobox.limits.listeners;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.eclipse.jdt.annotation.Nullable;
import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.limits.EntityGroup;
import world.bentobox.limits.Limits;
import world.bentobox.limits.objects.IslandBlockCount;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Listener for entity limit events and logic.
 */
public class EntityLimitListener implements Listener {
    /**
     * Permission node for bypassing limits.
     */
    private static final String MOD_BYPASS = "mod.bypass";
    /**
     * Reference to the Limits addon.
     */
    private final Limits addon;
    /**
     * List of entity UUIDs that have just spawned to prevent double-processing.
     */
    private final List<UUID> justSpawned = new ArrayList<>();
    /**
     * Cardinal directions used for block structure detection.
     */
    private static final List<BlockFace> CARDINALS = List.of(BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN);

    /**
     * Constructs the EntityLimitListener.
     *
     * @param addon Limits addon instance
     */
    public EntityLimitListener(Limits addon) {
        this.addon = addon;
    }

    /**
     * Handles minecart placement and checks entity limits.
     *
     * @param vehicleCreateEvent VehicleCreateEvent instance
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMinecart(VehicleCreateEvent vehicleCreateEvent) {
        // Return if not in a known world
        if (!addon.inGameModeWorld(vehicleCreateEvent.getVehicle().getWorld())) {
            return;
        }
        // Debounce
        if (justSpawned.contains(vehicleCreateEvent.getVehicle().getUniqueId())) {
            justSpawned.remove(vehicleCreateEvent.getVehicle().getUniqueId());
            return;
        }
        // Check island
        addon.getIslands().getProtectedIslandAt(vehicleCreateEvent.getVehicle().getLocation())
                // Ignore spawn
                .filter(i -> !i.isSpawn())
                .ifPresent(island -> {
                    // Check if the player is at the limit
                    AtLimitResult res = atLimit(island, vehicleCreateEvent.getVehicle());
                    if (res.hit()) {
                        vehicleCreateEvent.setCancelled(true);
                        this.tellPlayers(vehicleCreateEvent.getVehicle().getLocation(), vehicleCreateEvent.getVehicle(), SpawnReason.MOUNT, res);
                    }
                });
    }

    /**
     * Handles entity breeding and checks entity limits.
     *
     * @param entityBreedEvent EntityBreedEvent instance
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreed(final EntityBreedEvent entityBreedEvent) {
        if (addon.inGameModeWorld(entityBreedEvent.getEntity().getWorld())
                && entityBreedEvent.getBreeder() != null
                && (entityBreedEvent.getBreeder() instanceof Player player)
                && !(player.isOp() || player.hasPermission(addon.getPlugin().getIWM().getPermissionPrefix(entityBreedEvent.getEntity().getWorld()) + MOD_BYPASS))
                && !checkLimit(entityBreedEvent, entityBreedEvent.getEntity(), SpawnReason.BREEDING, false)
                && entityBreedEvent.getFather() instanceof Breedable father && entityBreedEvent.getMother() instanceof Breedable mother) {
            father.setBreed(false);
            mother.setBreed(false);
        }
    }

    /**
     * Handles creature spawning and checks entity limits.
     *
     * @param creatureSpawnEvent CreatureSpawnEvent instance
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCreatureSpawn(final CreatureSpawnEvent creatureSpawnEvent) {
        // Return if not in a known world
        if (!addon.inGameModeWorld(creatureSpawnEvent.getLocation().getWorld())) {
            return;
        }
        if (justSpawned.contains(creatureSpawnEvent.getEntity().getUniqueId())) {
            justSpawned.remove(creatureSpawnEvent.getEntity().getUniqueId());
            return;
        }
        if (creatureSpawnEvent.getSpawnReason().equals(SpawnReason.SHOULDER_ENTITY) || (!(creatureSpawnEvent.getEntity() instanceof Villager) && creatureSpawnEvent.getSpawnReason().equals(SpawnReason.BREEDING))) {
            // Special case - do nothing - jumping around spawns parrots as they drop off player's shoulder
            // Ignore breeding because it's handled in the EntityBreedEvent listener
            return;
        }
        // Some checks can be done async, some not
        if (creatureSpawnEvent.getSpawnReason().equals(SpawnReason.BUILD_SNOWMAN) || creatureSpawnEvent.getSpawnReason().equals(SpawnReason.BUILD_IRONGOLEM)) {
            checkLimit(creatureSpawnEvent, creatureSpawnEvent.getEntity(), creatureSpawnEvent.getSpawnReason(), addon.getSettings().isAsyncGolums());
        } else {
            // Check limit sync
            checkLimit(creatureSpawnEvent, creatureSpawnEvent.getEntity(), creatureSpawnEvent.getSpawnReason(), false);
        }

    }

    /**
     * handles paintings and item frames
     *
     * @param hangingPlaceEvent - event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlock(HangingPlaceEvent hangingPlaceEvent) {
        if (!addon.inGameModeWorld(hangingPlaceEvent.getBlock().getWorld())) {
            return;
        }
        Player player = hangingPlaceEvent.getPlayer();
        if (player == null) return;
        addon.getIslands().getIslandAt(hangingPlaceEvent.getEntity().getLocation()).ifPresent(island -> {
            boolean bypass = Objects.requireNonNull(player).isOp() || player.hasPermission(addon.getPlugin().getIWM().getPermissionPrefix(hangingPlaceEvent.getEntity().getWorld()) + MOD_BYPASS);
            // Check if entity can be hung
            AtLimitResult res;
            if (!bypass && !island.isSpawn() && (res = atLimit(island, hangingPlaceEvent.getEntity())).hit()) {
                // Not allowed
                hangingPlaceEvent.setCancelled(true);
                if (res.getTypelimit() != null) {
                    User.getInstance(player).notify("block-limits.hit-limit", "[material]",
                            Util.prettifyText(hangingPlaceEvent.getEntity().getType().toString()),
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
     * Checks if a creature is allowed to spawn or not.
     *
     * @param cancelableEvent Cancelable event instance
     * @param livingEntity Entity being spawned
     * @param spawnReason Reason for spawning
     * @param runAsync Whether to run asynchronously
     * @return true if allowed or async, false if not
     */
    private boolean checkLimit(Cancellable cancelableEvent, LivingEntity livingEntity, SpawnReason spawnReason, boolean runAsync) {
        Location l = livingEntity.getLocation();
        if (runAsync) {
            cancelableEvent.setCancelled(true);
        }
        return processIsland(cancelableEvent, livingEntity, l, spawnReason, runAsync);
    }

    /**
     * Processes the island for entity limit checks.
     *
     * @param cancelableEvent Cancelable event instance
     * @param livingEntity Entity being spawned
     * @param location Location of entity
     * @param spawnReason Reason for spawning
     * @param runAsync Whether to run asynchronously
     * @return true if allowed, false if not
     */
    private boolean processIsland(Cancellable cancelableEvent, LivingEntity livingEntity, Location location, SpawnReason spawnReason, boolean runAsync) {
        if (addon.getIslands().getIslandAt(livingEntity.getLocation()).isEmpty()) {
            cancelableEvent.setCancelled(false);
            return true;
        }
        Island island = addon.getIslands().getIslandAt(livingEntity.getLocation()).get();
        // Check if creature is allowed to spawn or not
        AtLimitResult res = atLimit(island, livingEntity);
        if (island.isSpawn() || !res.hit()) {
            // Allowed
            if (runAsync) {
                Bukkit.getScheduler().runTask(BentoBox.getInstance(), () -> preSpawn(livingEntity.getType(), spawnReason, location));
            } // else do nothing
        } else {
            if (runAsync) {
                livingEntity.remove();
            } else {
                cancelableEvent.setCancelled(true);
            }
            // If the reason is anything but because of a spawner then tell players within range
            tellPlayers(location, livingEntity, spawnReason, res);
            return false;
        }
        return true;
    }

    /**
     * Spawns an entity and handles special cases for golems, snowmen, and withers.
     *
     * @param entityType Type of entity to spawn
     * @param spawnReason Reason for spawning
     * @param location Location to spawn
     */
    private void preSpawn(EntityType entityType, SpawnReason spawnReason, Location location) {

        // Check for entities that need cleanup
        switch (spawnReason) {
            case BUILD_IRONGOLEM -> detectIronGolem(location);
            case BUILD_SNOWMAN -> detectSnowman(location);
            case BUILD_WITHER -> {
                detectWither(location);
            }
            default -> throw new IllegalArgumentException("Unexpected value: " + spawnReason);
        }
        Entity entity = location.getWorld().spawnEntity(location, entityType);
        justSpawned.add(entity.getUniqueId());
        if (spawnReason == SpawnReason.BUILD_WITHER) {
            // Create explosion
            location.getWorld().createExplosion(location, 7F, true, true, entity);
        }
    }

    /**
     * Detects and removes iron golem construction blocks.
     *
     * @param location Location of golem spawn
     */
    private void detectIronGolem(Location location) {
        Block legs = location.getBlock();
        // Erase legs
        addon.getBlockLimitListener().removeBlock(legs);
        legs.setType(Material.AIR);
        // Look around for possible constructions
        for (BlockFace bf : CARDINALS) {
            Block body = legs.getRelative(bf);
            if (body.getType().equals(Material.IRON_BLOCK)) {
                // Check for head
                Block head = body.getRelative(bf);
                if (head.getType() == Material.CARVED_PUMPKIN || head.getType() == Material.JACK_O_LANTERN) {
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

    /**
     * Detects and removes snowman construction blocks.
     *
     * @param location Location of snowman spawn
     */
    private void detectSnowman(Location location) {
        Block legs = location.getBlock();
        // Erase legs
        addon.getBlockLimitListener().removeBlock(legs);
        legs.setType(Material.AIR);
        // Look around for possible constructions
        for (BlockFace bf : CARDINALS) {
            Block body = legs.getRelative(bf);
            if (body.getType().equals(Material.SNOW_BLOCK)) {
                // Check for head
                Block head = body.getRelative(bf);
                if (head.getType() == Material.CARVED_PUMPKIN || head.getType() == Material.JACK_O_LANTERN) {
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

    /**
     * Detects and removes wither construction blocks.
     *
     * @param location Location of wither spawn
     */
    private void detectWither(Location location) {
        Block legs = location.getBlock();
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

    /**
     * Checks if the block is a valid wither base block.
     *
     * @param block Block to check
     * @return true if block is a wither base block
     */
    private boolean isWither(Block block) {
        if (Util.getMinecraftVersion() < 16) {
            return block.getType().equals(Material.SOUL_SAND);
        }
        return Tag.WITHER_SUMMON_BASE_BLOCKS.isTagged(block.getType());
    }

    /**
     * Notifies players within a 5x5x5 radius that entity spawning was denied.
     *
     * @param location Location of denied spawn
     * @param entity Entity that was denied
     * @param spawnReason Reason for spawning
     * @param atLimitResult Result of limit check
     */
    private void tellPlayers(Location location, Entity entity, SpawnReason spawnReason, AtLimitResult atLimitResult) {
        if (spawnReason.equals(SpawnReason.SPAWNER) || spawnReason.equals(SpawnReason.NATURAL)
                || spawnReason.equals(SpawnReason.INFECTION) || spawnReason.equals(SpawnReason.NETHER_PORTAL)
                || spawnReason.equals(SpawnReason.REINFORCEMENTS) || spawnReason.equals(SpawnReason.SLIME_SPLIT)) {
            return;
        }
        World w = location.getWorld();
        if (w == null) return;
        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
            for (Entity ent : w.getNearbyEntities(location, 5, 5, 5)) {
                if (ent instanceof Player p) {
                    p.updateInventory();
                    if (atLimitResult.getTypelimit() != null) {
                        User.getInstance(p).notify("entity-limits.hit-limit", "[entity]",
                                Util.prettifyText(entity.getType().toString()),
                                TextVariables.NUMBER, String.valueOf(atLimitResult.getTypelimit().getValue()));
                    } else {
                        User.getInstance(p).notify("entity-limits.hit-limit", "[entity]",
                                atLimitResult.getGrouplimit().getKey().getName() + " (" + atLimitResult.getGrouplimit().getKey().getTypes().stream().map(x -> Util.prettifyText(x.toString())).collect(Collectors.joining(", ")) + ")",
                                TextVariables.NUMBER, String.valueOf(atLimitResult.getGrouplimit().getValue()));
                    }
                }
            }
        });

    }

    /**
     * Checks if new entities can be added to the island.
     *
     * @param island Island to check
     * @param entity Entity to check
     * @return AtLimitResult indicating if at limit
     */
    AtLimitResult atLimit(Island island, Entity entity) {
        // Check island settings first
        int limitAmount = -1;
        Map<EntityGroup, Integer> groupsLimits = new HashMap<>();

        @Nullable
        IslandBlockCount ibc = addon.getBlockLimitListener().getIsland(island.getUniqueId());
        if (ibc != null) {
            // Get the limit amount for this type
            limitAmount = ibc.getEntityLimit(entity.getType());
            // Handle entity groups
            List<EntityGroup> groupdefs = addon.getSettings().getGroupLimits().getOrDefault(entity.getType(),
                    new ArrayList<>());
            groupdefs.forEach(def -> {
                int limit = ibc.getEntityGroupLimit(def.getName());
                if (limit >= 0)
                    groupsLimits.put(def, limit);
            });
        }
        // If no island settings then try global settings
        if (limitAmount < 0 && addon.getSettings().getLimits().containsKey(entity.getType())) {
            limitAmount = addon.getSettings().getLimits().get(entity.getType());
        }
        // Group limits
        if (addon.getSettings().getGroupLimits().containsKey(entity.getType())) {
            addon.getSettings().getGroupLimits().getOrDefault(entity.getType(), new ArrayList<>()).stream()
                    .filter(group -> !groupsLimits.containsKey(group) || groupsLimits.get(group) > group.getLimit())
                    .forEach(group -> groupsLimits.put(group, group.getLimit()));
        }
        if (limitAmount < 0 && groupsLimits.isEmpty()) {
            return new AtLimitResult();
        }

        // We have to count the entities
        if (limitAmount >= 0) {
            int count = (int) entity.getWorld().getNearbyEntities(island.getBoundingBox()).stream()
                    .filter(e -> e.getType().equals(entity.getType()))
                    .count();
            int max = limitAmount + (ibc == null ? 0 : ibc.getEntityLimitOffset(entity.getType()));
            if (count >= max) {
                return new AtLimitResult(entity.getType(), max);
            }
        }
        // Group limits
        if (ibc != null) {
            Map<String, EntityGroup> groupbyname = groupsLimits.keySet().stream()
                    .collect(Collectors.toMap(EntityGroup::getName, e -> e));
            ibc.getEntityGroupLimits().entrySet().stream()
                    .filter(e -> groupbyname.containsKey(e.getKey()))
                    .forEach(e -> groupsLimits.put(groupbyname.get(e.getKey()), e.getValue()));
        }
        // Now do the group limits
        for (Map.Entry<EntityGroup, Integer> group : groupsLimits.entrySet()) { //do not use lambda
            if (group.getValue() < 0)
                continue;
            //            int count = (int) ent.getWorld().getEntities().stream()
            //                    .filter(e -> group.getKey().contains(e.getType()))
            //                    .filter(e -> island.inIslandSpace(e.getLocation())).count();
            int count = (int) entity.getWorld().getNearbyEntities(island.getBoundingBox()).stream()
                    .filter(e -> group.getKey().contains(e.getType()))
                    .count();
            int max = group.getValue() + +(ibc == null ? 0 : ibc.getEntityGroupLimitOffset(group.getKey().getName()));
            if (count >= max) {
                return new AtLimitResult(group.getKey(), max);
            }
        }
        return new AtLimitResult();
    }

    /**
     * Result class for entity limit checks.
     */
    static class AtLimitResult {
        private Map.Entry<EntityType, Integer> typelimit;
        private Map.Entry<EntityGroup, Integer> grouplimit;

        /**
         * Default constructor for AtLimitResult.
         */
        public AtLimitResult() {
        }

        /**
         * Constructor for type limit result.
         *
         * @param type EntityType at limit
         * @param limit Limit value
         */
        public AtLimitResult(EntityType type, int limit) {
            typelimit = new AbstractMap.SimpleEntry<>(type, limit);
        }

        /**
         * Constructor for group limit result.
         *
         * @param type EntityGroup at limit
         * @param limit Limit value
         */
        public AtLimitResult(EntityGroup type, int limit) {
            grouplimit = new AbstractMap.SimpleEntry<>(type, limit);
        }

        /**
         * Returns true if at limit.
         *
         * @return true if at limit
         */
        public boolean hit() {
            return typelimit != null || grouplimit != null;
        }

        /**
         * Gets the type limit entry.
         *
         * @return Entry of EntityType and limit
         */
        public Map.Entry<EntityType, Integer> getTypelimit() {
            return typelimit;
        }

        /**
         * Gets the group limit entry.
         *
         * @return Entry of EntityGroup and limit
         */
        public Map.Entry<EntityGroup, Integer> getGrouplimit() {
            return grouplimit;
        }
    }
}



package world.bentobox.limits.listeners;

import org.bukkit.*;
import org.bukkit.World.Environment;
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
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
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
import world.bentobox.limits.Settings;
import world.bentobox.limits.objects.IslandBlockCount;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Listener for entity limit events and logic.
 *
 * <p>Counts are stored persistently per-island per-environment in {@link IslandBlockCount}
 * (see {@code envEntityCounts}). This listener increments on successful spawn and
 * decrements on {@link EntityRemoveEvent} (excluding chunk unload), so counts remain
 * accurate even when nether/end chunks are unloaded — fixes
 * <a href="https://github.com/BentoBoxWorld/Limits/issues/43">issue #43</a>.
 */
public class EntityLimitListener implements Listener {
    /** Permission node for bypassing limits. */
    private static final String MOD_BYPASS = "mod.bypass";
    private final Limits addon;
    /** Entity UUIDs that have just spawned to prevent double-processing. */
    private final List<UUID> justSpawned = new ArrayList<>();
    /** Cardinal directions used for block structure detection. */
    private static final List<BlockFace> CARDINALS = List.of(BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN);

    public EntityLimitListener(Limits addon) {
        this.addon = addon;
    }

    private static Environment envOf(World w) {
        Environment env = w.getEnvironment();
        return Settings.ENVIRONMENTS.contains(env) ? env : Environment.NORMAL;
    }

    /* =========================================================================
     * Limit-check handlers (LOW priority — cancel if at limit)
     * ========================================================================= */

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMinecart(VehicleCreateEvent vehicleCreateEvent) {
        if (!addon.inGameModeWorld(vehicleCreateEvent.getVehicle().getWorld())) return;
        if (justSpawned.contains(vehicleCreateEvent.getVehicle().getUniqueId())) {
            justSpawned.remove(vehicleCreateEvent.getVehicle().getUniqueId());
            return;
        }
        addon.getIslands().getProtectedIslandAt(vehicleCreateEvent.getVehicle().getLocation())
                .filter(i -> !i.isSpawn())
                .ifPresent(island -> {
                    AtLimitResult res = atLimit(island, vehicleCreateEvent.getVehicle());
                    if (res.hit()) {
                        vehicleCreateEvent.setCancelled(true);
                        this.tellPlayers(vehicleCreateEvent.getVehicle().getLocation(),
                                vehicleCreateEvent.getVehicle(), SpawnReason.MOUNT, res);
                    }
                });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreed(final EntityBreedEvent entityBreedEvent) {
        if (addon.inGameModeWorld(entityBreedEvent.getEntity().getWorld())
                && entityBreedEvent.getBreeder() != null
                && (entityBreedEvent.getBreeder() instanceof Player player)
                && !(player.isOp() || player.hasPermission(addon.getPlugin().getIWM()
                        .getPermissionPrefix(entityBreedEvent.getEntity().getWorld()) + MOD_BYPASS))
                && !checkLimit(entityBreedEvent, entityBreedEvent.getEntity(), SpawnReason.BREEDING, false)
                && entityBreedEvent.getFather() instanceof Breedable father
                && entityBreedEvent.getMother() instanceof Breedable mother) {
            father.setBreed(false);
            mother.setBreed(false);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCreatureSpawn(final CreatureSpawnEvent creatureSpawnEvent) {
        if (!addon.inGameModeWorld(creatureSpawnEvent.getLocation().getWorld())) return;
        if (justSpawned.contains(creatureSpawnEvent.getEntity().getUniqueId())) {
            justSpawned.remove(creatureSpawnEvent.getEntity().getUniqueId());
            return;
        }
        if (creatureSpawnEvent.getSpawnReason().equals(SpawnReason.SHOULDER_ENTITY)
                || (!(creatureSpawnEvent.getEntity() instanceof Villager)
                        && creatureSpawnEvent.getSpawnReason().equals(SpawnReason.BREEDING))) {
            return;
        }
        if (creatureSpawnEvent.getSpawnReason().equals(SpawnReason.BUILD_SNOWMAN)
                || creatureSpawnEvent.getSpawnReason().equals(SpawnReason.BUILD_IRONGOLEM)) {
            checkLimit(creatureSpawnEvent, creatureSpawnEvent.getEntity(), creatureSpawnEvent.getSpawnReason(),
                    addon.getSettings().isAsyncGolums());
        } else {
            checkLimit(creatureSpawnEvent, creatureSpawnEvent.getEntity(), creatureSpawnEvent.getSpawnReason(),
                    false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlock(HangingPlaceEvent hangingPlaceEvent) {
        if (!addon.inGameModeWorld(hangingPlaceEvent.getBlock().getWorld())) return;
        Player player = hangingPlaceEvent.getPlayer();
        if (player == null) return;
        addon.getIslands().getIslandAt(hangingPlaceEvent.getEntity().getLocation()).ifPresent(island -> {
            boolean bypass = Objects.requireNonNull(player).isOp() || player.hasPermission(
                    addon.getPlugin().getIWM().getPermissionPrefix(hangingPlaceEvent.getEntity().getWorld())
                            + MOD_BYPASS);
            AtLimitResult res;
            if (!bypass && !island.isSpawn() && (res = atLimit(island, hangingPlaceEvent.getEntity())).hit()) {
                hangingPlaceEvent.setCancelled(true);
                if (res.getTypelimit() != null) {
                    User.getInstance(player).notify("block-limits.hit-limit", "[material]",
                            Util.prettifyText(hangingPlaceEvent.getEntity().getType().toString()),
                            TextVariables.NUMBER, String.valueOf(res.getTypelimit().getValue()));
                } else {
                    User.getInstance(player).notify("block-limits.hit-limit", "[material]",
                            res.getGrouplimit().getKey().getName() + " ("
                                    + res.getGrouplimit().getKey().getTypes().stream()
                                            .map(x -> Util.prettifyText(x.toString()))
                                            .collect(Collectors.joining(", "))
                                    + ")",
                            TextVariables.NUMBER, String.valueOf(res.getGrouplimit().getValue()));
                }
            }
        });
    }

    /* =========================================================================
     * Increment handlers (MONITOR priority — count entities that actually spawned)
     * ========================================================================= */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawnTrack(final CreatureSpawnEvent e) {
        trackSpawn(e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleCreateTrack(final VehicleCreateEvent e) {
        trackSpawn(e.getVehicle());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingPlaceTrack(final HangingPlaceEvent e) {
        trackSpawn(e.getEntity());
    }

    private void trackSpawn(Entity entity) {
        if (entity == null) return;
        World w = entity.getWorld();
        if (!addon.inGameModeWorld(w)) return;
        addon.getIslands().getIslandAt(entity.getLocation())
                .filter(island -> !island.isSpawn())
                .ifPresent(island -> {
                    IslandBlockCount ibc = addon.getBlockLimitListener().getIsland(island);
                    ibc.incrementEntity(envOf(w), entity.getType());
                });
    }

    /* =========================================================================
     * Decrement on permanent removal
     * ========================================================================= */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent e) {
        // Entities unloaded with their chunk are still alive — don't decrement.
        if (e.getCause() == EntityRemoveEvent.Cause.UNLOAD) return;
        Entity entity = e.getEntity();
        World w = entity.getWorld();
        if (!addon.inGameModeWorld(w)) return;
        addon.getIslands().getIslandAt(entity.getLocation())
                .filter(island -> !island.isSpawn())
                .ifPresent(island -> {
                    IslandBlockCount ibc = addon.getBlockLimitListener().getIsland(island.getUniqueId());
                    if (ibc != null) {
                        ibc.decrementEntity(envOf(w), entity.getType());
                    }
                });
    }

    /* =========================================================================
     * Portal: move count between envs on same island
     * ========================================================================= */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == null) return;
        Entity entity = e.getEntity();
        World fromWorld = entity.getWorld();
        World toWorld = e.getTo().getWorld();
        Environment fromEnv = envOf(fromWorld);
        Environment toEnv = envOf(toWorld);
        if (fromEnv == toEnv) return;
        if (!addon.inGameModeWorld(fromWorld) && !addon.inGameModeWorld(toWorld)) return;

        // Decrement at source if on a tracked island
        if (addon.inGameModeWorld(fromWorld)) {
            addon.getIslands().getIslandAt(entity.getLocation())
                    .filter(island -> !island.isSpawn())
                    .ifPresent(island -> {
                        IslandBlockCount ibc = addon.getBlockLimitListener().getIsland(island.getUniqueId());
                        if (ibc != null) {
                            ibc.decrementEntity(fromEnv, entity.getType());
                        }
                    });
        }
        // Increment at destination if on a tracked island
        if (addon.inGameModeWorld(toWorld)) {
            addon.getIslands().getIslandAt(e.getTo())
                    .filter(island -> !island.isSpawn())
                    .ifPresent(island -> {
                        IslandBlockCount ibc = addon.getBlockLimitListener().getIsland(island);
                        ibc.incrementEntity(toEnv, entity.getType());
                    });
        }
    }

    /* =========================================================================
     * Limit-checking core
     * ========================================================================= */

    private boolean checkLimit(Cancellable cancelableEvent, LivingEntity livingEntity, SpawnReason spawnReason,
            boolean runAsync) {
        Location l = livingEntity.getLocation();
        if (runAsync) {
            cancelableEvent.setCancelled(true);
        }
        return processIsland(cancelableEvent, livingEntity, l, spawnReason, runAsync);
    }

    private boolean processIsland(Cancellable cancelableEvent, LivingEntity livingEntity, Location location,
            SpawnReason spawnReason, boolean runAsync) {
        if (addon.getIslands().getIslandAt(livingEntity.getLocation()).isEmpty()) {
            cancelableEvent.setCancelled(false);
            return true;
        }
        Island island = addon.getIslands().getIslandAt(livingEntity.getLocation()).get();
        AtLimitResult res = atLimit(island, livingEntity);
        if (island.isSpawn() || !res.hit()) {
            if (runAsync) {
                Bukkit.getScheduler().runTask(BentoBox.getInstance(),
                        () -> preSpawn(livingEntity.getType(), spawnReason, location));
            }
        } else {
            if (runAsync) {
                livingEntity.remove();
            } else {
                cancelableEvent.setCancelled(true);
            }
            tellPlayers(location, livingEntity, spawnReason, res);
            return false;
        }
        return true;
    }

    private void preSpawn(EntityType entityType, SpawnReason spawnReason, Location location) {
        switch (spawnReason) {
            case BUILD_IRONGOLEM -> detectIronGolem(location);
            case BUILD_SNOWMAN -> detectSnowman(location);
            case BUILD_WITHER -> detectWither(location);
            default -> throw new IllegalArgumentException("Unexpected value: " + spawnReason);
        }
        Entity entity = location.getWorld().spawnEntity(location, entityType);
        justSpawned.add(entity.getUniqueId());
        if (spawnReason == SpawnReason.BUILD_WITHER) {
            location.getWorld().createExplosion(location, 7F, true, true, entity);
        }
    }

    private void detectIronGolem(Location location) {
        Block legs = location.getBlock();
        addon.getBlockLimitListener().removeBlock(legs);
        legs.setType(Material.AIR);
        for (BlockFace bf : CARDINALS) {
            Block body = legs.getRelative(bf);
            if (body.getType().equals(Material.IRON_BLOCK)) {
                Block head = body.getRelative(bf);
                if (isGolemHead(head) && eraseGolemIfArmsMatch(body, head, bf)) {
                    return;
                }
            }
        }
    }

    private boolean isGolemHead(Block block) {
        return block.getType() == Material.CARVED_PUMPKIN || block.getType() == Material.JACK_O_LANTERN;
    }

    private boolean eraseGolemIfArmsMatch(Block body, Block head, BlockFace bf) {
        for (BlockFace bf2 : CARDINALS) {
            Block arm1 = body.getRelative(bf2);
            Block arm2 = body.getRelative(bf2.getOppositeFace());
            if (arm1.getType() == Material.IRON_BLOCK && arm2.getType() == Material.IRON_BLOCK
                    && arm1.getRelative(bf.getOppositeFace()).isEmpty()
                    && arm2.getRelative(bf.getOppositeFace()).isEmpty()) {
                addon.getBlockLimitListener().removeBlock(body);
                addon.getBlockLimitListener().removeBlock(arm1);
                addon.getBlockLimitListener().removeBlock(arm2);
                addon.getBlockLimitListener().removeBlock(head);
                body.setType(Material.AIR);
                arm1.setType(Material.AIR);
                arm2.setType(Material.AIR);
                head.setType(Material.AIR);
                return true;
            }
        }
        return false;
    }

    private void detectSnowman(Location location) {
        Block legs = location.getBlock();
        addon.getBlockLimitListener().removeBlock(legs);
        legs.setType(Material.AIR);
        for (BlockFace bf : CARDINALS) {
            Block body = legs.getRelative(bf);
            if (body.getType().equals(Material.SNOW_BLOCK)) {
                Block head = body.getRelative(bf);
                if (head.getType() == Material.CARVED_PUMPKIN || head.getType() == Material.JACK_O_LANTERN) {
                    addon.getBlockLimitListener().removeBlock(body);
                    addon.getBlockLimitListener().removeBlock(head);
                    body.setType(Material.AIR);
                    head.setType(Material.AIR);
                    return;
                }
            }
        }
    }

    private void detectWither(Location location) {
        Block legs = location.getBlock();
        addon.getBlockLimitListener().removeBlock(legs);
        legs.setType(Material.AIR);
        for (BlockFace bf : CARDINALS) {
            Block body = legs.getRelative(bf);
            if (isWither(body)) {
                Block head = body.getRelative(bf);
                if (isWitherHead(head) && eraseWitherIfArmsMatch(body, head, bf)) {
                    return;
                }
            }
        }
    }

    private boolean isWitherHead(Block block) {
        return block.getType().equals(Material.WITHER_SKELETON_SKULL)
                || block.getType().equals(Material.WITHER_SKELETON_WALL_SKULL);
    }

    private boolean eraseWitherIfArmsMatch(Block body, Block head, BlockFace bf) {
        for (BlockFace bf2 : CARDINALS) {
            Block arm1 = body.getRelative(bf2);
            Block arm2 = body.getRelative(bf2.getOppositeFace());
            Block head2 = arm1.getRelative(bf);
            Block head3 = arm2.getRelative(bf);
            if (isWither(arm1) && isWither(arm2)
                    && arm1.getRelative(bf.getOppositeFace()).isEmpty()
                    && arm2.getRelative(bf.getOppositeFace()).isEmpty()
                    && isWitherHead(head2) && isWitherHead(head3)) {
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
                return true;
            }
        }
        return false;
    }

    private boolean isWither(Block block) {
        if (Util.getMinecraftVersion() < 16) {
            return block.getType().equals(Material.SOUL_SAND);
        }
        return Tag.WITHER_SUMMON_BASE_BLOCKS.isTagged(block.getType());
    }

    private void tellPlayers(Location location, Entity entity, SpawnReason spawnReason, AtLimitResult res) {
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
                    if (res.getTypelimit() != null) {
                        User.getInstance(p).notify("entity-limits.hit-limit", "[entity]",
                                Util.prettifyText(entity.getType().toString()),
                                TextVariables.NUMBER, String.valueOf(res.getTypelimit().getValue()));
                    } else {
                        User.getInstance(p).notify("entity-limits.hit-limit", "[entity]",
                                res.getGrouplimit().getKey().getName() + " ("
                                        + res.getGrouplimit().getKey().getTypes().stream()
                                                .map(x -> Util.prettifyText(x.toString()))
                                                .collect(Collectors.joining(", "))
                                        + ")",
                                TextVariables.NUMBER, String.valueOf(res.getGrouplimit().getValue()));
                    }
                }
            }
        });
    }

    /**
     * Check whether a new entity of the given type would put the island over its limit
     * for the entity's current environment.
     */
    AtLimitResult atLimit(Island island, Entity entity) {
        Environment env = envOf(entity.getWorld());
        EntityType type = entity.getType();
        @Nullable
        IslandBlockCount ibc = addon.getBlockLimitListener().getIsland(island.getUniqueId());

        // 1. Resolve effective per-type limit
        int limitAmount = -1;
        if (ibc != null) {
            limitAmount = ibc.getEntityLimit(env, type);
        }
        if (limitAmount < 0) {
            Integer cfg = addon.getSettings().getLimits(env).get(type);
            if (cfg != null) limitAmount = cfg;
        }

        // 2. Build effective group-limits for this env, applying island and config overrides
        Map<EntityGroup, Integer> groupsLimits = new HashMap<>();
        List<EntityGroup> groupdefs = addon.getSettings().getGroupLimits().getOrDefault(type, Collections.emptyList());
        Map<String, Integer> envGroupCfg = addon.getSettings().getGroupLimits(env);
        Map<String, Integer> islandGroupOverrides = ibc == null ? Collections.emptyMap() : ibc.getEntityGroupLimits(env);
        for (EntityGroup def : groupdefs) {
            int limit = islandGroupOverrides.getOrDefault(def.getName(), envGroupCfg.getOrDefault(def.getName(), -1));
            if (limit >= 0) {
                groupsLimits.put(def, limit);
            }
        }

        if (limitAmount < 0 && groupsLimits.isEmpty()) {
            return new AtLimitResult();
        }

        // 3. Read counts from the IBC; use 0 if no IBC has been created yet
        if (limitAmount >= 0) {
            int count = ibc == null ? 0 : ibc.getEntityCount(env, type);
            int max = limitAmount + (ibc == null ? 0 : ibc.getEntityLimitOffset(env, type));
            if (count >= max) {
                return new AtLimitResult(type, max);
            }
        }
        for (Map.Entry<EntityGroup, Integer> group : groupsLimits.entrySet()) {
            if (group.getValue() < 0) continue;
            int count = sumGroupCount(ibc, env, group.getKey());
            int max = group.getValue() + (ibc == null ? 0 : ibc.getEntityGroupLimitOffset(env, group.getKey().getName()));
            if (count >= max) {
                return new AtLimitResult(group.getKey(), max);
            }
        }
        return new AtLimitResult();
    }

    private int sumGroupCount(@Nullable IslandBlockCount ibc, Environment env, EntityGroup group) {
        if (ibc == null) return 0;
        Map<EntityType, Integer> counts = ibc.getEntityCounts(env);
        int total = 0;
        for (EntityType t : group.getTypes()) {
            total += counts.getOrDefault(t, 0);
        }
        return total;
    }

    static class AtLimitResult {
        private Map.Entry<EntityType, Integer> typelimit;
        private Map.Entry<EntityGroup, Integer> grouplimit;

        public AtLimitResult() {
        }

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

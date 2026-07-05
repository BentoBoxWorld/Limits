package world.bentobox.limits.listeners;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.inventory.ItemStack;
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
    /** Locale reference for the entity limit notification. */
    private static final String ENTITY_LIMIT_HIT = "entity-limits.hit-limit";
    /** Notification placeholder for the entity name. */
    private static final String ENTITY_PLACEHOLDER = "[entity]";
    private final Limits addon;
    /** Entity UUIDs that have just spawned to prevent double-processing. */
    private final List<UUID> justSpawned = new ArrayList<>();
    /** Entity UUIDs that are currently portaling to prevent double-decrement on cross-world removal. */
    private final List<UUID> justPortaled = new ArrayList<>();
    /** Maps entity UUID to island ID so decrement works even when the entity dies off-island. */
    private final Map<UUID, String> entityIslandMap = new HashMap<>();
    /** Cardinal directions used for block structure detection. */
    private static final List<BlockFace> CARDINALS = List.of(BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN);
    /** One block face per horizontal axis, used to find golem arms regardless of facing. */
    private static final List<BlockFace> HORIZONTAL = List.of(BlockFace.NORTH, BlockFace.EAST);

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

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlock(HangingPlaceEvent hangingPlaceEvent) {
        if (!addon.inGameModeWorld(hangingPlaceEvent.getBlock().getWorld())) return;
        Player player = hangingPlaceEvent.getPlayer();
        if (player == null) return;
        addon.getIslands().getIslandAt(hangingPlaceEvent.getEntity().getLocation()).ifPresent(island -> {
            boolean bypass = Objects.requireNonNull(player).isOp() || player.hasPermission(
                    addon.getPlugin().getIWM().getPermissionPrefix(hangingPlaceEvent.getEntity().getWorld())
                            + MOD_BYPASS);
            if (bypass || island.isSpawn()) {
                return;
            }
            AtLimitResult res = atLimit(island, hangingPlaceEvent.getEntity());
            if (res.hit()) {
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

    /**
     * Deny spawn-egg use the moment a player would exceed an entity limit, before the egg is
     * consumed. The {@link CreatureSpawnEvent} path already cancels the over-limit spawn, but by
     * then the egg item has been used up; cancelling the interaction here keeps the egg (#134).
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawnEggUseOnBlock(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }
        EntityType type = spawnEggType(e.getItem());
        if (type != null) {
            checkSpawnEggLimit(e, e.getPlayer(), e.getClickedBlock().getLocation(), type);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawnEggUseOnEntity(PlayerInteractEntityEvent e) {
        // PlayerInteractEntityEvent.getHand() is never null (always HAND or OFF_HAND)
        EntityType type = spawnEggType(e.getPlayer().getInventory().getItem(e.getHand()));
        if (type != null) {
            checkSpawnEggLimit(e, e.getPlayer(), e.getRightClicked().getLocation(), type);
        }
    }

    private void checkSpawnEggLimit(Cancellable event, Player player, Location location, EntityType type) {
        if (!addon.inGameModeWorld(location.getWorld())) {
            return;
        }
        addon.getIslands().getIslandAt(location).ifPresent(island -> {
            boolean bypass = player.isOp() || player.hasPermission(
                    addon.getPlugin().getIWM().getPermissionPrefix(location.getWorld()) + MOD_BYPASS);
            if (bypass || island.isSpawn()) {
                return;
            }
            AtLimitResult res = atLimit(island, type, envOf(location.getWorld()));
            if (res.hit()) {
                event.setCancelled(true);
                notifyEntityLimit(player, type, res);
            }
        });
    }

    /**
     * Resolve the entity type a spawn-egg item would create, e.g. {@code PIG_SPAWN_EGG -> PIG}.
     *
     * @return the entity type, or {@code null} if the item is not a recognised spawn egg
     */
    private EntityType spawnEggType(ItemStack item) {
        if (item == null) {
            return null;
        }
        String name = item.getType().name();
        if (!name.endsWith("_SPAWN_EGG")) {
            return null;
        }
        try {
            return EntityType.valueOf(name.substring(0, name.length() - "_SPAWN_EGG".length()));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void notifyEntityLimit(Player player, EntityType type, AtLimitResult res) {
        if (res.getTypelimit() != null) {
            User.getInstance(player).notify(ENTITY_LIMIT_HIT, ENTITY_PLACEHOLDER,
                    Util.prettifyText(type.toString()), TextVariables.NUMBER,
                    String.valueOf(res.getTypelimit().getValue()));
        } else {
            User.getInstance(player).notify(ENTITY_LIMIT_HIT, ENTITY_PLACEHOLDER,
                    res.getGrouplimit().getKey().getName() + " ("
                            + res.getGrouplimit().getKey().getTypes().stream()
                                    .map(x -> Util.prettifyText(x.toString()))
                                    .collect(Collectors.joining(", "))
                            + ")",
                    TextVariables.NUMBER, String.valueOf(res.getGrouplimit().getValue()));
        }
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
                    addon.getBlockLimitListener().incrementEntity(island, envOf(w), entity.getType());
                    entityIslandMap.put(entity.getUniqueId(), island.getUniqueId());
                });
    }

    /**
     * Populate {@link #entityIslandMap} for entities loaded from chunks on
     * server start or chunk reload. Freshly spawned entities go through
     * {@link #trackSpawn} and are already in the map, so we skip them to
     * avoid an unnecessary {@code getIslandAt} lookup.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityAddToWorld(EntityAddToWorldEvent e) {
        Entity entity = e.getEntity();
        if (!(entity instanceof LivingEntity) && !(entity instanceof Vehicle)
                && !(entity instanceof Hanging)) {
            return;
        }

        UUID uuid = entity.getUniqueId();
        if (entityIslandMap.containsKey(uuid)) return;

        World w = entity.getWorld();
        if (!addon.inGameModeWorld(w)) return;

        addon.getIslands().getIslandAt(entity.getLocation())
                .filter(island -> !island.isSpawn())
                .ifPresent(island -> entityIslandMap.put(uuid, island.getUniqueId()));
    }

    /* =========================================================================
     * Decrement on permanent removal
     * ========================================================================= */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent e) {
        Entity entity = e.getEntity();
        // Entities unloaded with their chunk are still alive — don't decrement. Drop the cached
        // island mapping so it can't leak for entities that never load again; onEntityAddToWorld
        // re-populates it on chunk reload.
        if (e.getCause() == EntityRemoveEvent.Cause.UNLOAD) {
            entityIslandMap.remove(entity.getUniqueId());
            return;
        }
        World w = entity.getWorld();
        if (!addon.inGameModeWorld(w)) return;

        // Entity is being portaled — count transfer was already handled by onEntityPortal.
        if (justPortaled.remove(entity.getUniqueId())) return;

        String islandId = entityIslandMap.remove(entity.getUniqueId());
        if (islandId != null) {
            addon.getBlockLimitListener().decrementEntity(islandId, envOf(w), entity.getType());
            return;
        }

        addon.getIslands().getIslandAt(entity.getLocation())
                .filter(island -> !island.isSpawn())
                .ifPresent(island -> addon.getBlockLimitListener().decrementEntity(island.getUniqueId(), envOf(w),
                        entity.getType()));
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

        // Prevent onEntityRemove from double-decrementing for the source-world removal.
        justPortaled.add(entity.getUniqueId());

        // Decrement at source if on a tracked island
        if (addon.inGameModeWorld(fromWorld)) {
            entityIslandMap.remove(entity.getUniqueId());
            addon.getIslands().getIslandAt(entity.getLocation())
                    .filter(island -> !island.isSpawn())
                    .ifPresent(island -> addon.getBlockLimitListener().decrementEntity(island.getUniqueId(), fromEnv,
                            entity.getType()));
        }
        // Increment at destination if on a tracked island
        if (addon.inGameModeWorld(toWorld)) {
            addon.getIslands().getIslandAt(e.getTo())
                    .filter(island -> !island.isSpawn())
                    .ifPresent(island -> {
                        addon.getBlockLimitListener().incrementEntity(island, toEnv, entity.getType());
                        entityIslandMap.put(entity.getUniqueId(), island.getUniqueId());
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
        Optional<Island> optionalIsland = addon.getIslands().getIslandAt(livingEntity.getLocation());
        if (optionalIsland.isEmpty()) {
            if (runAsync) {
                cancelableEvent.setCancelled(false);
            }
            return true;
        }
        Island island = optionalIsland.get();
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
        // Anchor on the carved pumpkin: body and base are the two iron blocks below it,
        // arms are two opposite iron blocks beside the body on a horizontal axis.
        Block head = findPumpkinHead(location, Material.IRON_BLOCK);
        if (head == null) {
            return;
        }
        Block body = head.getRelative(BlockFace.DOWN);
        Block base = body.getRelative(BlockFace.DOWN);
        if (base.getType() != Material.IRON_BLOCK) {
            return;
        }
        for (BlockFace bf : HORIZONTAL) {
            Block arm1 = body.getRelative(bf);
            Block arm2 = body.getRelative(bf.getOppositeFace());
            if (arm1.getType() == Material.IRON_BLOCK && arm2.getType() == Material.IRON_BLOCK) {
                eraseBlocks(head, body, base, arm1, arm2);
                return;
            }
        }
    }

    private void detectSnowman(Location location) {
        // Anchor on the carved pumpkin: the two snow blocks below it are the body and base.
        Block head = findPumpkinHead(location, Material.SNOW_BLOCK);
        if (head == null) {
            return;
        }
        Block body = head.getRelative(BlockFace.DOWN);
        Block base = body.getRelative(BlockFace.DOWN);
        if (base.getType() != Material.SNOW_BLOCK) {
            return;
        }
        eraseBlocks(head, body, base);
    }

    /**
     * Locate the carved pumpkin / jack o'lantern that tops a freshly built golem or snowman.
     *
     * <p>The {@link CreatureSpawnEvent} location for a built mob is not guaranteed to be a
     * particular block of the structure, so we anchor on the unambiguous pumpkin rather than
     * assuming the spawn block is the base. Scan the spawn block and the two blocks above it
     * for a head that sits directly on top of the expected body material.
     *
     * @return the pumpkin block, or {@code null} if none is found
     */
    private Block findPumpkinHead(Location location, Material bodyMaterial) {
        Block b = location.getBlock();
        for (int i = 0; i < 3; i++) {
            if (isGolemHead(b) && b.getRelative(BlockFace.DOWN).getType() == bodyMaterial) {
                return b;
            }
            b = b.getRelative(BlockFace.UP);
        }
        return null;
    }

    private boolean isGolemHead(Block block) {
        return block.getType() == Material.CARVED_PUMPKIN || block.getType() == Material.JACK_O_LANTERN;
    }

    private void eraseBlocks(Block... blocks) {
        for (Block b : blocks) {
            addon.getBlockLimitListener().removeBlock(b);
            b.setType(Material.AIR);
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
                        User.getInstance(p).notify(ENTITY_LIMIT_HIT, ENTITY_PLACEHOLDER,
                                Util.prettifyText(entity.getType().toString()),
                                TextVariables.NUMBER, String.valueOf(res.getTypelimit().getValue()));
                    } else {
                        User.getInstance(p).notify(ENTITY_LIMIT_HIT, ENTITY_PLACEHOLDER,
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
        return atLimit(island, entity.getType(), envOf(entity.getWorld()));
    }

    AtLimitResult atLimit(Island island, EntityType type, Environment env) {
        @Nullable
        IslandBlockCount ibc = addon.getBlockLimitListener().getIsland(island.getUniqueId());

        int limitAmount = resolveTypeLimit(ibc, env, type);
        Map<EntityGroup, Integer> groupsLimits = resolveGroupLimits(ibc, env, type);

        if (limitAmount < 0 && groupsLimits.isEmpty()) {
            return new AtLimitResult();
        }

        AtLimitResult typeResult = checkTypeLimit(ibc, env, type, limitAmount);
        if (typeResult.hit()) return typeResult;

        return checkGroupLimits(ibc, env, groupsLimits);
    }

    private int resolveTypeLimit(@Nullable IslandBlockCount ibc, Environment env, EntityType type) {
        int limit = ibc == null ? -1 : ibc.getEntityLimit(env, type);
        if (limit < 0) {
            Integer cfg = addon.getSettings().getLimits(env).get(type);
            if (cfg != null) limit = cfg;
        }
        return limit;
    }

    private Map<EntityGroup, Integer> resolveGroupLimits(@Nullable IslandBlockCount ibc, Environment env,
            EntityType type) {
        Map<EntityGroup, Integer> groupsLimits = new HashMap<>();
        List<EntityGroup> groupdefs = addon.getSettings().getGroupLimits().getOrDefault(type, Collections.emptyList());
        Map<String, Integer> envGroupCfg = addon.getSettings().getGroupLimits(env);
        Map<String, Integer> islandGroupOverrides = ibc == null ? Collections.emptyMap()
                : ibc.getEntityGroupLimits(env);
        for (EntityGroup def : groupdefs) {
            int limit = islandGroupOverrides.getOrDefault(def.getName(), envGroupCfg.getOrDefault(def.getName(), -1));
            if (limit >= 0) {
                groupsLimits.put(def, limit);
            }
        }
        return groupsLimits;
    }

    private AtLimitResult checkTypeLimit(@Nullable IslandBlockCount ibc, Environment env, EntityType type,
            int limitAmount) {
        if (limitAmount < 0) return new AtLimitResult();
        int count = ibc == null ? 0 : ibc.getEntityCount(env, type);
        int max = limitAmount + (ibc == null ? 0 : ibc.getEntityLimitOffset(env, type));
        return count >= max ? new AtLimitResult(type, max) : new AtLimitResult();
    }

    private AtLimitResult checkGroupLimits(@Nullable IslandBlockCount ibc, Environment env,
            Map<EntityGroup, Integer> groupsLimits) {
        for (Map.Entry<EntityGroup, Integer> group : groupsLimits.entrySet()) {
            if (group.getValue() < 0) continue;
            int count = sumGroupCount(ibc, env, group.getKey());
            int max = group.getValue()
                    + (ibc == null ? 0 : ibc.getEntityGroupLimitOffset(env, group.getKey().getName()));
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

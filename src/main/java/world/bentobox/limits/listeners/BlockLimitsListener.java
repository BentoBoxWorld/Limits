package world.bentobox.limits.listeners;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.TechnicalPiston;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.events.island.IslandDeleteEvent;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.Database;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.limits.Limits;
import world.bentobox.limits.Settings;
import world.bentobox.limits.objects.IslandBlockCount;

/**
 * @author tastybento
 */
public class BlockLimitsListener implements Listener {

    /** Blocks that are not counted */
    private static final List<NamespacedKey> DO_NOT_COUNT = List.of(Material.LAVA.getKey(), Material.WATER.getKey(),
            Material.AIR.getKey(), Material.FIRE.getKey(), Material.END_PORTAL.getKey(),
            Material.NETHER_PORTAL.getKey());
    private static final List<NamespacedKey> STACKABLE = List.of(Material.SUGAR_CANE.getKey(), Material.BAMBOO.getKey());

    /** Save every 10 blocks of change */
    private static final Integer CHANGE_LIMIT = 9;
    private final Limits addon;
    private final Map<String, IslandBlockCount> islandCountMap = new HashMap<>();
    private final Map<String, Integer> saveMap = new HashMap<>();
    private final Database<IslandBlockCount> handler;
    private final Map<World, Map<NamespacedKey, Integer>> worldLimitMap = new HashMap<>();
    /**
     * Per-environment default limits. The "blocklimits" section seeds every env;
     * "blocklimits-nether" / "blocklimits-end" sections override one env each.
     */
    private final Map<Environment, Map<NamespacedKey, Integer>> envDefaultLimitMap = new EnumMap<>(Environment.class);

    public BlockLimitsListener(Limits addon) {
        this.addon = addon;
        for (Environment env : Settings.ENVIRONMENTS) {
            envDefaultLimitMap.put(env, new HashMap<>());
        }
        handler = new Database<>(addon, IslandBlockCount.class);
        List<String> toBeDeleted = new ArrayList<>();
        handler.loadObjects().forEach(ibc -> {
            if (addon.isCoveredGameMode(ibc.getGameMode())) {
                // Strip uncountable types from every env's count map
                ibc.getAllBlockCounts().values().forEach(m -> m.keySet().removeIf(DO_NOT_COUNT::contains));
                islandCountMap.put(ibc.getUniqueId(), ibc);
            } else {
                toBeDeleted.add(ibc.getUniqueId());
            }
        });
        toBeDeleted.forEach(handler::deleteID);
        loadAllLimits();
    }

    /**
     * Loads the default and world-specific limits
     */
    private void loadAllLimits() {
        // Pass 1: "blocklimits" applies to every env as the global default
        if (addon.getConfig().isConfigurationSection("blocklimits")) {
            ConfigurationSection limitConfig = addon.getConfig().getConfigurationSection("blocklimits");
            Map<NamespacedKey, Integer> base = loadLimits(Objects.requireNonNull(limitConfig));
            for (Environment env : Settings.ENVIRONMENTS) {
                envDefaultLimitMap.get(env).putAll(base);
            }
            addon.log("Loading default block limits");
        }
        // Pass 2: env-specific sections override the base for that env
        loadEnvSection("blocklimits-nether", Environment.NETHER);
        loadEnvSection("blocklimits-end", Environment.THE_END);

        // Per-world named overrides
        if (addon.getConfig().isConfigurationSection("worlds")) {
            ConfigurationSection worlds = addon.getConfig().getConfigurationSection("worlds");
            for (String worldName : Objects.requireNonNull(worlds).getKeys(false)) {
                World world = Bukkit.getWorld(worldName);
                if (world != null && addon.inGameModeWorld(world)) {
                    addon.log("Loading limits for " + world.getName());
                    ConfigurationSection matsConfig = worlds.getConfigurationSection(worldName);
                    worldLimitMap.put(world, loadLimits(Objects.requireNonNull(matsConfig)));
                }
            }
        }
    }

    private void loadEnvSection(String section, Environment env) {
        if (!addon.getConfig().isConfigurationSection(section)) return;
        ConfigurationSection cs = addon.getConfig().getConfigurationSection(section);
        addon.log("Loading " + env + " block limit overrides from " + section);
        envDefaultLimitMap.get(env).putAll(loadLimits(Objects.requireNonNull(cs)));
    }

    /**
     * Loads limit map from configuration section
     */
    private Map<NamespacedKey, Integer> loadLimits(ConfigurationSection cs) {
        Map<NamespacedKey, Integer> limits = new HashMap<>();
        for (String key : cs.getKeys(false)) {
            int limit = cs.getInt(key);
            NamespacedKey nsKey;
            if (key.contains(":")) {
                nsKey = NamespacedKey.fromString(key.toLowerCase(Locale.ROOT));
                if (nsKey == null) {
                    Bukkit.getLogger().warning("Invalid namespaced key in config, skipping: " + key);
                    continue;
                }
            } else {
                nsKey = new NamespacedKey(NamespacedKey.MINECRAFT, key.toLowerCase(Locale.ROOT));
            }
            boolean matched = false;
            Material mat = Registry.MATERIAL.get(nsKey);
            if (mat != null) {
                matched = true;
                if (!mat.isBlock()) {
                    Bukkit.getLogger().warning("Non-block material in block limits config: " + key);
                } else if (DO_NOT_COUNT.contains(mat.getKey())) {
                    Bukkit.getLogger().warning("Uncountable material in block limits config: " + key);
                } else {
                    limits.put(mat.getKey(), limit);
                }
            }
            if (!matched) {
                Tag<Material> tag = Bukkit.getTag("blocks", nsKey, Material.class);
                if (tag != null) {
                    limits.put(tag.getKey(), limit);
                    matched = true;
                }
            }
            if (!matched) {
                Bukkit.getLogger().warning("Unknown material or tag in config: " + key);
            }
        }
        return limits;
    }

    /** Save the count database completely */
    public void save() {
        islandCountMap.values().stream().filter(IslandBlockCount::isChanged).forEach(handler::saveObjectAsync);
    }

    /** Resolve the env, normalising any non-standard values to NORMAL. */
    private Environment envOf(World w) {
        Environment env = w.getEnvironment();
        return Settings.ENVIRONMENTS.contains(env) ? env : Environment.NORMAL;
    }

    // Player-related events
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlock(BlockPlaceEvent e) {
        notify(e, User.getInstance(e.getPlayer()), process(e.getBlock(), true), e.getBlock().getType());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlock(BlockBreakEvent e) {
        if (e.getBlock().hasMetadata("blockbreakevent-ignore")) {
            return;
        }
        handleBreak(e, e.getBlock());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTurtleEggBreak(PlayerInteractEvent e) {
        if (e.getAction().equals(Action.PHYSICAL) && e.getClickedBlock() != null
                && e.getClickedBlock().getType().equals(Material.TURTLE_EGG)) {
            handleBreak(e, e.getClickedBlock());
        }
    }

    private void handleBreak(Event e, Block b) {
        if (!addon.inGameModeWorld(b.getWorld())) {
            return;
        }
        Material mat = b.getType();
        if (STACKABLE.contains(b.getType().getKey())) {
            Block block = b;
            while (block.getRelative(BlockFace.UP).getType().equals(mat)
                    && block.getY() < b.getWorld().getMaxHeight()) {
                block = block.getRelative(BlockFace.UP);
                process(block, false);
            }
        }
        process(b, false);
        if (b.getRelative(BlockFace.UP).getType() == Material.REDSTONE_WIRE
                || b.getRelative(BlockFace.UP).getType() == Material.REPEATER
                || b.getRelative(BlockFace.UP).getType() == Material.COMPARATOR
                || b.getRelative(BlockFace.UP).getType() == Material.REDSTONE_TORCH) {
            process(b.getRelative(BlockFace.UP), false);
        }
        if (b.getRelative(BlockFace.EAST).getType() == Material.REDSTONE_WALL_TORCH) {
            process(b.getRelative(BlockFace.EAST), false);
        }
        if (b.getRelative(BlockFace.WEST).getType() == Material.REDSTONE_WALL_TORCH) {
            process(b.getRelative(BlockFace.WEST), false);
        }
        if (b.getRelative(BlockFace.SOUTH).getType() == Material.REDSTONE_WALL_TORCH) {
            process(b.getRelative(BlockFace.SOUTH), false);
        }
        if (b.getRelative(BlockFace.NORTH).getType() == Material.REDSTONE_WALL_TORCH) {
            process(b.getRelative(BlockFace.NORTH), false);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlock(BlockMultiPlaceEvent e) {
        notify(e, User.getInstance(e.getPlayer()), process(e.getBlock(), true), e.getBlock().getType());
    }

    private void notify(Cancellable e, User user, int limit, Material m) {
        if (limit > -1) {
            user.notify("block-limits.hit-limit",
                    "[material]", Util.prettifyText(m.toString()),
                    TextVariables.NUMBER, String.valueOf(limit));
            e.setCancelled(true);
        }
    }

    // Non-player events
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlock(BlockBurnEvent e) {
        process(e.getBlock(), false);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlock(BlockExplodeEvent e) {
        e.blockList().forEach(b -> process(b, false));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlock(BlockFadeEvent e) {
        process(e.getBlock(), false);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlock(BlockFormEvent e) {
        if (e instanceof EntityBlockFormEvent || e instanceof BlockSpreadEvent) {
            return;
        }
        process(e.getBlock(), false);
        if (process(e.getBlock(), e.getNewState().getBlockData(), true) > -1) {
            e.setCancelled(true);
            process(e.getBlock(), true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlock(BlockSpreadEvent e) {
        process(e.getBlock(), false);
        if (process(e.getBlock(), e.getNewState().getBlockData(), true) > -1) {
            e.setCancelled(true);
            process(e.getBlock(), true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlock(EntityBlockFormEvent e) {
        process(e.getBlock(), false);
        if (process(e.getBlock(), e.getNewState().getBlockData(), true) > -1) {
            e.setCancelled(true);
            process(e.getBlock(), true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlock(BlockGrowEvent e) {
        if (process(e.getNewState().getBlock(), true) > -1) {
            e.setCancelled(true);
            e.getBlock().getWorld().getBlockAt(e.getBlock().getLocation()).setBlockData(e.getBlock().getBlockData());
        } else {
            process(e.getBlock(), false);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlock(LeavesDecayEvent e) {
        process(e.getBlock(), false);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlock(EntityExplodeEvent e) {
        e.blockList().forEach(b -> process(b, false));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlock(EntityChangeBlockEvent e) {
        if (e.getTo() != Material.AIR) {
            int limit = process(e.getBlock(), e.getBlockData(), true);
            if (limit > -1) {
                e.setCancelled(true);
                return;
            }
        }
        process(e.getBlock(), false);
        if (!e.isCancelled() && e.getBlock().getType().equals(Material.FARMLAND)) {
            process(e.getBlock().getRelative(BlockFace.UP), false);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlock(BlockFromToEvent e) {
        if (e.getBlock().isLiquid()
                && (e.getToBlock().getType() == Material.REDSTONE_WIRE
                        || e.getToBlock().getType() == Material.REPEATER
                        || e.getToBlock().getType() == Material.COMPARATOR
                        || e.getToBlock().getType() == Material.REDSTONE_TORCH
                        || e.getToBlock().getType() == Material.REDSTONE_WALL_TORCH)) {
            process(e.getToBlock(), false);
        }
    }

    /**
     * Map variant materials to their canonical form.
     */
    public NamespacedKey fixMaterial(BlockData b) {
        Material mat = b.getMaterial();
        if (mat.equals(Material.CHIPPED_ANVIL) || mat.equals(Material.DAMAGED_ANVIL)) {
            return Material.ANVIL.getKey();
        } else if (mat == Material.REDSTONE_WALL_TORCH) {
            return Material.REDSTONE_TORCH.getKey();
        } else if (mat == Material.WALL_TORCH) {
            return Material.TORCH.getKey();
        } else if (mat == Material.COPPER_WALL_TORCH) {
            return Material.COPPER_TORCH.getKey();
        } else if (mat == Material.ZOMBIE_WALL_HEAD) {
            return Material.ZOMBIE_HEAD.getKey();
        } else if (mat == Material.CREEPER_WALL_HEAD) {
            return Material.CREEPER_HEAD.getKey();
        } else if (mat == Material.PLAYER_WALL_HEAD) {
            return Material.PLAYER_HEAD.getKey();
        } else if (mat == Material.DRAGON_WALL_HEAD) {
            return Material.DRAGON_HEAD.getKey();
        } else if (mat == Material.BAMBOO_SAPLING) {
            return Material.BAMBOO.getKey();
        } else if (mat == Material.PISTON_HEAD || mat == Material.MOVING_PISTON) {
            TechnicalPiston tp = (TechnicalPiston) b;
            if (tp.getType() == TechnicalPiston.Type.NORMAL) {
                return Material.PISTON.getKey();
            } else {
                return Material.STICKY_PISTON.getKey();
            }
        } else if (mat == Material.EXPOSED_COPPER_CHEST || mat == Material.WEATHERED_COPPER_CHEST
                || mat == Material.OXIDIZED_COPPER_CHEST || mat == Material.WAXED_COPPER_CHEST
                || mat == Material.WAXED_EXPOSED_COPPER_CHEST || mat == Material.WAXED_WEATHERED_COPPER_CHEST
                || mat == Material.WAXED_OXIDIZED_COPPER_CHEST) {
            return Material.COPPER_CHEST.getKey();
        }
        return mat.getKey();
    }

    private int process(Block b, boolean add) {
        return process(b, b.getBlockData(), add);
    }

    /**
     * Check if a block can be placed or needs to be removed based on limits.
     *
     * @return limit amount if over limit, or -1 if no limitation
     */
    private int process(Block b, BlockData blockData, boolean add) {
        if (DO_NOT_COUNT.contains(fixMaterial(blockData)) || !addon.inGameModeWorld(b.getWorld())) {
            return -1;
        }
        Environment env = envOf(b.getWorld());
        return addon.getIslands().getIslandAt(b.getLocation()).map(i -> {
            String id = i.getUniqueId();
            String gameMode = addon.getGameModeName(b.getWorld());
            if (gameMode.isEmpty()) {
                return -1;
            }
            if (addon.getConfig().getBoolean("ignore-center-block", true)
                    && i.getCenter().equals(b.getLocation())) {
                return -1;
            }
            islandCountMap.putIfAbsent(id, new IslandBlockCount(id, gameMode));
            NamespacedKey key = fixMaterial(blockData);
            if (add) {
                int limit = checkLimit(b.getWorld(), env, key, id);
                if (limit > -1) {
                    return limit;
                }
                islandCountMap.get(id).add(env, key);
            } else {
                islandCountMap.get(id).remove(env, key);
            }
            updateSaveMap(id);
            return -1;
        }).orElse(-1);
    }

    /**
     * Remove a block from any island limit count
     */
    public void removeBlock(Block b) {
        addon.getIslands().getIslandAt(b.getLocation()).ifPresent(i -> {
            String id = i.getUniqueId();
            String gameMode = addon.getGameModeName(b.getWorld());
            if (gameMode.isEmpty()) return;
            Environment env = envOf(b.getWorld());
            islandCountMap.computeIfAbsent(id, k -> new IslandBlockCount(id, gameMode))
                    .remove(env, fixMaterial(b.getBlockData()));
            updateSaveMap(id);
        });
    }

    private void updateSaveMap(String id) {
        saveMap.putIfAbsent(id, 0);
        if (saveMap.merge(id, 1, Integer::sum) > CHANGE_LIMIT) {
            handler.saveObjectAsync(islandCountMap.get(id));
            saveMap.remove(id);
        }
    }

    /**
     * Resolve the active limit for this material in this world for this island.
     * Priority: island-env limit, world-named limit, env-default, none.
     *
     * @return limit if at or over, or -1 if no limit
     */
    private int checkLimit(World w, Environment env, NamespacedKey m, String id) {
        IslandBlockCount ibc = islandCountMap.get(id);
        if (ibc.isBlockLimited(env, m)) {
            int offset = ibc.getBlockLimitOffset(env, m);
            return ibc.isAtLimit(env, m) ? ibc.getBlockLimit(env, m) + offset : -1;
        }
        Map<NamespacedKey, Integer> worldMap = worldLimitMap.get(w);
        if (worldMap != null && worldMap.containsKey(m)) {
            int worldLimit = worldMap.get(m);
            int offset = ibc.getBlockLimitOffset(env, m);
            return ibc.isAtLimit(env, m, worldLimit) ? worldLimit + offset : -1;
        }
        Map<NamespacedKey, Integer> envDefaults = envDefaultLimitMap.get(env);
        if (envDefaults != null && envDefaults.containsKey(m)) {
            int envLimit = envDefaults.get(m);
            int offset = ibc.getBlockLimitOffset(env, m);
            if (ibc.isAtLimit(env, m, envLimit)) {
                return envLimit + offset;
            }
        }
        return -1;
    }

    /**
     * Aggregate map of the resolved limits for this island in the given world.
     */
    public Map<NamespacedKey, Integer> getMaterialLimits(World w, String id) {
        Environment env = envOf(w);
        Map<NamespacedKey, Integer> result = new HashMap<>();
        Map<NamespacedKey, Integer> envDefaults = envDefaultLimitMap.get(env);
        if (envDefaults != null) {
            result.putAll(envDefaults);
        }
        Map<NamespacedKey, Integer> worldMap = worldLimitMap.get(w);
        if (worldMap != null) {
            result.putAll(worldMap);
        }
        IslandBlockCount ibc = islandCountMap.get(id);
        if (ibc != null) {
            result.putAll(ibc.getBlockLimits(env));
            ibc.getBlockLimitsOffset(env).forEach(
                    (material, offset) -> result.put(material, result.getOrDefault(material, 0) + offset));
        }
        return result;
    }

    /**
     * Per-environment map of the env-default limits, used for tests and external introspection.
     */
    public Map<Environment, Map<NamespacedKey, Integer>> getEnvDefaultLimitMap() {
        return envDefaultLimitMap;
    }

    public Map<World, Map<NamespacedKey, Integer>> getWorldLimitMap() {
        return worldLimitMap;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onIslandDelete(IslandDeleteEvent e) {
        islandCountMap.remove(e.getIsland().getUniqueId());
        saveMap.remove(e.getIsland().getUniqueId());
        if (handler.objectExists(e.getIsland().getUniqueId())) {
            handler.deleteID(e.getIsland().getUniqueId());
        }
    }

    public void setIsland(String islandId, IslandBlockCount ibc) {
        islandCountMap.put(islandId, ibc);
        handler.saveObjectAsync(ibc);
    }

    @Nullable
    public IslandBlockCount getIsland(String islandId) {
        return islandCountMap.get(islandId);
    }

    @NonNull
    public IslandBlockCount getIsland(Island island) {
        return islandCountMap.computeIfAbsent(island.getUniqueId(),
                k -> new IslandBlockCount(k, island.getGameMode()));
    }
}

package world.bentobox.limits.listeners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.World;
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
import world.bentobox.limits.objects.IslandBlockCount;

/**
 * @author tastybento
 *
 */
public class BlockLimitsListener implements Listener {

    /**
     * Blocks that are not counted
     */
    private static final List<NamespacedKey> DO_NOT_COUNT = List.of(Material.LAVA.getKey(), Material.WATER.getKey(), Material.AIR.getKey(), Material.FIRE.getKey(), Material.END_PORTAL.getKey(), Material.NETHER_PORTAL.getKey());
    private static final List<NamespacedKey> STACKABLE = List.of(Material.SUGAR_CANE.getKey(), Material.BAMBOO.getKey());

    /**
     * Save every 10 blocks of change
     */
    private static final Integer CHANGE_LIMIT = 9;
    private final Limits addon;
    private final Map<String, IslandBlockCount> islandCountMap = new HashMap<>();
    private final Map<String, Integer> saveMap = new HashMap<>();
    private final Database<IslandBlockCount> handler;
    private final Map<World, Map<NamespacedKey, Integer>> worldLimitMap = new HashMap<>();
    private Map<NamespacedKey, Integer> defaultLimitMap = new HashMap<>();

    public BlockLimitsListener(Limits addon) {
        this.addon = addon;
        handler = new Database<>(addon, IslandBlockCount.class);
        List<String> toBeDeleted = new ArrayList<>();
        handler.loadObjects().forEach(ibc -> {
            // Clean up
            if (addon.isCoveredGameMode(ibc.getGameMode())) {
                ibc.getBlockCounts().keySet().removeIf(DO_NOT_COUNT::contains);
                // Store
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
        // Load the default limits
        addon.log("Loading default limits");
        if (addon.getConfig().isConfigurationSection("blocklimits")) {
            ConfigurationSection limitConfig = addon.getConfig().getConfigurationSection("blocklimits");
            defaultLimitMap = loadLimits(Objects.requireNonNull(limitConfig));
        }

        // Load specific worlds
        if (addon.getConfig().isConfigurationSection("worlds")) {
            ConfigurationSection worlds = addon.getConfig().getConfigurationSection("worlds");
            for (String worldName : Objects.requireNonNull(worlds).getKeys(false)) {
                World world = Bukkit.getWorld(worldName);
                if (world != null && addon.inGameModeWorld(world)) {
                    addon.log("Loading limits for " + world.getName());
                    worldLimitMap.putIfAbsent(world, new HashMap<>());
                    ConfigurationSection matsConfig = worlds.getConfigurationSection(worldName);
                    worldLimitMap.put(world, loadLimits(Objects.requireNonNull(matsConfig)));
                }
            }
        }

    }
    /*
    private static final Set<Tag<Material>> TAGS = Set.of(Tag.ANVIL, 
            Tag.BAMBOO_BLOCKS, Tag.BANNERS, Tag.BEDS, Tag.BEEHIVES, Tag.BUTTONS, Tag.CAMPFIRES, Tag.CANDLE_CAKES, 
            Tag.CORALS, Tag.CAULDRONS, Tag.CAVE_VINES, Tag.DIRT, Tag.DOORS,
            Tag.FENCE_GATES, Tag.FENCES, Tag.ICE, Tag.LEAVES, Tag.LOGS,
            Tag.NYLIUM, Tag.PLANKS, Tag.PORTALS, Tag.RAILS, Tag.SAND, Tag.SAPLINGS, Tag.SHULKER_BOXES,
            Tag.SIGNS, Tag.SLABS, Tag.SNOW, Tag.STAIRS, Tag.STONE_BRICKS, 
            Tag.PRESSURE_PLATES, Tag.TERRACOTTA, Tag.TRAPDOORS, Tag.WALLS,
            Tag.WART_BLOCKS, Tag.WOOL, Tag.WOOL_CARPETS);*/
    /**
     * Loads limit map from configuration section
     *
     * @param cs - configuration section
     * @return limit map
     */
    private Map<NamespacedKey, Integer> loadLimits(ConfigurationSection cs) {
        Map<NamespacedKey, Integer> limits = new HashMap<>();
        for (String key : cs.getKeys(false)) {
            int limit = cs.getInt(key);
            NamespacedKey nsKey;
            if (key.contains(":")) {
                // Config already has a full namespaced key
                nsKey = NamespacedKey.fromString(key.toLowerCase(Locale.ROOT));
                if (nsKey == null) {
                    Bukkit.getLogger().warning("Invalid namespaced key in config, skipping: " + key);
                    continue;
                }
            } else {
                // Assume "minecraft" namespace if none provided
                nsKey = new NamespacedKey(NamespacedKey.MINECRAFT, key.toLowerCase(Locale.ROOT));
            }

            boolean matched = false;

            // Try match to Material - only accept block materials that are not in DO_NOT_COUNT
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

            // Try match to a Tag<Material>
            if (!matched) {
                Tag<Material> tag = Bukkit.getTag("blocks", nsKey, Material.class);
                if (tag != null) {
                    limits.put(tag.getKey(), limit);
                    matched = true;
                }
            }


            // Log warning if nothing matched
            if (!matched) {
                Bukkit.getLogger().warning("Unknown material or tag in config: " + key);
            }
        }
        return limits;
    }


    /**
     * Save the count database completely
     */
    public void save() {
        islandCountMap.values().stream().filter(IslandBlockCount::isChanged).forEach(handler::saveObjectAsync);
    }

    // Player-related events
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlock(BlockPlaceEvent e) {
        notify(e, User.getInstance(e.getPlayer()), process(e.getBlock(), true), e.getBlock().getType());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlock(BlockBreakEvent e) {
        if (e.getBlock().hasMetadata("blockbreakevent-ignore")) {
            // Ignore event due to Advanced Enchantments. See https://ae.advancedplugins.net/for-developers/plugin-compatiblity-issues
            // @since 1.28.0
            return;
        }
        handleBreak(e, e.getBlock());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTurtleEggBreak(PlayerInteractEvent e) {
        if (e.getAction().equals(Action.PHYSICAL) && e.getClickedBlock() != null && e.getClickedBlock().getType().equals(Material.TURTLE_EGG)) {
            handleBreak(e, e.getClickedBlock());
        }
    }

    private void handleBreak(Event e, Block b) {
        if (!addon.inGameModeWorld(b.getWorld())) {
            return;
        }
        Material mat = b.getType();

        // Check for stackable plants
        if (STACKABLE.contains(b.getType().getKey())) {
            // Check for blocks above
            Block block = b;
            while(block.getRelative(BlockFace.UP).getType().equals(mat) && block.getY() < b.getWorld().getMaxHeight()) {
                block = block.getRelative(BlockFace.UP);
                process(block, false);
            }
        }
        process(b, false);
        // Player breaks a block and there was a redstone dust/repeater/... above
        if (b.getRelative(BlockFace.UP).getType() == Material.REDSTONE_WIRE || b.getRelative(BlockFace.UP).getType() == Material.REPEATER || b.getRelative(BlockFace.UP).getType() == Material.COMPARATOR || b.getRelative(BlockFace.UP).getType() == Material.REDSTONE_TORCH) {
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

    /**
     * Cancel the event and notify the user of failure
     * @param e event
     * @param user user
     * @param limit maximum limit allowed
     * @param m material
     */
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
        // EntityBlockFormEvent and BlockSpreadEvent extend BlockFormEvent; skip here to avoid double-processing
        if (e instanceof EntityBlockFormEvent || e instanceof BlockSpreadEvent) {
            return;
        }
        // Remove the old block state count
        process(e.getBlock(), false);
        // Add the new block state count; cancel if the new state exceeds its limit
        if (process(e.getBlock(), e.getNewState().getBlockData(), true) > -1) {
            e.setCancelled(true);
            process(e.getBlock(), true); // Restore old count
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlock(BlockSpreadEvent e) {
        process(e.getBlock(), true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlock(EntityBlockFormEvent e) {
        // Remove the old block state count
        process(e.getBlock(), false);
        // Add the new block state count; cancel if the new state exceeds its limit
        if (process(e.getBlock(), e.getNewState().getBlockData(), true) > -1) {
            e.setCancelled(true);
            process(e.getBlock(), true); // Restore old count
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
        process(e.getBlock(), false);
        // Track new block state when entity changes a block to a non-AIR material
        // (e.g., copper golem creation transforming a copper block into a copper chest)
        if (e.getTo() != Material.AIR) {
            process(e.getBlock(), e.getBlockData(), true);
        }
        if (e.getBlock().getType().equals(Material.FARMLAND)) {
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
     * Return equivalents. Maps things like wall materials to their non-wall equivalents
     * @param b block data
     * @return material that matches the block data
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

    /**
     * Check if a block can be
     *
     * @param b - block
     * @param add - true to add a block, false to remove
     * @return limit amount if over limit, or -1 if no limitation
     */
    private int process(Block b, boolean add) {
        return process(b, b.getBlockData(), add);
    }

    /**
     * Check if a block can be placed or needs to be removed based on limits.
     * This variant allows specifying block data different from the block's current data,
     * which is needed for block state transitions (e.g., copper oxidation or waxing).
     *
     * @param b - block (used for location and world)
     * @param blockData - block data to use for material checks
     * @param add - true to add a block, false to remove
     * @return limit amount if over limit, or -1 if no limitation
     */
    private int process(Block b, BlockData blockData, boolean add) {
        if (DO_NOT_COUNT.contains(fixMaterial(blockData)) || !addon.inGameModeWorld(b.getWorld())) {
            return -1;
        }
        // Check if on island
        return addon.getIslands().getIslandAt(b.getLocation()).map(i -> {
            String id = i.getUniqueId();
            String gameMode = addon.getGameModeName(b.getWorld());
            if (gameMode.isEmpty()) {
                // Invalid world
                return -1;
            }
            // Ignore the center block - usually bedrock, but for AOneBlock it's the magic block
            if (addon.getConfig().getBoolean("ignore-center-block", true) && i.getCenter().equals(b.getLocation())) {
                return -1;
            }
            islandCountMap.putIfAbsent(id, new IslandBlockCount(id, gameMode));
            if (add) {
                // Check limit
                int limit = checkLimit(b.getWorld(), fixMaterial(blockData), id);
                if (limit > -1) {
                    return limit;
                }
                islandCountMap.get(id).add(fixMaterial(blockData));
            } else {
                if (islandCountMap.containsKey(id)) {
                    islandCountMap.get(id).remove(fixMaterial(blockData));
                }
            }
            updateSaveMap(id);
            return -1;
        }).orElse(-1);
    }

    /**
     * Removed a block from any island limit count
     * @param b - block to remove
     */
    public void removeBlock(Block b) {
        // Get island
        addon.getIslands().getIslandAt(b.getLocation()).ifPresent(i -> {
            String id = i.getUniqueId();
            String gameMode = addon.getGameModeName(b.getWorld());
            if (gameMode.isEmpty()) {
                // Invalid world
                return;
            }
            islandCountMap.computeIfAbsent(id, k -> new IslandBlockCount(id, gameMode)).remove(fixMaterial(b.getBlockData()));
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
     * Check if this material is at its limit for world on this island
     *
     * @param w - world
     * @param m - material
     * @param id - island id
     * @return limit amount if at limit or -1 if no limit
     */
    private int checkLimit(World w, NamespacedKey m, String id) {
        // Check island limits
        IslandBlockCount ibc = islandCountMap.get(id);
        if (ibc.isBlockLimited(m)) {
            return ibc.isAtLimit(m) ? ibc.getBlockLimit(m) + ibc.getBlockLimitOffset(m) : -1;
        }
        // Check specific world limits
        if (worldLimitMap.containsKey(w) && worldLimitMap.get(w).containsKey(m)) {
            // Material is overridden in world
            return ibc.isAtLimit(m, worldLimitMap.get(w).get(m)) ? worldLimitMap.get(w).get(m) + ibc.getBlockLimitOffset(m) : -1;
        }
        // Check default limit map
        if (defaultLimitMap.containsKey(m) && ibc.isAtLimit(m, defaultLimitMap.get(m))) {
            return defaultLimitMap.get(m) + ibc.getBlockLimitOffset(m);
        }
        // No limit
        return -1;
    }

    /**
     * Gets an aggregate map of the limits for this island
     *
     * @param w - world
     * @param id - island id
     * @return map of limits for materials
     */
    public Map<NamespacedKey, Integer> getMaterialLimits(World w, String id) {
        // Merge limits
        Map<NamespacedKey, Integer> result = new HashMap<>();
        // Default
        result.putAll(defaultLimitMap);
        // World
        if (worldLimitMap.containsKey(w)) {
            result.putAll(worldLimitMap.get(w));
        }
        // Island
        if (islandCountMap.containsKey(id)) {
            IslandBlockCount islandBlockCount = islandCountMap.get(id);
            result.putAll(islandBlockCount.getBlockLimits());

            // Add offsets to the every limit.
            islandBlockCount.getBlockLimitsOffset().forEach((material, offset) ->
            result.put(material, result.getOrDefault(material, 0) + offset));
        }
        return result;
    }

    /**
     * Removes island from the database
     *
     * @param e - island delete event
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onIslandDelete(IslandDeleteEvent e) {
        islandCountMap.remove(e.getIsland().getUniqueId());
        saveMap.remove(e.getIsland().getUniqueId());
        if (handler.objectExists(e.getIsland().getUniqueId())) {
            handler.deleteID(e.getIsland().getUniqueId());
        }
    }

    /**
     * Set the island block count values
     *
     * @param islandId - island unique id
     * @param ibc - island block count
     */
    public void setIsland(String islandId, IslandBlockCount ibc) {
        islandCountMap.put(islandId, ibc);
        handler.saveObjectAsync(ibc);
    }

    /**
     * Get the island block count
     *
     * @param islandId - island unique id
     * @return island block count or null if there is none yet
     */
    @Nullable
    public IslandBlockCount getIsland(String islandId) {
        return islandCountMap.get(islandId);
    }

    /**
     * Get the island block count for island and make one if it does not exist
     * @param island island
     * @return island block count
     */
    @NonNull
    public IslandBlockCount getIsland(Island island) {
        return islandCountMap.computeIfAbsent(island.getUniqueId(), k -> new IslandBlockCount(k, island.getGameMode()));
    }

}

/**
 *
 */
package bentobox.addon.limits.listeners;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import bentobox.addon.limits.Limits;
import bentobox.addon.limits.objects.IslandBlockCount;
import world.bentobox.bentobox.api.events.island.IslandEvent.IslandDeletedEvent;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.Database;
import world.bentobox.bentobox.util.Util;

/**
 * @author tastybento
 *
 */
public class BlockLimitsListener implements Listener {

    /**
     * Save every 10 blocks of change
     */
    private static final Integer CHANGE_LIMIT = 9;
    private Limits addon;
    private Map<String, IslandBlockCount> countMap = new HashMap<>();
    private Map<String, Integer> saveMap = new HashMap<>();
    private Database<IslandBlockCount> handler;
    private Map<World, Map<Material, Integer>> limitMap = new HashMap<>();
    private Map<Material, Integer> defaultLimitMap = new HashMap<>();

    public BlockLimitsListener(Limits addon) {
        this.addon = addon;
        handler = new Database<>(addon, IslandBlockCount.class);
        handler.loadObjects().forEach(ibc -> countMap.put(ibc.getUniqueId(), ibc));
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
            defaultLimitMap = loadLimits(limitConfig);
        }
        // Load specific worlds

        if (addon.getConfig().isConfigurationSection("worlds")) {
            ConfigurationSection worlds = addon.getConfig().getConfigurationSection("worlds");
            for (String worldName : worlds.getKeys(false)) {
                World world = Bukkit.getWorld(worldName);
                if (world != null && addon.getPlugin().getIWM().inWorld(world)) {
                    addon.log("Loading limits for " + world.getName());
                    limitMap.putIfAbsent(world, new HashMap<>());
                    ConfigurationSection matsConfig = worlds.getConfigurationSection(worldName);
                    limitMap.put(world, loadLimits(matsConfig));
                }
            }
        }

    }

    /**
     * Loads limit map from configuration section
     * @param cs - configuration section
     * @return limit map
     */
    private Map<Material, Integer> loadLimits(ConfigurationSection cs) {
        Map<Material, Integer> mats = new HashMap<>();
        for (String material : cs.getKeys(false)) {
            Material mat = Material.getMaterial(material);
            if (mat != null && mat.isBlock()) {
                mats.put(mat, cs.getInt(material));
                addon.log("Limit " + mat + " to " + cs.getInt(material));
            } else {
                addon.logError("Material " + material + " is not a valid block. Skipping...");
            }
        }
        return mats;
    }

    /**
     * Save the count database completely
     */
    public void save() {
        countMap.values().forEach(handler::saveObject);
    }

    // Player-related events
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlock(BlockPlaceEvent e) {
        notify(e, User.getInstance(e.getPlayer()), process(e.getBlock(), true), e.getBlock().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlock(BlockBreakEvent e) {
        notify(e, User.getInstance(e.getPlayer()), process(e.getBlock(), false), e.getBlock().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlock(BlockMultiPlaceEvent e) {
        notify(e, User.getInstance(e.getPlayer()), process(e.getBlock(), true), e.getBlock().getType());
    }

    private void notify(Cancellable e, User user, int limit, Material m) {
        if (limit > -1) {
            user.sendMessage("limits.hit-limit",
                    "[material]", Util.prettifyText(m.toString()),
                    TextVariables.NUMBER, String.valueOf(limit));
            e.setCancelled(true);
        }
    }

    // Non-player events
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlock(BlockBurnEvent e) {
        process(e.getBlock(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlock(BlockExplodeEvent e) {
        e.blockList().forEach(b -> process(b, false));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlock(BlockFadeEvent e) {
        process(e.getBlock(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlock(BlockFormEvent e) {
        process(e.getBlock(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlock(BlockGrowEvent e) {
        process(e.getBlock(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlock(BlockSpreadEvent e) {
        process(e.getBlock(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlock(EntityBlockFormEvent e) {
        process(e.getBlock(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlock(LeavesDecayEvent e) {
        process(e.getBlock(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlock(EntityExplodeEvent e) {
        e.blockList().forEach(b -> process(b, false));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlock(EntityChangeBlockEvent e) {
        process(e.getBlock(), false);
    }

    private int process(Block b, boolean add) {
        return process(b, add, b.getType());
    }

    /**
     * Check if a block can be
     * @param b - block
     * @param add - true to add a block, false to remove
     * @param changeTo - material this block will become
     * @return limit amount if over limit, or -1 if no limitation
     */
    private int process(Block b, boolean add, Material changeTo) {
        // Check if on island
        return addon.getIslands().getIslandAt(b.getLocation()).map(i -> {
            String id = i.getUniqueId();
            countMap.putIfAbsent(id, new IslandBlockCount(id));
            saveMap.putIfAbsent(id, 0);
            if (add) {
                // Check limit
                int limit = checkLimit(b.getWorld(), b.getType(), id);
                if (limit > -1) {
                    return limit;
                }
                countMap.get(id).add(b.getType());
                saveMap.merge(id, 1, Integer::sum);
            } else {
                if (countMap.containsKey(id)) {
                    // Check for changes
                    if (!changeTo.equals(b.getType()) && changeTo.isBlock()) {
                        // Check limit
                        int limit = checkLimit(b.getWorld(), changeTo, id);
                        if (limit > -1) {
                            return limit;
                        }
                        countMap.get(id).add(changeTo);
                    }
                    countMap.get(id).remove(b.getType());
                    saveMap.merge(id, 1, Integer::sum);
                }
            }
            if (saveMap.get(id) > CHANGE_LIMIT) {
                handler.saveObject(countMap.get(id));
                saveMap.remove(id);
            }
            return -1;
        }).orElse(-1);
    }

    /**
     * Check if this material is at its limit for world on this island
     * @param w - world
     * @param m - material
     * @param id - island id
     * @return limit amount if at limit
     */
    private int checkLimit(World w, Material m, String id) {
        // Check specific world first
        if (limitMap.containsKey(w) && limitMap.get(w).containsKey(m)) {
            // Material is overridden in world
            if (countMap.get(id).isAtLimit(m, limitMap.get(w).get(m))) {
                return limitMap.get(w).get(m);
            } else {
                // No limit
                return -1;
            }
        }
        // Check default limit map
        if (defaultLimitMap.containsKey(m) && countMap.get(id).isAtLimit(m, defaultLimitMap.get(m))) {
            return defaultLimitMap.get(m);
        }
        // No limit
        return -1;
    }

    /**
     * Removes island from the database
     * @param e - island delete event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandDelete(IslandDeletedEvent e) {
        countMap.remove(e.getIsland().getUniqueId());
        saveMap.remove(e.getIsland().getUniqueId());
        handler.deleteID(e.getIsland().getUniqueId());
    }

}

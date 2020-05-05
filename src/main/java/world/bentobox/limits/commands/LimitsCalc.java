package world.bentobox.limits.commands;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Pair;
import world.bentobox.bentobox.util.Util;
import world.bentobox.limits.Limits;
import world.bentobox.limits.listeners.BlockLimitsListener;
import world.bentobox.limits.objects.IslandBlockCount;

/**
 *
 * @author YellowZaki, tastybento
 */
public class LimitsCalc {

    private final Limits addon;
    private final World world;
    private final Island island;
    private final BlockLimitsListener bll;
    private IslandBlockCount ibc;
    private final Map<Material, AtomicInteger> blockCount;
    private final User sender;
    private final Set<Pair<Integer, Integer>> chunksToScan;
    private int count;
    private int chunksToScanCount;
    private BentoBox plugin;


    /**
     * Perform a count of all limited blocks or entities on an island
     * @param world - game world to scan
     * @param instance - BentoBox
     * @param targetPlayer - target player's island
     * @param addon - addon instance
     * @param sender - requester of the count
     */
    LimitsCalc(World world, BentoBox instance, UUID targetPlayer, Limits addon, User sender) {
        this.plugin = instance;
        this.addon = addon;
        this.island = instance.getIslands().getIsland(world, targetPlayer);
        this.bll = addon.getBlockLimitListener();
        this.ibc = bll.getIsland(island.getUniqueId());
        blockCount = new EnumMap<>(Material.class);
        this.sender = sender;
        this.world = world;

        // Get chunks to scan
        chunksToScan = getChunksToScan(island);
        count = 0;

        boolean isNether = plugin.getIWM().isNetherGenerate(world) && plugin.getIWM().isNetherIslands(world);
        boolean isEnd = plugin.getIWM().isEndGenerate(world) && plugin.getIWM().isEndIslands(world);
        // Calculate how many chunks need to be scanned
        chunksToScanCount = chunksToScan.size() + (isNether ? chunksToScan.size():0) + (isEnd ? chunksToScan.size():0);
        chunksToScan.forEach(c -> {
            asyncScan(world, c);
            if (isNether) asyncScan(plugin.getIWM().getNetherWorld(world), c);
            if (isEnd) asyncScan(plugin.getIWM().getEndWorld(world), c);
        });

    }
    


    private void asyncScan(World world2, Pair<Integer, Integer> c) {
        Util.getChunkAtAsync(world2, c.x, c.z).thenAccept(ch -> {
            ChunkSnapshot snapShot = ch.getChunkSnapshot();
            Bukkit.getScheduler().runTaskAsynchronously(addon.getPlugin(), () -> {
                this.scanChunk(snapShot);
                count++;
                if (count == chunksToScanCount) {
                    this.tidyUp();
                }
            });
        });
    }



    private void scanChunk(ChunkSnapshot chunk) {
        for (int x = 0; x < 16; x++) {
            // Check if the block coordinate is inside the protection zone and if not, don't count it
            if (chunk.getX() * 16 + x < island.getMinProtectedX() || chunk.getX() * 16 + x >= island.getMinProtectedX() + island.getProtectionRange() * 2) {
                continue;
            }
            for (int z = 0; z < 16; z++) {
                // Check if the block coordinate is inside the protection zone and if not, don't count it
                if (chunk.getZ() * 16 + z < island.getMinProtectedZ() || chunk.getZ() * 16 + z >= island.getMinProtectedZ() + island.getProtectionRange() * 2) {
                    continue;
                }
                for (int y = 0; y < Objects.requireNonNull(island.getCenter().getWorld()).getMaxHeight(); y++) {
                    Material blockData = chunk.getBlockType(x, y, z);
                    // Air is free
                    if (!blockData.equals(Material.AIR)) {
                        checkBlock(blockData);
                    }
                }
            }
        }
    }

    private void checkBlock(Material md) {
        md = bll.fixMaterial(md);
        // md is limited
        if (bll.getMaterialLimits(world, island.getUniqueId()).containsKey(md)) {
            if (!blockCount.containsKey(md)) {
                blockCount.put(md, new AtomicInteger(1));
            } else {
                blockCount.get(md).getAndIncrement();
            }
        }
    }

    private Set<Pair<Integer, Integer>> getChunksToScan(Island island) {
        Set<Pair<Integer, Integer>> chunkSnapshot = new HashSet<>();
        for (int x = island.getMinProtectedX(); x < (island.getMinProtectedX() + island.getProtectionRange() * 2 + 16); x += 16) {
            for (int z = island.getMinProtectedZ(); z < (island.getMinProtectedZ() + island.getProtectionRange() * 2 + 16); z += 16) {
                Pair<Integer, Integer> pair = new Pair<>(world.getBlockAt(x, 0, z).getChunk().getX(), world.getBlockAt(x, 0, z).getChunk().getZ());
                chunkSnapshot.add(pair);
            }
        }
        return chunkSnapshot;
    }

    private void tidyUp() {
        if (ibc == null) {
            ibc = new IslandBlockCount();
        }
        ibc.setBlockCount(blockCount.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().get())));
        bll.setIsland(island.getUniqueId(), ibc);
        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> sender.sendMessage("admin.limits.calc.finished"));
    }

}

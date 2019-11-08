package bentobox.addon.limits.commands;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import bentobox.addon.limits.Limits;
import bentobox.addon.limits.listeners.BlockLimitsListener;
import bentobox.addon.limits.objects.IslandBlockCount;
import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Pair;

/**
 *
 * @author YellowZaki
 */
public class LimitsCalc {

    private boolean checking;
    private Limits addon;
    private World world;
    private Island island;
    private BlockLimitsListener bll;
    private IslandBlockCount ibc;
    private Map<Material, Integer> blockCount;
    private BukkitTask task;
    private User sender;


    LimitsCalc(World world, BentoBox instance, UUID targetPlayer, Limits addon, User sender) {
        this.checking = true;
        this.addon = addon;
        this.world = world;
        this.island = instance.getIslands().getIsland(world, targetPlayer);
        this.bll = addon.getBlockLimitListener();
        this.ibc = bll.getIsland(island.getUniqueId());
        blockCount = new EnumMap<>(Material.class);
        this.sender = sender;
        Set<Pair<Integer, Integer>> chunksToScan = getChunksToScan(island);
        this.task = addon.getServer().getScheduler().runTaskTimer(addon.getPlugin(), () -> {
            Set<ChunkSnapshot> chunkSnapshot = new HashSet<>();
            if (checking) {
                Iterator<Pair<Integer, Integer>> it = chunksToScan.iterator();
                if (!it.hasNext()) {
                    // Nothing left
                    tidyUp();
                    return;
                }
                // Add chunk snapshots to the list
                while (it.hasNext() && chunkSnapshot.size() < 200) {
                    Pair<Integer, Integer> pair = it.next();
                    if (!world.isChunkLoaded(pair.x, pair.z)) {
                        world.loadChunk(pair.x, pair.z);
                        chunkSnapshot.add(world.getChunkAt(pair.x, pair.z).getChunkSnapshot());
                        world.unloadChunk(pair.x, pair.z);
                    } else {
                        chunkSnapshot.add(world.getChunkAt(pair.x, pair.z).getChunkSnapshot());
                    }
                    it.remove();
                }
                // Move to next step
                checking = false;
                checkChunksAsync(chunkSnapshot);
            }
        }, 0L, 1);
    }

    private void checkChunksAsync(final Set<ChunkSnapshot> chunkSnapshot) {
        // Run async task to scan chunks
        addon.getServer().getScheduler().runTaskAsynchronously(addon.getPlugin(), () -> {
            for (ChunkSnapshot chunk : chunkSnapshot) {
                scanChunk(chunk);
            }
            // Nothing happened, change state
            checking = true;
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
                for (int y = 0; y < island.getCenter().getWorld().getMaxHeight(); y++) {
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
                blockCount.put(md, 1);
            } else {
                blockCount.put(md, blockCount.get(md) + 1);
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
        // Cancel
        task.cancel();
        if (ibc == null) {
            ibc = new IslandBlockCount();
        }
        ibc.setBlockCount(blockCount);
        bll.setIsland(island.getUniqueId(), ibc);
        sender.sendMessage("admin.limits.calc.finished");
    }

}

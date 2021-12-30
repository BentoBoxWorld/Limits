package world.bentobox.limits.calculators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.scheduler.BukkitTask;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Pair;
import world.bentobox.bentobox.util.Util;
import world.bentobox.limits.Limits;
import world.bentobox.limits.calculators.Results.Result;
import world.bentobox.limits.listeners.BlockLimitsListener;
import world.bentobox.limits.objects.IslandBlockCount;

/**
 * Counter for limits
 * @author tastybento
 *
 */
public class RecountCalculator {
    public static final long MAX_AMOUNT = 10000;
    private static final int CHUNKS_TO_SCAN = 100;
    private static final int CALCULATION_TIMEOUT = 5; // Minutes


    private final Limits addon;
    private final Queue<Pair<Integer, Integer>> chunksToCheck;
    private final Island island;
    private final CompletableFuture<Results> r;


    private final Results results;
    private final Map<Environment, World> worlds = new EnumMap<>(Environment.class);
    private final List<Location> stackedBlocks = new ArrayList<>();
    private BukkitTask finishTask;
    private final BlockLimitsListener bll;
    private final World world;
    private IslandBlockCount ibc;


    /**
     * Constructor to get the level for an island
     * @param addon - addon
     * @param island - the island to scan
     * @param r - completable result that will be completed when the calculation is complete
     */
    public RecountCalculator(Limits addon, Island island, CompletableFuture<Results> r) {
        this.addon = addon;
        this.bll = addon.getBlockLimitListener();
        this.island = island;
        this.ibc = bll.getIsland(Objects.requireNonNull(island).getUniqueId());
        this.r = r;
        results = new Results();
        chunksToCheck = getChunksToScan(island);
        // Set up the worlds
        this.world = Objects.requireNonNull(Util.getWorld(island.getWorld()));
        worlds.put(Environment.NORMAL, world);
        boolean isNether = addon.getPlugin().getIWM().isNetherGenerate(world) && addon.getPlugin().getIWM().isNetherIslands(world);
        boolean isEnd = addon.getPlugin().getIWM().isEndGenerate(world) && addon.getPlugin().getIWM().isEndIslands(world);

        // Nether
        if (isNether) {
            World nether = addon.getPlugin().getIWM().getNetherWorld(island.getWorld());
            if (nether != null) {
                worlds.put(Environment.NETHER, nether);
            }
        }
        // End
        if (isEnd) {
            World end = addon.getPlugin().getIWM().getEndWorld(island.getWorld());
            if (end != null) {
                worlds.put(Environment.THE_END, end);
            }
        }
    }

    private void checkBlock(BlockData b) {
        Material md = bll.fixMaterial(b);
        // md is limited
        if (bll.getMaterialLimits(world, island.getUniqueId()).containsKey(md)) {
            results.mdCount.add(md);
        }
    }
    /**
     * Get a set of all the chunks in island
     * @param island - island
     * @return - set of pairs of x,z coordinates to check
     */
    private Queue<Pair<Integer, Integer>> getChunksToScan(Island island) {
        Queue<Pair<Integer, Integer>> chunkQueue = new ConcurrentLinkedQueue<>();
        for (int x = island.getMinProtectedX(); x < (island.getMinProtectedX() + island.getProtectionRange() * 2 + 16); x += 16) {
            for (int z = island.getMinProtectedZ(); z < (island.getMinProtectedZ() + island.getProtectionRange() * 2 + 16); z += 16) {
                chunkQueue.add(new Pair<>(x >> 4, z >> 4));
            }
        }
        return chunkQueue;
    }


    /**
     * @return the island
     */
    public Island getIsland() {
        return island;
    }

    /**
     * Get the completable result for this calculation
     * @return the r
     */
    public CompletableFuture<Results> getR() {
        return r;
    }

    /**
     * @return the results
     */
    public Results getResults() {
        return results;
    }

    /**
     * Get a chunk async
     * @param env - the environment
     * @param x - chunk x coordinate
     * @param z - chunk z coordinate
     * @return a future chunk or future null if there is no chunk to load, e.g., there is no island nether
     */
    private CompletableFuture<List<Chunk>> getWorldChunk(Environment env, Queue<Pair<Integer, Integer>> pairList) {
        if (worlds.containsKey(env)) {
            CompletableFuture<List<Chunk>> r2 = new CompletableFuture<>();
            List<Chunk> chunkList = new ArrayList<>();
            World world = worlds.get(env);
            // Get the chunk, and then coincidentally check the RoseStacker
            loadChunks(r2, world, pairList, chunkList);
            return r2;
        }
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    private void loadChunks(CompletableFuture<List<Chunk>> r2, World world, Queue<Pair<Integer, Integer>> pairList,
            List<Chunk> chunkList) {
        if (pairList.isEmpty()) {
            r2.complete(chunkList);
            return;
        }
        Pair<Integer, Integer> p = pairList.poll();
        Util.getChunkAtAsync(world, p.x, p.z, world.getEnvironment().equals(Environment.NETHER)).thenAccept(chunk -> {
            if (chunk != null) {
                chunkList.add(chunk);
                // roseStackerCheck(chunk);
            }
            loadChunks(r2, world, pairList, chunkList); // Iteration
        });
    }
    /*
    private void roseStackerCheck(Chunk chunk) {
        if (addon.isRoseStackersEnabled()) {
            RoseStackerAPI.getInstance().getStackedBlocks(Collections.singletonList(chunk)).forEach(e -> {
                // Blocks below sea level can be scored differently
                boolean belowSeaLevel = seaHeight > 0 && e.getLocation().getY() <= seaHeight;
                // Check block once because the base block will be counted in the chunk snapshot
                for (int _x = 0; _x < e.getStackSize() - 1; _x++) {
                    checkBlock(e.getBlock().getType(), belowSeaLevel);
                }
            });
        }
    }
     */
    /**
     * Count the blocks on the island
     * @param chunk chunk to scan
     */
    private void scanAsync(Chunk chunk) {
        ChunkSnapshot chunkSnapshot = chunk.getChunkSnapshot();
        for (int x = 0; x< 16; x++) {
            // Check if the block coordinate is inside the protection zone and if not, don't count it
            if (chunkSnapshot.getX() * 16 + x < island.getMinProtectedX() || chunkSnapshot.getX() * 16 + x >= island.getMinProtectedX() + island.getProtectionRange() * 2) {
                continue;
            }
            for (int z = 0; z < 16; z++) {
                // Check if the block coordinate is inside the protection zone and if not, don't count it
                if (chunkSnapshot.getZ() * 16 + z < island.getMinProtectedZ() || chunkSnapshot.getZ() * 16 + z >= island.getMinProtectedZ() + island.getProtectionRange() * 2) {
                    continue;
                }
                // Only count to the highest block in the world for some optimization
                for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
                    BlockData blockData = chunkSnapshot.getBlockData(x, y, z);
                    // Slabs can be doubled, so check them twice
                    if (Tag.SLABS.isTagged(blockData.getMaterial())) {
                        Slab slab = (Slab)blockData;
                        if (slab.getType().equals(Slab.Type.DOUBLE)) {
                            checkBlock(blockData);
                        }
                    }
                    // Hook for Wild Stackers (Blocks Only) - this has to use the real chunk
                    /*
                    if (addon.isStackersEnabled() && blockData.getMaterial() == Material.CAULDRON) {
                        stackedBlocks.add(new Location(chunk.getWorld(), x + chunkSnapshot.getX() * 16,y,z + chunkSnapshot.getZ() * 16));
                    }
                     */
                    // Add the value of the block's material
                    checkBlock(blockData);
                }
            }
        }
    }

    /**
     * Scan the chunk chests and count the blocks
     * @param chunks - the chunk to scan
     * @return future that completes when the scan is done and supplies a boolean that will be true if the scan was successful, false if not
     */
    private CompletableFuture<Boolean> scanChunk(List<Chunk> chunks) {
        // If the chunk hasn't been generated, return
        if (chunks == null || chunks.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        // Count blocks in chunk
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        Bukkit.getScheduler().runTaskAsynchronously(BentoBox.getInstance(), () -> {
            chunks.forEach(chunk -> scanAsync(chunk));
            Bukkit.getScheduler().runTask(addon.getPlugin(),() -> result.complete(true));
        });
        return result;
    }

    /**
     * Scan the next chunk on the island
     * @return completable boolean future that will be true if more chunks are left to be scanned, and false if not
     */
    public CompletableFuture<Boolean> scanNextChunk() {
        if (chunksToCheck.isEmpty()) {
            addon.logError("Unexpected: no chunks to scan!");
            // This should not be needed, but just in case
            return CompletableFuture.completedFuture(false);
        }
        // Retrieve and remove from the queue
        Queue<Pair<Integer, Integer>> pairList = new ConcurrentLinkedQueue<>();
        int i = 0;
        while (!chunksToCheck.isEmpty() && i++ < CHUNKS_TO_SCAN) {
            pairList.add(chunksToCheck.poll());
        }
        Queue<Pair<Integer, Integer>> endPairList = new ConcurrentLinkedQueue<>(pairList);
        Queue<Pair<Integer, Integer>> netherPairList = new ConcurrentLinkedQueue<>(pairList);
        // Set up the result
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        // Get chunks and scan
        // Get chunks and scan
        getWorldChunk(Environment.THE_END, endPairList).thenAccept(endChunks ->
        scanChunk(endChunks).thenAccept(b ->
        getWorldChunk(Environment.NETHER, netherPairList).thenAccept(netherChunks ->
        scanChunk(netherChunks).thenAccept(b2 ->
        getWorldChunk(Environment.NORMAL, pairList).thenAccept(normalChunks ->
        scanChunk(normalChunks).thenAccept(b3 ->
        // Complete the result now that all chunks have been scanned
        result.complete(!chunksToCheck.isEmpty()))))
                )
                )
                );

        return result;
    }

    /**
     * Finalizes the calculations and makes the report
     */
    public void tidyUp() {
        // Finalize calculations
        if (ibc == null) {
            ibc = new IslandBlockCount(island.getUniqueId(), addon.getPlugin().getIWM().getAddon(world).map(a -> a.getDescription().getName()).orElse("default"));
        }
        ibc.getBlockCounts().clear();
        results.getMdCount().forEach(ibc::add);
        bll.setIsland(island.getUniqueId(), ibc);
        //Bukkit.getScheduler().runTask(addon.getPlugin(), () -> sender.sendMessage("admin.limits.calc.finished"));

        // All done.
    }

    public void scanIsland(Pipeliner pipeliner) {
        // Scan the next chunk
        scanNextChunk().thenAccept(r -> {
            if (!Bukkit.isPrimaryThread()) {
                addon.getPlugin().logError("scanChunk not on Primary Thread!");
            }
            // Timeout check
            if (System.currentTimeMillis() - pipeliner.getInProcessQueue().get(this) > CALCULATION_TIMEOUT * 60000) {
                // Done
                pipeliner.getInProcessQueue().remove(this);
                getR().complete(new Results(Result.TIMEOUT));
                addon.logError("Level calculation timed out after " + CALCULATION_TIMEOUT + "m for island: " + getIsland());
                return;
            }
            if (Boolean.TRUE.equals(r) && !pipeliner.getTask().isCancelled()) {
                // scanNextChunk returns true if there are more chunks to scan
                scanIsland(pipeliner);
            } else {
                // Done
                pipeliner.getInProcessQueue().remove(this);
                // Chunk finished
                // This was the last chunk
                handleStackedBlocks();
                long checkTime = System.currentTimeMillis();
                finishTask = Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), () -> {
                    // Check every half second if all the chests and stacks have been cleared
                    if ((stackedBlocks.isEmpty()) || System.currentTimeMillis() - checkTime > MAX_AMOUNT) {
                        this.tidyUp();
                        this.getR().complete(getResults());
                        finishTask.cancel();
                    }
                }, 0, 10L);

            }
        });
    }

    private void handleStackedBlocks() {
        // Deal with any stacked blocks
        /*
        Iterator<Location> it = stackedBlocks.iterator();
        while (it.hasNext()) {
            Location v = it.next();
            Util.getChunkAtAsync(v).thenAccept(c -> {
                Block cauldronBlock = v.getBlock();
                boolean belowSeaLevel = seaHeight > 0 && v.getBlockY() <= seaHeight;
                if (WildStackerAPI.getWildStacker().getSystemManager().isStackedBarrel(cauldronBlock)) {
                    StackedBarrel barrel = WildStackerAPI.getStackedBarrel(cauldronBlock);
                    int barrelAmt = WildStackerAPI.getBarrelAmount(cauldronBlock);
                    for (int _x = 0; _x < barrelAmt; _x++) {
                        checkBlock(barrel.getType(), belowSeaLevel);
                    }
                }
                it.remove();
            });
        }
         */
    }
}

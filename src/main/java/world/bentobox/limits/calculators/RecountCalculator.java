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
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
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
 * Counter for limits.
 *
 * <p>Scans every loaded chunk in each of the island's environment worlds (overworld,
 * nether, end) and rebuilds {@link IslandBlockCount}'s per-env block and entity
 * counts from scratch.
 *
 * @author tastybento
 */
public class RecountCalculator {
    public static final long MAX_AMOUNT = 10000;
    private static final int CHUNKS_TO_SCAN = 100;
    private static final int CALCULATION_TIMEOUT = 5;

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

    public RecountCalculator(Limits addon, Island island, CompletableFuture<Results> r) {
        this.addon = addon;
        this.bll = addon.getBlockLimitListener();
        this.island = island;
        this.ibc = bll.getIsland(Objects.requireNonNull(island).getUniqueId());
        this.r = r;
        results = new Results();
        chunksToCheck = getChunksToScan(island);
        this.world = Objects.requireNonNull(Util.getWorld(island.getWorld()));
        worlds.put(Environment.NORMAL, world);
        boolean isNether = addon.getPlugin().getIWM().isNetherGenerate(world)
                && addon.getPlugin().getIWM().isNetherIslands(world);
        boolean isEnd = addon.getPlugin().getIWM().isEndGenerate(world)
                && addon.getPlugin().getIWM().isEndIslands(world);
        if (isNether) {
            World nether = addon.getPlugin().getIWM().getNetherWorld(island.getWorld());
            if (nether != null) worlds.put(Environment.NETHER, nether);
        }
        if (isEnd) {
            World end = addon.getPlugin().getIWM().getEndWorld(island.getWorld());
            if (end != null) worlds.put(Environment.THE_END, end);
        }
    }

    private void checkBlock(Environment env, BlockData b) {
        NamespacedKey md = bll.fixMaterial(b);
        // Only count materials that are tracked at any level (env default, world override, island).
        if (bll.getMaterialLimits(worlds.get(env), island.getUniqueId()).containsKey(md)) {
            results.getBlockCount(env).add(md);
        }
    }

    private Queue<Pair<Integer, Integer>> getChunksToScan(Island island) {
        Queue<Pair<Integer, Integer>> chunkQueue = new ConcurrentLinkedQueue<>();
        for (int x = island.getMinProtectedX(); x < (island.getMinProtectedX() + island.getProtectionRange() * 2
                + 16); x += 16) {
            for (int z = island.getMinProtectedZ(); z < (island.getMinProtectedZ() + island.getProtectionRange() * 2
                    + 16); z += 16) {
                chunkQueue.add(new Pair<>(x >> 4, z >> 4));
            }
        }
        return chunkQueue;
    }

    public Island getIsland() {
        return island;
    }

    public CompletableFuture<Results> getR() {
        return r;
    }

    public Results getResults() {
        return results;
    }

    private CompletableFuture<List<Chunk>> getWorldChunk(Environment env, Queue<Pair<Integer, Integer>> pairList) {
        if (worlds.containsKey(env)) {
            CompletableFuture<List<Chunk>> r2 = new CompletableFuture<>();
            List<Chunk> chunkList = new ArrayList<>();
            World world = worlds.get(env);
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
            if (chunk != null) chunkList.add(chunk);
            loadChunks(r2, world, pairList, chunkList);
        });
    }

    private void scanAsync(Environment env, Chunk chunk) {
        ChunkSnapshot chunkSnapshot = chunk.getChunkSnapshot();
        for (int x = 0; x < 16; x++) {
            if (chunkSnapshot.getX() * 16 + x < island.getMinProtectedX()
                    || chunkSnapshot.getX() * 16 + x >= island.getMinProtectedX()
                            + island.getProtectionRange() * 2) {
                continue;
            }
            for (int z = 0; z < 16; z++) {
                if (chunkSnapshot.getZ() * 16 + z < island.getMinProtectedZ()
                        || chunkSnapshot.getZ() * 16 + z >= island.getMinProtectedZ()
                                + island.getProtectionRange() * 2) {
                    continue;
                }
                for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
                    BlockData blockData = chunkSnapshot.getBlockData(x, y, z);
                    if (Tag.SLABS.isTagged(blockData.getMaterial())) {
                        Slab slab = (Slab) blockData;
                        if (slab.getType().equals(Slab.Type.DOUBLE)) {
                            checkBlock(env, blockData);
                        }
                    }
                    checkBlock(env, blockData);
                }
            }
        }
    }

    private CompletableFuture<Boolean> scanChunk(Environment env, List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(BentoBox.getInstance(), () -> {
            chunks.forEach(chunk -> scanAsync(env, chunk));
            Bukkit.getScheduler().runTask(addon.getPlugin(), () -> result.complete(true));
        });
        return result;
    }

    public CompletableFuture<Boolean> scanNextChunk() {
        if (chunksToCheck.isEmpty()) {
            addon.logError("Unexpected: no chunks to scan!");
            return CompletableFuture.completedFuture(false);
        }
        Queue<Pair<Integer, Integer>> pairList = new ConcurrentLinkedQueue<>();
        int i = 0;
        while (!chunksToCheck.isEmpty() && i++ < CHUNKS_TO_SCAN) {
            pairList.add(chunksToCheck.poll());
        }
        Queue<Pair<Integer, Integer>> endPairList = new ConcurrentLinkedQueue<>(pairList);
        Queue<Pair<Integer, Integer>> netherPairList = new ConcurrentLinkedQueue<>(pairList);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        getWorldChunk(Environment.THE_END, endPairList).thenAccept(endChunks ->
        scanChunk(Environment.THE_END, endChunks).thenAccept(b ->
        getWorldChunk(Environment.NETHER, netherPairList).thenAccept(netherChunks ->
        scanChunk(Environment.NETHER, netherChunks).thenAccept(b2 ->
        getWorldChunk(Environment.NORMAL, pairList).thenAccept(normalChunks ->
        scanChunk(Environment.NORMAL, normalChunks).thenAccept(b3 ->
        result.complete(!chunksToCheck.isEmpty())))))));
        return result;
    }

    private void scanEntities() {
        for (Map.Entry<Environment, World> e : worlds.entrySet()) {
            Environment env = e.getKey();
            World w = e.getValue();
            for (Entity entity : w.getEntities()) {
                if (!island.inIslandSpace(entity.getLocation())) continue;
                if (entity instanceof LivingEntity || entity.getType().name().endsWith("_MINECART")
                        || entity.getType().name().equals("ARMOR_STAND")
                        || entity.getType().name().equals("ITEM_FRAME")
                        || entity.getType().name().equals("PAINTING")) {
                    results.getEntityCount(env).add(entity.getType());
                }
            }
        }
    }

    public void tidyUp() {
        if (ibc == null) {
            ibc = new IslandBlockCount(island.getUniqueId(),
                    addon.getPlugin().getIWM().getAddon(world).map(a -> a.getDescription().getName()).orElse("default"));
        }
        // Reset and write per-env block counts
        ibc.clearAllBlockCounts();
        results.getEnvBlockCount().forEach((env, multiset) -> multiset.forEach(key -> ibc.add(env, key)));

        // Recount entities (loaded only) and write per-env
        scanEntities();
        ibc.clearAllEntityCounts();
        results.getEnvEntityCount().forEach((env, multiset) -> multiset
                .entrySet().forEach(en -> {
                    for (int i = 0; i < en.getCount(); i++) {
                        ibc.incrementEntity(env, en.getElement());
                    }
                }));
        bll.setIsland(island.getUniqueId(), ibc);
    }

    public void scanIsland(LongSupplier startTime, Runnable onRemove, BooleanSupplier isCancelled, Runnable recurse) {
        scanNextChunk().thenAccept(r -> {
            if (!Bukkit.isPrimaryThread()) {
                addon.getPlugin().logError("scanChunk not on Primary Thread!");
            }
            if (System.currentTimeMillis() - startTime.getAsLong() > CALCULATION_TIMEOUT * 60000) {
                onRemove.run();
                getR().complete(new Results(Result.TIMEOUT));
                addon.logError("Level calculation timed out after " + CALCULATION_TIMEOUT + "m for island: "
                        + getIsland());
                return;
            }
            if (Boolean.TRUE.equals(r) && !isCancelled.getAsBoolean()) {
                recurse.run();
            } else {
                onRemove.run();
                handleStackedBlocks();
                long checkTime = System.currentTimeMillis();
                finishTask = Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), () -> {
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
        // Stacked-block plugin support is currently disabled; placeholder for future re-enablement.
    }
}

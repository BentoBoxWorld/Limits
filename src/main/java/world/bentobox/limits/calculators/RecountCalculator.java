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
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Vehicle;
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
        this.ibc = bll.getIsland(Objects.requireNonNull(island));
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
        // Only count materials that are tracked at any level (env default, world override,
        // island, or block group membership).
        if (bll.getMaterialLimits(worlds.get(env), island.getUniqueId()).containsKey(md)
                || addon.getSettings().isInBlockGroup(md)) {
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
            World envWorld = worlds.get(env);
            boolean isNether = envWorld.getEnvironment().equals(Environment.NETHER);
            List<CompletableFuture<Chunk>> futures = new ArrayList<>();
            while (!pairList.isEmpty()) {
                Pair<Integer, Integer> p = pairList.poll();
                futures.add(Util.getChunkAtAsync(envWorld, p.x, p.z, isNether));
            }
            if (futures.isEmpty()) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream()
                            .map(CompletableFuture::join)
                            .filter(Objects::nonNull)
                            .toList());
        }
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    private void scanAsync(Environment env, Chunk chunk) {
        ChunkSnapshot chunkSnapshot = chunk.getChunkSnapshot();
        int minX = island.getMinProtectedX();
        int maxX = minX + island.getProtectionRange() * 2;
        int minZ = island.getMinProtectedZ();
        int maxZ = minZ + island.getProtectionRange() * 2;
        int chunkBaseX = chunkSnapshot.getX() * 16;
        int chunkBaseZ = chunkSnapshot.getZ() * 16;
        int minY = chunk.getWorld().getMinHeight();
        int maxY = chunk.getWorld().getMaxHeight();
        for (int x = 0; x < 16; x++) {
            int absX = chunkBaseX + x;
            if (absX < minX || absX >= maxX) continue;
            for (int z = 0; z < 16; z++) {
                int absZ = chunkBaseZ + z;
                if (absZ < minZ || absZ >= maxZ) continue;
                scanColumn(env, chunkSnapshot, x, z, minY, maxY);
            }
        }
    }

    private void scanColumn(Environment env, ChunkSnapshot chunkSnapshot, int x, int z, int minY, int maxY) {
        boolean stackedAsOne = addon.getSettings().isStackedPlantsCountAsOne();
        NamespacedKey below = null;
        for (int y = minY; y < maxY; y++) {
            BlockData blockData = chunkSnapshot.getBlockData(x, y, z);
            if (Tag.SLABS.isTagged(blockData.getMaterial())
                    && ((Slab) blockData).getType().equals(Slab.Type.DOUBLE)) {
                checkBlock(env, blockData);
            }
            NamespacedKey key = bll.fixMaterial(blockData);
            // Stacked-plants-as-one: segments sitting on the same plant are not counted
            if (!(stackedAsOne && BlockLimitsListener.STACKABLE.contains(key) && key.equals(below))) {
                checkBlock(env, blockData);
            }
            below = key;
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

        CompletableFuture<List<Chunk>> endFuture = getWorldChunk(Environment.THE_END, endPairList);
        CompletableFuture<List<Chunk>> netherFuture = getWorldChunk(Environment.NETHER, netherPairList);
        CompletableFuture<List<Chunk>> normalFuture = getWorldChunk(Environment.NORMAL, pairList);

        return CompletableFuture.allOf(endFuture, netherFuture, normalFuture)
                .thenCompose(v -> scanChunk(Environment.THE_END, endFuture.join())
                        .thenCompose(b -> scanChunk(Environment.NETHER, netherFuture.join()))
                        .thenCompose(b2 -> scanChunk(Environment.NORMAL, normalFuture.join()))
                        .thenApply(b3 -> !chunksToCheck.isEmpty()));
    }

    private void scanEntities() {
        for (Map.Entry<Environment, World> e : worlds.entrySet()) {
            Environment env = e.getKey();
            World w = e.getValue();
            for (Entity entity : w.getEntities()) {
                if (!island.inIslandSpace(entity.getLocation())) continue;
                if (entity instanceof LivingEntity || entity instanceof Hanging
                        || entity instanceof Vehicle
                        || entity.getType().name().equals("ARMOR_STAND")) {
                    results.getEntityCount(env).add(entity.getType());
                }
            }
        }
    }

    public void tidyUp() {
        ibc = bll.getIsland(island);
        // Custom-namespace counts (ItemsAdder/Oraxen blocks) are event-tracked and
        // invisible to the chunk scan, so carry them across the reset untouched.
        Map<Environment, Map<NamespacedKey, Integer>> customCounts = new EnumMap<>(Environment.class);
        for (Environment env : List.of(Environment.NORMAL, Environment.NETHER, Environment.THE_END)) {
            ibc.getBlockCounts(env).forEach((key, count) -> {
                if (!NamespacedKey.MINECRAFT.equals(key.getNamespace())) {
                    customCounts.computeIfAbsent(env, e -> new java.util.HashMap<>()).put(key, count);
                }
            });
        }
        // Reset and write per-env block counts
        ibc.clearAllBlockCounts();
        results.getEnvBlockCount().forEach((env, multiset) -> multiset.forEach(key -> ibc.add(env, key)));
        customCounts.forEach((env, m) -> m.forEach((key, count) -> {
            for (int i = 0; i < count; i++) {
                ibc.add(env, key);
            }
        }));

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
        scanNextChunk().thenAccept(hasMoreChunks -> {
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
            if (Boolean.TRUE.equals(hasMoreChunks) && !isCancelled.getAsBoolean()) {
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

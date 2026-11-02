package world.bentobox.limits.calculators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Entity;
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
 * <p>Loads the island's chunks in each of its environment worlds (overworld, nether, end)
 * in batches and rebuilds {@link IslandBlockCount}'s per-env block and entity counts from
 * scratch. Each batch is held loaded only until its blocks and entities are counted, so
 * memory use is bounded by the batch size, not the island size.
 *
 * @author tastybento
 */
public class RecountCalculator {
    /** Longest time, in milliseconds, to wait for one batch's entities to load before counting it anyway. */
    public static final long MAX_AMOUNT = 10000;
    private static final int CHUNKS_TO_SCAN = 100;
    private static final int CALCULATION_TIMEOUT = 5;

    private final Limits addon;
    private final Queue<Pair<Integer, Integer>> chunksToCheck;
    private final Island island;
    private final CompletableFuture<Results> r;
    private final Results results;
    private final Map<Environment, World> worlds = new EnumMap<>(Environment.class);
    /**
     * Chunks this recount put a plugin ticket on. Without a ticket, a chunk nobody stands in can
     * unload before its entities are counted, and they would be counted as zero.
     */
    private final Set<Chunk> ticketedChunks = new HashSet<>();
    private BukkitTask entityWaitTask;
    private final BlockLimitsListener bll;
    private final World world;
    /** When true, chunks are loaded but the per-block scan is skipped; only entity counts are rebuilt. */
    private final boolean entitiesOnly;
    private IslandBlockCount ibc;

    public RecountCalculator(Limits addon, Island island, CompletableFuture<Results> r) {
        this(addon, island, r, false);
    }

    public RecountCalculator(Limits addon, Island island, CompletableFuture<Results> r, boolean entitiesOnly) {
        this.entitiesOnly = entitiesOnly;
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

    /**
     * @param island island
     * @return the number of chunk coordinates a recount of this island loads in each world
     */
    public static int chunksPerWorld(Island island) {
        int perAxis = (island.getProtectionRange() * 2 + 16 + 15) / 16;
        return perAxis * perAxis;
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
                futures.add(Util.getChunkAtAsync(envWorld, p.x, p.z, isNether).thenApply(this::holdChunk));
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

    /**
     * Keeps a freshly loaded chunk loaded until {@link #releaseChunks()}, so its entities are still
     * there when the recount finishes. Chunks BentoBox already holds a ticket on are left alone, so
     * releasing never drops a ticket this recount did not add.
     */
    private Chunk holdChunk(Chunk chunk) {
        if (chunk != null && !ticketedChunks.contains(chunk)
                && !chunk.getPluginChunkTickets().contains(addon.getPlugin())
                && chunk.addPluginChunkTicket(addon.getPlugin())) {
            ticketedChunks.add(chunk);
        }
        return chunk;
    }

    /**
     * Removes the plugin tickets this recount added, letting the chunks unload normally again.
     */
    public void releaseChunks() {
        if (entityWaitTask != null) {
            entityWaitTask.cancel();
            entityWaitTask = null;
        }
        ticketedChunks.forEach(c -> c.removePluginChunkTicket(addon.getPlugin()));
        ticketedChunks.clear();
    }

    private void releaseChunks(Collection<Chunk> chunks) {
        chunks.stream().filter(ticketedChunks::remove).forEach(c -> c.removePluginChunkTicket(addon.getPlugin()));
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
            // An extended piston occupies two blocks (base + head) and a piston mid-move is a
            // MOVING_PISTON; both normalise to the base's material, so only the base is counted.
            // Blocks being pushed are also MOVING_PISTON and must not be counted as pistons.
            // Stacked-plants-as-one: segments sitting on the same plant are not counted
            if (!BlockLimitsListener.isPistonPart(blockData.getMaterial())
                    && !(stackedAsOne && BlockLimitsListener.STACKABLE.contains(key) && key.equals(below))) {
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

    /**
     * Loads, counts and releases the next batch of chunks.
     *
     * @return true if there are more chunks to scan
     */
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

        CompletableFuture<Map<Environment, List<Chunk>>> loaded = CompletableFuture
                .allOf(endFuture, netherFuture, normalFuture).thenApply(v -> {
                    Map<Environment, List<Chunk>> batch = new EnumMap<>(Environment.class);
                    batch.put(Environment.THE_END, endFuture.join());
                    batch.put(Environment.NETHER, netherFuture.join());
                    batch.put(Environment.NORMAL, normalFuture.join());
                    return batch;
                });

        // Entity-only recount: chunks are still loaded so their entities can be counted,
        // but the expensive per-block scan is skipped.
        CompletableFuture<Map<Environment, List<Chunk>>> scanned = entitiesOnly ? loaded
                : loaded.thenCompose(batch -> scanChunk(Environment.THE_END, batch.get(Environment.THE_END))
                        .thenCompose(b -> scanChunk(Environment.NETHER, batch.get(Environment.NETHER)))
                        .thenCompose(b2 -> scanChunk(Environment.NORMAL, batch.get(Environment.NORMAL)))
                        .thenApply(b3 -> batch));

        return scanned.thenCompose(this::countEntities).thenApply(v -> !chunksToCheck.isEmpty());
    }

    /**
     * Paper loads a chunk's entities separately from, and slightly after, the chunk itself.
     * Counting before they arrive would under-count, so this waits (up to {@link #MAX_AMOUNT} ms)
     * for the batch's entities, counts them, then releases the batch's chunks.
     */
    private CompletableFuture<Void> countEntities(Map<Environment, List<Chunk>> batch) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        if (entitiesLoaded(batch)) {
            countAndRelease(batch);
            done.complete(null);
            return done;
        }
        long start = System.currentTimeMillis();
        entityWaitTask = Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), () -> {
            if (entitiesLoaded(batch) || System.currentTimeMillis() - start > MAX_AMOUNT) {
                entityWaitTask.cancel();
                entityWaitTask = null;
                countAndRelease(batch);
                done.complete(null);
            }
        }, 1L, 1L);
        return done;
    }

    /**
     * @return true once every chunk in the batch has its entities loaded, or has since unloaded
     */
    static boolean entitiesLoaded(Map<Environment, List<Chunk>> batch) {
        return batch.values().stream().flatMap(List::stream).allMatch(c -> !c.isLoaded() || c.isEntitiesLoaded());
    }

    private void countAndRelease(Map<Environment, List<Chunk>> batch) {
        batch.forEach((env, chunks) -> {
            // A chunk is counted once even if it was returned for more than one coordinate
            Set<Chunk> unique = new LinkedHashSet<>(chunks);
            for (Chunk chunk : unique) {
                for (Entity entity : chunk.getEntities()) {
                    if (Limits.isTracked(entity) && !Limits.isExempt(entity)
                            && island.inIslandSpace(entity.getLocation())) {
                        results.getEntityCount(env).add(entity.getType());
                    }
                }
            }
            releaseChunks(unique);
        });
    }

    public void tidyUp() {
        try {
            writeCounts();
        } finally {
            releaseChunks();
        }
    }

    private void writeCounts() {
        ibc = bll.getIsland(island);
        if (!entitiesOnly) {
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
        }

        // Write the per-env entity counts gathered batch by batch
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
                releaseChunks();
                getR().complete(new Results(Result.TIMEOUT));
                addon.logError("Level calculation timed out after " + CALCULATION_TIMEOUT + "m for island: "
                        + getIsland());
                return;
            }
            if (isCancelled.getAsBoolean()) {
                // The queue was stopped; don't write a partial count, but let the chunks go
                onRemove.run();
                releaseChunks();
                getR().complete(null);
                return;
            }
            if (Boolean.TRUE.equals(hasMoreChunks)) {
                recurse.run();
            } else {
                handleStackedBlocks();
                this.tidyUp();
                // Leave the queue only once the counts are written, so the next island's
                // chunks never load while this one's are still held
                onRemove.run();
                this.getR().complete(getResults());
            }
        });
    }

    private void handleStackedBlocks() {
        // Stacked-block plugin support is currently disabled; placeholder for future re-enablement.
    }
}

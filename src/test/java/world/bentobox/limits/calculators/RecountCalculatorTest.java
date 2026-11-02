package world.bentobox.limits.calculators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.TechnicalPiston;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.bukkit.persistence.PersistentDataType;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.persistence.PersistentDataContainerMock;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandWorldManager;
import world.bentobox.limits.Limits;
import world.bentobox.limits.Settings;
import world.bentobox.limits.listeners.BlockLimitsListener;
import world.bentobox.limits.objects.IslandBlockCount;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecountCalculatorTest {

    private static final String ISLAND_ID = "island-1";

    @Mock
    private Limits addon;
    @Mock
    private BentoBox plugin;
    @Mock
    private IslandWorldManager iwm;
    @Mock
    private BlockLimitsListener bll;
    @Mock
    private Island island;
    @Mock
    private World world;
    @Mock
    private Location location;

    private IslandBlockCount ibc;
    private MockedStatic<BentoBox> mockedBentoBox;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        mockedBentoBox = Mockito.mockStatic(BentoBox.class);
        mockedBentoBox.when(BentoBox::getInstance).thenReturn(plugin);
        when(addon.getPlugin()).thenReturn(plugin);
        when(plugin.getIWM()).thenReturn(iwm);
        when(addon.getBlockLimitListener()).thenReturn(bll);
        ibc = new IslandBlockCount(ISLAND_ID, "BSkyBlock");
        when(bll.getIsland(island)).thenReturn(ibc);

        when(world.getEnvironment()).thenReturn(Environment.NORMAL);
        when(world.getName()).thenReturn("bskyblock_world");
        when(island.getWorld()).thenReturn(world);
        when(island.getUniqueId()).thenReturn(ISLAND_ID);
        when(island.getMinProtectedX()).thenReturn(0);
        when(island.getMinProtectedZ()).thenReturn(0);
        when(island.getProtectionRange()).thenReturn(50);
        when(island.inIslandSpace(any(Location.class))).thenReturn(true);
        when(location.getWorld()).thenReturn(world);
    }

    @AfterEach
    void tearDown() {
        mockedBentoBox.close();
        MockBukkit.unmock();
    }

    private Island islandWithRange(int range) {
        Island i = mock(Island.class);
        when(i.getProtectionRange()).thenReturn(range);
        return i;
    }

    private Chunk chunk(boolean loaded, boolean entitiesLoaded) {
        Chunk c = mock(Chunk.class);
        when(c.isLoaded()).thenReturn(loaded);
        when(c.isEntitiesLoaded()).thenReturn(entitiesLoaded);
        when(c.getEntities()).thenReturn(new Entity[0]);
        return c;
    }

    /**
     * BentoBox loads chunks through PaperLib, which uses the async API on Paper and falls
     * back to the synchronous one elsewhere (including under MockBukkit). Stub both.
     */
    private void loadsChunk(Chunk c) {
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean())).thenReturn(CompletableFuture.completedFuture(c));
        when(world.isChunkGenerated(anyInt(), anyInt())).thenReturn(true);
        when(world.getChunkAt(anyInt(), anyInt())).thenReturn(c);
        when(world.getChunkAt(anyInt(), anyInt(), anyBoolean())).thenReturn(c);
    }

    @Test
    void testChunksPerWorldMatchesScanArea() {
        assertEquals(1, RecountCalculator.chunksPerWorld(islandWithRange(0)));
        assertEquals(64, RecountCalculator.chunksPerWorld(islandWithRange(50)));
        assertEquals(196, RecountCalculator.chunksPerWorld(islandWithRange(100)));
        assertEquals(676, RecountCalculator.chunksPerWorld(islandWithRange(200)));
        assertEquals(2601, RecountCalculator.chunksPerWorld(islandWithRange(400)));
        assertEquals(4096, RecountCalculator.chunksPerWorld(islandWithRange(500)));
    }

    @Test
    void testEntitiesLoadedChecksEveryChunkInTheBatch() {
        Chunk ready = chunk(true, true);
        Chunk loading = chunk(true, false);
        Chunk unloaded = chunk(false, false);

        assertTrue(RecountCalculator.entitiesLoaded(Map.of(Environment.NORMAL, List.of(ready))));
        assertFalse(RecountCalculator.entitiesLoaded(Map.of(Environment.NORMAL, List.of(ready),
                Environment.NETHER, List.of(loading))), "a chunk's entities have not arrived");
        assertTrue(RecountCalculator.entitiesLoaded(Map.of(Environment.NORMAL, List.of(ready, unloaded))),
                "an unloaded chunk cannot deliver entities, so do not wait for it");
    }

    @Test
    void testBatchWaitsForEntitiesBeforeCountingAndReleasing() {
        Chunk c = chunk(true, false);
        loadsChunk(c);
        when(c.addPluginChunkTicket(plugin)).thenReturn(true);
        Villager villager = villager();
        when(c.getEntities()).thenReturn(new Entity[] { villager });
        RecountCalculator calc = new RecountCalculator(addon, island, new CompletableFuture<>(), true);

        CompletableFuture<Boolean> scan = calc.scanNextChunk();
        MockBukkit.getMock().getScheduler().performTicks(30L);
        assertFalse(scan.isDone(), "must not count while entities are still loading");
        verify(c).addPluginChunkTicket(plugin);
        verify(c, never()).removePluginChunkTicket(plugin);

        when(c.isEntitiesLoaded()).thenReturn(true);
        MockBukkit.getMock().getScheduler().performOneTick();
        assertFalse(scan.join(), "64 chunks fit in one batch");
        assertEquals(1, calc.getResults().getEntityCount(Environment.NORMAL).count(EntityType.VILLAGER),
                "a chunk is counted once even if returned for several coordinates");
        verify(c).removePluginChunkTicket(plugin);
    }

    @Test
    void testBatchIsReleasedBeforeTheNextBatchLoads() {
        // Range 100 is 196 chunks per world: two batches of up to 100
        when(island.getProtectionRange()).thenReturn(100);
        Map<Long, Chunk> chunks = new HashMap<>();
        when(world.isChunkGenerated(anyInt(), anyInt())).thenReturn(true);
        Answer<Object> chunkAt = inv -> chunks.computeIfAbsent(
                ((long) (int) inv.getArgument(0) << 32) ^ ((int) inv.getArgument(1) & 0xffffffffL), k -> {
                    Chunk c = chunk(true, true);
                    when(c.addPluginChunkTicket(plugin)).thenReturn(true);
                    return c;
                });
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean()))
                .thenAnswer(inv -> CompletableFuture.completedFuture(chunkAt.answer(inv)));
        when(world.getChunkAt(anyInt(), anyInt())).thenAnswer(chunkAt);
        when(world.getChunkAt(anyInt(), anyInt(), anyBoolean())).thenAnswer(chunkAt);
        RecountCalculator calc = new RecountCalculator(addon, island, new CompletableFuture<>(), true);

        assertTrue(calc.scanNextChunk().join(), "a second batch remains");
        assertEquals(100, chunks.size(), "only the first batch is loaded");
        chunks.values().forEach(c -> verify(c).removePluginChunkTicket(plugin));

        assertFalse(calc.scanNextChunk().join());
        assertEquals(196, chunks.size());
        chunks.values().forEach(c -> verify(c).removePluginChunkTicket(plugin));
    }

    @Test
    void testIslandLeavesTheQueueOnlyAfterCountsAreWritten() {
        Chunk c = chunk(true, true);
        loadsChunk(c);
        Runnable onRemove = mock(Runnable.class);
        CompletableFuture<Results> result = new CompletableFuture<>();
        RecountCalculator calc = new RecountCalculator(addon, island, result, true);

        calc.scanIsland(System::currentTimeMillis, onRemove, () -> false, () -> { });

        assertTrue(result.isDone());
        assertNotNull(result.join());
        InOrder order = inOrder(bll, onRemove);
        order.verify(bll).setIsland(eq(ISLAND_ID), any(IslandBlockCount.class));
        order.verify(onRemove).run();
    }

    @Test
    void testChunkAlreadyHeldByBentoBoxKeepsItsTicket() {
        Chunk c = chunk(true, true);
        loadsChunk(c);
        when(c.getPluginChunkTickets()).thenReturn(List.of(plugin));
        RecountCalculator calc = new RecountCalculator(addon, island, new CompletableFuture<>(), true);

        calc.scanNextChunk().join();
        calc.tidyUp();

        verify(c, never()).addPluginChunkTicket(plugin);
        verify(c, never()).removePluginChunkTicket(plugin);
    }

    @Test
    void testCancelledRecountReleasesChunksWithoutWritingCounts() {
        Chunk c = chunk(true, false);
        loadsChunk(c);
        when(c.addPluginChunkTicket(plugin)).thenReturn(true);
        CompletableFuture<Results> result = new CompletableFuture<>();
        RecountCalculator calc = new RecountCalculator(addon, island, result, true);

        calc.scanIsland(System::currentTimeMillis, () -> { }, () -> true, () -> { });
        when(c.isEntitiesLoaded()).thenReturn(true);
        MockBukkit.getMock().getScheduler().performTicks(10L);

        assertTrue(result.isDone());
        verify(c).removePluginChunkTicket(plugin);
        verify(bll, never()).setIsland(any(), any());
    }

    @Test
    void testStoppingWhileWaitingForEntitiesReleasesChunks() {
        Chunk c = chunk(true, false);
        loadsChunk(c);
        when(c.addPluginChunkTicket(plugin)).thenReturn(true);
        RecountCalculator calc = new RecountCalculator(addon, island, new CompletableFuture<>(), true);
        CompletableFuture<Boolean> scan = calc.scanNextChunk();

        calc.releaseChunks();
        when(c.isEntitiesLoaded()).thenReturn(true);
        MockBukkit.getMock().getScheduler().performTicks(10L);

        verify(c).removePluginChunkTicket(plugin);
        assertFalse(scan.isDone(), "the wait task was cancelled, so nothing is counted");
    }

    @Test
    void testTimedOutRecountReleasesChunks() {
        Chunk c = chunk(true, true);
        loadsChunk(c);
        when(c.addPluginChunkTicket(plugin)).thenReturn(true);
        CompletableFuture<Results> result = new CompletableFuture<>();
        RecountCalculator calc = new RecountCalculator(addon, island, result, true);

        calc.scanIsland(() -> 0L, () -> { }, () -> false, () -> { });

        assertEquals(Results.Result.TIMEOUT, result.join().getState());
        verify(c).removePluginChunkTicket(plugin);
        verify(bll, never()).setIsland(any(), any());
    }

    @Test
    void testEntityCountSkipsPlayersExemptAndOutsideIsland() {
        Player player = mock(Player.class);
        when(player.getType()).thenReturn(EntityType.PLAYER);
        when(player.getLocation()).thenReturn(location);
        Villager exempt = villager();
        exempt.getPersistentDataContainer().set(Limits.EXEMPT_KEY, PersistentDataType.BYTE, (byte) 1);
        Location outside = mock(Location.class);
        when(island.inIslandSpace(outside)).thenReturn(false);
        Villager neighbour = villager();
        when(neighbour.getLocation()).thenReturn(outside);
        Chunk c = chunk(true, true);
        loadsChunk(c);
        Villager villager = villager();
        when(c.getEntities()).thenReturn(new Entity[] { player, exempt, neighbour, villager });
        CompletableFuture<Results> result = new CompletableFuture<>();
        RecountCalculator calc = new RecountCalculator(addon, island, result, true);

        calc.scanIsland(System::currentTimeMillis, () -> { }, () -> false, () -> { });

        assertTrue(result.isDone());
        assertEquals(1, ibc.getEntityCount(Environment.NORMAL, EntityType.VILLAGER));
        assertEquals(0, ibc.getEntityCount(Environment.NORMAL, EntityType.PLAYER));
        verify(bll).setIsland(ISLAND_ID, ibc);
    }

    @Test
    void testEntityOnlyRecountLeavesBlockCountsAlone() {
        ibc.add(Environment.NORMAL, org.bukkit.Material.HOPPER.getKey());
        RecountCalculator calc = new RecountCalculator(addon, island, new CompletableFuture<>(), true);

        calc.tidyUp();

        assertEquals(1, ibc.getBlockCount(Environment.NORMAL, org.bukkit.Material.HOPPER.getKey()));
    }

    private Villager villager() {
        Villager v = withPdc(mock(Villager.class));
        when(v.getType()).thenReturn(EntityType.VILLAGER);
        when(v.getLocation()).thenReturn(location);
        return v;
    }

    private BlockData blockData(Material m) {
        BlockData d = m == Material.PISTON_HEAD || m == Material.MOVING_PISTON ? mock(TechnicalPiston.class)
                : mock(BlockData.class);
        when(d.getMaterial()).thenReturn(m);
        return d;
    }

    /**
     * A recount sees an extended piston as two blocks (PISTON base + PISTON_HEAD) and a
     * piston mid-move or a pushed block as MOVING_PISTON. All of these normalise to the
     * base material, so only the base itself may be counted.
     */
    @Test
    void testScanCountsExtendedPistonOnce() {
        Settings settings = mock(Settings.class);
        when(addon.getSettings()).thenReturn(settings);
        when(bll.getMaterialLimits(world, ISLAND_ID)).thenReturn(Map.of(Material.PISTON.getKey(), 30));
        when(bll.fixMaterial(any(BlockData.class))).thenAnswer(inv -> {
            Material m = ((BlockData) inv.getArgument(0)).getMaterial();
            return BlockLimitsListener.isPistonPart(m) ? Material.PISTON.getKey() : m.getKey();
        });

        BlockData air = blockData(Material.AIR);
        BlockData base1 = blockData(Material.PISTON);
        BlockData head = blockData(Material.PISTON_HEAD);
        BlockData base2 = blockData(Material.PISTON);
        BlockData movingHead = blockData(Material.MOVING_PISTON);
        BlockData pushed = blockData(Material.MOVING_PISTON);
        ChunkSnapshot snapshot = mock(ChunkSnapshot.class);
        when(snapshot.getBlockData(anyInt(), anyInt(), anyInt())).thenReturn(air);
        when(snapshot.getBlockData(0, 1, 0)).thenReturn(base1);
        when(snapshot.getBlockData(0, 2, 0)).thenReturn(head);
        when(snapshot.getBlockData(1, 1, 0)).thenReturn(base2);
        when(snapshot.getBlockData(1, 2, 0)).thenReturn(movingHead);
        when(snapshot.getBlockData(2, 1, 0)).thenReturn(pushed);

        Chunk c = chunk(true, true);
        when(c.getChunkSnapshot()).thenReturn(snapshot);
        when(c.getWorld()).thenReturn(world);
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(4);
        when(world.isChunkGenerated(anyInt(), anyInt())).thenReturn(true);
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean())).thenAnswer(inv -> CompletableFuture
                .completedFuture(inv.getArgument(0).equals(0) && inv.getArgument(1).equals(0) ? c : null));
        when(world.getChunkAt(anyInt(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(0).equals(0) && inv.getArgument(1).equals(0) ? c : null);
        when(world.getChunkAt(anyInt(), anyInt(), anyBoolean()))
                .thenAnswer(inv -> inv.getArgument(0).equals(0) && inv.getArgument(1).equals(0) ? c : null);

        RecountCalculator calc = new RecountCalculator(addon, island, new CompletableFuture<>(), false);
        CompletableFuture<Boolean> scan = calc.scanNextChunk();
        for (int i = 0; i < 200 && !scan.isDone(); i++) {
            MockBukkit.getMock().getScheduler().performOneTick();
            MockBukkit.getMock().getScheduler().waitAsyncTasksFinished();
        }
        assertTrue(scan.isDone(), "scan did not complete");

        NamespacedKey piston = Material.PISTON.getKey();
        assertEquals(2, calc.getResults().getBlockCount(Environment.NORMAL).count(piston),
                "two piston bases; head, moving head and pushed block are not pistons");
    }

    /** Give a mocked entity a real, empty persistent data container, as every Paper entity has. */
    private static <T extends Entity> T withPdc(T entity) {
        when(entity.getPersistentDataContainer()).thenReturn(new PersistentDataContainerMock());
        return entity;
    }
}

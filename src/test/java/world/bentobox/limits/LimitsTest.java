package world.bentobox.limits;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.logging.Logger;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.Settings;
import world.bentobox.bentobox.api.addons.AddonDescription;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.placeholders.PlaceholderReplacer;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.DatabaseSetup.DatabaseType;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.AddonsManager;
import world.bentobox.bentobox.managers.CommandsManager;
import world.bentobox.bentobox.managers.FlagsManager;
import world.bentobox.bentobox.managers.IslandWorldManager;
import world.bentobox.bentobox.managers.IslandsManager;
import world.bentobox.bentobox.managers.PlaceholdersManager;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import world.bentobox.limits.calculators.Pipeliner;

/**
 * @author tastybento
 *
 */
@SuppressWarnings("deprecation")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LimitsTest {
    private static File jFile;
    @Mock
    private User user;
    @Mock
    private IslandsManager im;
    @Mock
    private Island island;
    @Mock
    private BentoBox plugin;
    @Mock
    private FlagsManager fm;
    @Mock
    private GameModeAddon gameMode;
    @Mock
    private AddonsManager am;
    @Mock
    private Settings pluginSettings;
    @Mock
    private PlaceholdersManager phm;
    @Mock
    private CompositeCommand cmd;
    @Mock
    private CompositeCommand adminCmd;
    @Mock
    private World world;
    private UUID uuid;

    private Limits addon;

    private MockedStatic<BentoBox> mockedBentoBox;

    @BeforeAll
    static void beforeClass() throws Exception {
        cleanUp();
        // Make the addon jar
        jFile = new File("addon.jar");
        // Copy over config file from src folder
        Path fromPath = Paths.get("src/main/resources/config.yml");
        Path path = Paths.get("config.yml");
        Files.copy(fromPath, path);
        try (JarOutputStream tempJarOutputStream = new JarOutputStream(new FileOutputStream(jFile))) {
            //Added the new files to the jar.
            try (FileInputStream fis = new FileInputStream(path.toFile())) {
                byte[] buffer = new byte[1024];
                int bytesRead = 0;
                JarEntry entry = new JarEntry(path.toString());
                tempJarOutputStream.putNextEntry(entry);
                while((bytesRead = fis.read(buffer)) != -1) {
                    tempJarOutputStream.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    /**
     * @throws java.lang.Exception
     */
    @BeforeEach
    void setUp() {
        MockBukkit.mock();

        // Set up plugin
        mockedBentoBox = Mockito.mockStatic(BentoBox.class);
        mockedBentoBox.when(BentoBox::getInstance).thenReturn(plugin);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());

        // The database type has to be created one line before the thenReturn() to work!
        DatabaseType value = DatabaseType.JSON;
        when(plugin.getSettings()).thenReturn(pluginSettings);
        when(pluginSettings.getDatabaseType()).thenReturn(value);

        // Command manager
        CommandsManager cm = mock(CommandsManager.class);
        when(plugin.getCommandsManager()).thenReturn(cm);

        // Player
        Player p = mock(Player.class);
        // Sometimes use Mockito.withSettings().verboseLogging()
        when(user.isOp()).thenReturn(false);
        uuid = UUID.randomUUID();
        when(user.getUniqueId()).thenReturn(uuid);
        when(user.getPlayer()).thenReturn(p);
        when(user.getName()).thenReturn("tastybento");
        User.setPlugin(plugin);

        // Island World Manager
        IslandWorldManager iwm = mock(IslandWorldManager.class);
        when(plugin.getIWM()).thenReturn(iwm);



        // Player has island to begin with
        when(im.getIsland(Mockito.any(), Mockito.any(UUID.class))).thenReturn(island);
        when(plugin.getIslands()).thenReturn(im);

        // Locales
        // Return the reference (USE THIS IN THE FUTURE)
        when(user.getTranslation(Mockito.anyString())).thenAnswer((Answer<String>) invocation -> invocation.getArgument(0, String.class));

        // MockBukkit provides real implementations for server methods

        // Addon
        addon = new Limits();
        File dataFolder = new File("addons/Level");
        addon.setDataFolder(dataFolder);
        addon.setFile(jFile);
        AddonDescription desc = new AddonDescription.Builder("bentobox", "Level", "1.3").description("test").authors("tastybento").build();
        addon.setDescription(desc);

        // Addons manager
        when(plugin.getAddonsManager()).thenReturn(am);
        // One game mode
        when(am.getGameModeAddons()).thenReturn(Collections.singletonList(gameMode));
        AddonDescription desc2 = new AddonDescription.Builder("bentobox", "BSkyBlock", "1.3").description("test").authors("tasty").build();
        when(gameMode.getDescription()).thenReturn(desc2);
        when(gameMode.getOverWorld()).thenReturn(world);

        // Player command
        @NonNull
        Optional<CompositeCommand> opCmd = Optional.of(cmd);
        when(gameMode.getPlayerCommand()).thenReturn(opCmd);
        // Admin command
        Optional<CompositeCommand> opAdminCmd = Optional.of(adminCmd);
        when(gameMode.getAdminCommand()).thenReturn(opAdminCmd);

        // Perm prefix
        when(gameMode.getPermissionPrefix()).thenReturn("bskyblock.");

        // Flags manager
        when(plugin.getFlagsManager()).thenReturn(fm);
        when(fm.getFlags()).thenReturn(Collections.emptyList());

        // placeholders
        when(plugin.getPlaceholdersManager()).thenReturn(phm);

        // World
        when(world.getName()).thenReturn("bskyblock-world");
        // Island
        when(island.getWorld()).thenReturn(world);
        when(island.getOwner()).thenReturn(uuid);
    }

    /**
     * @throws java.lang.Exception
     */
    @AfterEach
    void tearDown() throws Exception {
        if (mockedBentoBox != null) {
            mockedBentoBox.close();
        }
        MockBukkit.unmock();
        User.clearUsers();
        Mockito.framework().clearInlineMocks();
        deleteAll(new File("database"));
        deleteAll(new File("addons"));
    }

    @AfterAll
    static void cleanUp() throws Exception {
        new File("addon.jar").delete();
        new File("config.yml").delete();
        deleteAll(new File("addons"));
    }

    private static void deleteAll(File file) throws IOException {
        if (file.exists()) {
            Files.walk(file.toPath())
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
        }
    }

    /**
     * Test method for {@link world.bentobox.limits.Limits#onEnable()}.
     */
    @Test
    void testOnEnable() {
        addon.onEnable();
        File f = new File("config.yml");
        assertTrue(f.exists());

    }

    /**
     * Block placeholders must be registered under the bare material key, e.g.
     * {@code bskyblock_island_spawner_limit}, not the namespaced form
     * {@code bskyblock_island_minecraft:spawner_limit} (issue #298).
     */
    @Test
    void testOnEnableRegistersBlockPlaceholdersWithoutNamespace() {
        addon.onEnable();
        ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
        Mockito.verify(phm, Mockito.atLeastOnce()).registerPlaceholder(Mockito.eq(addon), names.capture(),
                Mockito.any(PlaceholderReplacer.class));
        List<String> registered = names.getAllValues();
        assertTrue(registered.contains("bskyblock_island_spawner_count"), "spawner count placeholder");
        assertTrue(registered.contains("bskyblock_island_spawner_limit"), "spawner limit placeholder");
        assertTrue(registered.contains("bskyblock_island_spawner_base_limit"), "spawner base limit placeholder");
        assertTrue(registered.contains("bskyblock_island_spawner_nether_limit"), "env-scoped spawner limit placeholder");
        // Entity placeholders are unaffected
        assertTrue(registered.contains("bskyblock_island_zombie_limit"), "zombie limit placeholder");
        assertFalse(registered.stream().anyMatch(n -> n.contains(":")), "no placeholder name may contain a colon");
    }

    /**
     * Test method for {@link world.bentobox.limits.Limits#placeholderKey(NamespacedKey)}.
     */
    @Test
    void testPlaceholderKey() {
        assertEquals("spawner", Limits.placeholderKey(Material.SPAWNER.getKey()));
        assertEquals("itemsadder_ruby_block", Limits.placeholderKey(new NamespacedKey("itemsadder", "ruby_block")));
    }

    /**
     * Test method for {@link world.bentobox.limits.Limits#onDisable()}.
     */
    @Test
    void testOnDisable() {
        assertDoesNotThrow(() -> addon.onDisable());
    }

    /**
     * Test method for {@link world.bentobox.limits.Limits#getSettings()}.
     */
    @Test
    void testGetSettings() {
        assertNull(addon.getSettings());
        addon.onEnable();
        world.bentobox.limits.Settings set = addon.getSettings();
        assertFalse(set.getLimits(org.bukkit.World.Environment.NORMAL).isEmpty());
    }

    /**
     * Test method for {@link world.bentobox.limits.Limits#getGameModes()}.
     */
    @Test
    void testGetGameModes() {
        assertTrue(addon.getGameModes().isEmpty());
        addon.onEnable();
        assertFalse(addon.getGameModes().isEmpty());
    }

    /**
     * Test method for {@link world.bentobox.limits.Limits#getReachedLimits(world.bentobox.bentobox.api.user.User, world.bentobox.bentobox.api.addons.GameModeAddon, org.bukkit.World.Environment)}.
     */
    @Test
    void testGetReachedLimits() {
        addon.onEnable();
        when(gameMode.getIslands()).thenReturn(im);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(im.getIsland(Mockito.any(World.class), Mockito.any(world.bentobox.bentobox.api.user.User.class)))
                .thenReturn(island);
        when(island.getUniqueId()).thenReturn("unique_id");

        world.bentobox.limits.objects.IslandBlockCount ibc = new world.bentobox.limits.objects.IslandBlockCount(
                "unique_id", "BSkyBlock");
        // Default config: HOPPER block limit 10, ENDERMAN entity limit 5, CHICKEN 10
        for (int i = 0; i < 10; i++) {
            ibc.add(World.Environment.NORMAL, Material.HOPPER.getKey());
        }
        for (int i = 0; i < 5; i++) {
            ibc.incrementEntity(World.Environment.NORMAL, EntityType.ENDERMAN);
        }
        ibc.incrementEntity(World.Environment.NORMAL, EntityType.CHICKEN);
        addon.getBlockLimitListener().setIsland("unique_id", ibc);

        List<String> reached = addon.getReachedLimits(user, gameMode, World.Environment.NORMAL);
        assertTrue(reached.contains("Hopper"), reached.toString());
        assertTrue(reached.contains("Enderman"), reached.toString());
        assertFalse(reached.contains("Chicken"), reached.toString());

        // Union variant includes the same; nether alone has nothing reached
        assertTrue(addon.getReachedLimits(user, gameMode, null).contains("Hopper"));
        assertTrue(addon.getReachedLimits(user, gameMode, World.Environment.NETHER).isEmpty());
    }

    /**
     * Test method for {@link world.bentobox.limits.Limits#getReachedLimits(world.bentobox.bentobox.api.user.User, world.bentobox.bentobox.api.addons.GameModeAddon, org.bukkit.World.Environment)}.
     */
    @Test
    void testGetReachedLimitsNoIsland() {
        addon.onEnable();
        when(gameMode.getIslands()).thenReturn(im);
        when(im.getIsland(Mockito.any(World.class), Mockito.any(world.bentobox.bentobox.api.user.User.class)))
                .thenReturn(null);
        assertTrue(addon.getReachedLimits(user, gameMode, null).isEmpty());
    }

    /**
     * Test method for {@link world.bentobox.limits.Limits#getBlockLimitListener()}.
     */
    @Test
    void testGetBlockLimitListener() {
        assertNull(addon.getBlockLimitListener());
        addon.onEnable();
        assertNotNull(addon.getBlockLimitListener());
    }

    /**
     * Test method for {@link world.bentobox.limits.Limits#inGameModeWorld(org.bukkit.World)}.
     */
    @Test
    void testInGameModeWorld() {
        addon.onEnable();
        assertFalse(addon.inGameModeWorld(world));
        when(gameMode.inWorld(world)).thenReturn(true);
        assertTrue(addon.inGameModeWorld(world));
    }

    /**
     * Test method for {@link world.bentobox.limits.Limits#getGameModeName(org.bukkit.World)}.
     */
    @Test
    void testGetGameModeName() {
        when(gameMode.inWorld(world)).thenReturn(true);
        assertTrue(addon.getGameModeName(world).isEmpty());
        addon.onEnable();
        assertEquals("BSkyBlock", addon.getGameModeName(world));
    }

    /**
     * Test method for {@link world.bentobox.limits.Limits#getGameModePermPrefix(org.bukkit.World)}.
     */
    @Test
    void testGetGameModePermPrefix() {
        when(gameMode.inWorld(world)).thenReturn(true);
        addon.onEnable();
        assertEquals("bskyblock.", addon.getGameModePermPrefix(world));
    }

    /**
     * Test method for {@link world.bentobox.limits.Limits#isCoveredGameMode(java.lang.String)}.
     */
    @Test
    void testIsCoveredGameMode() {
        assertFalse(addon.isCoveredGameMode("BSkyBlock"));
        addon.onEnable();
        assertTrue(addon.isCoveredGameMode("BSkyBlock"));
    }

    /**
     * Test method for {@link world.bentobox.limits.Limits#getJoinListener()}.
     */
    @Test
    void testGetJoinListener() {
        assertNull(addon.getJoinListener());
        addon.onEnable();
        assertNotNull(addon.getJoinListener());
    }

    /* =========================================================================
     * Automatic entity recount: throttle and periodic sweep
     * ========================================================================= */

    private static final String ISLAND_ONE = "island-1";
    private static final String ISLAND_TWO = "island-2";
    private static final String RECOUNT_ON_JOIN = "recount-on-join";
    private static final String RECOUNT_COOLDOWN = "recount-on-join-cooldown";
    private static final String RECOUNT_PERIODIC = "recount-periodic";
    private static final String RECOUNT_INTERVAL = "recount-periodic-interval";
    private static final String RECOUNT_BATCH = "recount-periodic-batch";
    private static final String RECOUNT_MAX_CHUNKS = "recount-max-chunks";

    /**
     * Enable the addon with config overrides applied on top of the bundled config.yml.
     * {@code saveDefaultConfig()} does not overwrite an existing file, so writing the
     * config into the data folder first is enough for {@link Settings} to pick it up.
     * The shared {@link Pipeliner} is replaced by a mock so tests can verify what was
     * queued without running a real recount.
     *
     * @return the mocked pipeliner the addon queues recounts on
     */
    private Pipeliner enableWith(Map<String, Object> overrides) throws IOException {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        overrides.forEach(cfg::set);
        File dataFolder = addon.getDataFolder();
        assertTrue(dataFolder.mkdirs() || dataFolder.isDirectory());
        cfg.save(new File(dataFolder, "config.yml"));
        try (MockedConstruction<Pipeliner> mocked = Mockito.mockConstruction(Pipeliner.class)) {
            addon.onEnable();
            assertEquals(1, mocked.constructed().size(), "addon must create exactly one shared pipeliner");
            return mocked.constructed().get(0);
        }
    }

    private Pipeliner enableWith(String key, Object value) throws IOException {
        Map<String, Object> m = new HashMap<>();
        m.put(key, value);
        return enableWith(m);
    }

    /**
     * Put a player online whose island(s) are the given ones. The join listener is
     * unregistered first so the sweep tests exercise only the periodic path.
     */
    private PlayerMock online(Island... islands) {
        HandlerList.unregisterAll(addon.getJoinListener());
        PlayerMock p = MockBukkit.getMock().addPlayer();
        when(im.getIslands(world, p.getUniqueId())).thenReturn(List.of(islands));
        return p;
    }

    private Island mockIsland(String id) {
        Island i = mock(Island.class);
        when(i.getUniqueId()).thenReturn(id);
        when(i.getWorld()).thenReturn(world);
        return i;
    }

    /** Advance the scheduler by one full periodic sweep interval. */
    private void tickOneSweep() {
        MockBukkit.getMock().getScheduler().performTicks(addon.getSettings().getRecountPeriodicInterval() * 20L);
    }

    @Test
    void testMaybeRecountIslandQueuesEntityOnlyRecount() throws IOException {
        Pipeliner pipeliner = enableWith(RECOUNT_ON_JOIN, true);
        assertSame(pipeliner, addon.getPipeliner());
        Island one = mockIsland(ISLAND_ONE);

        addon.maybeRecountIsland(one);

        verify(pipeliner).addIslandEntitiesOnly(one);
        verify(pipeliner, never()).addIsland(Mockito.any());
    }

    @Test
    void testMaybeRecountIslandDisabledByConfig() throws IOException {
        Pipeliner pipeliner = enableWith(RECOUNT_ON_JOIN, false);

        addon.maybeRecountIsland(mockIsland(ISLAND_ONE));

        verify(pipeliner, never()).addIslandEntitiesOnly(Mockito.any());
    }

    @Test
    void testMaybeRecountIslandThrottledWithinCooldown() throws IOException {
        Pipeliner pipeliner = enableWith(Map.of(RECOUNT_ON_JOIN, true, RECOUNT_COOLDOWN, 300));
        Island one = mockIsland(ISLAND_ONE);

        addon.maybeRecountIsland(one);
        addon.maybeRecountIsland(one);
        addon.maybeRecountIsland(one);

        verify(pipeliner, times(1)).addIslandEntitiesOnly(one);
    }

    @Test
    void testMaybeRecountIslandRecountsAgainAfterCooldown() throws IOException {
        Pipeliner pipeliner = enableWith(Map.of(RECOUNT_ON_JOIN, true, RECOUNT_COOLDOWN, 0));
        Island one = mockIsland(ISLAND_ONE);

        addon.maybeRecountIsland(one);
        addon.maybeRecountIsland(one);

        verify(pipeliner, times(2)).addIslandEntitiesOnly(one);
    }

    @Test
    void testMaybeRecountIslandCooldownIsPerIsland() throws IOException {
        Pipeliner pipeliner = enableWith(Map.of(RECOUNT_ON_JOIN, true, RECOUNT_COOLDOWN, 300));
        Island one = mockIsland(ISLAND_ONE);
        Island two = mockIsland(ISLAND_TWO);

        addon.maybeRecountIsland(one);
        addon.maybeRecountIsland(two);
        addon.maybeRecountIsland(one);
        addon.maybeRecountIsland(two);

        verify(pipeliner, times(1)).addIslandEntitiesOnly(one);
        verify(pipeliner, times(1)).addIslandEntitiesOnly(two);
    }

    @Test
    void testPeriodicSweepQueuesOnlineIslandAfterInterval() throws IOException {
        Pipeliner pipeliner = enableWith(Map.of(RECOUNT_PERIODIC, true, RECOUNT_ON_JOIN, true, RECOUNT_INTERVAL, 1, RECOUNT_COOLDOWN, 0));
        Island one = mockIsland(ISLAND_ONE);
        online(one);

        // Nothing fires before the first interval has elapsed
        MockBukkit.getMock().getScheduler().performTicks(19L);
        verify(pipeliner, never()).addIslandEntitiesOnly(Mockito.any());

        MockBukkit.getMock().getScheduler().performOneTick();
        verify(pipeliner, times(1)).addIslandEntitiesOnly(one);

        // ...and keeps sweeping every interval
        tickOneSweep();
        verify(pipeliner, times(2)).addIslandEntitiesOnly(one);
    }

    @Test
    void testPeriodicSweepDisabledByConfig() throws IOException {
        Pipeliner pipeliner = enableWith(Map.of(RECOUNT_PERIODIC, false, RECOUNT_INTERVAL, 1, RECOUNT_COOLDOWN, 0));
        online(mockIsland(ISLAND_ONE));

        tickOneSweep();
        tickOneSweep();

        verify(pipeliner, never()).addIslandEntitiesOnly(Mockito.any());
    }

    @Test
    void testPeriodicSweepIgnoresOfflineIslands() throws IOException {
        Pipeliner pipeliner = enableWith(Map.of(RECOUNT_PERIODIC, true, RECOUNT_ON_JOIN, true, RECOUNT_INTERVAL, 1, RECOUNT_COOLDOWN, 0));
        // No players online at all
        HandlerList.unregisterAll(addon.getJoinListener());

        tickOneSweep();

        verify(pipeliner, never()).addIslandEntitiesOnly(Mockito.any());
        verify(im, never()).getIslands(Mockito.any(World.class), Mockito.any(UUID.class));
    }

    @Test
    void testPeriodicSweepHonoursBatchSize() throws IOException {
        Pipeliner pipeliner = enableWith(Map.of(RECOUNT_PERIODIC, true, RECOUNT_ON_JOIN, true, RECOUNT_INTERVAL, 1, RECOUNT_COOLDOWN, 0, RECOUNT_BATCH, 1));
        Island one = mockIsland(ISLAND_ONE);
        Island two = mockIsland(ISLAND_TWO);
        online(one);
        online(two);

        tickOneSweep();

        verify(pipeliner, times(1)).addIslandEntitiesOnly(Mockito.any());
    }

    @Test
    void testPeriodicSweepBatchZeroDoesNothing() throws IOException {
        Pipeliner pipeliner = enableWith(Map.of(RECOUNT_PERIODIC, true, RECOUNT_ON_JOIN, true, RECOUNT_INTERVAL, 1, RECOUNT_COOLDOWN, 0, RECOUNT_BATCH, 0));
        online(mockIsland(ISLAND_ONE));

        tickOneSweep();

        verify(pipeliner, never()).addIslandEntitiesOnly(Mockito.any());
    }

    @Test
    void testPeriodicSweepPrefersMostStaleIsland() throws IOException {
        Pipeliner pipeliner = enableWith(Map.of(RECOUNT_PERIODIC, true, RECOUNT_ON_JOIN, true, RECOUNT_INTERVAL, 1, RECOUNT_COOLDOWN, 0, RECOUNT_BATCH, 1));
        Island one = mockIsland(ISLAND_ONE);
        Island two = mockIsland(ISLAND_TWO);
        online(one);
        online(two);

        // Island one was just reconciled on join, so the sweep must pick island two first
        addon.maybeRecountIsland(one);
        verify(pipeliner, times(1)).addIslandEntitiesOnly(one);

        tickOneSweep();

        verify(pipeliner, times(1)).addIslandEntitiesOnly(two);
        verify(pipeliner, times(1)).addIslandEntitiesOnly(one);

        // Next cycle rotates back to island one, now the stalest
        tickOneSweep();
        verify(pipeliner, times(2)).addIslandEntitiesOnly(one);
        verify(pipeliner, times(1)).addIslandEntitiesOnly(two);
    }

    @Test
    void testPeriodicSweepSkipsDeletedAndUnownedIslands() throws IOException {
        Pipeliner pipeliner = enableWith(Map.of(RECOUNT_PERIODIC, true, RECOUNT_ON_JOIN, true, RECOUNT_INTERVAL, 1, RECOUNT_COOLDOWN, 0));
        Island deleted = mockIsland("deleted");
        when(deleted.isDeleted()).thenReturn(true);
        Island unowned = mockIsland("unowned");
        when(unowned.isUnowned()).thenReturn(true);
        Island live = mockIsland(ISLAND_ONE);
        online(deleted, unowned, live);

        tickOneSweep();

        verify(pipeliner, times(1)).addIslandEntitiesOnly(live);
        verify(pipeliner, never()).addIslandEntitiesOnly(deleted);
        verify(pipeliner, never()).addIslandEntitiesOnly(unowned);
    }

    @Test
    void testPeriodicSweepDedupesIslandSharedByOnlinePlayers() throws IOException {
        Pipeliner pipeliner = enableWith(Map.of(RECOUNT_PERIODIC, true, RECOUNT_ON_JOIN, true, RECOUNT_INTERVAL, 1, RECOUNT_COOLDOWN, 0, RECOUNT_BATCH, 5));
        Island shared = mockIsland(ISLAND_ONE);
        online(shared);
        online(shared);

        tickOneSweep();

        verify(pipeliner, times(1)).addIslandEntitiesOnly(shared);
    }

    @Test
    void testPeriodicSweepSharesCooldownWithJoinRecount() throws IOException {
        Pipeliner pipeliner = enableWith(Map.of(RECOUNT_PERIODIC, true, RECOUNT_ON_JOIN, true, RECOUNT_INTERVAL, 1, RECOUNT_COOLDOWN, 300));
        Island one = mockIsland(ISLAND_ONE);
        online(one);

        addon.maybeRecountIsland(one);
        tickOneSweep();
        tickOneSweep();

        verify(pipeliner, times(1)).addIslandEntitiesOnly(one);
    }

    @Test
    void testOnDisableStopsPipelinerAndCancelsSweep() throws IOException {
        Pipeliner pipeliner = enableWith(Map.of(RECOUNT_PERIODIC, true, RECOUNT_ON_JOIN, true, RECOUNT_INTERVAL, 1, RECOUNT_COOLDOWN, 0));
        online(mockIsland(ISLAND_ONE));

        addon.onDisable();
        tickOneSweep();

        verify(pipeliner).stop();
        verify(pipeliner, never()).addIslandEntitiesOnly(Mockito.any());
    }

    @Test
    void testMaybeRecountIslandSkipsOversizedIslandAndWarnsOnce() throws IOException {
        Pipeliner pipeliner = enableWith(Map.of(RECOUNT_ON_JOIN, true, RECOUNT_COOLDOWN, 0));
        Island big = mockIsland(ISLAND_ONE);
        when(big.getProtectionRange()).thenReturn(500); // 4,096 chunks per world

        addon.maybeRecountIsland(big);
        addon.maybeRecountIsland(big);

        verify(pipeliner, never()).addIslandEntitiesOnly(any());
        verify(plugin, times(1)).logWarning(contains("recount-max-chunks"));
    }

    @Test
    void testMaybeRecountIslandMaxChunksIsConfigurable() throws IOException {
        Pipeliner pipeliner = enableWith(Map.of(RECOUNT_ON_JOIN, true, RECOUNT_MAX_CHUNKS, 5000));
        Island big = mockIsland(ISLAND_ONE);
        when(big.getProtectionRange()).thenReturn(500);

        addon.maybeRecountIsland(big);

        verify(pipeliner).addIslandEntitiesOnly(big);
    }

    @Test
    void testPeriodicSweepSkipsOversizedIslands() throws IOException {
        Pipeliner pipeliner = enableWith(Map.of(RECOUNT_PERIODIC, true, RECOUNT_INTERVAL, 1, RECOUNT_COOLDOWN, 0));
        Island big = mockIsland(ISLAND_ONE);
        when(big.getProtectionRange()).thenReturn(500);
        Island small = mockIsland(ISLAND_TWO);
        online(big, small);

        tickOneSweep();

        verify(pipeliner).addIslandEntitiesOnly(small);
        verify(pipeliner, never()).addIslandEntitiesOnly(big);
    }
}

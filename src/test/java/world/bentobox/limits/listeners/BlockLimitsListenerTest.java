package world.bentobox.limits.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.TechnicalPiston;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.ExplosionResult;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.Settings;
import world.bentobox.bentobox.api.events.island.IslandDeleteEvent;
import world.bentobox.bentobox.api.user.Notifier;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.managers.LocalesManager;
import world.bentobox.bentobox.managers.PlaceholdersManager;
import world.bentobox.bentobox.database.Database;
import world.bentobox.bentobox.database.DatabaseSetup.DatabaseType;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandsManager;
import world.bentobox.limits.Limits;
import org.mockbukkit.mockbukkit.MockBukkit;
import world.bentobox.limits.objects.IslandBlockCount;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class BlockLimitsListenerTest {

    @Mock
    private Limits addon;

    @Mock
    private World world;

    @Mock
    private BentoBox plugin;

    @Mock
    private Settings pluginSettings;

    @Mock
    private IslandsManager islandsManager;

    @Mock
    private Island island;

    @Mock
    private Player player;

    private BlockLimitsListener listener;
    private MockedConstruction<Database> mockedDb;
    private MockedStatic<BentoBox> mockedBentoBox;
    private FileConfiguration config;
    private Location blockLocation;
    private Location centerLocation;

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() throws Exception {
        MockBukkit.mock();

        // Set up BentoBox static mock so Database class can initialize
        mockedBentoBox = Mockito.mockStatic(BentoBox.class);
        mockedBentoBox.when(BentoBox::getInstance).thenReturn(plugin);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        DatabaseType value = DatabaseType.JSON;
        when(plugin.getSettings()).thenReturn(pluginSettings);
        when(pluginSettings.getDatabaseType()).thenReturn(value);

        config = new YamlConfiguration();
        config.load("src/main/resources/config.yml");
        when(addon.getConfig()).thenReturn(config);
        doAnswer(invocation -> null).when(addon).log(anyString());
        doAnswer(invocation -> null).when(addon).logError(anyString());
        when(addon.isCoveredGameMode(anyString())).thenReturn(true);
        when(addon.inGameModeWorld(any(World.class))).thenReturn(false);
        // Override to true for our test world so event handlers don't bail early
        when(addon.inGameModeWorld(world)).thenReturn(true);

        // Islands manager
        when(addon.getIslands()).thenReturn(islandsManager);
        when(islandsManager.getIslandAt(any(Location.class))).thenReturn(Optional.of(island));
        when(island.getUniqueId()).thenReturn("test-island-id");

        // Block and center locations (distinct so center-block check doesn't skip)
        blockLocation = new Location(world, 100, 65, 100);
        centerLocation = new Location(world, 0, 65, 0);
        when(island.getCenter()).thenReturn(centerLocation);

        when(addon.getGameModeName(world)).thenReturn("BSkyBlock");

        // User and locales setup for event notification
        User.setPlugin(plugin);
        LocalesManager localesManager = mock(LocalesManager.class);
        when(localesManager.get(any(), anyString())).thenReturn("limit hit");
        when(plugin.getLocalesManager()).thenReturn(localesManager);
        PlaceholdersManager placeholdersManager = mock(PlaceholdersManager.class);
        when(placeholdersManager.replacePlaceholders(any(Player.class), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(plugin.getPlaceholdersManager()).thenReturn(placeholdersManager);
        Notifier notifier = mock(Notifier.class);
        when(plugin.getNotifier()).thenReturn(notifier);

        mockedDb = Mockito.mockConstruction(Database.class, (mock, context) -> {
            when(mock.loadObjects()).thenReturn(Collections.emptyList());
        });

        listener = new BlockLimitsListener(addon);
    }

    @AfterEach
    public void tearDown() {
        User.clearUsers();
        if (mockedDb != null) {
            mockedDb.close();
        }
        if (mockedBentoBox != null) {
            mockedBentoBox.close();
        }
        MockBukkit.unmock();
    }

    // --- fixMaterial tests ---

    @Test
    public void testFixMaterialChippedAnvil() {
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.CHIPPED_ANVIL);
        assertEquals(Material.ANVIL.getKey(), listener.fixMaterial(blockData));
    }

    @Test
    public void testFixMaterialDamagedAnvil() {
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.DAMAGED_ANVIL);
        assertEquals(Material.ANVIL.getKey(), listener.fixMaterial(blockData));
    }

    @Test
    public void testFixMaterialRedstoneWallTorch() {
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.REDSTONE_WALL_TORCH);
        assertEquals(Material.REDSTONE_TORCH.getKey(), listener.fixMaterial(blockData));
    }

    @Test
    public void testFixMaterialWallTorch() {
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.WALL_TORCH);
        assertEquals(Material.TORCH.getKey(), listener.fixMaterial(blockData));
    }

    @Test
    public void testFixMaterialZombieWallHead() {
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.ZOMBIE_WALL_HEAD);
        assertEquals(Material.ZOMBIE_HEAD.getKey(), listener.fixMaterial(blockData));
    }

    @Test
    public void testFixMaterialBambooSapling() {
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.BAMBOO_SAPLING);
        assertEquals(Material.BAMBOO.getKey(), listener.fixMaterial(blockData));
    }

    @Test
    public void testFixMaterialPistonHeadNormal() {
        TechnicalPiston tp = mock(TechnicalPiston.class);
        when(tp.getMaterial()).thenReturn(Material.PISTON_HEAD);
        when(tp.getType()).thenReturn(TechnicalPiston.Type.NORMAL);
        assertEquals(Material.PISTON.getKey(), listener.fixMaterial(tp));
    }

    @Test
    public void testFixMaterialPistonHeadSticky() {
        TechnicalPiston tp = mock(TechnicalPiston.class);
        when(tp.getMaterial()).thenReturn(Material.PISTON_HEAD);
        when(tp.getType()).thenReturn(TechnicalPiston.Type.STICKY);
        assertEquals(Material.STICKY_PISTON.getKey(), listener.fixMaterial(tp));
    }

    @Test
    public void testFixMaterialRegularStone() {
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.STONE);
        assertEquals(Material.STONE.getKey(), listener.fixMaterial(blockData));
    }

    @Test
    public void testFixMaterialCopperWallTorch() {
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.COPPER_WALL_TORCH);
        assertEquals(Material.COPPER_TORCH.getKey(), listener.fixMaterial(blockData));
    }

    @Test
    public void testFixMaterialExposedCopperChest() {
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.EXPOSED_COPPER_CHEST);
        assertEquals(Material.COPPER_CHEST.getKey(), listener.fixMaterial(blockData));
    }

    @Test
    public void testFixMaterialWeatheredCopperChest() {
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.WEATHERED_COPPER_CHEST);
        assertEquals(Material.COPPER_CHEST.getKey(), listener.fixMaterial(blockData));
    }

    @Test
    public void testFixMaterialOxidizedCopperChest() {
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.OXIDIZED_COPPER_CHEST);
        assertEquals(Material.COPPER_CHEST.getKey(), listener.fixMaterial(blockData));
    }

    @Test
    public void testFixMaterialWaxedCopperChest() {
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.WAXED_COPPER_CHEST);
        assertEquals(Material.COPPER_CHEST.getKey(), listener.fixMaterial(blockData));
    }

    @Test
    public void testFixMaterialWaxedExposedCopperChest() {
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.WAXED_EXPOSED_COPPER_CHEST);
        assertEquals(Material.COPPER_CHEST.getKey(), listener.fixMaterial(blockData));
    }

    @Test
    public void testFixMaterialWaxedWeatheredCopperChest() {
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.WAXED_WEATHERED_COPPER_CHEST);
        assertEquals(Material.COPPER_CHEST.getKey(), listener.fixMaterial(blockData));
    }

    @Test
    public void testFixMaterialWaxedOxidizedCopperChest() {
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.WAXED_OXIDIZED_COPPER_CHEST);
        assertEquals(Material.COPPER_CHEST.getKey(), listener.fixMaterial(blockData));
    }

    @Test
    public void testFixMaterialCopperChestUnchanged() {
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(Material.COPPER_CHEST);
        assertEquals(Material.COPPER_CHEST.getKey(), listener.fixMaterial(blockData));
    }

    // --- setIsland / getIsland tests ---

    @Test
    public void testSetAndGetIsland() {
        String islandId = "test-island-123";
        IslandBlockCount ibc = new IslandBlockCount(islandId, "BSkyBlock");
        listener.setIsland(islandId, ibc);
        IslandBlockCount result = listener.getIsland(islandId);
        assertNotNull(result);
        assertEquals(islandId, result.getUniqueId());
    }

    @Test
    public void testGetIslandUnknownIdReturnsNull() {
        assertNull(listener.getIsland("nonexistent-island-id"));
    }

    // --- getMaterialLimits tests ---

    @Test
    public void testGetMaterialLimitsDefaultOnly() {
        Map<NamespacedKey, Integer> limits = listener.getMaterialLimits(world, "some-island");
        assertNotNull(limits);
        // The default config has HOPPER: 10 in blocklimits
        assertTrue(limits.containsKey(Material.HOPPER.getKey()));
        assertEquals(10, limits.get(Material.HOPPER.getKey()));
    }

    @Test
    public void testGetMaterialLimitsIslandOverridesDefault() {
        String islandId = "island-override-test";
        IslandBlockCount ibc = new IslandBlockCount(islandId, "BSkyBlock");
        ibc.setBlockLimit(Material.HOPPER.getKey(), 25);
        listener.setIsland(islandId, ibc);

        Map<NamespacedKey, Integer> limits = listener.getMaterialLimits(world, islandId);
        // Island-specific limit should override the default
        assertEquals(25, limits.get(Material.HOPPER.getKey()));
    }

    // --- helper methods ---

    private Block mockBlock(Material material, Location location) {
        Block block = mock(Block.class);
        BlockData blockData = mock(BlockData.class);
        when(block.getType()).thenReturn(material);
        when(block.getLocation()).thenReturn(location);
        when(block.getWorld()).thenReturn(world);
        when(block.getBlockData()).thenReturn(blockData);
        when(blockData.getMaterial()).thenReturn(material);
        when(block.hasMetadata("blockbreakevent-ignore")).thenReturn(false);
        // Stub getRelative for all faces to return an AIR block (avoids NPE in handleBreak)
        Block airBlock = mock(Block.class);
        when(airBlock.getType()).thenReturn(Material.AIR);
        when(block.getRelative(any(BlockFace.class))).thenReturn(airBlock);
        return block;
    }

    // --- BlockPlaceEvent tests ---

    @Test
    public void testBlockPlaceIncrementsCount() {
        Block block = mockBlock(Material.STONE, blockLocation);
        BlockState replacedState = mock(BlockState.class);
        BlockPlaceEvent event = new BlockPlaceEvent(block, replacedState, block, new ItemStack(Material.STONE), player, true, EquipmentSlot.HAND);

        listener.onBlock(event);

        IslandBlockCount ibc = listener.getIsland("test-island-id");
        assertNotNull(ibc);
        assertEquals(1, ibc.getBlockCount(Material.STONE.getKey()));
    }

    @Test
    public void testBlockPlaceAtLimitCancelsEvent() {
        // Pre-populate island with 10 HOPPERs (default config limit is 10)
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        for (int i = 0; i < 10; i++) {
            ibc.add(Material.HOPPER.getKey());
        }
        listener.setIsland("test-island-id", ibc);

        Block block = mockBlock(Material.HOPPER, blockLocation);
        BlockState replacedState = mock(BlockState.class);
        BlockPlaceEvent event = new BlockPlaceEvent(block, replacedState, block, new ItemStack(Material.HOPPER), player, true, EquipmentSlot.HAND);

        listener.onBlock(event);

        assertTrue(event.isCancelled());
    }

    @Test
    public void testBlockPlaceUnlimitedMaterialAllowed() {
        Block block = mockBlock(Material.DIRT, blockLocation);
        BlockState replacedState = mock(BlockState.class);
        BlockPlaceEvent event = new BlockPlaceEvent(block, replacedState, block, new ItemStack(Material.DIRT), player, true, EquipmentSlot.HAND);

        listener.onBlock(event);

        assertFalse(event.isCancelled());
    }

    @Test
    public void testBlockPlaceDoNotCountWaterBlock() {
        Block block = mockBlock(Material.WATER, blockLocation);
        BlockState replacedState = mock(BlockState.class);
        BlockPlaceEvent event = new BlockPlaceEvent(block, replacedState, block, new ItemStack(Material.WATER_BUCKET), player, true, EquipmentSlot.HAND);

        listener.onBlock(event);

        // WATER is in the DO_NOT_COUNT list, so no island count should be created
        assertNull(listener.getIsland("test-island-id"));
    }

    @Test
    public void testBlockPlaceOutsideGameModeWorldIgnored() {
        when(addon.inGameModeWorld(world)).thenReturn(false);

        Block block = mockBlock(Material.STONE, blockLocation);
        BlockState replacedState = mock(BlockState.class);
        BlockPlaceEvent event = new BlockPlaceEvent(block, replacedState, block, new ItemStack(Material.STONE), player, true, EquipmentSlot.HAND);

        listener.onBlock(event);

        assertNull(listener.getIsland("test-island-id"));
    }

    // --- BlockBreakEvent tests ---

    @Test
    public void testBlockBreakDecrementsCount() {
        // Pre-populate with 3 STONE
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.add(Material.STONE.getKey());
        ibc.add(Material.STONE.getKey());
        ibc.add(Material.STONE.getKey());
        listener.setIsland("test-island-id", ibc);

        Block block = mockBlock(Material.STONE, blockLocation);
        BlockBreakEvent event = new BlockBreakEvent(block, player);

        listener.onBlock(event);

        assertEquals(2, listener.getIsland("test-island-id").getBlockCount(Material.STONE.getKey()));
    }

    @Test
    public void testBlockBreakCountNeverGoesNegative() {
        // Start with 0 STONE (no pre-population)
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        listener.setIsland("test-island-id", ibc);

        Block block = mockBlock(Material.STONE, blockLocation);
        BlockBreakEvent event = new BlockBreakEvent(block, player);

        listener.onBlock(event);

        assertEquals(0, listener.getIsland("test-island-id").getBlockCount(Material.STONE.getKey()));
    }

    @Test
    public void testBlockBreakWithMetadataIgnoreFlagSkipped() {
        // Pre-populate with 1 STONE
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.add(Material.STONE.getKey());
        listener.setIsland("test-island-id", ibc);

        Block block = mockBlock(Material.STONE, blockLocation);
        when(block.hasMetadata("blockbreakevent-ignore")).thenReturn(true);
        BlockBreakEvent event = new BlockBreakEvent(block, player);

        listener.onBlock(event);

        // Count should remain unchanged because the metadata flag caused the handler to skip
        assertEquals(1, listener.getIsland("test-island-id").getBlockCount(Material.STONE.getKey()));
    }

    // --- BlockMultiPlaceEvent tests ---

    @Test
    public void testBlockMultiPlaceAtLimitCancels() {
        // Set limit for OAK_PLANKS to 1 via island-specific limit
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.setBlockLimit(Material.OAK_PLANKS.getKey(), 1);
        ibc.add(Material.OAK_PLANKS.getKey());
        listener.setIsland("test-island-id", ibc);

        Block block = mockBlock(Material.OAK_PLANKS, blockLocation);
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(block);
        List<BlockState> states = List.of(state);
        Block clicked = mockBlock(Material.DIRT, blockLocation);
        BlockMultiPlaceEvent event = new BlockMultiPlaceEvent(states, clicked, new ItemStack(Material.OAK_PLANKS), player, true);

        listener.onBlock(event);

        assertTrue(event.isCancelled());
    }

    // --- PlayerInteractEvent tests ---

    @Test
    public void testTurtleEggNonPhysicalActionIgnored() {
        Block block = mockBlock(Material.TURTLE_EGG, blockLocation);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, null, block, BlockFace.UP);

        listener.onTurtleEggBreak(event);

        // Non-PHYSICAL action should be ignored — no island count should be created
        assertNull(listener.getIsland("test-island-id"));
    }

    // --- BlockBurnEvent / BlockFadeEvent / LeavesDecayEvent tests ---

    @Test
    public void testBlockBurnDecrementsCount() {
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.add(Material.OAK_PLANKS.getKey());
        ibc.add(Material.OAK_PLANKS.getKey());
        listener.setIsland("test-island-id", ibc);

        Block block = mockBlock(Material.OAK_PLANKS, blockLocation);
        BlockBurnEvent event = new BlockBurnEvent(block, null);

        listener.onBlock(event);

        assertEquals(1, listener.getIsland("test-island-id").getBlockCount(Material.OAK_PLANKS.getKey()));
    }

    @Test
    public void testBlockFadeDecrementsCount() {
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.add(Material.SAND.getKey());
        ibc.add(Material.SAND.getKey());
        listener.setIsland("test-island-id", ibc);

        Block block = mockBlock(Material.SAND, blockLocation);
        BlockState newState = mock(BlockState.class);
        BlockFadeEvent event = new BlockFadeEvent(block, newState);

        listener.onBlock(event);

        assertEquals(1, listener.getIsland("test-island-id").getBlockCount(Material.SAND.getKey()));
    }

    @Test
    public void testLeavesDecayDecrementsCount() {
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.add(Material.OAK_LEAVES.getKey());
        ibc.add(Material.OAK_LEAVES.getKey());
        listener.setIsland("test-island-id", ibc);

        Block block = mockBlock(Material.OAK_LEAVES, blockLocation);
        LeavesDecayEvent event = new LeavesDecayEvent(block);

        listener.onBlock(event);

        assertEquals(1, listener.getIsland("test-island-id").getBlockCount(Material.OAK_LEAVES.getKey()));
    }

    // --- BlockSpreadEvent tests ---

    @Test
    public void testBlockSpreadIncrementsCount() {
        Block block = mockBlock(Material.GRASS_BLOCK, blockLocation);
        Block source = mockBlock(Material.GRASS_BLOCK, new Location(world, 101, 65, 100));
        BlockState newState = mock(BlockState.class);
        BlockSpreadEvent event = new BlockSpreadEvent(block, source, newState);

        listener.onBlock(event);

        IslandBlockCount ibc = listener.getIsland("test-island-id");
        assertNotNull(ibc);
        assertEquals(1, ibc.getBlockCount(Material.GRASS_BLOCK.getKey()));
    }

    // --- BlockFromToEvent tests ---

    @Test
    public void testBlockFromToNonLiquidIgnored() {
        Block sourceBlock = mockBlock(Material.STONE, blockLocation);
        when(sourceBlock.isLiquid()).thenReturn(false);
        Block toBlock = mockBlock(Material.REDSTONE_WIRE, new Location(world, 101, 65, 100));
        BlockFromToEvent event = new BlockFromToEvent(sourceBlock, toBlock);

        listener.onBlock(event);

        // Non-liquid source means handler skips — no island count should be created
        assertNull(listener.getIsland("test-island-id"));
    }

    // --- BlockFormEvent / EntityBlockFormEvent tests ---

    @Test
    public void testBlockFormIgnoresEntityBlockFormEvent() {
        Block block = mockBlock(Material.STONE, blockLocation);
        BlockState newState = mock(BlockState.class);
        Entity entity = mock(Entity.class);
        EntityBlockFormEvent event = new EntityBlockFormEvent(entity, block, newState);

        // Call the BlockFormEvent handler — it should return early for EntityBlockFormEvent
        listener.onBlock((BlockFormEvent) event);

        // No island count should be created since the handler skipped processing
        verify(islandsManager, never()).getIslandAt(any(Location.class));
    }

    // --- Block cascade tests ---

    @Test
    public void testBlockBreakSugarCaneCascade() {
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.add(Material.SUGAR_CANE.getKey());
        ibc.add(Material.SUGAR_CANE.getKey());
        ibc.add(Material.SUGAR_CANE.getKey());
        listener.setIsland("test-island-id", ibc);

        when(world.getMaxHeight()).thenReturn(320);

        Block bottomBlock = mockBlock(Material.SUGAR_CANE, new Location(world, 100, 65, 100));
        when(bottomBlock.getY()).thenReturn(65);
        Block midBlock = mockBlock(Material.SUGAR_CANE, new Location(world, 100, 66, 100));
        when(midBlock.getY()).thenReturn(66);
        Block topBlock = mockBlock(Material.SUGAR_CANE, new Location(world, 100, 67, 100));
        when(topBlock.getY()).thenReturn(67);

        // Wire the chain: bottom → mid → top → air (default from mockBlock)
        when(bottomBlock.getRelative(BlockFace.UP)).thenReturn(midBlock);
        when(midBlock.getRelative(BlockFace.UP)).thenReturn(topBlock);
        // topBlock.getRelative(UP) already returns airBlock from mockBlock helper

        BlockBreakEvent event = new BlockBreakEvent(bottomBlock, player);
        listener.onBlock(event);

        assertEquals(0, listener.getIsland("test-island-id").getBlockCount(Material.SUGAR_CANE.getKey()));
    }

    @Test
    public void testBlockBreakBambooCascade() {
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.add(Material.BAMBOO.getKey());
        ibc.add(Material.BAMBOO.getKey());
        ibc.add(Material.BAMBOO.getKey());
        listener.setIsland("test-island-id", ibc);

        when(world.getMaxHeight()).thenReturn(320);

        Block bottomBlock = mockBlock(Material.BAMBOO, new Location(world, 100, 65, 100));
        when(bottomBlock.getY()).thenReturn(65);
        Block midBlock = mockBlock(Material.BAMBOO, new Location(world, 100, 66, 100));
        when(midBlock.getY()).thenReturn(66);
        Block topBlock = mockBlock(Material.BAMBOO, new Location(world, 100, 67, 100));
        when(topBlock.getY()).thenReturn(67);

        when(bottomBlock.getRelative(BlockFace.UP)).thenReturn(midBlock);
        when(midBlock.getRelative(BlockFace.UP)).thenReturn(topBlock);

        BlockBreakEvent event = new BlockBreakEvent(bottomBlock, player);
        listener.onBlock(event);

        assertEquals(0, listener.getIsland("test-island-id").getBlockCount(Material.BAMBOO.getKey()));
    }

    @Test
    public void testBlockBreakRedstoneOnTopRemoved() {
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.add(Material.STONE.getKey());
        ibc.add(Material.REDSTONE_WIRE.getKey());
        listener.setIsland("test-island-id", ibc);

        Block stoneBlock = mockBlock(Material.STONE, blockLocation);
        Block redstoneBlock = mockBlock(Material.REDSTONE_WIRE, new Location(world, 100, 66, 100));
        when(stoneBlock.getRelative(BlockFace.UP)).thenReturn(redstoneBlock);

        BlockBreakEvent event = new BlockBreakEvent(stoneBlock, player);
        listener.onBlock(event);

        assertEquals(0, listener.getIsland("test-island-id").getBlockCount(Material.STONE.getKey()));
        assertEquals(0, listener.getIsland("test-island-id").getBlockCount(Material.REDSTONE_WIRE.getKey()));
    }

    @Test
    public void testBlockBreakRedstoneWallTorchOnSideRemoved() {
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.add(Material.STONE.getKey());
        // fixMaterial normalises REDSTONE_WALL_TORCH → REDSTONE_TORCH
        ibc.add(Material.REDSTONE_TORCH.getKey());
        listener.setIsland("test-island-id", ibc);

        Block stoneBlock = mockBlock(Material.STONE, blockLocation);
        Block wallTorchBlock = mockBlock(Material.REDSTONE_WALL_TORCH, new Location(world, 101, 65, 100));
        when(stoneBlock.getRelative(BlockFace.EAST)).thenReturn(wallTorchBlock);

        BlockBreakEvent event = new BlockBreakEvent(stoneBlock, player);
        listener.onBlock(event);

        assertEquals(0, listener.getIsland("test-island-id").getBlockCount(Material.STONE.getKey()));
        assertEquals(0, listener.getIsland("test-island-id").getBlockCount(Material.REDSTONE_TORCH.getKey()));
    }

    // --- Center block test ---

    @Test
    public void testBlockPlaceCenterBlockIgnored() {
        // Make the block location equal to the island center
        when(island.getCenter()).thenReturn(blockLocation);

        Block block = mockBlock(Material.STONE, blockLocation);
        BlockState replacedState = mock(BlockState.class);
        BlockPlaceEvent event = new BlockPlaceEvent(block, replacedState, block, new ItemStack(Material.STONE), player, true, EquipmentSlot.HAND);

        listener.onBlock(event);

        // Center block is ignored, so no island count entry should be created
        assertNull(listener.getIsland("test-island-id"));
    }

    // --- Turtle egg physical interaction test ---

    @Test
    public void testTurtleEggPhysicalBreakDecrementsCount() {
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.add(Material.TURTLE_EGG.getKey());
        listener.setIsland("test-island-id", ibc);

        Block block = mockBlock(Material.TURTLE_EGG, blockLocation);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.PHYSICAL, null, block, BlockFace.UP);

        listener.onTurtleEggBreak(event);

        assertEquals(0, listener.getIsland("test-island-id").getBlockCount(Material.TURTLE_EGG.getKey()));
    }

    // --- Explosion tests ---

    @Test
    public void testBlockExplodeDecrementsBatch() {
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.add(Material.STONE.getKey());
        ibc.add(Material.STONE.getKey());
        ibc.add(Material.STONE.getKey());
        listener.setIsland("test-island-id", ibc);

        List<Block> blocks = List.of(
                mockBlock(Material.STONE, new Location(world, 100, 65, 100)),
                mockBlock(Material.STONE, new Location(world, 101, 65, 100)),
                mockBlock(Material.STONE, new Location(world, 102, 65, 100)));
        Block sourceBlock = mockBlock(Material.AIR, blockLocation);
        BlockState blockState = mock(BlockState.class);
        BlockExplodeEvent event = new BlockExplodeEvent(sourceBlock, blockState, blocks, 1.0f, ExplosionResult.DESTROY);

        listener.onBlock(event);

        assertEquals(0, listener.getIsland("test-island-id").getBlockCount(Material.STONE.getKey()));
    }

    @Test
    public void testEntityExplodeDecrementsBatch() {
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.add(Material.STONE.getKey());
        ibc.add(Material.STONE.getKey());
        ibc.add(Material.STONE.getKey());
        listener.setIsland("test-island-id", ibc);

        List<Block> blocks = List.of(
                mockBlock(Material.STONE, new Location(world, 100, 65, 100)),
                mockBlock(Material.STONE, new Location(world, 101, 65, 100)),
                mockBlock(Material.STONE, new Location(world, 102, 65, 100)));
        Entity entity = mock(Entity.class);
        EntityExplodeEvent event = new EntityExplodeEvent(entity, blockLocation, blocks, 1.0f, ExplosionResult.DESTROY);

        listener.onBlock(event);

        assertEquals(0, listener.getIsland("test-island-id").getBlockCount(Material.STONE.getKey()));
    }

    // --- EntityChangeBlockEvent tests ---

    @Test
    public void testEntityChangeBlockToAirDecrements() {
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.add(Material.STONE.getKey());
        listener.setIsland("test-island-id", ibc);

        Block block = mockBlock(Material.STONE, blockLocation);
        BlockData airData = mock(BlockData.class);
        when(airData.getMaterial()).thenReturn(Material.AIR);
        Entity entity = mock(Entity.class);
        EntityChangeBlockEvent event = new EntityChangeBlockEvent(entity, block, airData);

        listener.onBlock(event);

        assertEquals(0, listener.getIsland("test-island-id").getBlockCount(Material.STONE.getKey()));
    }

    // --- BlockFromToEvent liquid destroys redstone test ---

    @Test
    public void testBlockFromToLiquidDestroysRedstone() {
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.add(Material.REDSTONE_WIRE.getKey());
        listener.setIsland("test-island-id", ibc);

        Block sourceBlock = mockBlock(Material.WATER, blockLocation);
        when(sourceBlock.isLiquid()).thenReturn(true);
        Block toBlock = mockBlock(Material.REDSTONE_WIRE, new Location(world, 101, 65, 100));
        BlockFromToEvent event = new BlockFromToEvent(sourceBlock, toBlock);

        listener.onBlock(event);

        assertEquals(0, listener.getIsland("test-island-id").getBlockCount(Material.REDSTONE_WIRE.getKey()));
    }

    // --- Limit hierarchy tests ---

    @Test
    public void testIslandLimitTakesPrecedenceOverWorldLimit() throws Exception {
        // Set world limit for COBBLESTONE = 5
        Field worldLimitField = BlockLimitsListener.class.getDeclaredField("worldLimitMap");
        worldLimitField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<World, Map<NamespacedKey, Integer>> worldLimitMap =
                (Map<World, Map<NamespacedKey, Integer>>) worldLimitField.get(listener);
        Map<NamespacedKey, Integer> worldLimits = new HashMap<>();
        worldLimits.put(Material.COBBLESTONE.getKey(), 5);
        worldLimitMap.put(world, worldLimits);

        // Set island-specific limit for COBBLESTONE = 2, pre-populate with 2
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.setBlockLimit(Material.COBBLESTONE.getKey(), 2);
        ibc.add(Material.COBBLESTONE.getKey());
        ibc.add(Material.COBBLESTONE.getKey());
        listener.setIsland("test-island-id", ibc);

        Block block = mockBlock(Material.COBBLESTONE, blockLocation);
        BlockState replacedState = mock(BlockState.class);
        BlockPlaceEvent event = new BlockPlaceEvent(block, replacedState, block, new ItemStack(Material.COBBLESTONE), player, true, EquipmentSlot.HAND);

        listener.onBlock(event);

        assertTrue(event.isCancelled());
    }

    @Test
    public void testWorldLimitTakesPrecedenceOverDefaultLimit() throws Exception {
        // Set default limit for COBBLESTONE = 10
        Field defaultLimitField = BlockLimitsListener.class.getDeclaredField("defaultLimitMap");
        defaultLimitField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<NamespacedKey, Integer> defaultLimitMap =
                (Map<NamespacedKey, Integer>) defaultLimitField.get(listener);
        defaultLimitMap.put(Material.COBBLESTONE.getKey(), 10);

        // Set world limit for COBBLESTONE = 3
        Field worldLimitField = BlockLimitsListener.class.getDeclaredField("worldLimitMap");
        worldLimitField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<World, Map<NamespacedKey, Integer>> worldLimitMap =
                (Map<World, Map<NamespacedKey, Integer>>) worldLimitField.get(listener);
        Map<NamespacedKey, Integer> worldLimits = new HashMap<>();
        worldLimits.put(Material.COBBLESTONE.getKey(), 3);
        worldLimitMap.put(world, worldLimits);

        // No island-specific limit; pre-populate with 3
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.add(Material.COBBLESTONE.getKey());
        ibc.add(Material.COBBLESTONE.getKey());
        ibc.add(Material.COBBLESTONE.getKey());
        listener.setIsland("test-island-id", ibc);

        Block block = mockBlock(Material.COBBLESTONE, blockLocation);
        BlockState replacedState = mock(BlockState.class);
        BlockPlaceEvent event = new BlockPlaceEvent(block, replacedState, block, new ItemStack(Material.COBBLESTONE), player, true, EquipmentSlot.HAND);

        listener.onBlock(event);

        assertTrue(event.isCancelled());
    }

    @Test
    public void testDefaultLimitAppliedWhenNoIslandOrWorldLimit() throws Exception {
        // Set default limit for COBBLESTONE = 2
        Field defaultLimitField = BlockLimitsListener.class.getDeclaredField("defaultLimitMap");
        defaultLimitField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<NamespacedKey, Integer> defaultLimitMap =
                (Map<NamespacedKey, Integer>) defaultLimitField.get(listener);
        defaultLimitMap.put(Material.COBBLESTONE.getKey(), 2);

        // No island or world limit; pre-populate with 2
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.add(Material.COBBLESTONE.getKey());
        ibc.add(Material.COBBLESTONE.getKey());
        listener.setIsland("test-island-id", ibc);

        Block block = mockBlock(Material.COBBLESTONE, blockLocation);
        BlockState replacedState = mock(BlockState.class);
        BlockPlaceEvent event = new BlockPlaceEvent(block, replacedState, block, new ItemStack(Material.COBBLESTONE), player, true, EquipmentSlot.HAND);

        listener.onBlock(event);

        assertTrue(event.isCancelled());
    }

    @Test
    public void testIslandOffsetIncreasesEffectiveLimit() throws Exception {
        // Set default limit for COBBLESTONE = 2
        Field defaultLimitField = BlockLimitsListener.class.getDeclaredField("defaultLimitMap");
        defaultLimitField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<NamespacedKey, Integer> defaultLimitMap =
                (Map<NamespacedKey, Integer>) defaultLimitField.get(listener);
        defaultLimitMap.put(Material.COBBLESTONE.getKey(), 2);

        // Set island offset = +3 (effective limit = 5); pre-populate with 4
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        ibc.setBlockLimitsOffset(Material.COBBLESTONE.getKey(), 3);
        ibc.add(Material.COBBLESTONE.getKey());
        ibc.add(Material.COBBLESTONE.getKey());
        ibc.add(Material.COBBLESTONE.getKey());
        ibc.add(Material.COBBLESTONE.getKey());
        listener.setIsland("test-island-id", ibc);

        Block block = mockBlock(Material.COBBLESTONE, blockLocation);
        BlockState replacedState = mock(BlockState.class);
        BlockPlaceEvent event = new BlockPlaceEvent(block, replacedState, block, new ItemStack(Material.COBBLESTONE), player, true, EquipmentSlot.HAND);

        listener.onBlock(event);

        assertFalse(event.isCancelled());
    }

    // --- Batch save tests ---

    @Test
    public void testBatchSaveTriggersAfterThreshold() {
        // CHANGE_LIMIT = 9, so the 10th change triggers a save
        for (int i = 0; i < 10; i++) {
            Block block = mockBlock(Material.STONE, blockLocation);
            BlockState replacedState = mock(BlockState.class);
            BlockPlaceEvent event = new BlockPlaceEvent(block, replacedState, block, new ItemStack(Material.STONE), player, true, EquipmentSlot.HAND);
            listener.onBlock(event);
        }

        Database<?> dbMock = mockedDb.constructed().get(0);
        verify(dbMock, atLeastOnce()).saveObjectAsync(any());
    }

    @Test
    public void testNoSaveBeforeThresholdReached() {
        // Fire 9 events (CHANGE_LIMIT = 9, save triggers when > 9, i.e. on 10th)
        for (int i = 0; i < 9; i++) {
            Block block = mockBlock(Material.STONE, blockLocation);
            BlockState replacedState = mock(BlockState.class);
            BlockPlaceEvent event = new BlockPlaceEvent(block, replacedState, block, new ItemStack(Material.STONE), player, true, EquipmentSlot.HAND);
            listener.onBlock(event);
        }

        Database<?> dbMock = mockedDb.constructed().get(0);
        verify(dbMock, never()).saveObjectAsync(any());
    }

    // --- IslandDeleteEvent tests ---

    @Test
    public void testIslandDeleteRemovesFromMaps() {
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        listener.setIsland("test-island-id", ibc);
        assertNotNull(listener.getIsland("test-island-id"));

        Island deleteIsland = mock(Island.class);
        when(deleteIsland.getUniqueId()).thenReturn("test-island-id");
        IslandDeleteEvent event = new IslandDeleteEvent(deleteIsland, UUID.randomUUID(), false, blockLocation);

        listener.onIslandDelete(event);

        assertNull(listener.getIsland("test-island-id"));
    }

    @Test
    public void testIslandDeleteCallsDatabaseDelete() {
        IslandBlockCount ibc = new IslandBlockCount("test-island-id", "BSkyBlock");
        listener.setIsland("test-island-id", ibc);

        Database<?> dbMock = mockedDb.constructed().get(0);
        when(dbMock.objectExists("test-island-id")).thenReturn(true);

        Island deleteIsland = mock(Island.class);
        when(deleteIsland.getUniqueId()).thenReturn("test-island-id");
        IslandDeleteEvent event = new IslandDeleteEvent(deleteIsland, UUID.randomUUID(), false, blockLocation);

        listener.onIslandDelete(event);

        verify(dbMock).deleteID(eq("test-island-id"));
    }

    // --- save tests ---

    @Test
    public void testSaveSavesChangedIslands() {
        String islandId = "save-test-island";
        IslandBlockCount ibc = new IslandBlockCount(islandId, "BSkyBlock");
        ibc.setChanged(true);
        listener.setIsland(islandId, ibc);

        listener.save();

        // Verify the Database mock's saveObjectAsync was called
        Database<?> dbMock = mockedDb.constructed().get(0);
        // saveObjectAsync is called once by setIsland and once by save
        verify(dbMock, atLeastOnce()).saveObjectAsync(any());
    }
}

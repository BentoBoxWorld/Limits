package world.bentobox.limits.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.TechnicalPiston;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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
import world.bentobox.bentobox.database.Database;
import world.bentobox.bentobox.database.DatabaseSetup.DatabaseType;
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

    private BlockLimitsListener listener;
    private MockedConstruction<Database> mockedDb;
    private MockedStatic<BentoBox> mockedBentoBox;
    private FileConfiguration config;

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

        mockedDb = Mockito.mockConstruction(Database.class, (mock, context) -> {
            when(mock.loadObjects()).thenReturn(Collections.emptyList());
        });

        listener = new BlockLimitsListener(addon);
    }

    @AfterEach
    public void tearDown() {
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

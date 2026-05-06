package world.bentobox.limits.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World.Environment;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockbukkit.mockbukkit.MockBukkit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class IslandBlockCountTest {

    private IslandBlockCount ibc;
    private NamespacedKey stoneKey;

    @BeforeEach
    public void setUp() {
        MockBukkit.mock();
        stoneKey = Material.STONE.getKey();
        ibc = new IslandBlockCount("island1", "BSkyBlock");
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void testConstructorSetsFields() {
        assertEquals("island1", ibc.getUniqueId());
        assertEquals("BSkyBlock", ibc.getGameMode());
        assertTrue(ibc.isChanged());
    }

    @Test
    public void testAddIncrementsCount() {
        ibc.add(Environment.NORMAL, stoneKey);
        assertEquals(1, ibc.getBlockCount(stoneKey));
        ibc.add(Environment.NORMAL, stoneKey);
        assertEquals(2, ibc.getBlockCount(stoneKey));
    }

    @Test
    public void testRemoveDecrementsAndRemovesAtZero() {
        ibc.add(Environment.NORMAL, stoneKey);
        ibc.add(Environment.NORMAL, stoneKey);
        assertEquals(2, ibc.getBlockCount(stoneKey));

        ibc.remove(Environment.NORMAL, stoneKey);
        assertEquals(1, ibc.getBlockCount(stoneKey));

        ibc.remove(Environment.NORMAL, stoneKey);
        // At 0 the entry should be removed entirely
        assertEquals(0, ibc.getBlockCount(stoneKey));
        assertFalse(ibc.getBlockCounts(Environment.NORMAL).containsKey(stoneKey));
    }

    @Test
    public void testGetBlockCountUnknownMaterial() {
        assertEquals(0, ibc.getBlockCount(stoneKey));
    }

    @Test
    public void testGetBlockLimitUnknownMaterial() {
        assertEquals(-1, ibc.getBlockLimit(Environment.NORMAL, stoneKey));
    }

    @Test
    public void testSetBlockLimitThenGet() {
        ibc.setBlockLimit(Environment.NORMAL, stoneKey, 50);
        assertEquals(50, ibc.getBlockLimit(Environment.NORMAL, stoneKey));
    }

    @Test
    public void testIsAtLimitNoLimitSet() {
        assertFalse(ibc.isAtLimit(Environment.NORMAL, stoneKey));
    }

    @Test
    public void testIsAtLimitCountBelowLimit() {
        ibc.setBlockLimit(Environment.NORMAL, stoneKey, 10);
        ibc.add(Environment.NORMAL, stoneKey);
        assertFalse(ibc.isAtLimit(Environment.NORMAL, stoneKey));
    }

    @Test
    public void testIsAtLimitCountAtLimit() {
        ibc.setBlockLimit(Environment.NORMAL, stoneKey, 2);
        ibc.add(Environment.NORMAL, stoneKey);
        ibc.add(Environment.NORMAL, stoneKey);
        assertTrue(ibc.isAtLimit(Environment.NORMAL, stoneKey));
    }

    @Test
    public void testIsAtLimitWithOffset() {
        // count=10, limit=10, offset=5 → effective limit is 15, so NOT at limit
        ibc.setBlockLimit(Environment.NORMAL, stoneKey, 10);
        ibc.setBlockLimitsOffset(Environment.NORMAL, stoneKey, 5);
        for (int i = 0; i < 10; i++) {
            ibc.add(Environment.NORMAL, stoneKey);
        }
        assertFalse(ibc.isAtLimit(Environment.NORMAL, stoneKey));
    }

    @Test
    public void testIsAtLimitOverloadWithOffset() {
        ibc.setBlockLimitsOffset(Environment.NORMAL, stoneKey, 5);
        for (int i = 0; i < 10; i++) {
            ibc.add(Environment.NORMAL, stoneKey);
        }
        // isAtLimit(material, limit) → count(10) >= limit(10) + offset(5) = 15 → false
        assertFalse(ibc.isAtLimit(Environment.NORMAL, stoneKey, 10));
        // count(10) >= limit(5) + offset(5) = 10 → true
        assertTrue(ibc.isAtLimit(Environment.NORMAL, stoneKey, 5));
    }

    @Test
    public void testIsBlockLimited() {
        assertFalse(ibc.isBlockLimited(Environment.NORMAL, stoneKey));
        ibc.setBlockLimit(Environment.NORMAL, stoneKey, 10);
        assertTrue(ibc.isBlockLimited(Environment.NORMAL, stoneKey));
    }

    @Test
    public void testEntityLimits() {
        assertEquals(-1, ibc.getEntityLimit(Environment.NORMAL, EntityType.ZOMBIE));
        ibc.setEntityLimit(Environment.NORMAL, EntityType.ZOMBIE, 25);
        assertEquals(25, ibc.getEntityLimit(Environment.NORMAL, EntityType.ZOMBIE));
        ibc.clearAllEntityLimits();
        assertEquals(-1, ibc.getEntityLimit(Environment.NORMAL, EntityType.ZOMBIE));
    }

    @Test
    public void testEntityGroupLimits() {
        assertEquals(-1, ibc.getEntityGroupLimit(Environment.NORMAL, "monsters"));
        ibc.setEntityGroupLimit(Environment.NORMAL, "monsters", 30);
        assertEquals(30, ibc.getEntityGroupLimit(Environment.NORMAL, "monsters"));
        ibc.clearAllEntityGroupLimits();
        assertEquals(-1, ibc.getEntityGroupLimit(Environment.NORMAL, "monsters"));
    }

    @Test
    public void testBlockLimitsOffset() {
        assertEquals(0, ibc.getBlockLimitOffset(Environment.NORMAL, stoneKey));
        ibc.setBlockLimitsOffset(Environment.NORMAL, stoneKey, 7);
        assertEquals(7, ibc.getBlockLimitOffset(Environment.NORMAL, stoneKey));
    }

    @Test
    public void testEntityLimitsOffset() {
        assertEquals(0, ibc.getEntityLimitOffset(Environment.NORMAL, EntityType.ZOMBIE));
        ibc.setEntityLimitsOffset(Environment.NORMAL, EntityType.ZOMBIE, 3);
        assertEquals(3, ibc.getEntityLimitOffset(Environment.NORMAL, EntityType.ZOMBIE));
    }

    @Test
    public void testEntityGroupLimitsOffset() {
        assertEquals(0, ibc.getEntityGroupLimitOffset(Environment.NORMAL, "monsters"));
        ibc.setEntityGroupLimitsOffset(Environment.NORMAL, "monsters", 4);
        assertEquals(4, ibc.getEntityGroupLimitOffset(Environment.NORMAL, "monsters"));
    }

    @Test
    public void testSetChangedFalse() {
        ibc.setChanged(false);
        assertFalse(ibc.isChanged());
    }

    @Test
    public void testIsGameMode() {
        assertTrue(ibc.isGameMode("BSkyBlock"));
        assertFalse(ibc.isGameMode("AcidIsland"));
    }

    @Test
    public void testSetAndGetUniqueId() {
        ibc.setUniqueId("island2");
        assertEquals("island2", ibc.getUniqueId());
    }

    /**
     * Mirrors BentoBox's {@code AbstractJSONDatabaseHandler} configuration.
     */
    private static Gson buildBentoboxGson() {
        return new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .enableComplexMapKeySerialization()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
    }

    @Test
    public void testReadLegacyJsonWithBareMaterialNames() {
        // This is the on-disk shape produced by versions of Limits where
        // blockCounts was Map<Material, Integer>. Pre-1d0a3b7 databases look
        // exactly like this and must still load.
        String legacy = """
                {
                  "uniqueId": "AOneBlockabc",
                  "gameMode": "AOneBlock",
                  "blockCounts": {
                    "POLISHED_DIORITE": 11,
                    "DIRT": 459,
                    "OAK_LOG": 8
                  },
                  "blockLimits": {},
                  "entityLimits": {},
                  "entityGroupLimits": {}
                }
                """;
        IslandBlockCount loaded = buildBentoboxGson().fromJson(legacy, IslandBlockCount.class);
        assertEquals("AOneBlockabc", loaded.getUniqueId());
        assertEquals(11, loaded.getBlockCount(NamespacedKey.minecraft("polished_diorite")));
        assertEquals(459, loaded.getBlockCount(NamespacedKey.minecraft("dirt")));
        assertEquals(8, loaded.getBlockCount(NamespacedKey.minecraft("oak_log")));
    }

    @Test
    public void legacyJsonMigratesIntoOverworldEnv() {
        // Legacy data with no envBlockCounts must end up scoped to Environment.NORMAL.
        String legacy = """
                {
                  "uniqueId": "isle",
                  "gameMode": "BSkyBlock",
                  "blockCounts": { "minecraft:hopper": 7 },
                  "blockLimits": { "minecraft:hopper": 20 },
                  "blockLimitsOffset": { "minecraft:hopper": 3 },
                  "entityLimits": { "CHICKEN": 12 },
                  "entityLimitsOffset": { "CHICKEN": 2 },
                  "entityGroupLimits": { "Monsters": 50 },
                  "entityGroupLimitsOffset": { "Monsters": 10 }
                }
                """;
        IslandBlockCount loaded = buildBentoboxGson().fromJson(legacy, IslandBlockCount.class);
        NamespacedKey hopper = NamespacedKey.minecraft("hopper");

        // Touching any getter triggers migration, after which only the NORMAL env should hold data.
        assertEquals(7, loaded.getBlockCount(Environment.NORMAL, hopper));
        assertEquals(0, loaded.getBlockCount(Environment.NETHER, hopper));
        assertEquals(0, loaded.getBlockCount(Environment.THE_END, hopper));

        assertEquals(20, loaded.getBlockLimit(Environment.NORMAL, hopper));
        assertEquals(-1, loaded.getBlockLimit(Environment.NETHER, hopper));

        assertEquals(3, loaded.getBlockLimitOffset(Environment.NORMAL, hopper));
        assertEquals(0, loaded.getBlockLimitOffset(Environment.NETHER, hopper));

        assertEquals(12, loaded.getEntityLimit(Environment.NORMAL, EntityType.CHICKEN));
        assertEquals(-1, loaded.getEntityLimit(Environment.NETHER, EntityType.CHICKEN));

        assertEquals(2, loaded.getEntityLimitOffset(Environment.NORMAL, EntityType.CHICKEN));
        assertEquals(50, loaded.getEntityGroupLimit(Environment.NORMAL, "Monsters"));
        assertEquals(10, loaded.getEntityGroupLimitOffset(Environment.NORMAL, "Monsters"));
    }

    @Test
    public void allEnvsSetterAppliesUniformly() {
        ibc.setBlockLimitAllEnvs(stoneKey, 50);
        assertEquals(50, ibc.getBlockLimit(Environment.NORMAL, stoneKey));
        assertEquals(50, ibc.getBlockLimit(Environment.NETHER, stoneKey));
        assertEquals(50, ibc.getBlockLimit(Environment.THE_END, stoneKey));

        ibc.setEntityLimitAllEnvs(EntityType.CHICKEN, 30);
        assertEquals(30, ibc.getEntityLimit(Environment.NORMAL, EntityType.CHICKEN));
        assertEquals(30, ibc.getEntityLimit(Environment.NETHER, EntityType.CHICKEN));
        assertEquals(30, ibc.getEntityLimit(Environment.THE_END, EntityType.CHICKEN));
    }

    @Test
    public void countsAreEnvIndependent() {
        ibc.add(Environment.NORMAL, stoneKey);
        ibc.add(Environment.NORMAL, stoneKey);
        ibc.add(Environment.NETHER, stoneKey);

        assertEquals(2, ibc.getBlockCount(Environment.NORMAL, stoneKey));
        assertEquals(1, ibc.getBlockCount(Environment.NETHER, stoneKey));
        assertEquals(0, ibc.getBlockCount(Environment.THE_END, stoneKey));
        // Sum across envs
        assertEquals(3, ibc.getBlockCount(stoneKey));
    }

    @Test
    public void entityCountIncrementDecrement() {
        ibc.incrementEntity(Environment.NORMAL, EntityType.PIG);
        ibc.incrementEntity(Environment.NORMAL, EntityType.PIG);
        ibc.incrementEntity(Environment.NETHER, EntityType.PIG);
        assertEquals(2, ibc.getEntityCount(Environment.NORMAL, EntityType.PIG));
        assertEquals(1, ibc.getEntityCount(Environment.NETHER, EntityType.PIG));
        assertEquals(3, ibc.getEntityCount(EntityType.PIG));

        ibc.decrementEntity(Environment.NORMAL, EntityType.PIG);
        assertEquals(1, ibc.getEntityCount(Environment.NORMAL, EntityType.PIG));
        // Decrementing past zero is a no-op.
        ibc.decrementEntity(Environment.THE_END, EntityType.PIG);
        assertEquals(0, ibc.getEntityCount(Environment.THE_END, EntityType.PIG));
    }

    @Test
    public void testReadJsonWithNamespacedStringKeys() {
        // This is the format the new adapter writes; it must round-trip.
        String json = """
                {
                  "uniqueId": "i",
                  "gameMode": "BSkyBlock",
                  "blockCounts": {
                    "minecraft:dirt": 12,
                    "myaddon:custom_block": 3
                  }
                }
                """;
        IslandBlockCount loaded = buildBentoboxGson().fromJson(json, IslandBlockCount.class);
        assertEquals(12, loaded.getBlockCount(NamespacedKey.minecraft("dirt")));
        assertEquals(3, loaded.getBlockCount(new NamespacedKey("myaddon", "custom_block")));
    }

    @Test
    public void testWriteRoundTripWithNamespacedKeys() {
        ibc.add(Environment.NORMAL, stoneKey);
        ibc.add(Environment.NORMAL, stoneKey);
        ibc.setBlockLimit(Environment.NORMAL, NamespacedKey.minecraft("hopper"), 20);
        ibc.setBlockLimitsOffset(Environment.NORMAL, NamespacedKey.minecraft("hopper"), 5);

        Gson gson = buildBentoboxGson();
        String json = gson.toJson(ibc);
        IslandBlockCount loaded = gson.fromJson(json, IslandBlockCount.class);

        assertEquals(2, loaded.getBlockCount(stoneKey));
        assertEquals(20, loaded.getBlockLimit(Environment.NORMAL, NamespacedKey.minecraft("hopper")));
        assertEquals(5, loaded.getBlockLimitOffset(Environment.NORMAL, NamespacedKey.minecraft("hopper")));
    }
}

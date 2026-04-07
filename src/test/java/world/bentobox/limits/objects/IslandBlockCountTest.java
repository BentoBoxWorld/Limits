package world.bentobox.limits.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
        ibc.add(stoneKey);
        assertEquals(1, ibc.getBlockCount(stoneKey));
        ibc.add(stoneKey);
        assertEquals(2, ibc.getBlockCount(stoneKey));
    }

    @Test
    public void testRemoveDecrementsAndRemovesAtZero() {
        ibc.add(stoneKey);
        ibc.add(stoneKey);
        assertEquals(2, ibc.getBlockCount(stoneKey));

        ibc.remove(stoneKey);
        assertEquals(1, ibc.getBlockCount(stoneKey));

        ibc.remove(stoneKey);
        // At 0 the entry should be removed entirely
        assertEquals(0, ibc.getBlockCount(stoneKey));
        assertFalse(ibc.getBlockCounts().containsKey(stoneKey));
    }

    @Test
    public void testGetBlockCountUnknownMaterial() {
        assertEquals(0, ibc.getBlockCount(stoneKey));
    }

    @Test
    public void testGetBlockLimitUnknownMaterial() {
        assertEquals(-1, ibc.getBlockLimit(stoneKey));
    }

    @Test
    public void testSetBlockLimitThenGet() {
        ibc.setBlockLimit(stoneKey, 50);
        assertEquals(50, ibc.getBlockLimit(stoneKey));
    }

    @Test
    public void testIsAtLimitNoLimitSet() {
        assertFalse(ibc.isAtLimit(stoneKey));
    }

    @Test
    public void testIsAtLimitCountBelowLimit() {
        ibc.setBlockLimit(stoneKey, 10);
        ibc.add(stoneKey);
        assertFalse(ibc.isAtLimit(stoneKey));
    }

    @Test
    public void testIsAtLimitCountAtLimit() {
        ibc.setBlockLimit(stoneKey, 2);
        ibc.add(stoneKey);
        ibc.add(stoneKey);
        assertTrue(ibc.isAtLimit(stoneKey));
    }

    @Test
    public void testIsAtLimitWithOffset() {
        // count=10, limit=10, offset=5 → effective limit is 15, so NOT at limit
        ibc.setBlockLimit(stoneKey, 10);
        ibc.setBlockLimitsOffset(stoneKey, 5);
        for (int i = 0; i < 10; i++) {
            ibc.add(stoneKey);
        }
        assertFalse(ibc.isAtLimit(stoneKey));
    }

    @Test
    public void testIsAtLimitOverloadWithOffset() {
        ibc.setBlockLimitsOffset(stoneKey, 5);
        for (int i = 0; i < 10; i++) {
            ibc.add(stoneKey);
        }
        // isAtLimit(material, limit) → count(10) >= limit(10) + offset(5) = 15 → false
        assertFalse(ibc.isAtLimit(stoneKey, 10));
        // count(10) >= limit(5) + offset(5) = 10 → true
        assertTrue(ibc.isAtLimit(stoneKey, 5));
    }

    @Test
    public void testIsBlockLimited() {
        assertFalse(ibc.isBlockLimited(stoneKey));
        ibc.setBlockLimit(stoneKey, 10);
        assertTrue(ibc.isBlockLimited(stoneKey));
    }

    @Test
    public void testEntityLimits() {
        assertEquals(-1, ibc.getEntityLimit(EntityType.ZOMBIE));
        ibc.setEntityLimit(EntityType.ZOMBIE, 25);
        assertEquals(25, ibc.getEntityLimit(EntityType.ZOMBIE));
        ibc.clearEntityLimits();
        assertEquals(-1, ibc.getEntityLimit(EntityType.ZOMBIE));
    }

    @Test
    public void testEntityGroupLimits() {
        assertEquals(-1, ibc.getEntityGroupLimit("monsters"));
        ibc.setEntityGroupLimit("monsters", 30);
        assertEquals(30, ibc.getEntityGroupLimit("monsters"));
        ibc.clearEntityGroupLimits();
        assertEquals(-1, ibc.getEntityGroupLimit("monsters"));
    }

    @Test
    public void testBlockLimitsOffset() {
        assertEquals(0, ibc.getBlockLimitOffset(stoneKey));
        ibc.setBlockLimitsOffset(stoneKey, 7);
        assertEquals(7, ibc.getBlockLimitOffset(stoneKey));
    }

    @Test
    public void testEntityLimitsOffset() {
        assertEquals(0, ibc.getEntityLimitOffset(EntityType.ZOMBIE));
        ibc.setEntityLimitsOffset(EntityType.ZOMBIE, 3);
        assertEquals(3, ibc.getEntityLimitOffset(EntityType.ZOMBIE));
    }

    @Test
    public void testEntityGroupLimitsOffset() {
        assertEquals(0, ibc.getEntityGroupLimitOffset("monsters"));
        ibc.setEntityGroupLimitsOffset("monsters", 4);
        assertEquals(4, ibc.getEntityGroupLimitOffset("monsters"));
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
        ibc.add(stoneKey);
        ibc.add(stoneKey);
        ibc.setBlockLimit(NamespacedKey.minecraft("hopper"), 20);
        ibc.setBlockLimitsOffset(NamespacedKey.minecraft("hopper"), 5);

        Gson gson = buildBentoboxGson();
        String json = gson.toJson(ibc);
        IslandBlockCount loaded = gson.fromJson(json, IslandBlockCount.class);

        assertEquals(2, loaded.getBlockCount(stoneKey));
        assertEquals(20, loaded.getBlockLimit(NamespacedKey.minecraft("hopper")));
        assertEquals(5, loaded.getBlockLimitOffset(NamespacedKey.minecraft("hopper")));
    }
}

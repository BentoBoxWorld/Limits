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
}

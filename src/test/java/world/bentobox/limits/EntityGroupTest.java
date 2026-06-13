package world.bentobox.limits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockbukkit.mockbukkit.MockBukkit;

class EntityGroupTest {

    private EntityGroup group;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        group = new EntityGroup("monsters", Set.of(EntityType.ZOMBIE, EntityType.SKELETON), 10, Material.ZOMBIE_HEAD);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testConstructorAndGetters() {
        assertEquals("monsters", group.getName());
        assertEquals(Set.of(EntityType.ZOMBIE, EntityType.SKELETON), group.getTypes());
        assertEquals(10, group.getLimit());
    }

    @Test
    void testContainsMember() {
        assertTrue(group.contains(EntityType.ZOMBIE));
    }

    @Test
    void testContainsNonMember() {
        assertFalse(group.contains(EntityType.CREEPER));
    }

    @Test
    void testGetIconWhenSet() {
        assertEquals(Material.ZOMBIE_HEAD, group.getIcon());
    }

    @Test
    void testGetIconWhenNull() {
        EntityGroup noIcon = new EntityGroup("passive", Set.of(EntityType.COW), 5, null);
        assertEquals(Material.BARRIER, noIcon.getIcon());
    }

    @Test
    void testEqualsSameName() {
        EntityGroup other = new EntityGroup("monsters", Set.of(EntityType.CREEPER), 20, Material.STONE);
        assertEquals(group, other);
    }

    @Test
    void testEqualsDifferentName() {
        EntityGroup other = new EntityGroup("animals", Set.of(EntityType.ZOMBIE), 10, Material.ZOMBIE_HEAD);
        assertNotEquals(group, other);
    }

    @Test
    void testEqualsNull() {
        assertNotEquals(null, group);
    }

    @Test
    void testEqualsDifferentClass() {
        assertNotEquals("monsters", group);
    }

    @Test
    void testHashCodeConsistent() {
        EntityGroup other = new EntityGroup("monsters", Set.of(EntityType.CREEPER), 20, Material.STONE);
        assertEquals(group.hashCode(), other.hashCode());
    }

    @Test
    void testHashCodeDifferent() {
        EntityGroup other = new EntityGroup("animals", Set.of(EntityType.ZOMBIE), 10, Material.ZOMBIE_HEAD);
        assertNotEquals(group.hashCode(), other.hashCode());
    }

    @Test
    void testToStringContainsName() {
        String str = group.toString();
        assertNotNull(str);
        assertTrue(str.contains("monsters"));
    }
}

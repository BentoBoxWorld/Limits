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

import world.bentobox.limits.mocks.ServerMocks;

public class EntityGroupTest {

    private EntityGroup group;

    @BeforeEach
    public void setUp() {
        ServerMocks.newServer();
        group = new EntityGroup("monsters", Set.of(EntityType.ZOMBIE, EntityType.SKELETON), 10, Material.ZOMBIE_HEAD);
    }

    @AfterEach
    public void tearDown() {
        ServerMocks.unsetBukkitServer();
    }

    @Test
    public void testConstructorAndGetters() {
        assertEquals("monsters", group.getName());
        assertEquals(Set.of(EntityType.ZOMBIE, EntityType.SKELETON), group.getTypes());
        assertEquals(10, group.getLimit());
    }

    @Test
    public void testContainsMember() {
        assertTrue(group.contains(EntityType.ZOMBIE));
    }

    @Test
    public void testContainsNonMember() {
        assertFalse(group.contains(EntityType.CREEPER));
    }

    @Test
    public void testGetIconWhenSet() {
        assertEquals(Material.ZOMBIE_HEAD, group.getIcon());
    }

    @Test
    public void testGetIconWhenNull() {
        EntityGroup noIcon = new EntityGroup("passive", Set.of(EntityType.COW), 5, null);
        assertEquals(Material.BARRIER, noIcon.getIcon());
    }

    @Test
    public void testEqualsSameName() {
        EntityGroup other = new EntityGroup("monsters", Set.of(EntityType.CREEPER), 20, Material.STONE);
        assertEquals(group, other);
    }

    @Test
    public void testEqualsDifferentName() {
        EntityGroup other = new EntityGroup("animals", Set.of(EntityType.ZOMBIE), 10, Material.ZOMBIE_HEAD);
        assertNotEquals(group, other);
    }

    @Test
    public void testEqualsNull() {
        assertNotEquals(null, group);
    }

    @Test
    public void testEqualsDifferentClass() {
        assertNotEquals("monsters", group);
    }

    @Test
    public void testHashCodeConsistent() {
        EntityGroup other = new EntityGroup("monsters", Set.of(EntityType.CREEPER), 20, Material.STONE);
        assertEquals(group.hashCode(), other.hashCode());
    }

    @Test
    public void testHashCodeDifferent() {
        EntityGroup other = new EntityGroup("animals", Set.of(EntityType.ZOMBIE), 10, Material.ZOMBIE_HEAD);
        assertNotEquals(group.hashCode(), other.hashCode());
    }

    @Test
    public void testToStringContainsName() {
        String str = group.toString();
        assertNotNull(str);
        assertTrue(str.contains("monsters"));
    }
}

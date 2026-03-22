package world.bentobox.limits.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class EntityLimitsDOTest {

    @Test
    public void testDefaultConstructorCreatesEmptySpawnLoc() {
        EntityLimitsDO eld = new EntityLimitsDO();
        assertNotNull(eld.getSpawnLoc());
        assertTrue(eld.getSpawnLoc().isEmpty());
    }

    @Test
    public void testConstructorWithIdSetsUniqueId() {
        EntityLimitsDO eld = new EntityLimitsDO("test-id");
        assertEquals("test-id", eld.getUniqueId());
    }

    @Test
    public void testGetSetUniqueId() {
        EntityLimitsDO eld = new EntityLimitsDO();
        eld.setUniqueId("new-id");
        assertEquals("new-id", eld.getUniqueId());
    }

    @Test
    public void testGetSetSpawnLoc() {
        EntityLimitsDO eld = new EntityLimitsDO("id");
        Map<UUID, String> spawnLoc = new HashMap<>();
        UUID uuid = UUID.randomUUID();
        spawnLoc.put(uuid, "world:10:20:30");
        eld.setSpawnLoc(spawnLoc);
        assertEquals(spawnLoc, eld.getSpawnLoc());
        assertEquals("world:10:20:30", eld.getSpawnLoc().get(uuid));
    }

    @Test
    public void testEqualsSameId() {
        EntityLimitsDO a = new EntityLimitsDO("same");
        EntityLimitsDO b = new EntityLimitsDO("same");
        assertEquals(a, b);
    }

    @Test
    public void testEqualsDifferentId() {
        EntityLimitsDO a = new EntityLimitsDO("one");
        EntityLimitsDO b = new EntityLimitsDO("two");
        assertNotEquals(a, b);
    }

    @Test
    public void testEqualsNull() {
        EntityLimitsDO a = new EntityLimitsDO("one");
        assertFalse(a.equals(null));
    }

    @Test
    public void testEqualsSameInstance() {
        EntityLimitsDO a = new EntityLimitsDO("one");
        assertEquals(a, a);
    }

    @Test
    public void testEqualsDifferentClass() {
        EntityLimitsDO a = new EntityLimitsDO("one");
        assertNotEquals("one", a);
    }

    @Test
    public void testHashCodeSameId() {
        EntityLimitsDO a = new EntityLimitsDO("same");
        EntityLimitsDO b = new EntityLimitsDO("same");
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testHashCodeDifferentId() {
        EntityLimitsDO a = new EntityLimitsDO("one");
        EntityLimitsDO b = new EntityLimitsDO("two");
        assertNotEquals(a.hashCode(), b.hashCode());
    }
}

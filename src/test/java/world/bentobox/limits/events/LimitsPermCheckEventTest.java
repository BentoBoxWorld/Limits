package world.bentobox.limits.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import world.bentobox.limits.EntityGroup;
import world.bentobox.limits.mocks.ServerMocks;
import world.bentobox.limits.objects.IslandBlockCount;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class LimitsPermCheckEventTest {

    @Mock
    private Player player;

    private IslandBlockCount ibc;
    private EntityGroup entityGroup;
    private LimitsPermCheckEvent event;

    @BeforeEach
    public void setUp() {
        ServerMocks.newServer();
        ibc = new IslandBlockCount("island1", "BSkyBlock");
        entityGroup = new EntityGroup("monsters", Set.of(EntityType.ZOMBIE), 10, Material.ZOMBIE_HEAD);
        event = new LimitsPermCheckEvent(player, "island1", ibc, entityGroup, EntityType.ZOMBIE, Material.STONE, 42);
    }

    @AfterEach
    public void tearDown() {
        ServerMocks.unsetBukkitServer();
    }

    @Test
    public void testConstructorSetsAllFields() {
        assertEquals(player, event.getPlayer());
        assertEquals("island1", event.getIslandId());
        assertEquals(ibc, event.getIbc());
        assertEquals(entityGroup, event.getEntityGroup());
        assertEquals(EntityType.ZOMBIE, event.getEntityType());
        assertEquals(Material.STONE, event.getMaterial());
        assertEquals(42, event.getValue());
    }

    @Test
    public void testGetSetEntityGroup() {
        EntityGroup newGroup = new EntityGroup("animals", Set.of(EntityType.COW), 5, null);
        event.setEntityGroup(newGroup);
        assertEquals(newGroup, event.getEntityGroup());
    }

    @Test
    public void testGetSetEntityType() {
        event.setEntityType(EntityType.SKELETON);
        assertEquals(EntityType.SKELETON, event.getEntityType());
    }

    @Test
    public void testGetSetMaterial() {
        event.setMaterial(Material.DIAMOND_BLOCK);
        assertEquals(Material.DIAMOND_BLOCK, event.getMaterial());
    }

    @Test
    public void testGetSetValue() {
        event.setValue(100);
        assertEquals(100, event.getValue());
    }

    @Test
    public void testNullEntityGroup() {
        event.setEntityGroup(null);
        assertNull(event.getEntityGroup());
    }

    @Test
    public void testNullEntityType() {
        event.setEntityType(null);
        assertNull(event.getEntityType());
    }

    @Test
    public void testNullMaterial() {
        event.setMaterial(null);
        assertNull(event.getMaterial());
    }
}

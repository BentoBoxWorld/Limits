package world.bentobox.limits.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import world.bentobox.limits.objects.IslandBlockCount;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class LimitsJoinPermCheckEventTest {

    @Mock
    private Player player;

    private IslandBlockCount ibc;
    private LimitsJoinPermCheckEvent event;

    @BeforeEach
    public void setUp() {
        ibc = new IslandBlockCount("island1", "BSkyBlock");
        event = new LimitsJoinPermCheckEvent(player, "island1", ibc);
    }

    @Test
    public void testConstructorSetsFields() {
        assertEquals(player, event.getPlayer());
        assertEquals("island1", event.getIslandId());
        assertEquals(ibc, event.getIbc());
    }

    @Test
    public void testGetPlayer() {
        assertEquals(player, event.getPlayer());
    }

    @Test
    public void testGetIslandId() {
        assertEquals("island1", event.getIslandId());
    }

    @Test
    public void testGetSetIbc() {
        IslandBlockCount newIbc = new IslandBlockCount("island2", "AcidIsland");
        event.setIbc(newIbc);
        assertEquals(newIbc, event.getIbc());
    }

    @Test
    public void testSetIbcNull() {
        event.setIbc(null);
        assertNull(event.getIbc());
    }

    @Test
    public void testIsCancelledDefaultFalse() {
        assertFalse(event.isCancelled());
    }

    @Test
    public void testSetCancelled() {
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    public void testIsIgnorePermsDefaultFalse() {
        assertFalse(event.isIgnorePerms());
    }

    @Test
    public void testSetIgnorePerms() {
        event.setIgnorePerms(true);
        assertTrue(event.isIgnorePerms());
    }

    @Test
    public void testGetHandlers() {
        assertNotNull(event.getHandlers());
    }

    @Test
    public void testGetHandlerListStatic() {
        assertNotNull(LimitsJoinPermCheckEvent.getHandlerList());
    }
}

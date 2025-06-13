package world.bentobox.limits.commands.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandWorldManager;
import world.bentobox.limits.Limits;
import world.bentobox.limits.Settings;
import world.bentobox.limits.objects.IslandBlockCount;

@RunWith(PowerMockRunner.class)
@PrepareForTest( Bukkit.class )
public class LimitTabTest {

    @Mock
    private Limits addon;

    private LimitTab lp;

    @Mock
    private Island island;
    @Mock
    private World world;
    @Mock
    private World nether;
    @Mock
    private World end;
    @Mock
    private BentoBox plugin;
    @Mock
    private IslandWorldManager iwm;
    @Mock
    private Settings settings;

    @Before
    public void setUp() {
        // Island
        when(island.getWorld()).thenReturn(world);
        // Addon
        when(addon.getPlugin()).thenReturn(plugin);
        when(addon.getSettings()).thenReturn(settings);
        when(settings.getLimits()).thenReturn(Collections.emptyMap());
        when(plugin.getIWM()).thenReturn(iwm);
        when(iwm.isNetherIslands(any())).thenReturn(true);
        when(iwm.isEndIslands(any())).thenReturn(true);
        when(iwm.getNetherWorld(eq(world))).thenReturn(nether);
        when(iwm.getEndWorld(eq(world))).thenReturn(end);
        // Worlds
        Entity entity = mock(Entity.class);
        when(entity.getType()).thenReturn(EntityType.BAT);
        when(entity.getLocation()).thenReturn(mock(Location.class));
        when(world.getEntities()).thenReturn(Collections.singletonList(entity));
        when(nether.getEntities()).thenReturn(Collections.singletonList(entity));
        when(end.getEntities()).thenReturn(Collections.singletonList(entity));
        lp = new LimitTab(addon, new IslandBlockCount("", ""), Collections.emptyMap(), island, world, null, LimitTab.SORT_BY.A2Z);
    }

    @After
    public void tearDown() {
    }

    @Test
    @Ignore
    public void testShowLimits() {
        fail("Not yet implemented");
    }

    @Test
    public void testGetCountInIslandSpace() {
        when(island.inIslandSpace(any(Location.class))).thenReturn(true);
        EntityType ent = EntityType.BAT;
        assertEquals(3L, lp.getCount(island, ent));
        ent = EntityType.GHAST;
        assertEquals(0L, lp.getCount(island, ent));
        when(iwm.isEndIslands(any())).thenReturn(false);
        ent = EntityType.BAT;
        assertEquals(2L, lp.getCount(island, ent));
        when(iwm.isNetherIslands(any())).thenReturn(false);
        ent = EntityType.BAT;
        assertEquals(1L, lp.getCount(island, ent));
    }

    @Test
    public void testGetCountNotInIslandSpace() {
        EntityType ent = EntityType.BAT;
        assertEquals(0L, lp.getCount(island, ent));
    }

}

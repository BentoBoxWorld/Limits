package world.bentobox.limits.listeners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.limits.Limits;
import world.bentobox.limits.Settings;
import world.bentobox.limits.listeners.EntityLimitListener.AtLimitResult;
import world.bentobox.limits.objects.IslandBlockCount;

@RunWith(PowerMockRunner.class)
@PrepareForTest( Bukkit.class )
public class EntityLimitListenerTest {
    @Mock
    private Limits addon;
    private EntityLimitListener ell;
    @Mock
    private Island island;
    @Mock
    private LivingEntity ent;
    @Mock
    private BlockLimitsListener bll;
    private Settings settings;
    @Mock
    private World world;
    private List<Entity> collection;
    @Mock
    private Location location;
    private IslandBlockCount ibc;


    @Before
    public void setUp() throws Exception {
        // Entity
        when(ent.getType()).thenReturn(EntityType.ENDERMAN);
        when(ent.getLocation()).thenReturn(location);
        // Island
        when(island.getUniqueId()).thenReturn(UUID.randomUUID().toString());
        when(island.inIslandSpace(any(Location.class))).thenReturn(true);

        ibc = new IslandBlockCount();
        when(bll.getIsland(anyString())).thenReturn(ibc);
        when(addon.getBlockLimitListener()).thenReturn(bll);

        FileConfiguration config = new YamlConfiguration();
        config.load("src/main/resources/config.yml");
        // Settings
        when(addon.getConfig()).thenReturn(config);
        settings = new Settings(addon);
        when(addon.getSettings()).thenReturn(settings);

        // World
        when(ent.getWorld()).thenReturn(world);
        collection = new ArrayList<>();
        collection.add(ent);
        collection.add(ent);
        collection.add(ent);
        collection.add(ent);
        when(world.getEntities()).thenReturn(collection);

        ell = new EntityLimitListener(addon);
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * Test for {@link EntityLimitListener#atLimit(Island, Entity)}
     */
    @Test
    public void testAtLimitUnderLimit() {
        AtLimitResult result = ell.atLimit(island, ent);
        assertFalse(result.hit());
    }

    /**
     * Test for {@link EntityLimitListener#atLimit(Island, Entity)}
     */
    @Test
    public void testAtLimitAtLimit() {
        collection.add(ent);
        AtLimitResult result = ell.atLimit(island, ent);
        assertTrue(result.hit());
        assertEquals(EntityType.ENDERMAN, result.getTypelimit().getKey());
        assertEquals(Integer.valueOf(5), result.getTypelimit().getValue());

    }

    /**
     * Test for {@link EntityLimitListener#atLimit(Island, Entity)}
     */
    @Test
    public void testAtLimitUnderLimitIslandLimit() {
        ibc.setEntityLimit(EntityType.ENDERMAN, 6);
        AtLimitResult result = ell.atLimit(island, ent);
        assertFalse(result.hit());
    }

    /**
     * Test for {@link EntityLimitListener#atLimit(Island, Entity)}
     */
    @Test
    public void testAtLimitAtLimitIslandLimitNotAtLimit() {
        ibc.setEntityLimit(EntityType.ENDERMAN, 6);
        collection.add(ent);
        AtLimitResult result = ell.atLimit(island, ent);
        assertFalse(result.hit());
    }

    /**
     * Test for {@link EntityLimitListener#atLimit(Island, Entity)}
     */
    @Test
    public void testAtLimitAtLimitIslandLimit() {
        ibc.setEntityLimit(EntityType.ENDERMAN, 6);
        collection.add(ent);
        collection.add(ent);
        AtLimitResult result = ell.atLimit(island, ent);
        assertTrue(result.hit());
        assertEquals(EntityType.ENDERMAN, result.getTypelimit().getKey());
        assertEquals(Integer.valueOf(6), result.getTypelimit().getValue());

    }


}

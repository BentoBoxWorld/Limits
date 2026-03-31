package world.bentobox.limits.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.entity.Hanging;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandsManager;
import world.bentobox.limits.Limits;
import world.bentobox.limits.Settings;
import world.bentobox.limits.listeners.EntityLimitListener.AtLimitResult;
import org.mockbukkit.mockbukkit.MockBukkit;
import world.bentobox.limits.objects.IslandBlockCount;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    @Mock
    private World world;
    @Mock
    private IslandsManager islandsManager;
    private List<Entity> collection;
    @Mock
    private Location location;
    private IslandBlockCount ibc;


    @BeforeEach
    public void setUp() throws Exception {
        MockBukkit.mock();

        // Entity
        when(ent.getType()).thenReturn(EntityType.ENDERMAN);
        when(ent.getLocation()).thenReturn(location);
        // Island
        when(island.getUniqueId()).thenReturn(UUID.randomUUID().toString());
        when(island.inIslandSpace(any(Location.class))).thenReturn(true);

        ibc = new IslandBlockCount("","");
        when(bll.getIsland(anyString())).thenReturn(ibc);
        when(addon.getBlockLimitListener()).thenReturn(bll);

        FileConfiguration config = new YamlConfiguration();
        config.load("src/main/resources/config.yml");
        // Settings
        when(addon.getConfig()).thenReturn(config);
        Settings settings = new Settings(addon);
        when(addon.getSettings()).thenReturn(settings);

        // World
        when(location.getWorld()).thenReturn(world);
        when(ent.getWorld()).thenReturn(world);
        collection = new ArrayList<>();
        collection.add(ent);
        collection.add(ent);
        collection.add(ent);
        collection.add(ent);
        when(world.getNearbyEntities(any())).thenReturn(collection);

        // Enable game mode world and islands manager for event handler tests
        when(addon.inGameModeWorld(world)).thenReturn(true);
        when(addon.getIslands()).thenReturn(islandsManager);
        when(islandsManager.getIslandAt(any(Location.class))).thenReturn(Optional.of(island));
        when(island.getUniqueId()).thenReturn("test-island-id");
        when(island.isSpawn()).thenReturn(false);
        when(addon.getGameModeName(world)).thenReturn("BSkyBlock");

        ell = new EntityLimitListener(addon);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
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

    // --- CreatureSpawnEvent tests ---

    @Test
    public void testCreatureSpawnShoulderEntityIgnored() {
        LivingEntity chicken = mockEntity(EntityType.CHICKEN, location);
        CreatureSpawnEvent event = new CreatureSpawnEvent(chicken, SpawnReason.SHOULDER_ENTITY);

        ell.onCreatureSpawn(event);

        assertFalse(event.isCancelled());
    }

    @Test
    public void testCreatureSpawnBreedingNonVillagerIgnored() {
        LivingEntity cow = mockEntity(EntityType.COW, location);
        CreatureSpawnEvent event = new CreatureSpawnEvent(cow, SpawnReason.BREEDING);

        ell.onCreatureSpawn(event);

        assertFalse(event.isCancelled());
    }

    @Test
    public void testCreatureSpawnOutsideGameModeWorldIgnored() {
        when(addon.inGameModeWorld(world)).thenReturn(false);

        LivingEntity chicken = mockEntity(EntityType.CHICKEN, location);
        CreatureSpawnEvent event = new CreatureSpawnEvent(chicken, SpawnReason.NATURAL);

        ell.onCreatureSpawn(event);

        assertFalse(event.isCancelled());
        verify(islandsManager, never()).getIslandAt(any(Location.class));
    }

    @Test
    public void testCreatureSpawnOnSpawnIslandAllowed() {
        when(island.isSpawn()).thenReturn(true);

        // Put CHICKEN at its limit (10 in config) by adding 10 chickens
        LivingEntity chicken = mockEntity(EntityType.CHICKEN, location);
        List<Entity> chickens = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            chickens.add(mockEntity(EntityType.CHICKEN, location));
        }
        when(world.getNearbyEntities(any())).thenReturn(chickens);

        CreatureSpawnEvent event = new CreatureSpawnEvent(chicken, SpawnReason.NATURAL);

        ell.onCreatureSpawn(event);

        assertFalse(event.isCancelled());
    }

    @Test
    public void testSpawnerSpawnReasonNoPlayerNotification() {
        // Put CHICKEN at limit so it gets cancelled
        LivingEntity chicken = mockEntity(EntityType.CHICKEN, location);
        List<Entity> chickens = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            chickens.add(mockEntity(EntityType.CHICKEN, location));
        }
        when(world.getNearbyEntities(any())).thenReturn(chickens);

        CreatureSpawnEvent event = new CreatureSpawnEvent(chicken, SpawnReason.SPAWNER);

        ell.onCreatureSpawn(event);

        assertTrue(event.isCancelled());
        // tellPlayers returns early for SPAWNER reason, so no player messages
        // (no exception from missing User/notification mocks confirms this)
    }

    // --- HangingPlaceEvent tests ---

    @Test
    public void testHangingPlaceNullPlayerIgnored() {
        Hanging hanging = mock(Hanging.class);
        when(hanging.getLocation()).thenReturn(location);
        when(hanging.getWorld()).thenReturn(world);
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        HangingPlaceEvent event = new HangingPlaceEvent(hanging, null, block, BlockFace.SOUTH, EquipmentSlot.HAND, null);

        ell.onBlock(event);

        assertFalse(event.isCancelled());
    }

    // --- EntityBreedEvent tests ---

    @Test
    public void testBreedingOpPlayerBypasses() {
        // Create an op player as breeder
        Player opPlayer = mock(Player.class);
        when(opPlayer.isOp()).thenReturn(true);

        // Create child, father, mother as Chicken mocks (implements Breedable)
        Chicken child = mock(Chicken.class);
        when(child.getType()).thenReturn(EntityType.CHICKEN);
        when(child.getWorld()).thenReturn(world);
        when(child.getLocation()).thenReturn(location);
        when(child.getUniqueId()).thenReturn(UUID.randomUUID());

        Chicken father = mock(Chicken.class);
        when(father.getType()).thenReturn(EntityType.CHICKEN);
        when(father.getWorld()).thenReturn(world);
        when(father.getLocation()).thenReturn(location);
        when(father.getUniqueId()).thenReturn(UUID.randomUUID());

        Chicken mother = mock(Chicken.class);
        when(mother.getType()).thenReturn(EntityType.CHICKEN);
        when(mother.getWorld()).thenReturn(world);
        when(mother.getLocation()).thenReturn(location);
        when(mother.getUniqueId()).thenReturn(UUID.randomUUID());

        // Set CHICKEN limit to 0 so any entity would be over limit
        ibc.setEntityLimit(EntityType.CHICKEN, 0);

        EntityBreedEvent event = new EntityBreedEvent(child, mother, father, opPlayer, null, 0);

        ell.onBreed(event);

        // Op player bypasses — setBreed(false) should NOT be called
        verify(father, never()).setBreed(false);
        verify(mother, never()).setBreed(false);
    }

    // --- helper methods ---

    private LivingEntity mockEntity(EntityType type, Location location) {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getType()).thenReturn(type);
        when(entity.getLocation()).thenReturn(location);
        when(entity.getWorld()).thenReturn(world);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        return entity;
    }

}

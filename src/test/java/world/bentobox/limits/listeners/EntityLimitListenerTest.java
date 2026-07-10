package world.bentobox.limits.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Villager;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.user.Notifier;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandWorldManager;
import world.bentobox.bentobox.managers.IslandsManager;
import world.bentobox.bentobox.managers.LocalesManager;
import world.bentobox.bentobox.managers.PlaceholdersManager;
import world.bentobox.limits.EntityGroup;
import world.bentobox.limits.Limits;
import world.bentobox.limits.Settings;
import world.bentobox.limits.listeners.EntityLimitListener.AtLimitResult;
import org.mockbukkit.mockbukkit.MockBukkit;
import world.bentobox.limits.objects.IslandBlockCount;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EntityLimitListenerTest {
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
    void setUp() throws Exception {
        MockBukkit.mock();

        // Entity
        when(ent.getType()).thenReturn(EntityType.ENDERMAN);
        when(ent.getLocation()).thenReturn(location);
        // Island
        when(island.getUniqueId()).thenReturn(UUID.randomUUID().toString());
        when(island.inIslandSpace(any(Location.class))).thenReturn(true);

        ibc = new IslandBlockCount("test-island-id","BSkyBlock");
        // Seed initial entity count for ENDERMAN to 4 (to match the 4 in collection)
        ibc.incrementEntity(Environment.NORMAL, EntityType.ENDERMAN);
        ibc.incrementEntity(Environment.NORMAL, EntityType.ENDERMAN);
        ibc.incrementEntity(Environment.NORMAL, EntityType.ENDERMAN);
        ibc.incrementEntity(Environment.NORMAL, EntityType.ENDERMAN);
        when(bll.getIsland(anyString())).thenReturn(ibc);
        when(addon.getBlockLimitListener()).thenReturn(bll);
        // Delegate the count-mutation wrappers to the real IslandBlockCount so tests
        // can assert on counts. Mirrors BlockLimitsListener's contract: increment
        // creates the record, decrement is a no-op when there is none.
        doAnswer(inv -> {
            ibc.incrementEntity(inv.getArgument(1), inv.getArgument(2));
            return null;
        }).when(bll).incrementEntity(any(Island.class), any(Environment.class), any(EntityType.class));
        doAnswer(inv -> {
            IslandBlockCount target = bll.getIsland((String) inv.getArgument(0));
            if (target != null) {
                target.decrementEntity(inv.getArgument(1), inv.getArgument(2));
            }
            return null;
        }).when(bll).decrementEntity(anyString(), any(Environment.class), any(EntityType.class));

        FileConfiguration config = new YamlConfiguration();
        config.load("src/main/resources/config.yml");
        // Settings
        when(addon.getConfig()).thenReturn(config);
        Settings settings = new Settings(addon);
        when(addon.getSettings()).thenReturn(settings);

        // World
        when(location.getWorld()).thenReturn(world);
        when(ent.getWorld()).thenReturn(world);
        when(world.getEnvironment()).thenReturn(Environment.NORMAL);
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
        when(islandsManager.getProtectedIslandAt(any(Location.class))).thenReturn(Optional.of(island));
        when(island.getUniqueId()).thenReturn("test-island-id");
        when(island.isSpawn()).thenReturn(false);
        when(addon.getGameModeName(world)).thenReturn("BSkyBlock");

        // Plugin/IWM for permission checks in hanging/breed events
        BentoBox bentoBox = mock(BentoBox.class);
        when(addon.getPlugin()).thenReturn(bentoBox);
        IslandWorldManager iwm = mock(IslandWorldManager.class);
        when(bentoBox.getIWM()).thenReturn(iwm);
        when(iwm.getPermissionPrefix(any(World.class))).thenReturn("bskyblock.");

        // User setup for notification in hanging place events
        User.setPlugin(bentoBox);
        LocalesManager localesManager = mock(LocalesManager.class);
        when(localesManager.get(any(), anyString())).thenReturn("limit hit");
        when(bentoBox.getLocalesManager()).thenReturn(localesManager);
        PlaceholdersManager placeholdersManager = mock(PlaceholdersManager.class);
        when(placeholdersManager.replacePlaceholders(any(Player.class), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(bentoBox.getPlaceholdersManager()).thenReturn(placeholdersManager);
        Notifier notifier = mock(Notifier.class);
        when(bentoBox.getNotifier()).thenReturn(notifier);

        ell = new EntityLimitListener(addon);
    }

    @AfterEach
    void tearDown() {
        User.clearUsers();
        MockBukkit.unmock();
    }

    /**
     * Test for {@link EntityLimitListener#atLimit(Island, Entity)}
     */
    @Test
    void testAtLimitUnderLimit() {
        AtLimitResult result = ell.atLimit(island, ent);
        assertFalse(result.hit());
    }

    /**
     * Test for {@link EntityLimitListener#atLimit(Island, Entity)}
     */
    @Test
    void testAtLimitAtLimit() {
        // The default limit for ENDERMAN is 5 (from config), and we have 4 already
        // Adding one more puts us at the limit
        ibc.incrementEntity(Environment.NORMAL, EntityType.ENDERMAN);
        AtLimitResult result = ell.atLimit(island, ent);
        assertTrue(result.hit());
        assertEquals(EntityType.ENDERMAN, result.getTypelimit().getKey());
        assertEquals(Integer.valueOf(5), result.getTypelimit().getValue());

    }

    /**
     * Test for {@link EntityLimitListener#atLimit(Island, Entity)}
     */
    @Test
    void testAtLimitUnderLimitIslandLimit() {
        ibc.setEntityLimit(Environment.NORMAL, EntityType.ENDERMAN, 6);
        AtLimitResult result = ell.atLimit(island, ent);
        assertFalse(result.hit());
    }

    /**
     * Test for {@link EntityLimitListener#atLimit(Island, Entity)}
     */
    @Test
    void testAtLimitAtLimitIslandLimitNotAtLimit() {
        // Island limit of 6, we have 4 already, so 5th entity should not be at limit
        ibc.setEntityLimit(Environment.NORMAL, EntityType.ENDERMAN, 6);
        ibc.incrementEntity(Environment.NORMAL, EntityType.ENDERMAN);
        AtLimitResult result = ell.atLimit(island, ent);
        assertFalse(result.hit());
    }

    /**
     * Test for {@link EntityLimitListener#atLimit(Island, Entity)}
     */
    @Test
    void testAtLimitAtLimitIslandLimit() {
        // Island limit of 6, we have 4, add 2 more to reach exactly 6
        ibc.setEntityLimit(Environment.NORMAL, EntityType.ENDERMAN, 6);
        ibc.incrementEntity(Environment.NORMAL, EntityType.ENDERMAN);
        ibc.incrementEntity(Environment.NORMAL, EntityType.ENDERMAN);
        AtLimitResult result = ell.atLimit(island, ent);
        assertTrue(result.hit());
        assertEquals(EntityType.ENDERMAN, result.getTypelimit().getKey());
        assertEquals(Integer.valueOf(6), result.getTypelimit().getValue());

    }

    // --- CreatureSpawnEvent tests ---

    @Test
    void testCreatureSpawnShoulderEntityIgnored() {
        LivingEntity chicken = mockEntity(EntityType.CHICKEN, location);
        CreatureSpawnEvent event = new CreatureSpawnEvent(chicken, SpawnReason.SHOULDER_ENTITY);

        ell.onCreatureSpawn(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void testCreatureSpawnBreedingNonVillagerIgnored() {
        LivingEntity cow = mockEntity(EntityType.COW, location);
        CreatureSpawnEvent event = new CreatureSpawnEvent(cow, SpawnReason.BREEDING);

        ell.onCreatureSpawn(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void testCreatureSpawnOutsideGameModeWorldIgnored() {
        when(addon.inGameModeWorld(world)).thenReturn(false);

        LivingEntity chicken = mockEntity(EntityType.CHICKEN, location);
        CreatureSpawnEvent event = new CreatureSpawnEvent(chicken, SpawnReason.NATURAL);

        ell.onCreatureSpawn(event);

        assertFalse(event.isCancelled());
        verify(islandsManager, never()).getIslandAt(any(Location.class));
    }

    @Test
    void testCreatureSpawnOnSpawnIslandAllowed() {
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
    void testSpawnerSpawnReasonNoPlayerNotification() {
        // Put CHICKEN at limit (10 from config) so it gets cancelled
        // Pre-fill with 10 chickens
        for (int i = 0; i < 10; i++) {
            ibc.incrementEntity(Environment.NORMAL, EntityType.CHICKEN);
        }
        LivingEntity chicken = mockEntity(EntityType.CHICKEN, location);

        CreatureSpawnEvent event = new CreatureSpawnEvent(chicken, SpawnReason.SPAWNER);

        ell.onCreatureSpawn(event);

        assertTrue(event.isCancelled());
        // tellPlayers returns early for SPAWNER reason, so no player messages
        // (no exception from missing User/notification mocks confirms this)
    }

    // --- HangingPlaceEvent tests ---

    @Test
    void testHangingPlaceNullPlayerIgnored() {
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
    void testBreedingOpPlayerBypasses() {
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
        ibc.setEntityLimit(Environment.NORMAL, EntityType.CHICKEN, 0);

        EntityBreedEvent event = new EntityBreedEvent(child, mother, father, opPlayer, null, 0);

        ell.onBreed(event);

        // Op player bypasses — setBreed(false) should NOT be called
        verify(father, never()).setBreed(false);
        verify(mother, never()).setBreed(false);
    }

    // --- CreatureSpawn at-limit tests ---

    @Test
    void testCreatureSpawnAtLimitCancels() {
        // Set island-specific CHICKEN limit to 1, pre-fill with 1
        ibc.setEntityLimit(Environment.NORMAL, EntityType.CHICKEN, 1);
        ibc.incrementEntity(Environment.NORMAL, EntityType.CHICKEN);
        LivingEntity chicken = mockEntity(EntityType.CHICKEN, location);

        CreatureSpawnEvent event = new CreatureSpawnEvent(chicken, SpawnReason.NATURAL);

        ell.onCreatureSpawn(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void testCreatureSpawnBreedingVillagerProcessed() {
        // Set island-specific VILLAGER limit to 1, pre-fill with 1
        ibc.setEntityLimit(Environment.NORMAL, EntityType.VILLAGER, 1);
        ibc.incrementEntity(Environment.NORMAL, EntityType.VILLAGER);
        Villager villager = mock(Villager.class);
        when(villager.getType()).thenReturn(EntityType.VILLAGER);
        when(villager.getLocation()).thenReturn(location);
        when(villager.getWorld()).thenReturn(world);
        when(villager.getUniqueId()).thenReturn(UUID.randomUUID());

        CreatureSpawnEvent event = new CreatureSpawnEvent(villager, SpawnReason.BREEDING);

        ell.onCreatureSpawn(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void testCreatureSpawnDebounceSkipsSecond() throws Exception {
        // Access justSpawned list via reflection
        Field justSpawnedField = EntityLimitListener.class.getDeclaredField("justSpawned");
        justSpawnedField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<UUID> justSpawned = (List<UUID>) justSpawnedField.get(ell);

        // Set CHICKEN at limit
        ibc.setEntityLimit(Environment.NORMAL, EntityType.CHICKEN, 1);
        ibc.incrementEntity(Environment.NORMAL, EntityType.CHICKEN);
        LivingEntity chicken = mockEntity(EntityType.CHICKEN, location);

        // Add entity UUID to justSpawned (debounce)
        justSpawned.add(chicken.getUniqueId());

        CreatureSpawnEvent event = new CreatureSpawnEvent(chicken, SpawnReason.NATURAL);

        ell.onCreatureSpawn(event);

        assertFalse(event.isCancelled());
    }

    // --- VehicleCreateEvent tests ---

    @Test
    void testVehicleCreateAtLimitCancels() {
        // Set island-specific MINECART limit to 1, pre-fill with 1
        ibc.setEntityLimit(Environment.NORMAL, EntityType.MINECART, 1);
        ibc.incrementEntity(Environment.NORMAL, EntityType.MINECART);
        Minecart minecart = mock(Minecart.class);
        when(minecart.getType()).thenReturn(EntityType.MINECART);
        when(minecart.getLocation()).thenReturn(location);
        when(minecart.getWorld()).thenReturn(world);
        when(minecart.getUniqueId()).thenReturn(UUID.randomUUID());

        VehicleCreateEvent event = new VehicleCreateEvent(minecart);

        ell.onMinecart(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void testVehicleCreateDebounceSkipsSecond() throws Exception {
        Field justSpawnedField = EntityLimitListener.class.getDeclaredField("justSpawned");
        justSpawnedField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<UUID> justSpawned = (List<UUID>) justSpawnedField.get(ell);

        ibc.setEntityLimit(Environment.NORMAL, EntityType.MINECART, 1);
        Minecart minecart = mock(Minecart.class);
        when(minecart.getType()).thenReturn(EntityType.MINECART);
        when(minecart.getLocation()).thenReturn(location);
        when(minecart.getWorld()).thenReturn(world);
        UUID minecartUuid = UUID.randomUUID();
        when(minecart.getUniqueId()).thenReturn(minecartUuid);

        List<Entity> minecarts = new ArrayList<>();
        minecarts.add(minecart);
        when(world.getNearbyEntities(any())).thenReturn(minecarts);

        // Add to justSpawned (debounce)
        justSpawned.add(minecartUuid);

        VehicleCreateEvent event = new VehicleCreateEvent(minecart);

        ell.onMinecart(event);

        assertFalse(event.isCancelled());
    }

    // --- HangingPlaceEvent at-limit and bypass tests ---

    @Test
    void testHangingPlaceAtLimitCancels() {
        // Set island-specific PAINTING limit to 1, pre-fill with 1
        ibc.setEntityLimit(Environment.NORMAL, EntityType.PAINTING, 1);
        ibc.incrementEntity(Environment.NORMAL, EntityType.PAINTING);
        Painting painting = mock(Painting.class);
        when(painting.getType()).thenReturn(EntityType.PAINTING);
        when(painting.getLocation()).thenReturn(location);
        when(painting.getWorld()).thenReturn(world);
        when(painting.getUniqueId()).thenReturn(UUID.randomUUID());

        Player player = mock(Player.class);
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(anyString())).thenReturn(false);

        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        HangingPlaceEvent event = new HangingPlaceEvent(painting, player, block, BlockFace.SOUTH, EquipmentSlot.HAND, null);

        ell.onBlock(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void testHangingPlaceOpPlayerBypasses() {
        // Set island-specific PAINTING limit to 1, pre-fill with 1
        ibc.setEntityLimit(Environment.NORMAL, EntityType.PAINTING, 1);
        Painting painting = mock(Painting.class);
        when(painting.getType()).thenReturn(EntityType.PAINTING);
        when(painting.getLocation()).thenReturn(location);
        when(painting.getWorld()).thenReturn(world);
        when(painting.getUniqueId()).thenReturn(UUID.randomUUID());

        List<Entity> paintings = new ArrayList<>();
        paintings.add(painting);
        when(world.getNearbyEntities(any())).thenReturn(paintings);

        Player opPlayer = mock(Player.class);
        when(opPlayer.isOp()).thenReturn(true);

        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        HangingPlaceEvent event = new HangingPlaceEvent(painting, opPlayer, block, BlockFace.SOUTH, EquipmentSlot.HAND, null);

        ell.onBlock(event);

        assertFalse(event.isCancelled());
    }

    // --- Item frame limiting (#66) ---

    @Test
    void testItemFramePlaceAtLimitCancels() {
        ibc.setEntityLimit(Environment.NORMAL, EntityType.ITEM_FRAME, 1);
        ibc.incrementEntity(Environment.NORMAL, EntityType.ITEM_FRAME);
        ItemFrame frame = mockItemFrame();

        Player player = mock(Player.class);
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(anyString())).thenReturn(false);

        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        HangingPlaceEvent event = new HangingPlaceEvent(frame, player, block, BlockFace.SOUTH, EquipmentSlot.HAND, null);

        ell.onBlock(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void testItemFramePlaceUnderLimitAllowedAndTracked() {
        ibc.setEntityLimit(Environment.NORMAL, EntityType.ITEM_FRAME, 2);
        ibc.incrementEntity(Environment.NORMAL, EntityType.ITEM_FRAME);
        ItemFrame frame = mockItemFrame();

        Player player = mock(Player.class);
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(anyString())).thenReturn(false);

        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        HangingPlaceEvent event = new HangingPlaceEvent(frame, player, block, BlockFace.SOUTH, EquipmentSlot.HAND, null);

        ell.onBlock(event);
        assertFalse(event.isCancelled());

        // MONITOR-priority tracker counts the placed frame
        ell.onHangingPlaceTrack(event);
        assertEquals(2, ibc.getEntityCount(Environment.NORMAL, EntityType.ITEM_FRAME));
    }

    @Test
    void testItemFrameRemoveDecrements() throws Exception {
        ItemFrame frame = mockItemFrame();
        entityIslandMap().put(frame.getUniqueId(), "test-island-id");
        ibc.incrementEntity(Environment.NORMAL, EntityType.ITEM_FRAME);
        int before = ibc.getEntityCount(Environment.NORMAL, EntityType.ITEM_FRAME);

        // Frame popped off the wall (dropped as an item) — must decrement like any removal
        ell.onEntityRemove(new EntityRemoveEvent(frame, EntityRemoveEvent.Cause.DROP));

        assertEquals(before - 1, ibc.getEntityCount(Environment.NORMAL, EntityType.ITEM_FRAME));
        assertFalse(entityIslandMap().containsKey(frame.getUniqueId()));
    }

    private ItemFrame mockItemFrame() {
        ItemFrame frame = mock(ItemFrame.class);
        when(frame.getType()).thenReturn(EntityType.ITEM_FRAME);
        when(frame.getLocation()).thenReturn(location);
        when(frame.getWorld()).thenReturn(world);
        when(frame.getUniqueId()).thenReturn(UUID.randomUUID());
        return frame;
    }

    // --- Entity group limit tests ---

    @Test
    void testEntityGroupLimitBlocksSpawnWhenGroupFull() {
        // Create a custom group "testanimals" covering CHICKEN and COW with limit 2
        EntityGroup testGroup = new EntityGroup("testanimals", Set.of(EntityType.CHICKEN, EntityType.COW), 2, Material.BARRIER);
        Settings settings = addon.getSettings();
        // Add to entity type → group lookup
        settings.getGroupLimits().put(EntityType.CHICKEN, new ArrayList<>(List.of(testGroup)));
        settings.getGroupLimits().put(EntityType.COW, new ArrayList<>(List.of(testGroup)));
        // Also add the per-environment limit for the group (via reflection since we can't directly access the map)
        try {
            java.lang.reflect.Field envGroupLimitsField = Settings.class.getDeclaredField("envGroupLimits");
            envGroupLimitsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Environment, java.util.Map<String, Integer>> envGroupLimits =
                    (java.util.Map<Environment, java.util.Map<String, Integer>>) envGroupLimitsField.get(settings);
            envGroupLimits.computeIfAbsent(Environment.NORMAL, k -> new java.util.HashMap<>())
                    .put("testanimals", 2);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        // Pre-fill with 2 entities in the group (1 CHICKEN + 1 COW)
        ibc.incrementEntity(Environment.NORMAL, EntityType.CHICKEN);
        ibc.incrementEntity(Environment.NORMAL, EntityType.COW);

        // Try to spawn another CHICKEN
        LivingEntity newChicken = mockEntity(EntityType.CHICKEN, location);
        CreatureSpawnEvent event = new CreatureSpawnEvent(newChicken, SpawnReason.NATURAL);

        ell.onCreatureSpawn(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void testEntityGroupLimitAllowsSpawnWhenGroupUnderLimit() {
        // Create a custom group "testanimals" covering CHICKEN and COW with limit 3
        EntityGroup testGroup = new EntityGroup("testanimals", Set.of(EntityType.CHICKEN, EntityType.COW), 3, Material.BARRIER);
        Settings settings = addon.getSettings();
        settings.getGroupLimits().put(EntityType.CHICKEN, new ArrayList<>(List.of(testGroup)));
        settings.getGroupLimits().put(EntityType.COW, new ArrayList<>(List.of(testGroup)));

        // Pre-fill with 2 entities in the group (under limit of 3)
        LivingEntity chickenEntity = mockEntity(EntityType.CHICKEN, location);
        LivingEntity cowEntity = mockEntity(EntityType.COW, location);
        List<Entity> entities = new ArrayList<>();
        entities.add(chickenEntity);
        entities.add(cowEntity);
        when(world.getNearbyEntities(any())).thenReturn(entities);

        // Try to spawn a COW (group count = 2, limit = 3 → allowed)
        LivingEntity newCow = mockEntity(EntityType.COW, location);
        CreatureSpawnEvent event = new CreatureSpawnEvent(newCow, SpawnReason.NATURAL);

        ell.onCreatureSpawn(event);

        assertFalse(event.isCancelled());
    }

    // --- EntityAddToWorldEvent / restart-drift tests (#270) ---

    @Test
    void testEntityAddToWorldPopulatesMapForLoadedEntity() throws Exception {
        LivingEntity enderman = mockEntity(EntityType.ENDERMAN, location);
        EntityAddToWorldEvent event = new EntityAddToWorldEvent(enderman, world);

        ell.onEntityAddToWorld(event);

        assertEquals("test-island-id", entityIslandMap().get(enderman.getUniqueId()));
    }

    @Test
    void testEntityAddToWorldIgnoresNonTrackedEntity() throws Exception {
        // A projectile/item is neither LivingEntity, Vehicle nor Hanging — never mapped, no lookup.
        Entity arrow = mock(Entity.class);
        when(arrow.getUniqueId()).thenReturn(UUID.randomUUID());
        EntityAddToWorldEvent event = new EntityAddToWorldEvent(arrow, world);

        ell.onEntityAddToWorld(event);

        assertTrue(entityIslandMap().isEmpty());
        verify(islandsManager, never()).getIslandAt(any(Location.class));
    }

    @Test
    void testEntityAddToWorldOutsideGameModeWorldIgnored() throws Exception {
        when(addon.inGameModeWorld(world)).thenReturn(false);
        LivingEntity enderman = mockEntity(EntityType.ENDERMAN, location);
        EntityAddToWorldEvent event = new EntityAddToWorldEvent(enderman, world);

        ell.onEntityAddToWorld(event);

        assertTrue(entityIslandMap().isEmpty());
    }

    @Test
    void testEntityAddToWorldOnSpawnIslandNotMapped() throws Exception {
        when(island.isSpawn()).thenReturn(true);
        LivingEntity enderman = mockEntity(EntityType.ENDERMAN, location);
        EntityAddToWorldEvent event = new EntityAddToWorldEvent(enderman, world);

        ell.onEntityAddToWorld(event);

        assertTrue(entityIslandMap().isEmpty());
    }

    @Test
    void testEntityAddToWorldSkipsAlreadyMappedEntity() throws Exception {
        LivingEntity enderman = mockEntity(EntityType.ENDERMAN, location);
        entityIslandMap().put(enderman.getUniqueId(), "existing-island");
        EntityAddToWorldEvent event = new EntityAddToWorldEvent(enderman, world);

        ell.onEntityAddToWorld(event);

        // Mapping is untouched and no redundant getIslandAt lookup is performed.
        assertEquals("existing-island", entityIslandMap().get(enderman.getUniqueId()));
        verify(islandsManager, never()).getIslandAt(any(Location.class));
    }

    @Test
    void testEntityRemoveUnloadEvictsMappingWithoutDecrement() throws Exception {
        LivingEntity enderman = mockEntity(EntityType.ENDERMAN, location);
        entityIslandMap().put(enderman.getUniqueId(), "test-island-id");
        int before = ibc.getEntityCount(Environment.NORMAL, EntityType.ENDERMAN);

        ell.onEntityRemove(new EntityRemoveEvent(enderman, EntityRemoveEvent.Cause.UNLOAD));

        // Mapping dropped so it can't leak, but the count is untouched — the entity is still alive,
        // just unloaded with its chunk; onEntityAddToWorld re-populates it on reload.
        assertFalse(entityIslandMap().containsKey(enderman.getUniqueId()));
        assertEquals(before, ibc.getEntityCount(Environment.NORMAL, EntityType.ENDERMAN));
    }

    @Test
    void testReloadedEntityDecrementsWhenItDiesOffIsland() throws Exception {
        // Simulate a restart: the in-memory map starts empty and the entity is reloaded from a chunk.
        LivingEntity enderman = mockEntity(EntityType.ENDERMAN, location);
        ell.onEntityAddToWorld(new EntityAddToWorldEvent(enderman, world));
        assertEquals("test-island-id", entityIslandMap().get(enderman.getUniqueId()));

        int before = ibc.getEntityCount(Environment.NORMAL, EntityType.ENDERMAN);

        // It wanders off the island and dies there, so getIslandAt no longer resolves an island.
        Location offIsland = mock(Location.class);
        when(offIsland.getWorld()).thenReturn(world);
        when(islandsManager.getIslandAt(offIsland)).thenReturn(Optional.empty());
        when(enderman.getLocation()).thenReturn(offIsland);

        ell.onEntityRemove(new EntityRemoveEvent(enderman, EntityRemoveEvent.Cause.DEATH));

        // The cached mapping lets the decrement happen even though the death was off-island —
        // before #270 this entry was lost on restart and the count drifted upward.
        assertEquals(before - 1, ibc.getEntityCount(Environment.NORMAL, EntityType.ENDERMAN));
        assertFalse(entityIslandMap().containsKey(enderman.getUniqueId()));
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, String> entityIslandMap() throws Exception {
        Field f = EntityLimitListener.class.getDeclaredField("entityIslandMap");
        f.setAccessible(true);
        return (Map<UUID, String>) f.get(ell);
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

    // --- Golem / snowman block-removal tests (#127) ---

    @Test
    void testDetectIronGolemRemovesAllBlocksWhenSpawnedAtBody() throws Exception {
        grid = new HashMap<>();
        Block base = setBlock(0, 64, 0, Material.IRON_BLOCK);
        Block body = setBlock(0, 65, 0, Material.IRON_BLOCK);
        Block head = setBlock(0, 66, 0, Material.CARVED_PUMPKIN);
        Block arm1 = setBlock(0, 65, -1, Material.IRON_BLOCK); // NORTH
        Block arm2 = setBlock(0, 65, 1, Material.IRON_BLOCK); // SOUTH

        // Vanilla can spawn the golem at the BODY block; the old base-anchored search missed
        // everything except the spawn block, leaving the base, arms and pumpkin behind (#127).
        invokeDetect("detectIronGolem", spawnAt(body));

        for (Block b : List.of(base, body, head, arm1, arm2)) {
            verify(bll).removeBlock(b);
            verify(b).setType(Material.AIR);
        }
    }

    @Test
    void testDetectIronGolemRemovesAllBlocksWhenSpawnedAtBase() throws Exception {
        grid = new HashMap<>();
        Block base = setBlock(0, 64, 0, Material.IRON_BLOCK);
        Block body = setBlock(0, 65, 0, Material.IRON_BLOCK);
        Block head = setBlock(0, 66, 0, Material.CARVED_PUMPKIN);
        Block arm1 = setBlock(1, 65, 0, Material.IRON_BLOCK); // EAST
        Block arm2 = setBlock(-1, 65, 0, Material.IRON_BLOCK); // WEST

        invokeDetect("detectIronGolem", spawnAt(base));

        for (Block b : List.of(base, body, head, arm1, arm2)) {
            verify(bll).removeBlock(b);
            verify(b).setType(Material.AIR);
        }
    }

    @Test
    void testDetectSnowmanRemovesAllBlocks() throws Exception {
        grid = new HashMap<>();
        Block base = setBlock(0, 64, 0, Material.SNOW_BLOCK);
        Block body = setBlock(0, 65, 0, Material.SNOW_BLOCK);
        Block head = setBlock(0, 66, 0, Material.JACK_O_LANTERN);

        invokeDetect("detectSnowman", spawnAt(body));

        for (Block b : List.of(base, body, head)) {
            verify(bll).removeBlock(b);
            verify(b).setType(Material.AIR);
        }
    }

    @Test
    void testDetectIronGolemLeavesUnrelatedBlocksAlone() throws Exception {
        grid = new HashMap<>();
        // A lone iron block with no pumpkin is not a golem — nothing may be erased.
        Block stray = setBlock(0, 65, 0, Material.IRON_BLOCK);

        invokeDetect("detectIronGolem", spawnAt(stray));

        verify(stray, never()).setType(Material.AIR);
        verify(bll, never()).removeBlock(any(Block.class));
    }

    /** Coordinate-keyed mock blocks whose getRelative() walks the shared grid. */
    private Map<List<Integer>, Block> grid;

    private Block gridBlock(int x, int y, int z) {
        return grid.computeIfAbsent(List.of(x, y, z), k -> {
            Block b = mock(Block.class);
            when(b.getType()).thenReturn(Material.AIR);
            when(b.getRelative(any(BlockFace.class))).thenAnswer(inv -> {
                BlockFace f = inv.getArgument(0);
                return gridBlock(x + f.getModX(), y + f.getModY(), z + f.getModZ());
            });
            return b;
        });
    }

    private Block setBlock(int x, int y, int z, Material type) {
        Block b = gridBlock(x, y, z);
        when(b.getType()).thenReturn(type);
        return b;
    }

    private Location spawnAt(Block block) {
        Location loc = mock(Location.class);
        when(loc.getBlock()).thenReturn(block);
        return loc;
    }

    private void invokeDetect(String method, Location loc) throws Exception {
        Method m = EntityLimitListener.class.getDeclaredMethod(method, Location.class);
        m.setAccessible(true);
        m.invoke(ell, loc);
    }

    // --- Spawn-egg interact guard tests (#134) ---

    @Test
    void testSpawnEggOnEntityAtLimitIsCancelled() {
        ibc.setEntityLimit(Environment.NORMAL, EntityType.ENDERMAN, 4); // seeded count is 4 -> at limit
        Player p = eggPlayer(Material.ENDERMAN_SPAWN_EGG, EquipmentSlot.HAND);
        Entity clicked = mock(Entity.class);
        when(clicked.getLocation()).thenReturn(location);
        PlayerInteractEntityEvent e = new PlayerInteractEntityEvent(p, clicked, EquipmentSlot.HAND);

        ell.onSpawnEggUseOnEntity(e);

        // Cancelled before the egg is consumed, rather than only blocking the later spawn.
        assertTrue(e.isCancelled());
    }

    @Test
    void testSpawnEggOnEntityUnderLimitNotCancelled() {
        ibc.setEntityLimit(Environment.NORMAL, EntityType.ENDERMAN, 10); // count 4 < 10
        Player p = eggPlayer(Material.ENDERMAN_SPAWN_EGG, EquipmentSlot.HAND);
        Entity clicked = mock(Entity.class);
        when(clicked.getLocation()).thenReturn(location);
        PlayerInteractEntityEvent e = new PlayerInteractEntityEvent(p, clicked, EquipmentSlot.HAND);

        ell.onSpawnEggUseOnEntity(e);

        assertFalse(e.isCancelled());
    }

    @Test
    void testNonSpawnEggOnEntityIgnored() {
        ibc.setEntityLimit(Environment.NORMAL, EntityType.ENDERMAN, 4);
        Player p = eggPlayer(Material.STICK, EquipmentSlot.HAND);
        Entity clicked = mock(Entity.class);
        when(clicked.getLocation()).thenReturn(location);
        PlayerInteractEntityEvent e = new PlayerInteractEntityEvent(p, clicked, EquipmentSlot.HAND);

        ell.onSpawnEggUseOnEntity(e);

        assertFalse(e.isCancelled());
    }

    @Test
    void testSpawnEggOnBlockAtLimitIsCancelled() {
        ibc.setEntityLimit(Environment.NORMAL, EntityType.ENDERMAN, 4);
        Player p = mock(Player.class);
        when(p.isOp()).thenReturn(false);
        when(p.hasPermission(anyString())).thenReturn(false);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        Block clicked = mock(Block.class);
        when(clicked.getLocation()).thenReturn(location);
        PlayerInteractEvent e = new PlayerInteractEvent(p, Action.RIGHT_CLICK_BLOCK,
                new ItemStack(Material.ENDERMAN_SPAWN_EGG), clicked, BlockFace.UP);

        ell.onSpawnEggUseOnBlock(e);

        // PlayerInteractEvent.isCancelled() is deprecated; cancelling denies the item-in-hand use
        assertEquals(Event.Result.DENY, e.useItemInHand());
    }

    private Player eggPlayer(Material item, EquipmentSlot hand) {
        Player p = mock(Player.class);
        when(p.isOp()).thenReturn(false);
        when(p.hasPermission(anyString())).thenReturn(false);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        PlayerInventory inv = mock(PlayerInventory.class);
        when(p.getInventory()).thenReturn(inv);
        when(inv.getItem(hand)).thenReturn(new ItemStack(item));
        return p;
    }

}

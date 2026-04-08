package world.bentobox.limits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.mockbukkit.mockbukkit.MockBukkit;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SettingsTest {

    @Mock
    private Limits addon;

    private Settings settings;
    private FileConfiguration config;

    @BeforeEach
    public void setUp() throws Exception {
        MockBukkit.mock();

        config = new YamlConfiguration();
        config.load("src/main/resources/config.yml");
        when(addon.getConfig()).thenReturn(config);
        doAnswer(invocation -> null).when(addon).log(anyString());
        doAnswer(invocation -> null).when(addon).logError(anyString());

        settings = new Settings(addon);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void testGameModesNotEmpty() {
        List<String> gameModes = settings.getGameModes();
        assertNotNull(gameModes);
        assertFalse(gameModes.isEmpty());
        assertTrue(gameModes.contains("BSkyBlock"));
    }

    @Test
    public void testEntityLimitsParsed() {
        Map<EntityType, Integer> limits = settings.getLimits();
        assertNotNull(limits);
        assertFalse(limits.isEmpty());
        assertEquals(5, limits.get(EntityType.ENDERMAN));
        assertEquals(10, limits.get(EntityType.CHICKEN));
    }

    @Test
    public void testGroupLimitsParsed() {
        Map<EntityType, List<EntityGroup>> groupLimits = settings.getGroupLimits();
        assertNotNull(groupLimits);
        assertFalse(groupLimits.isEmpty());
        // ENDERMAN should be in a group (Monsters)
        assertTrue(groupLimits.containsKey(EntityType.ENDERMAN));
    }

    @Test
    public void testGetGroupLimitDefinitions() {
        List<EntityGroup> definitions = settings.getGroupLimitDefinitions();
        assertNotNull(definitions);
        assertFalse(definitions.isEmpty());
        // Should have Monsters and Animals groups
        assertTrue(definitions.stream().anyMatch(g -> g.getName().equals("Monsters")));
        assertTrue(definitions.stream().anyMatch(g -> g.getName().equals("Animals")));
    }

    @Test
    public void testLogLimitsOnJoinDefaultsTrue() {
        assertTrue(settings.isLogLimitsOnJoin());
    }

    @Test
    public void testAsyncGolumsDefaultsTrue() {
        assertTrue(settings.isAsyncGolums());
    }

    @Test
    public void testAutoRefreshDefaultsOneHour() {
        assertEquals(3600, settings.getAutoRefreshSeconds());
    }

    @Test
    public void testAutoRefreshCanBeOverridden() {
        config.set("auto-refresh-seconds", 120);
        Settings s = new Settings(addon);
        assertEquals(120, s.getAutoRefreshSeconds());
    }

    @Test
    public void testGetGeneralEmpty() {
        // Default config.yml does not have ANIMALS or MOBS entries in entitylimits
        Map<Settings.GeneralGroup, Integer> general = settings.getGeneral();
        assertNotNull(general);
        // The real config.yml doesn't define ANIMALS/MOBS general groups
        assertTrue(general.isEmpty());
    }

    @Test
    public void testGetGeneralWithAnimalsAndMobs() throws Exception {
        // Add ANIMALS and MOBS to the entitylimits section
        config.set("entitylimits.ANIMALS", 100);
        config.set("entitylimits.MOBS", 50);
        Settings s = new Settings(addon);
        Map<Settings.GeneralGroup, Integer> general = s.getGeneral();
        assertEquals(100, general.get(Settings.GeneralGroup.ANIMALS));
        assertEquals(50, general.get(Settings.GeneralGroup.MOBS));
    }

    @Test
    public void testUnknownEntityTypeLogsError() throws Exception {
        config.set("entitylimits.UNKNOWN_ENTITY_XYZ", 99);
        Settings s = new Settings(addon);
        verify(addon, atLeastOnce()).logError("Unknown entity type: UNKNOWN_ENTITY_XYZ - skipping...");
        assertFalse(s.getLimits().containsValue(99));
    }

    @Test
    public void testDisallowedEntityTypeLogsError() throws Exception {
        config.set("entitylimits.TNT", 10);
        Settings s = new Settings(addon);
        verify(addon).logError("Entity type: TNT is not supported - skipping...");
        assertFalse(s.getLimits().containsKey(EntityType.TNT));
    }

    @Test
    public void testInvalidGroupIconFallsBackToBarrier() throws Exception {
        // Clear existing groups and add one with invalid icon
        config.set("entitygrouplimits", null);
        config.set("entitygrouplimits.TestGroup.icon", "NOT_A_REAL_MATERIAL");
        config.set("entitygrouplimits.TestGroup.limit", 10);
        config.set("entitygrouplimits.TestGroup.entities", List.of("CHICKEN", "COW"));

        Settings s = new Settings(addon);
        verify(addon).logError("Invalid group icon name: NOT_A_REAL_MATERIAL. Use a Bukkit Material.");

        List<EntityGroup> groups = s.getGroupLimitDefinitions();
        EntityGroup testGroup = groups.stream()
                .filter(g -> g.getName().equals("TestGroup"))
                .findFirst()
                .orElse(null);
        assertNotNull(testGroup);
        assertEquals(Material.BARRIER, testGroup.getIcon());
    }
}

package world.bentobox.limits.commands.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.World.Environment;
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

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.panels.PanelItem;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandWorldManager;
import world.bentobox.limits.Limits;
import world.bentobox.limits.Settings;
import world.bentobox.limits.objects.IslandBlockCount;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class LimitTabTest {

    @Mock private Limits addon;
    @Mock private Island island;
    @Mock private World world;
    @Mock private BentoBox plugin;
    @Mock private IslandWorldManager iwm;
    @Mock private Settings settings;
    @Mock private User user;

    @BeforeEach
    public void setUp() {
        MockBukkit.mock();
        when(island.getWorld()).thenReturn(world);
        when(addon.getPlugin()).thenReturn(plugin);
        when(addon.getSettings()).thenReturn(settings);
        when(plugin.getIWM()).thenReturn(iwm);
        when(iwm.isNetherIslands(any())).thenReturn(false);
        when(iwm.isEndIslands(any())).thenReturn(false);
        when(settings.getLimits(any(Environment.class))).thenReturn(Collections.emptyMap());
        when(settings.getGroupLimits(any(Environment.class))).thenReturn(Collections.emptyMap());
        when(settings.getGroupLimitDefinitions()).thenReturn(Collections.emptyList());
        // User translations: render a known set of keys to their locale templates so that
        // placeholder substitution produces something we can grep in test assertions.
        lenient().when(user.getTranslation(any(World.class), anyString(), any(String[].class))).thenAnswer(inv -> {
            Object[] all = inv.getArguments();
            String key = (String) all[1];
            Object[] vars = new Object[all.length - 2];
            System.arraycopy(all, 2, vars, 0, vars.length);
            return renderTranslation(templateFor(key), vars);
        });
        lenient().when(user.getTranslation(anyString(), any(String[].class))).thenAnswer(inv -> {
            Object[] all = inv.getArguments();
            String key = (String) all[0];
            Object[] vars = new Object[all.length - 1];
            System.arraycopy(all, 1, vars, 0, vars.length);
            return renderTranslation(templateFor(key), vars);
        });
        lenient().when(user.getTranslation(anyString())).thenAnswer(inv -> templateFor(inv.getArgument(0)));
        lenient().when(user.getTranslation(any(World.class), anyString()))
                .thenAnswer(inv -> templateFor(inv.getArgument(1)));
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    /** Resolve a translation key to its locale template text (subset matching the en-US.yml). */
    private static String templateFor(String key) {
        return switch (key) {
            case "island.limits.panel.title-syntax" -> "[title] [env]";
            case "island.limits.panel.entity-name-syntax",
                 "island.limits.panel.entity-group-name-syntax",
                 "island.limits.panel.block-name-syntax" -> "[name]";
            case "island.limits.panel.env-overworld" -> "Overworld";
            case "island.limits.panel.env-nether" -> "Nether";
            case "island.limits.panel.env-end" -> "End";
            case "limits.panel-title" -> "Island limits";
            case "island.limits.block-limit-syntax" -> "[number]/[limit]";
            case "island.limits.max-color" -> "&c";
            case "island.limits.regular-color" -> "&a";
            default -> key;
        };
    }

    /** Replace [name]/[number]/[limit]/[env] placeholders so test assertions can read them back. */
    private static String renderTranslation(String template, Object[] vars) {
        StringBuilder out = new StringBuilder(template);
        if (vars != null) {
            for (int i = 0; i + 1 < vars.length; i += 2) {
                String placeholder = String.valueOf(vars[i]);
                String val = String.valueOf(vars[i + 1]);
                int idx;
                while ((idx = out.indexOf(placeholder)) >= 0) {
                    out.replace(idx, idx + placeholder.length(), val);
                }
            }
        }
        return out.toString();
    }

    @Test
    void blockCountReadsFromEnvKeyedIbc() {
        IslandBlockCount ibc = new IslandBlockCount("island", "BSkyBlock");
        NamespacedKey hopper = Material.HOPPER.getKey();
        // Place 3 hoppers in the overworld and 7 in the nether.
        for (int i = 0; i < 3; i++) ibc.add(Environment.NORMAL, hopper);
        for (int i = 0; i < 7; i++) ibc.add(Environment.NETHER, hopper);

        Map<NamespacedKey, Integer> matLimits = Map.of(hopper, 10);

        LimitTab overworldTab = new LimitTab(addon, ibc, matLimits, island, world, user, Environment.NORMAL);
        LimitTab netherTab = new LimitTab(addon, ibc, matLimits, island, world, user, Environment.NETHER);

        assertEquals("3", findCount(overworldTab, "hopper"), "3 hoppers visible in overworld tab");
        assertEquals("7", findCount(netherTab, "hopper"), "7 hoppers visible in nether tab");
    }

    @Test
    void entityCountReadsFromEnvKeyedIbc() {
        IslandBlockCount ibc = new IslandBlockCount("island", "BSkyBlock");
        for (int i = 0; i < 4; i++) ibc.incrementEntity(Environment.NORMAL, EntityType.CHICKEN);
        for (int i = 0; i < 9; i++) ibc.incrementEntity(Environment.NETHER, EntityType.CHICKEN);
        when(settings.getLimits(Environment.NORMAL)).thenReturn(Map.of(EntityType.CHICKEN, 10));
        when(settings.getLimits(Environment.NETHER)).thenReturn(Map.of(EntityType.CHICKEN, 10));

        LimitTab overworldTab = new LimitTab(addon, ibc, Collections.emptyMap(), island, world, user, Environment.NORMAL);
        LimitTab netherTab = new LimitTab(addon, ibc, Collections.emptyMap(), island, world, user, Environment.NETHER);

        assertEquals("4", findCount(overworldTab, "chicken"));
        assertEquals("9", findCount(netherTab, "chicken"));
    }

    @Test
    void tabIconAndTitleReflectEnvironment() {
        IslandBlockCount ibc = new IslandBlockCount("island", "BSkyBlock");
        LimitTab netherTab = new LimitTab(addon, ibc, Collections.emptyMap(), island, world, user, Environment.NETHER);
        assertNotNull(netherTab.getIcon());
        assertEquals(Material.NETHERRACK, netherTab.getIcon().getItem().getType());
        assertTrue(netherTab.getName().contains("Nether"), "Title should contain 'Nether'; was: " + netherTab.getName());
        assertTrue(netherTab.getName().contains("Island limits"),
                "Title should contain 'Island limits'; was: " + netherTab.getName());
    }

    /**
     * Pull the count value (the {@code [number]} placeholder) out of the first panel-item description
     * containing the given material/entity key. Returns null if not found.
     */
    private static String findCount(LimitTab tab, String keyFragment) {
        List<? extends PanelItem> items = tab.getPanelItems();
        for (PanelItem item : items) {
            String desc = String.valueOf(item.getDescription());
            String name = String.valueOf(item.getName());
            if (name.toLowerCase().contains(keyFragment) || desc.toLowerCase().contains(keyFragment)) {
                // description format is "<color>[number]/[limit]" and renderTranslation replaced the placeholders
                // Find the first run of digits
                StringBuilder digits = new StringBuilder();
                for (int i = 0; i < desc.length(); i++) {
                    char c = desc.charAt(i);
                    if (Character.isDigit(c)) digits.append(c);
                    else if (!digits.isEmpty()) break;
                }
                return !digits.isEmpty() ? digits.toString() : null;
            }
        }
        return null;
    }
}

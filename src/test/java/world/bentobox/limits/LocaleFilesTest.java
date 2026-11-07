package world.bentobox.limits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Checks that every bundled locale fills in the placeholders the code passes, so no player
 * sees a raw placeholder such as {@code [sort]} in the limits panel title.
 */
class LocaleFilesTest {

    private static final String TITLE_SYNTAX = "island.limits.panel.title-syntax";

    private static List<File> localeFiles() {
        File[] files = new File("src/main/resources/locales").listFiles((dir, name) -> name.endsWith(".yml"));
        assertNotNull(files);
        assertTrue(files.length > 1, "expected the bundled locale files");
        return Arrays.asList(files);
    }

    @Test
    void testPanelTitleUsesTheEnvPlaceholder() {
        for (File file : localeFiles()) {
            String title = YamlConfiguration.loadConfiguration(file).getString(TITLE_SYNTAX);
            assertNotNull(title, file.getName() + " has no " + TITLE_SYNTAX);
            assertTrue(title.contains("[title]") && title.contains("[env]"),
                    file.getName() + " title-syntax must use [title] and [env]; was " + title);
        }
    }

    @Test
    void testEveryDimensionHasAName() {
        for (File file : localeFiles()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String env : List.of("overworld", "nether", "end", "all")) {
                String key = "island.limits.panel.env-" + env;
                assertNotNull(yaml.getString(key), file.getName() + " has no " + key);
            }
            assertEquals(4, yaml.getConfigurationSection("island.limits.panel").getKeys(false).stream()
                    .filter(k -> k.startsWith("env-")).count(), file.getName());
        }
    }
}

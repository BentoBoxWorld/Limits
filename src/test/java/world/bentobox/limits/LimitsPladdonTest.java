package world.bentobox.limits;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.mock;

import world.bentobox.bentobox.api.addons.Addon;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class LimitsPladdonTest {

    private LimitsPladdon pladdon;

    @BeforeEach
    public void setUp() {
        // LimitsPladdon extends Pladdon extends JavaPlugin, which requires PluginClassLoader.
        // Use mock with CALLS_REAL_METHODS to bypass the JavaPlugin constructor.
        pladdon = mock(LimitsPladdon.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    }

    @Test
    public void testGetAddonReturnsNonNull() {
        Addon addon = pladdon.getAddon();
        assertNotNull(addon);
    }

    @Test
    public void testGetAddonReturnsSameInstance() {
        Addon first = pladdon.getAddon();
        Addon second = pladdon.getAddon();
        assertSame(first, second);
    }

    @Test
    public void testGetAddonIsInstanceOfLimits() {
        Addon addon = pladdon.getAddon();
        assertInstanceOf(Limits.class, addon);
    }
}

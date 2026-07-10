package world.bentobox.limits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import world.bentobox.bentobox.api.user.User;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisplayNamesTest {

    @Mock
    private User user;

    @Test
    void testMaterialTranslated() {
        when(user.getTranslationOrNothing(anyString())).thenReturn("");
        when(user.getTranslationOrNothing("island.limits.materials.hopper")).thenReturn("Trichter");
        assertEquals("Trichter", DisplayNames.material(user, Material.HOPPER.getKey()));
    }

    @Test
    void testMaterialFallsBackToPrettyName() {
        when(user.getTranslationOrNothing(anyString())).thenReturn("");
        assertEquals("Hopper", DisplayNames.material(user, Material.HOPPER.getKey()));
    }

    @Test
    void testEntityTranslated() {
        when(user.getTranslationOrNothing(anyString())).thenReturn("");
        when(user.getTranslationOrNothing("island.limits.entities.enderman")).thenReturn("Enderman (DE)");
        assertEquals("Enderman (DE)", DisplayNames.entity(user, EntityType.ENDERMAN));
    }

    @Test
    void testEntityFallsBackToPrettyName() {
        when(user.getTranslationOrNothing(anyString())).thenReturn("");
        assertEquals("Enderman", DisplayNames.entity(user, EntityType.ENDERMAN));
    }
}

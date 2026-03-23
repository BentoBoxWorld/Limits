package world.bentobox.limits.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Shulker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.papermc.paper.event.entity.ShulkerDuplicateEvent;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandsManager;
import world.bentobox.limits.Limits;
import world.bentobox.limits.listeners.EntityLimitListener.AtLimitResult;
import org.mockbukkit.mockbukkit.MockBukkit;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PaperShulkerLimitListenerTest {

    @Mock
    private Limits addon;
    @Mock
    private EntityLimitListener entityLimitListener;
    @Mock
    private ShulkerDuplicateEvent event;
    @Mock
    private Shulker shulker;
    @Mock
    private World world;
    @Mock
    private Location location;
    @Mock
    private Island island;
    @Mock
    private IslandsManager im;

    private PaperShulkerLimitListener listener;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();

        when(event.getEntity()).thenReturn(shulker);
        when(shulker.getWorld()).thenReturn(world);
        when(shulker.getLocation()).thenReturn(location);

        when(addon.getIslands()).thenReturn(im);
        when(im.getIslandAt(any(Location.class))).thenReturn(Optional.of(island));
        when(island.isSpawn()).thenReturn(false);

        listener = new PaperShulkerLimitListener(addon, entityLimitListener);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testOnShulkerDuplicateNotInGameModeWorld() {
        when(addon.inGameModeWorld(world)).thenReturn(false);

        listener.onShulkerDuplicate(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void testOnShulkerDuplicateNoIslandAtLocation() {
        when(addon.inGameModeWorld(world)).thenReturn(true);
        when(im.getIslandAt(any(Location.class))).thenReturn(Optional.empty());

        listener.onShulkerDuplicate(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void testOnShulkerDuplicateIslandIsSpawn() {
        when(addon.inGameModeWorld(world)).thenReturn(true);
        when(island.isSpawn()).thenReturn(true);

        listener.onShulkerDuplicate(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void testOnShulkerDuplicateAtLimit() {
        when(addon.inGameModeWorld(world)).thenReturn(true);
        AtLimitResult hitResult = new AtLimitResult(EntityType.SHULKER, 5);

        when(entityLimitListener.atLimit(any(Island.class), any(Entity.class))).thenReturn(hitResult);

        listener.onShulkerDuplicate(event);

        verify(event).setCancelled(true);
    }

    @Test
    void testOnShulkerDuplicateNotAtLimit() {
        when(addon.inGameModeWorld(world)).thenReturn(true);
        AtLimitResult noHitResult = new AtLimitResult();

        when(entityLimitListener.atLimit(any(Island.class), any(Entity.class))).thenReturn(noHitResult);

        listener.onShulkerDuplicate(event);

        verify(event, never()).setCancelled(true);
    }
}

package world.bentobox.limits.commands.player;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandsManager;
import world.bentobox.limits.Limits;
import world.bentobox.limits.Settings;
import world.bentobox.limits.listeners.BlockLimitsListener;
import world.bentobox.limits.listeners.JoinListener;
import world.bentobox.limits.mocks.ServerMocks;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class LimitPanelTest {

    @Mock
    private Limits addon;
    @Mock
    private GameModeAddon gm;
    @Mock
    private User user;
    @Mock
    private World world;
    @Mock
    private Island island;
    @Mock
    private IslandsManager im;
    @Mock
    private BlockLimitsListener bll;
    @Mock
    private JoinListener jl;
    @Mock
    private Settings settings;

    private LimitPanel limitPanel;
    private UUID userUUID;
    private UUID targetUUID;
    private Server server;

    @BeforeEach
    void setUp() {
        server = ServerMocks.newServer();

        userUUID = UUID.randomUUID();
        targetUUID = UUID.randomUUID();

        when(gm.getOverWorld()).thenReturn(world);
        when(addon.getIslands()).thenReturn(im);
        when(addon.getBlockLimitListener()).thenReturn(bll);
        when(addon.getJoinListener()).thenReturn(jl);
        when(addon.getSettings()).thenReturn(settings);
        when(user.getUniqueId()).thenReturn(userUUID);

        limitPanel = new LimitPanel(addon);
    }

    @AfterEach
    void tearDown() {
        ServerMocks.unsetBukkitServer();
    }

    @Test
    void testShowLimitsNoIslandSelf() {
        when(im.getIsland(world, userUUID)).thenReturn(null);

        limitPanel.showLimits(gm, user, userUUID);

        verify(user).sendMessage("general.errors.no-island");
    }

    @Test
    void testShowLimitsNoIslandOther() {
        when(im.getIsland(world, targetUUID)).thenReturn(null);

        limitPanel.showLimits(gm, user, targetUUID);

        verify(user).sendMessage("general.errors.player-has-no-island");
    }

    @Test
    void testShowLimitsNoLimits() {
        when(im.getIsland(world, targetUUID)).thenReturn(island);
        when(island.getUniqueId()).thenReturn("island-id");
        when(bll.getMaterialLimits(eq(world), eq("island-id"))).thenReturn(Collections.emptyMap());
        when(settings.getLimits()).thenReturn(Collections.emptyMap());
        // Target player is offline
        when(server.getPlayer(any(UUID.class))).thenReturn(null);

        limitPanel.showLimits(gm, user, targetUUID);

        verify(user).sendMessage("island.limits.no-limits");
    }

    @Test
    void testShowLimitsTargetPlayerOfflineDoesNotCallCheckPerms() {
        when(im.getIsland(world, targetUUID)).thenReturn(island);
        when(island.getUniqueId()).thenReturn("island-id");
        when(bll.getMaterialLimits(eq(world), eq("island-id"))).thenReturn(Collections.emptyMap());
        when(settings.getLimits()).thenReturn(Collections.emptyMap());
        // Target player is offline
        when(server.getPlayer(any(UUID.class))).thenReturn(null);

        limitPanel.showLimits(gm, user, targetUUID);

        verify(jl, never()).checkPerms(any(Player.class), anyString(), anyString(), anyString());
    }
}

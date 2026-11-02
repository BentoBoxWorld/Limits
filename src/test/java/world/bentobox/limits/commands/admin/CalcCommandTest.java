package world.bentobox.limits.commands.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandWorldManager;
import world.bentobox.bentobox.managers.IslandsManager;
import world.bentobox.bentobox.managers.PlayersManager;
import world.bentobox.limits.Limits;
import world.bentobox.limits.calculators.Pipeliner;
import world.bentobox.limits.calculators.Results;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalcCommandTest {

    private static final String PLAYER_NAME = "tastybento";
    private static final String ALL = "all";
    private static final String ENTITIES = "entities";

    @Mock
    private Limits addon;
    @Mock
    private BentoBox plugin;
    @Mock
    private CompositeCommand parent;
    @Mock
    private World world;
    @Mock
    private IslandWorldManager iwm;
    @Mock
    private IslandsManager islandsManager;
    @Mock
    private PlayersManager playersManager;
    @Mock
    private Pipeliner pipeliner;
    @Mock
    private User user;
    @Mock
    private Island island;

    private final UUID playerUUID = UUID.randomUUID();
    private MockedStatic<BentoBox> mockedBentoBox;
    private CalcCommand command;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        mockedBentoBox = Mockito.mockStatic(BentoBox.class);
        mockedBentoBox.when(BentoBox::getInstance).thenReturn(plugin);
        when(plugin.getPlayers()).thenReturn(playersManager);
        when(plugin.getIWM()).thenReturn(iwm);
        when(iwm.getFriendlyName(any())).thenReturn("BSkyBlock");
        when(parent.getAddon()).thenReturn(addon);
        when(parent.getSubCommands()).thenReturn(new HashMap<>());
        when(parent.getSubCommandAliases()).thenReturn(new HashMap<>());
        when(parent.getTopLabel()).thenReturn("bsbadmin");
        when(parent.getLabel()).thenReturn("limits");
        when(parent.getPermissionPrefix()).thenReturn("bskyblock.");
        when(parent.getWorld()).thenReturn(world);
        when(addon.getIslands()).thenReturn(islandsManager);
        when(addon.getPipeliner()).thenReturn(pipeliner);
        when(playersManager.getUUID(PLAYER_NAME)).thenReturn(playerUUID);
        when(islandsManager.getIsland(world, playerUUID)).thenReturn(island);
        when(pipeliner.addIsland(any())).thenReturn(CompletableFuture.completedFuture(new Results()));
        when(pipeliner.addIslandEntitiesOnly(any()))
                .thenReturn(CompletableFuture.completedFuture(new Results()));
        command = new CalcCommand(addon, parent);
    }

    @AfterEach
    void tearDown() {
        mockedBentoBox.close();
        MockBukkit.unmock();
    }

    private Island island(boolean deleted, boolean unowned) {
        Island i = mock(Island.class);
        when(i.isDeleted()).thenReturn(deleted);
        when(i.isUnowned()).thenReturn(unowned);
        return i;
    }

    @Test
    void testPlayerRecountUsesSharedQueue() {
        assertTrue(command.execute(user, "calc", List.of(PLAYER_NAME)));

        verify(pipeliner).addIsland(island);
        verify(pipeliner, never()).addIslandEntitiesOnly(any());
        verify(user).sendMessage("admin.limits.calc.finished");
    }

    @Test
    void testPlayerEntitiesOnlyRecount() {
        assertTrue(command.execute(user, "calc", List.of(PLAYER_NAME, ENTITIES)));

        verify(pipeliner).addIslandEntitiesOnly(island);
        verify(pipeliner, never()).addIsland(any());
    }

    @Test
    void testAlreadyQueuedIslandReportsInProgress() {
        when(pipeliner.addIsland(any()))
                .thenReturn(CompletableFuture.completedFuture(new Results(Results.Result.IN_PROGRESS)));

        command.execute(user, "calc", List.of(PLAYER_NAME));

        verify(user).sendMessage("island.limits.recount.in-progress");
        verify(user, never()).sendMessage("admin.limits.calc.finished");
    }

    @Test
    void testTimeoutUsesExistingLocaleKey() {
        when(pipeliner.addIsland(any()))
                .thenReturn(CompletableFuture.completedFuture(new Results(Results.Result.TIMEOUT)));

        command.execute(user, "calc", List.of(PLAYER_NAME));

        verify(user).sendMessage("island.limits.recount.time-out");
    }

    @Test
    void testUnknownPlayer() {
        assertFalse(command.execute(user, "calc", List.of("nobody")));

        verify(user).sendMessage("general.errors.unknown-player", TextVariables.NAME, "nobody");
        verify(pipeliner, never()).addIsland(any());
    }

    @Test
    void testBadSecondArgumentShowsHelp() {
        assertFalse(command.execute(user, "calc", List.of(PLAYER_NAME, "blocks")));

        verify(pipeliner, never()).addIsland(any());
        verify(pipeliner, never()).addIslandEntitiesOnly(any());
    }

    @Test
    void testAllQueuesEveryOwnedIsland() {
        Island a = island(false, false);
        Island b = island(false, false);
        Island deleted = island(true, false);
        Island spawn = island(false, true);
        when(islandsManager.getIslands(world)).thenReturn(List.of(a, b, deleted, spawn));

        assertTrue(command.execute(user, "calc", List.of(ALL)));

        verify(pipeliner).addIsland(a);
        verify(pipeliner).addIsland(b);
        verify(pipeliner, never()).addIsland(deleted);
        verify(pipeliner, never()).addIsland(spawn);
        verify(user).sendMessage("admin.limits.calc.all-queued", TextVariables.NUMBER, "2");
        verify(user).sendMessage("admin.limits.calc.all-finished", TextVariables.NUMBER, "2", "[timeouts]", "0");
    }

    @Test
    void testAllEntitiesOnlyCountsTimeouts() {
        Island a = island(false, false);
        Island b = island(false, false);
        when(islandsManager.getIslands(world)).thenReturn(List.of(a, b));
        when(pipeliner.addIslandEntitiesOnly(b))
                .thenReturn(CompletableFuture.completedFuture(new Results(Results.Result.TIMEOUT)));

        assertTrue(command.execute(user, "calc", List.of(ALL, ENTITIES)));

        verify(pipeliner).addIslandEntitiesOnly(a);
        verify(pipeliner).addIslandEntitiesOnly(b);
        verify(pipeliner, never()).addIsland(any());
        verify(user).sendMessage("admin.limits.calc.all-finished", TextVariables.NUMBER, "2", "[timeouts]", "1");
    }

    @Test
    void testAllFinishesOnlyWhenEveryIslandIsDone() {
        Island a = island(false, false);
        when(islandsManager.getIslands(world)).thenReturn(List.of(a));
        CompletableFuture<Results> pending = new CompletableFuture<>();
        when(pipeliner.addIsland(a)).thenReturn(pending);

        command.execute(user, "calc", List.of(ALL));
        verify(user, never()).sendMessage(eq("admin.limits.calc.all-finished"), anyString(), anyString(),
                anyString(), anyString());

        pending.complete(new Results());
        verify(user).sendMessage("admin.limits.calc.all-finished", TextVariables.NUMBER, "1", "[timeouts]", "0");
    }
}

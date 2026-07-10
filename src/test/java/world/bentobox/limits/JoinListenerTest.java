package world.bentobox.limits;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World.Environment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.PluginManager;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.google.common.collect.ImmutableSet;

import world.bentobox.bentobox.api.addons.AddonDescription;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.events.island.IslandEvent;
import world.bentobox.bentobox.api.events.team.TeamSetownerEvent;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandsManager;
import world.bentobox.limits.listeners.BlockLimitsListener;
import world.bentobox.limits.listeners.JoinListener;
import org.mockbukkit.mockbukkit.MockBukkit;
import world.bentobox.limits.objects.IslandBlockCount;

/**
 * @author tastybento
 *
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JoinListenerTest {

    @Mock
    private Limits addon;
    @Mock
    private Settings settings;
    @Mock
    private GameModeAddon bskyblock;
    @Mock
    private Player player;

    private JoinListener jl;
    @Mock
    private IslandsManager im;
    @Mock
    private BlockLimitsListener bll;
    @Mock
    private IslandBlockCount ibc;
    @Mock
    private OfflinePlayer owner;
    @Mock
    private Island island;
    @Mock
    private PluginManager pim;
    private @Nullable UUID uuid = UUID.randomUUID();

    private MockedStatic<Bukkit> mockedBukkit;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        jl = new JoinListener(addon);
        // Setup addon
        when(addon.getGameModes()).thenReturn(Collections.singletonList(bskyblock));
        when(addon.getGameModeName(any())).thenReturn("bskyblock");
        when(addon.getGameModePermPrefix(any())).thenReturn("bskyblock.");
        when(addon.getSettings()).thenReturn(settings);
        // Settings
        when(settings.getGroupLimitDefinitions())
                .thenReturn(new ArrayList<>(List.of(new EntityGroup("friendly", new HashSet<>(), -1, null))));
        // Island Manager
        when(island.getUniqueId()).thenReturn("unique_id");
        when(island.getOwner()).thenReturn(uuid);
        when(im.getIsland(any(), any(UUID.class))).thenReturn(island);
        when(im.getIslands(any(), any(UUID.class))).thenReturn(List.of(island));
        // Default is that player has island
        when(addon.getIslands()).thenReturn(im);
        // Player
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("tastybento");
        // No permissions by default
        when(player.getEffectivePermissions()).thenReturn(Collections.emptySet());
        // bsKyBlock
        when(bskyblock.getPermissionPrefix()).thenReturn("bskyblock.");
        AddonDescription desc = new AddonDescription.Builder("main", "BSkyBlock", "1.0").build();
        when(bskyblock.getDescription()).thenReturn(desc);

        // Block limit listener
        when(addon.getBlockLimitListener()).thenReturn(bll);
        when(bll.getIsland(anyString())).thenReturn(ibc);

        // bukkit
        mockedBukkit = Mockito.mockStatic(Bukkit.class);
        // default is that owner is online
        when(owner.isOnline()).thenReturn(true);
        when(owner.getPlayer()).thenReturn(player);
        mockedBukkit.when(() -> Bukkit.getOfflinePlayer(any(UUID.class))).thenReturn(owner);
        mockedBukkit.when(Bukkit::getPluginManager).thenReturn(pim);

    }

    @AfterEach
    void tearDown() {
        mockedBukkit.close();
        MockBukkit.unmock();
    }

    /**
     * Test method for
     * {@link world.bentobox.limits.listeners.JoinListener#onNewIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    void testOnNewIslandWrongReason() {
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.BAN);
        jl.onNewIsland(e);
        verify(island, never()).getWorld();
    }

    /**
     * Test method for
     * {@link world.bentobox.limits.listeners.JoinListener#onNewIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    void testOnNewIslandRegistered() {
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.REGISTERED);
        jl.onNewIsland(e);
        verify(island).getWorld();
    }

    /**
     * Test method for
     * {@link world.bentobox.limits.listeners.JoinListener#onNewIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    void testOnNewIslandResetted() {
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.RESETTED);
        jl.onNewIsland(e);
        verify(island).getWorld();
    }

    /**
     * Test method for {@link world.bentobox.limits.listeners.JoinListener#onNewIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    void testOnNewIslandCreated() {
        when(addon.inGameModeWorld(any())).thenReturn(true);
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.CREATED);
        jl.onNewIsland(e);
        verify(island).getWorld();
        verify(owner, times(2)).getPlayer();
    }

    /**
     * Test method for {@link world.bentobox.limits.listeners.JoinListener#onNewIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    void testOnNewIslandCreatedOffline() {
        when(owner.isOnline()).thenReturn(false);
        when(addon.inGameModeWorld(any())).thenReturn(true);
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.CREATED);
        jl.onNewIsland(e);
        verify(island).getWorld();
        verify(owner, never()).getPlayer();
    }

    /**
     * Test method for {@link world.bentobox.limits.listeners.JoinListener#onNewIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    void testOnNewIslandCreatedNoNameOrPermPrefix() {
        when(addon.getGameModeName(any())).thenReturn("");
        when(addon.getGameModePermPrefix(any())).thenReturn("bskyblock.");
        when(addon.inGameModeWorld(any())).thenReturn(true);
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.CREATED);
        jl.onNewIsland(e);
        when(addon.getGameModeName(any())).thenReturn("bskyblock");
        when(addon.getGameModePermPrefix(any())).thenReturn("");
        jl.onNewIsland(e);
        verify(owner, never()).getPlayer();
    }

    /**
     * Test method for
     * {@link world.bentobox.limits.listeners.JoinListener#onOwnerChange(world.bentobox.bentobox.api.events.team.TeamEvent.TeamSetownerEvent)}.
     */
    @Test
    void testOnOwnerChange() {
        TeamSetownerEvent e = mock(TeamSetownerEvent.class);
        when(e.getIsland()).thenReturn(island);
        when(e.getNewOwner()).thenReturn(UUID.randomUUID());
        jl.onOwnerChange(e);
        verify(e, Mockito.times(2)).getIsland();
        verify(e).getNewOwner();
    }

    /**
     * Test method for
     * {@link world.bentobox.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    void testOnPlayerJoin() {
        PlayerJoinEvent e = new PlayerJoinEvent(player, Component.text("welcome"));
        jl.onPlayerJoin(e);
        verify(addon).getGameModes();
        verify(bll).setIsland("unique_id", ibc);
    }

    /**
     * Test method for
     * {@link world.bentobox.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    void testOnPlayerJoinIBCNull() {
        ibc = null;
        PlayerJoinEvent e = new PlayerJoinEvent(player, Component.text("welcome"));
        jl.onPlayerJoin(e);
        verify(addon).getGameModes();
        verify(bll, never()).setIsland("unique_id", ibc);
    }

    /**
     * Test method for
     * {@link world.bentobox.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    void testOnPlayerJoinWithPermNotLimits() {
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo permAtt = mock(PermissionAttachmentInfo.class);
        when(permAtt.getPermission()).thenReturn("bskyblock.my.perm.for.game");
        perms.add(permAtt);
        when(player.getEffectivePermissions()).thenReturn(perms);
        PlayerJoinEvent e = new PlayerJoinEvent(player, Component.text("welcome"));
        jl.onPlayerJoin(e);
        verify(addon).getGameModes();
        verify(bll).setIsland("unique_id", ibc);
    }

    private static Stream<Arguments> invalidPermissionLimits() {
        return Stream.of(
                Arguments.of("bskyblock.island.limit.my.perm.for.game",
                        "Player tastybento has permission: 'bskyblock.island.limit.my.perm.for.game' but format must be 'bskyblock.island.limit.[ENV.]KEY.NUMBER' where ENV is overworld|nether|end and KEY is a material, entity type, or group name. Ignoring..."),
                Arguments.of("bskyblock.island.limit.mumbo.34",
                        "Player tastybento has permission: 'bskyblock.island.limit.mumbo.34' but MUMBO is not a valid material or entity type/group. Ignoring..."),
                Arguments.of("bskyblock.island.limit.*",
                        "Player tastybento has permission: 'bskyblock.island.limit.*' but wildcards are not allowed. Ignoring..."),
                Arguments.of("bskyblock.island.limit.STONE.abc",
                        "Player tastybento has permission: 'bskyblock.island.limit.STONE.abc' but the last part MUST be an integer! Ignoring..."));
    }

    /**
     * Test method for
     * {@link world.bentobox.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     * Each invalid permission format should be logged and ignored.
     */
    @ParameterizedTest
    @MethodSource("invalidPermissionLimits")
    void testOnPlayerJoinWithInvalidPermLimits(String permission, String expectedError) {
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo permAtt = mock(PermissionAttachmentInfo.class);
        when(permAtt.getPermission()).thenReturn(permission);
        when(permAtt.getValue()).thenReturn(true);
        perms.add(permAtt);
        when(player.getEffectivePermissions()).thenReturn(perms);
        PlayerJoinEvent e = new PlayerJoinEvent(player, Component.text("welcome"));
        jl.onPlayerJoin(e);
        verify(addon).logError(expectedError);
    }

    /**
     * Test method for
     * {@link world.bentobox.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    void testOnPlayerJoinWithPermLimitsSuccess() {
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo permAtt = mock(PermissionAttachmentInfo.class);
        when(permAtt.getPermission()).thenReturn("bskyblock.island.limit.STONE.24");
        when(permAtt.getValue()).thenReturn(true);
        perms.add(permAtt);
        when(player.getEffectivePermissions()).thenReturn(perms);
        PlayerJoinEvent e = new PlayerJoinEvent(player, Component.text("welcome"));
        jl.onPlayerJoin(e);
        verify(addon, never()).logError(anyString());
        verify(ibc).setBlockLimit(Environment.NORMAL, Material.STONE.getKey(), 24);
    }

    /**
     * Test method for
     * {@link world.bentobox.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    void testOnPlayerJoinWithPermLimitsSuccessEntity() {
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo permAtt = mock(PermissionAttachmentInfo.class);
        when(permAtt.getPermission()).thenReturn("bskyblock.island.limit.BAT.24");
        when(permAtt.getValue()).thenReturn(true);
        perms.add(permAtt);
        when(player.getEffectivePermissions()).thenReturn(perms);
        PlayerJoinEvent e = new PlayerJoinEvent(player, Component.text("welcome"));
        jl.onPlayerJoin(e);
        verify(addon, never()).logError(anyString());
        verify(ibc).setEntityLimit(Environment.NORMAL, EntityType.BAT, 24);
    }

    /**
     * Test method for
     * {@link world.bentobox.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    void testOnPlayerJoinWithPermLimitsSuccessEntityGroup() {
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo permAtt = mock(PermissionAttachmentInfo.class);
        when(permAtt.getPermission()).thenReturn("bskyblock.island.limit.friendly.24");
        when(permAtt.getValue()).thenReturn(true);
        perms.add(permAtt);
        when(player.getEffectivePermissions()).thenReturn(perms);
        PlayerJoinEvent e = new PlayerJoinEvent(player, Component.text("welcome"));
        jl.onPlayerJoin(e);
        verify(addon, never()).logError(anyString());
        verify(ibc).setEntityGroupLimit(Environment.NORMAL, "friendly", 24);
    }

    /**
     * Test method for
     * {@link world.bentobox.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    void testOnPlayerJoinWithPermLimitsMultiPerms() {
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo permAtt = mock(PermissionAttachmentInfo.class);
        when(permAtt.getPermission()).thenReturn("bskyblock.island.limit.STONE.24");
        when(permAtt.getValue()).thenReturn(true);
        perms.add(permAtt);
        PermissionAttachmentInfo permAtt2 = mock(PermissionAttachmentInfo.class);
        when(permAtt2.getPermission()).thenReturn("bskyblock.island.limit.short_grass.14");
        when(permAtt2.getValue()).thenReturn(true);
        perms.add(permAtt2);
        PermissionAttachmentInfo permAtt3 = mock(PermissionAttachmentInfo.class);
        when(permAtt3.getPermission()).thenReturn("bskyblock.island.limit.dirt.34");
        when(permAtt3.getValue()).thenReturn(true);
        perms.add(permAtt3);
        PermissionAttachmentInfo permAtt4 = mock(PermissionAttachmentInfo.class);
        when(permAtt4.getPermission()).thenReturn("bskyblock.island.limit.chicken.34");
        when(permAtt4.getValue()).thenReturn(true);
        perms.add(permAtt4);
        PermissionAttachmentInfo permAtt5 = mock(PermissionAttachmentInfo.class);
        when(permAtt5.getPermission()).thenReturn("bskyblock.island.limit.cave_spider.4");
        when(permAtt5.getValue()).thenReturn(true);
        perms.add(permAtt5);
        PermissionAttachmentInfo permAtt6 = mock(PermissionAttachmentInfo.class);
        when(permAtt6.getPermission()).thenReturn("bskyblock.island.limit.cave_spider.4");
        when(permAtt6.getValue()).thenReturn(false); // negative perm
        perms.add(permAtt6);

        when(player.getEffectivePermissions()).thenReturn(perms);
        PlayerJoinEvent e = new PlayerJoinEvent(player, Component.text("welcome"));
        jl.onPlayerJoin(e);
        verify(addon, never()).logError(anyString());
        verify(ibc).setBlockLimit(Environment.NORMAL, Material.STONE.getKey(), 24);
        verify(ibc).setBlockLimit(Environment.NORMAL, Material.SHORT_GRASS.getKey(), 14);
        verify(ibc).setBlockLimit(Environment.NORMAL, Material.DIRT.getKey(), 34);
        verify(ibc).setEntityLimit(Environment.NORMAL, EntityType.CHICKEN, 34);
        verify(ibc).setEntityLimit(Environment.NORMAL, EntityType.CAVE_SPIDER, 4);
    }

    // --- Team member limit perms (#241) ---

    private PermissionAttachmentInfo mockPerm(String permission) {
        PermissionAttachmentInfo permAtt = mock(PermissionAttachmentInfo.class);
        when(permAtt.getPermission()).thenReturn(permission);
        when(permAtt.getValue()).thenReturn(true);
        return permAtt;
    }

    @Test
    void testOnPlayerJoinMemberIgnoredWhenDisabled() {
        // Player is a team member, not the owner; feature off (default)
        when(island.getOwner()).thenReturn(UUID.randomUUID());
        when(settings.isApplyMemberLimitPerms()).thenReturn(false);
        Set<PermissionAttachmentInfo> perms = Set.of(mockPerm("bskyblock.island.limit.STONE.24"));
        when(player.getEffectivePermissions()).thenReturn(perms);

        jl.onPlayerJoin(new PlayerJoinEvent(player, Component.text("welcome")));

        verify(ibc, never()).setBlockLimit(any(), any(), anyInt());
        verify(bll, never()).setIsland(anyString(), any());
    }

    @Test
    void testOnPlayerJoinMemberPermsMergedWhenEnabled() {
        // Player is a team member, not the owner; feature on
        when(island.getOwner()).thenReturn(UUID.randomUUID());
        when(island.getMemberSet()).thenReturn(ImmutableSet.of(uuid));
        when(settings.isApplyMemberLimitPerms()).thenReturn(true);
        Set<PermissionAttachmentInfo> perms = Set.of(mockPerm("bskyblock.island.limit.STONE.24"));
        when(player.getEffectivePermissions()).thenReturn(perms);

        jl.onPlayerJoin(new PlayerJoinEvent(player, Component.text("welcome")));

        verify(ibc).setBlockLimit(Environment.NORMAL, Material.STONE.getKey(), 24);
        // A member join merges — it must never clear the island's existing perm limits
        verify(ibc, never()).clearAllBlockLimits();
        verify(ibc, never()).clearAllEntityLimits();
        verify(ibc, never()).clearAllEntityGroupLimits();
    }

    @Test
    void testOnPlayerJoinMemberMergeKeepsHigherExistingLimit() {
        // Island already has STONE at 30 (e.g. from the owner); member perm of 24 must not lower it
        when(island.getOwner()).thenReturn(UUID.randomUUID());
        when(island.getMemberSet()).thenReturn(ImmutableSet.of(uuid));
        when(settings.isApplyMemberLimitPerms()).thenReturn(true);
        when(ibc.getBlockLimit(any(Environment.class), any(NamespacedKey.class))).thenReturn(30);
        Set<PermissionAttachmentInfo> perms = Set.of(mockPerm("bskyblock.island.limit.STONE.24"));
        when(player.getEffectivePermissions()).thenReturn(perms);

        jl.onPlayerJoin(new PlayerJoinEvent(player, Component.text("welcome")));

        verify(ibc).setBlockLimit(Environment.NORMAL, Material.STONE.getKey(), 30);
        verify(ibc, never()).setBlockLimit(Environment.NORMAL, Material.STONE.getKey(), 24);
    }

    @Test
    void testOnPlayerJoinOwnerMergesOnlineMemberPerms() {
        // Owner joins with no perms of their own; an online member has STONE.44
        UUID memberUUID = UUID.randomUUID();
        when(island.getMemberSet()).thenReturn(ImmutableSet.of(uuid, memberUUID));
        when(settings.isApplyMemberLimitPerms()).thenReturn(true);
        Player member = mock(Player.class);
        when(member.getUniqueId()).thenReturn(memberUUID);
        when(member.getName()).thenReturn("member");
        Set<PermissionAttachmentInfo> memberPerms = Set.of(mockPerm("bskyblock.island.limit.STONE.44"));
        when(member.getEffectivePermissions()).thenReturn(memberPerms);
        mockedBukkit.when(() -> Bukkit.getPlayer(memberUUID)).thenReturn(member);

        jl.onPlayerJoin(new PlayerJoinEvent(player, Component.text("welcome")));

        // Owner join recalculates from scratch, then the member's perm is merged in
        verify(ibc).clearAllBlockLimits();
        verify(ibc).setBlockLimit(Environment.NORMAL, Material.STONE.getKey(), 44);
    }

    @Test
    void testOnPlayerJoinOwnerIgnoresOfflineMemberPerms() {
        // Owner joins; the only member is offline, so nothing extra is merged
        UUID memberUUID = UUID.randomUUID();
        when(island.getMemberSet()).thenReturn(ImmutableSet.of(uuid, memberUUID));
        when(settings.isApplyMemberLimitPerms()).thenReturn(true);
        mockedBukkit.when(() -> Bukkit.getPlayer(memberUUID)).thenReturn(null);

        jl.onPlayerJoin(new PlayerJoinEvent(player, Component.text("welcome")));

        verify(ibc).clearAllBlockLimits();
        verify(ibc, never()).setBlockLimit(any(), any(), anyInt());
    }

    /**
     * Test method for {@link world.bentobox.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    void testOnPlayerJoinWithPermLimitsMultiPermsSameMaterial() {
        // IBC - set the block limit for STONE to be 25 already
        when(ibc.getBlockLimit(any(Environment.class), any(NamespacedKey.class))).thenReturn(25);
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo permAtt = mock(PermissionAttachmentInfo.class);
        when(permAtt.getPermission()).thenReturn("bskyblock.island.limit.STONE.24");
        when(permAtt.getValue()).thenReturn(true);
        perms.add(permAtt);
        PermissionAttachmentInfo permAtt2 = mock(PermissionAttachmentInfo.class);
        when(permAtt2.getPermission()).thenReturn("bskyblock.island.limit.STONE.14");
        when(permAtt2.getValue()).thenReturn(true);
        perms.add(permAtt2);
        PermissionAttachmentInfo permAtt3 = mock(PermissionAttachmentInfo.class);
        when(permAtt3.getPermission()).thenReturn("bskyblock.island.limit.STONE.34");
        when(permAtt3.getValue()).thenReturn(true);
        perms.add(permAtt3);
        when(player.getEffectivePermissions()).thenReturn(perms);
        PlayerJoinEvent e = new PlayerJoinEvent(player, Component.text("welcome"));
        jl.onPlayerJoin(e);
        verify(addon, never()).logError(anyString());
        // Only the limit over 25 should be set
        verify(ibc, never()).setBlockLimit(Environment.NORMAL, Material.STONE.getKey(), 24);
        verify(ibc, never()).setBlockLimit(Environment.NORMAL, Material.STONE.getKey(), 14);
        verify(ibc).setBlockLimit(Environment.NORMAL, Material.STONE.getKey(), 34);
    }

    /**
     * Test method for {@link world.bentobox.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    void testOnPlayerJoinWithPermLimitsMultiPermsSameEntity() {
        // IBC - set the entity limit for BAT to be 25 already
        when(ibc.getEntityLimit(any(Environment.class), any(EntityType.class))).thenReturn(25);
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo permAtt = mock(PermissionAttachmentInfo.class);
        when(permAtt.getPermission()).thenReturn("bskyblock.island.limit.BAT.24");
        when(permAtt.getValue()).thenReturn(true);
        perms.add(permAtt);
        PermissionAttachmentInfo permAtt2 = mock(PermissionAttachmentInfo.class);
        when(permAtt2.getPermission()).thenReturn("bskyblock.island.limit.BAT.14");
        when(permAtt2.getValue()).thenReturn(true);
        perms.add(permAtt2);
        PermissionAttachmentInfo permAtt3 = mock(PermissionAttachmentInfo.class);
        when(permAtt3.getPermission()).thenReturn("bskyblock.island.limit.BAT.34");
        when(permAtt3.getValue()).thenReturn(true);
        perms.add(permAtt3);
        when(player.getEffectivePermissions()).thenReturn(perms);
        PlayerJoinEvent e = new PlayerJoinEvent(player, Component.text("welcome"));
        jl.onPlayerJoin(e);
        verify(addon, never()).logError(anyString());
        // Only the limit over 25 should be set
        verify(ibc, never()).setEntityLimit(Environment.NORMAL, EntityType.BAT, 24);
        verify(ibc, never()).setEntityLimit(Environment.NORMAL, EntityType.BAT, 14);
        verify(ibc).setEntityLimit(Environment.NORMAL, EntityType.BAT, 34);
    }

    /**
     * Test method for
     * {@link world.bentobox.limits.listeners.JoinListener#onUnregisterIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    void testOnUnregisterIslandNotUnregistered() {
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.BAN);
        jl.onUnregisterIsland(e);
        verify(island, never()).getWorld();
    }

    /**
     * Test method for
     * {@link world.bentobox.limits.listeners.JoinListener#onUnregisterIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    void testOnUnregisterIslandNotInWorld() {
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.UNREGISTERED);
        jl.onUnregisterIsland(e);
        verify(island).getWorld();
        verify(addon, never()).getBlockLimitListener();
    }

    /**
     * Test method for
     * {@link world.bentobox.limits.listeners.JoinListener#onUnregisterIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    void testOnUnregisterIslandInWorld() {
        when(addon.inGameModeWorld(any())).thenReturn(true);
        when(addon.getBlockLimitListener()).thenReturn(bll);
        when(bll.getIsland(island.getUniqueId())).thenReturn(ibc);
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.UNREGISTERED);
        jl.onUnregisterIsland(e);
        verify(island).getWorld();
        verify(addon).getBlockLimitListener();
        verify(ibc).clearAllBlockLimits();
        verify(ibc).clearAllEntityLimits();
        verify(ibc).clearAllEntityGroupLimits();
    }

    /**
     * Test method for {@link world.bentobox.limits.listeners.JoinListener#onUnregisterIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    void testOnUnregisterIslandInWorldNullIBC() {
        when(bll.getIsland(anyString())).thenReturn(null);
        @SuppressWarnings("unchecked")
        Map<NamespacedKey, Integer> map = mock(Map.class);
        when(ibc.getBlockLimits(any(Environment.class))).thenReturn(map);
        when(addon.inGameModeWorld(any())).thenReturn(true);
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.UNREGISTERED);
        jl.onUnregisterIsland(e);
        verify(island).getWorld();
        verify(addon).getBlockLimitListener();
        verify(map, never()).clear();

    }

    /**
     * 5-segment permission applies the limit independently to every standard env.
     */
    @Test
    void unprefixedPermAppliesToAllEnvs() {
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo p = mock(PermissionAttachmentInfo.class);
        when(p.getPermission()).thenReturn("bskyblock.island.limit.STONE.24");
        when(p.getValue()).thenReturn(true);
        perms.add(p);
        when(player.getEffectivePermissions()).thenReturn(perms);
        jl.onPlayerJoin(new PlayerJoinEvent(player, Component.text("welcome")));
        verify(ibc).setBlockLimit(Environment.NORMAL, Material.STONE.getKey(), 24);
        verify(ibc).setBlockLimit(Environment.NETHER, Material.STONE.getKey(), 24);
        verify(ibc).setBlockLimit(Environment.THE_END, Material.STONE.getKey(), 24);
    }

    /**
     * 6-segment permission with `nether` env applies only to nether.
     */
    @Test
    void netherPrefixedPermAppliesToNetherOnly() {
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo p = mock(PermissionAttachmentInfo.class);
        when(p.getPermission()).thenReturn("bskyblock.island.limit.nether.HOPPER.5");
        when(p.getValue()).thenReturn(true);
        perms.add(p);
        when(player.getEffectivePermissions()).thenReturn(perms);
        jl.onPlayerJoin(new PlayerJoinEvent(player, Component.text("welcome")));
        verify(ibc).setBlockLimit(Environment.NETHER, Material.HOPPER.getKey(), 5);
        verify(ibc, never()).setBlockLimit(eq(Environment.NORMAL), any(NamespacedKey.class), anyInt());
        verify(ibc, never()).setBlockLimit(eq(Environment.THE_END), any(NamespacedKey.class), anyInt());
    }

    /**
     * `end` and `overworld` aliases also work.
     */
    @Test
    void endPrefixedPermAppliesToEndOnly() {
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo p = mock(PermissionAttachmentInfo.class);
        when(p.getPermission()).thenReturn("bskyblock.island.limit.end.ENDERMAN.50");
        when(p.getValue()).thenReturn(true);
        perms.add(p);
        when(player.getEffectivePermissions()).thenReturn(perms);
        jl.onPlayerJoin(new PlayerJoinEvent(player, Component.text("welcome")));
        verify(ibc).setEntityLimit(Environment.THE_END, EntityType.ENDERMAN, 50);
        verify(ibc, never()).setEntityLimit(eq(Environment.NORMAL), any(EntityType.class), anyInt());
        verify(ibc, never()).setEntityLimit(eq(Environment.NETHER), any(EntityType.class), anyInt());
    }

    /**
     * 6-segment permission with an unknown env token is rejected with a logged error.
     */
    @Test
    void unknownEnvPrefixIsRejected() {
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo p = mock(PermissionAttachmentInfo.class);
        when(p.getPermission()).thenReturn("bskyblock.island.limit.aether.HOPPER.5");
        when(p.getValue()).thenReturn(true);
        perms.add(p);
        when(player.getEffectivePermissions()).thenReturn(perms);
        jl.onPlayerJoin(new PlayerJoinEvent(player, Component.text("welcome")));
        verify(addon).logError(contains("'aether' is not a recognised environment"));
        verify(ibc, never()).setBlockLimit(any(Environment.class), any(NamespacedKey.class), anyInt());
    }

}

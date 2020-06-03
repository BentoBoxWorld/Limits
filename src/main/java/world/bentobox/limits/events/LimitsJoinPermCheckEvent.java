package world.bentobox.limits.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.events.BentoBoxEvent;
import world.bentobox.limits.objects.IslandBlockCount;

/**
 * Fired when a player joins the server and before limit settings for their island are changed based
 * on the player's permissions. If cancelled, no limit settings will be made.
 * @author tastybento
 *
 */
public class LimitsJoinPermCheckEvent extends BentoBoxEvent implements Cancellable {

    private final Player player;
    private final String islandId;
    private IslandBlockCount ibc;
    private boolean cancel;
    private boolean ignorePerms;

    /**
     * Fired when a player joins the server and before limit settings for their island are changed based
     * on the player's permissions. If cancelled, no limit settings will be made.
     * @param player - player joining
     * @param islandId - the unique island id.
     *  @param ibc - IslandBlockCount object for this island
     */
    public LimitsJoinPermCheckEvent(@NonNull Player player, @NonNull String islandId, @Nullable IslandBlockCount ibc) {
        super();
        this.player = player;
        this.islandId = islandId;
        this.ibc = ibc;
    }


    /**
     * Get the player joining
     * @return the player
     */
    @NonNull
    public Player getPlayer() {
        return player;
    }


    /**
     * Get the unique island id. Use the islands manager to obtain the island
     * @return the islandId
     */
    @NonNull
    public String getIslandId() {
        return islandId;
    }


    /**
     * Get the island block count
     * @return the ibc
     */
    @Nullable
    public IslandBlockCount getIbc() {
        return ibc;
    }


    /**
     * Set the island block count to a specific setting
     * @param ibc the ibc to set
     */
    public void setIbc(@Nullable IslandBlockCount ibc) {
        this.ibc = ibc;
    }


    @Override
    public boolean isCancelled() {
        return cancel;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancel = cancel;

    }


    /**
     * Check if player's perms should be considered or not
     * @return the ignorePerms
     */
    public boolean isIgnorePerms() {
        return ignorePerms;
    }


    /**
     * Ignore player's perms. This differs to canceling the event in that the IslandBlockCount will be used if given via
     * {@link setIbc(IslandBlockCount ibc)}
     * @param ignorePerms the ignorePerms to set
     */
    public void setIgnorePerms(boolean ignorePerms) {
        this.ignorePerms = ignorePerms;
    }


}

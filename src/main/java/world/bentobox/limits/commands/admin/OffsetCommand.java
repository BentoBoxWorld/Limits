//
// Created by BONNe
// Copyright - 2022
//


package world.bentobox.limits.commands.admin;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.eclipse.jdt.annotation.Nullable;

import com.google.common.base.Enums;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.limits.Limits;
import world.bentobox.limits.objects.IslandBlockCount;


/**
 * This command manages offsets to the player island limits.
 */
public class OffsetCommand extends CompositeCommand
{
    /**
     * Instantiates a new Offset command.
     *
     * @param addon the addon
     * @param parent the parent
     */
    public OffsetCommand(Limits addon, CompositeCommand parent)
    {
        super(parent, "offset");

        new OffsetSetCommand(addon, this);
        new OffsetAddCommand(addon, this);
        new OffsetRemoveCommand(addon, this);
        new OffsetResetCommand(addon, this);
        new OffsetDisplayCommand(addon, this);
    }


    @Override
    public void setup()
    {
        this.setOnlyPlayer(false);

        this.setPermission("admin.limits.offset");
        this.setParametersHelp("admin.limits.offset.parameters");
        this.setDescription("admin.limits.offset.description");
    }


    @Override
    public boolean execute(User user, String s, List<String> list)
    {
        this.showHelp(this, user);
        return true;
    }


    /**
     * This command allows setting limit offset for material or entity.
     */
    private static class OffsetSetCommand extends CompositeCommand
    {
        /**
         * Instantiates a new Offset set command.
         *
         * @param addon the addon
         * @param parent the parent
         */
        public OffsetSetCommand(Limits addon, CompositeCommand parent)
        {
            super(parent, "set");
            this.addon = addon;
        }


        @Override
        public void setup()
        {
            this.setOnlyPlayer(false);

            this.setPermission("admin.limits.offset.set");
            this.setParametersHelp("admin.limits.offset.set.parameters");
            this.setDescription("admin.limits.offset.set.description");
        }


        @Override
        public boolean execute(User user, String label, List<String> args)
        {
            if (args.size() != 3)
            {
                // Show help
                this.showHelp(this, user);
                return false;
            }

            // Get target player
            UUID targetUUID = Util.getUUID(args.get(0));

            if (targetUUID == null)
            {
                user.sendMessage("general.errors.unknown-player", TextVariables.NAME, args.get(0));
                return false;
            }

            Island island = this.getIslands().getIsland(this.getWorld(), targetUUID);

            if (island == null)
            {
                user.sendMessage("general.errors.player-has-no-island");
                return false;
            }

            IslandBlockCount islandData = this.addon.getBlockLimitListener().getIsland(island);

            // Get new offset
            if (!Util.isInteger(args.get(2), true))
            {
                user.sendMessage("general.errors.must-be-a-number", TextVariables.NUMBER, args.get(2));
                return false;
            }

            Material material = Material.matchMaterial(args.get(1));
            EntityType entityType = matchEntity(args.get(1));

            if (material == null && entityType == null)
            {
                user.sendMessage("admin.limits.offset.unknown", TextVariables.NAME, args.get(1));
                return false;
            }

            int offset = Integer.parseInt(args.get(2));

            if (material != null && offset == islandData.getBlockLimitOffset(material) ||
                entityType != null && offset == islandData.getEntityLimitOffset(entityType))
            {
                user.sendMessage("admin.limits.offset.set.same",
                    TextVariables.NAME,
                    args.get(1),
                    TextVariables.NUMBER,
                    args.get(2));
                return false;
            }

            if (material != null)
            {
                islandData.setBlockLimitsOffset(material, offset);
                islandData.setChanged();

                user.sendMessage("admin.limits.offset.set.success",
                    TextVariables.NUMBER, String.valueOf(offset),
                    TextVariables.NAME, material.name());
            }
            else
            {
                islandData.setEntityLimitsOffset(entityType, offset);
                islandData.setChanged();

                user.sendMessage("admin.limits.offset.set.success",
                    TextVariables.NUMBER, String.valueOf(offset),
                    TextVariables.NAME, entityType.name());
            }

            return true;
        }


        @Override
        public Optional<List<String>> tabComplete(User user, String alias, List<String> args)
        {
            return OffsetCommand.craftTabComplete(user, alias, args);
        }


        /**
         * Instance of limits addon.
         */
        private final Limits addon;
    }


    /**
     * This command allows increasing limit offset for material or entity.
     */
    private static class OffsetAddCommand extends CompositeCommand
    {
        /**
         * Instantiates a new Offset add command.
         *
         * @param addon the addon
         * @param parent the parent
         */
        public OffsetAddCommand(Limits addon, CompositeCommand parent)
        {
            super(parent, "add");
            this.addon = addon;
        }


        @Override
        public void setup()
        {
            this.setOnlyPlayer(false);

            this.setPermission("admin.limits.offset.add");
            this.setParametersHelp("admin.limits.offset.add.parameters");
            this.setDescription("admin.limits.offset.add.description");
        }


        @Override
        public boolean execute(User user, String label, List<String> args)
        {
            if (args.size() != 3)
            {
                // Show help
                this.showHelp(this, user);
                return false;
            }

            // Get target player
            UUID targetUUID = Util.getUUID(args.get(0));

            if (targetUUID == null)
            {
                user.sendMessage("general.errors.unknown-player", TextVariables.NAME, args.get(0));
                return false;
            }

            Island island = this.getIslands().getIsland(this.getWorld(), targetUUID);

            if (island == null)
            {
                user.sendMessage("general.errors.player-has-no-island");
                return false;
            }

            IslandBlockCount islandData = this.addon.getBlockLimitListener().getIsland(island);

            // Get new offset
            if (!Util.isInteger(args.get(2), true) || Integer.parseInt(args.get(2)) < 0)
            {
                user.sendMessage("general.errors.must-be-positive-number", TextVariables.NUMBER, args.get(2));
                return false;
            }

            Material material = Material.matchMaterial(args.get(1));
            EntityType entityType = matchEntity(args.get(1));

            if (material == null && entityType == null)
            {
                user.sendMessage("admin.limits.offset.unknown", TextVariables.NAME, args.get(1));
                return false;
            }

            int offset = Integer.parseInt(args.get(2));

            if (material != null)
            {
                offset += islandData.getBlockLimitOffset(material);

                islandData.setBlockLimitsOffset(material, offset);
                islandData.setChanged();

                user.sendMessage("admin.limits.offset.add.success",
                    TextVariables.NUMBER, String.valueOf(offset),
                    TextVariables.NAME, material.name());
            }
            else
            {
                offset += islandData.getEntityLimitOffset(entityType);

                islandData.setEntityLimitsOffset(entityType, offset);
                islandData.setChanged();

                user.sendMessage("admin.limits.offset.add.success",
                    TextVariables.NUMBER, String.valueOf(offset),
                    TextVariables.NAME, entityType.name());
            }

            return true;
        }


        @Override
        public Optional<List<String>> tabComplete(User user, String alias, List<String> args)
        {
            return OffsetCommand.craftTabComplete(user, alias, args);
        }


        /**
         * Instance of limits addon.
         */
        private final Limits addon;
    }


    /**
     * This command allows reducing limit offset for material or entity.
     */
    private static class OffsetRemoveCommand extends CompositeCommand
    {
        /**
         * Instantiates a new Offset remove command.
         *
         * @param addon the addon
         * @param parent the parent
         */
        public OffsetRemoveCommand(Limits addon, CompositeCommand parent)
        {
            super(parent, "remove");
            this.addon = addon;
        }


        @Override
        public void setup()
        {
            this.setOnlyPlayer(false);

            this.setPermission("admin.limits.offset.remove");
            this.setParametersHelp("admin.limits.offset.remove.parameters");
            this.setDescription("admin.limits.offset.remove.description");
        }


        @Override
        public boolean execute(User user, String label, List<String> args)
        {
            if (args.size() != 3)
            {
                // Show help
                this.showHelp(this, user);
                return false;
            }

            // Get target player
            UUID targetUUID = Util.getUUID(args.get(0));

            if (targetUUID == null)
            {
                user.sendMessage("general.errors.unknown-player", TextVariables.NAME, args.get(0));
                return false;
            }

            Island island = this.getIslands().getIsland(this.getWorld(), targetUUID);

            if (island == null)
            {
                user.sendMessage("general.errors.player-has-no-island");
                return false;
            }

            IslandBlockCount islandData = this.addon.getBlockLimitListener().getIsland(island);

            // Get new offset
            if (!Util.isInteger(args.get(2), true) || Integer.parseInt(args.get(2)) < 0)
            {
                user.sendMessage("general.errors.must-be-positive-number", TextVariables.NUMBER, args.get(2));
                return false;
            }

            Material material = Material.matchMaterial(args.get(1));
            EntityType entityType = matchEntity(args.get(1));

            if (material == null && entityType == null)
            {
                user.sendMessage("admin.limits.offset.unknown", TextVariables.NAME, args.get(1));
                return false;
            }

            int offset = Integer.parseInt(args.get(2));

            if (material != null)
            {
                offset = islandData.getBlockLimitOffset(material) - offset;

                islandData.setBlockLimitsOffset(material, offset);
                islandData.setChanged();

                user.sendMessage("admin.limits.offset.remove.success",
                    TextVariables.NUMBER, String.valueOf(offset),
                    TextVariables.NAME, material.name());
            }
            else
            {
                offset = islandData.getEntityLimitOffset(entityType) - offset;

                islandData.setEntityLimitsOffset(entityType, offset);
                islandData.setChanged();

                user.sendMessage("admin.limits.offset.remove.success",
                    TextVariables.NUMBER, String.valueOf(offset),
                    TextVariables.NAME, entityType.name());
            }

            return true;
        }


        @Override
        public Optional<List<String>> tabComplete(User user, String alias, List<String> args)
        {
            return OffsetCommand.craftTabComplete(user, alias, args);
        }


        /**
         * Instance of limits addon.
         */
        private final Limits addon;
    }


    /**
     * This command allows resetting limit offset for material or entity.
     */
    private static class OffsetResetCommand extends CompositeCommand
    {
        /**
         * Instantiates a new Offset reset command.
         *
         * @param addon the addon
         * @param parent the parent
         */
        public OffsetResetCommand(Limits addon, CompositeCommand parent)
        {
            super(parent, "reset");
            this.addon = addon;
        }


        @Override
        public void setup()
        {
            this.setOnlyPlayer(false);

            this.setPermission("admin.limits.offset.reset");
            this.setParametersHelp("admin.limits.offset.reset.parameters");
            this.setDescription("admin.limits.offset.reset.description");
        }


        @Override
        public boolean execute(User user, String label, List<String> args)
        {
            if (args.size() != 2)
            {
                // Show help
                this.showHelp(this, user);
                return false;
            }

            // Get target player
            UUID targetUUID = Util.getUUID(args.get(0));

            if (targetUUID == null)
            {
                user.sendMessage("general.errors.unknown-player", TextVariables.NAME, args.get(0));
                return false;
            }

            Island island = this.getIslands().getIsland(this.getWorld(), targetUUID);

            if (island == null)
            {
                user.sendMessage("general.errors.player-has-no-island");
                return false;
            }

            IslandBlockCount islandData = this.addon.getBlockLimitListener().getIsland(island);

            Material material = Material.matchMaterial(args.get(1));
            EntityType entityType = matchEntity(args.get(1));

            if (material == null && entityType == null)
            {
                user.sendMessage("admin.limits.offset.unknown", TextVariables.NAME, args.get(1));
                return false;
            }

            if (material != null)
            {
                islandData.setBlockLimitsOffset(material, 0);
                islandData.setChanged();

                user.sendMessage("admin.limits.offset.reset.success",
                    TextVariables.NAME, material.name());
            }
            else
            {
                islandData.setEntityLimitsOffset(entityType, 0);
                islandData.setChanged();

                user.sendMessage("admin.limits.offset.reset.success",
                    TextVariables.NAME, entityType.name());
            }

            return true;
        }


        @Override
        public Optional<List<String>> tabComplete(User user, String alias, List<String> args)
        {
            return OffsetCommand.craftTabComplete(user, alias, args);
        }


        /**
         * Instance of limits addon.
         */
        private final Limits addon;
    }


    /**
     * This command allows viewing limit offset for material or entity.
     */
    private static class OffsetDisplayCommand extends CompositeCommand
    {
        /**
         * Instantiates a new Offset display command.
         *
         * @param addon the addon
         * @param parent the parent
         */
        public OffsetDisplayCommand(Limits addon, CompositeCommand parent)
        {
            super(parent, "view", "display");
            this.addon = addon;
        }


        @Override
        public void setup()
        {
            this.setOnlyPlayer(false);

            this.setPermission("admin.limits.offset.view");
            this.setParametersHelp("admin.limits.offset.view.parameters");
            this.setDescription("admin.limits.offset.view.description");
        }


        @Override
        public boolean execute(User user, String label, List<String> args)
        {
            if (args.size() != 2)
            {
                // Show help
                this.showHelp(this, user);
                return false;
            }

            // Get target player
            UUID targetUUID = Util.getUUID(args.get(0));

            if (targetUUID == null)
            {
                user.sendMessage("general.errors.unknown-player", TextVariables.NAME, args.get(0));
                return false;
            }

            Island island = this.getIslands().getIsland(this.getWorld(), targetUUID);

            if (island == null)
            {
                user.sendMessage("general.errors.player-has-no-island");
                return false;
            }

            IslandBlockCount islandData = this.addon.getBlockLimitListener().getIsland(island);

            Material material = Material.matchMaterial(args.get(1));
            EntityType entityType = matchEntity(args.get(1));

            if (material == null && entityType == null)
            {
                user.sendMessage("admin.limits.offset.unknown", TextVariables.NAME, args.get(1));
                return false;
            }

            if (material != null)
            {
                int offset = islandData.getBlockLimitOffset(material);
                user.sendMessage("admin.limits.offset.view.message",
                    TextVariables.NAME, material.name(),
                    TextVariables.NUMBER, String.valueOf(offset));
            }
            else
            {
                int offset = islandData.getEntityLimitOffset(entityType);
                user.sendMessage("admin.limits.offset.view.message",
                    TextVariables.NAME, entityType.name(),
                    TextVariables.NUMBER, String.valueOf(offset));
            }

            return true;
        }


        @Override
        public Optional<List<String>> tabComplete(User user, String alias, List<String> args)
        {
            return OffsetCommand.craftTabComplete(user, alias, args);
        }


        /**
         * Instance of limits addon.
         */
        private final Limits addon;
    }


    /**
     * This material matches name to an entity type.
     * @param name Name that must be matched.
     * @return EntityType or null.
     */
    @Nullable
    private static EntityType matchEntity(String name)
    {
        String filtered = name;

        if (filtered.startsWith(NamespacedKey.MINECRAFT + ":"))
        {
            filtered = filtered.substring((NamespacedKey.MINECRAFT + ":").length());
        }

        filtered = filtered.toUpperCase(java.util.Locale.ENGLISH);
        filtered = filtered.replaceAll("\\s+", "_").replaceAll("\\W", "");

        return Enums.getIfPresent(EntityType.class, filtered).orNull();
    }


    /**
     * This method crafts tab complete for all subcommands
     * @param user User who runs command.
     * @param alias Command alias.
     * @param args List of args.
     * @return Optional list of strings.
     */
    private static Optional<List<String>> craftTabComplete(User user, String alias, List<String> args)
    {
        String lastArg = !args.isEmpty() ? args.get(args.size() - 1) : "";

        if (args.isEmpty())
        {
            // Don't show every player on the server. Require at least the first letter
            return Optional.empty();
        }
        else if (args.size() == 4)
        {
            List<String> options = new ArrayList<>(Util.getOnlinePlayerList(user));
            return Optional.of(Util.tabLimit(options, lastArg));
        }
        else if (args.size() == 5)
        {
            List<String> options = Arrays.stream(Material.values()).
                map(Enum::name).
                collect(Collectors.toList());

            options.addAll(Arrays.stream(EntityType.values()).
                map(Enum::name).
                collect(Collectors.toList()));

            return Optional.of(Util.tabLimit(options, lastArg));
        }
        else
        {
            return Optional.empty();
        }
    }
}

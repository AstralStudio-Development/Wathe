package net.momirealms.craftengine.bukkit.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.bukkit.plugin.command.BukkitCommandFeature;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.command.CraftEngineCommandManager;
import net.momirealms.craftengine.core.plugin.command.sender.Sender;
import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;

public class DebugIsChunkPersistentLoadedCommand extends BukkitCommandFeature<CommandSender> {

    public DebugIsChunkPersistentLoadedCommand(CraftEngineCommandManager<CommandSender> commandManager, CraftEngine plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder
                .senderType(Player.class)
                .handler(context -> {
                    Player player = context.sender();
                    Chunk chunk = player.getChunk();
                    Sender sender = plugin().senderFactory().wrap(player);
                    sender.sendMessage(Component.text(chunk.isForceLoaded()));
                });
    }

    @Override
    public String getFeatureID() {
        return "debug_is_chunk_persistent_loaded";
    }
}

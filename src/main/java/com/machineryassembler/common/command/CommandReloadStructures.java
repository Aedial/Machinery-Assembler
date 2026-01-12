package com.machineryassembler.common.command;

import javax.annotation.Nonnull;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

import com.machineryassembler.common.structure.StructureRegistry;


public class CommandReloadStructures extends CommandBase {

    @Nonnull
    @Override
    public String getName() {
        return "ma-reload";
    }

    @Nonnull
    @Override
    public String getUsage(@Nonnull ICommandSender sender) {
        return "/ma-reload - Reloads all registered structure definitions";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        StructureRegistry.reloadStructures(sender);
    }
}

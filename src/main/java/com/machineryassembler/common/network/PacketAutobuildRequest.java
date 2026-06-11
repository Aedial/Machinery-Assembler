// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.machineryassembler.common.autobuild.BlockSourceSettings;
import com.machineryassembler.common.autobuild.ServerAutobuildHandler;


/**
 * Client -> Server packet requesting autobuild of a structure.
 */
public class PacketAutobuildRequest implements IMessage {

    private ResourceLocation structureId;
    private BlockPos origin;
    private NBTTagCompound blockSourceSettings;

    public PacketAutobuildRequest() {
    }

    public PacketAutobuildRequest(ResourceLocation structureId, BlockPos origin, NBTTagCompound blockSourceSettings) {
        this.structureId = structureId;
        this.origin = origin;
        this.blockSourceSettings = blockSourceSettings == null ? new NBTTagCompound() : blockSourceSettings.copy();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        structureId = new ResourceLocation(ByteBufUtils.readUTF8String(buf));
        origin = BlockPos.fromLong(buf.readLong());
        blockSourceSettings = ByteBufUtils.readTag(buf);

        if (blockSourceSettings == null) blockSourceSettings = new NBTTagCompound();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, structureId.toString());
        buf.writeLong(origin.toLong());
        ByteBufUtils.writeTag(buf, blockSourceSettings);
    }

    public ResourceLocation getStructureId() {
        return structureId;
    }

    public BlockPos getOrigin() {
        return origin;
    }

    public BlockSourceSettings getBlockSourceSettings() {
        return BlockSourceSettings.fromTag(blockSourceSettings);
    }

    public static class Handler implements IMessageHandler<PacketAutobuildRequest, IMessage> {
        @Override
        public IMessage onMessage(PacketAutobuildRequest message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                ServerAutobuildHandler.handleAutobuildRequest(
                    player,
                    message.getStructureId(),
                    message.getOrigin(),
                    message.getBlockSourceSettings());
            });

            return null;
        }
    }
}

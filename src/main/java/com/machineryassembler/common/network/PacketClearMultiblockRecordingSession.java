package com.machineryassembler.common.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.machineryassembler.common.recording.MultiblockRecordingService;


/**
 * Client -> Server packet clearing frozen recorder state for the player.
 */
public class PacketClearMultiblockRecordingSession implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<PacketClearMultiblockRecordingSession, IMessage> {
        @Override
        public IMessage onMessage(PacketClearMultiblockRecordingSession message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> MultiblockRecordingService.clearFrozenCapture(player.getUniqueID()));
            return null;
        }
    }
}
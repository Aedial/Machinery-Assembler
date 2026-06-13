package com.machineryassembler.common.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.machineryassembler.common.recording.MultiblockRecordingService;
import com.machineryassembler.common.recording.MultiblockRecordingSnapshot;


/**
 * Client -> Server packet requesting a frozen recorder snapshot for the current bounds.
 */
public class PacketRequestMultiblockRecordingCapture implements IMessage {

    private BlockPos firstCorner;
    private BlockPos secondCorner;

    public PacketRequestMultiblockRecordingCapture() {
    }

    public PacketRequestMultiblockRecordingCapture(BlockPos firstCorner, BlockPos secondCorner) {
        this.firstCorner = firstCorner;
        this.secondCorner = secondCorner;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        firstCorner = BlockPos.fromLong(buf.readLong());
        secondCorner = BlockPos.fromLong(buf.readLong());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(firstCorner.toLong());
        buf.writeLong(secondCorner.toLong());
    }

    public static class Handler implements IMessageHandler<PacketRequestMultiblockRecordingCapture, IMessage> {
        @Override
        public IMessage onMessage(PacketRequestMultiblockRecordingCapture message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                MultiblockRecordingSnapshot snapshot = MultiblockRecordingService.captureSnapshot(
                    player.getUniqueID(),
                    player.getServerWorld(),
                    message.firstCorner,
                    message.secondCorner
                );

                NetworkHandler.INSTANCE.sendTo(
                    new PacketMultiblockRecordingCapture(
                        snapshot == null ? null : snapshot.toNBT(),
                        snapshot == null ? "message.machineryassembler.recorder.empty" : ""
                    ),
                    player
                );
            });

            return null;
        }
    }
}
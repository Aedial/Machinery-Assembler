package com.machineryassembler.common.network;

import java.io.IOException;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.machineryassembler.common.recording.MultiblockRecordingExclusions;
import com.machineryassembler.common.recording.MultiblockRecordingService;
import com.machineryassembler.common.recording.MultiblockRecordingService.SaveResult;


/**
 * Client -> Server packet requesting a recorder export save.
 */
public class PacketRequestMultiblockRecordingSave implements IMessage {

    private BlockPos firstCorner;
    private BlockPos secondCorner;
    private NBTTagCompound exclusionTag;

    public PacketRequestMultiblockRecordingSave() {
    }

    public PacketRequestMultiblockRecordingSave(BlockPos firstCorner, BlockPos secondCorner,
            MultiblockRecordingExclusions exclusions) {
        this.firstCorner = firstCorner;
        this.secondCorner = secondCorner;
        this.exclusionTag = exclusions == null ? new NBTTagCompound() : exclusions.toNBT();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        firstCorner = BlockPos.fromLong(buf.readLong());
        secondCorner = BlockPos.fromLong(buf.readLong());
        exclusionTag = ByteBufUtils.readTag(buf);

        if (exclusionTag == null) exclusionTag = new NBTTagCompound();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(firstCorner.toLong());
        buf.writeLong(secondCorner.toLong());
        ByteBufUtils.writeTag(buf, exclusionTag);
    }

    public static class Handler implements IMessageHandler<PacketRequestMultiblockRecordingSave, IMessage> {
        @Override
        public IMessage onMessage(PacketRequestMultiblockRecordingSave message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                try {
                    SaveResult result = MultiblockRecordingService.saveCapture(
                        player.getUniqueID(),
                        player.getServerWorld(),
                        message.firstCorner,
                        message.secondCorner,
                        MultiblockRecordingExclusions.fromNBT(message.exclusionTag)
                    );

                    if (result == null) {
                        player.sendMessage(new TextComponentTranslation("message.machineryassembler.recorder.empty_after_exclusions"));
                        return;
                    }

                    player.sendMessage(new TextComponentTranslation(
                        "message.machineryassembler.recorder.saved",
                        result.getFile().getName()
                    ));
                } catch (IOException exception) {
                    player.sendMessage(new TextComponentTranslation("message.machineryassembler.recorder.save_failed"));
                }
            });

            return null;
        }
    }
}
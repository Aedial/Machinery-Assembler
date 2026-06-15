package com.machineryassembler.common.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.client.recording.MultiblockRecordingClientController;
import com.machineryassembler.common.recording.MultiblockRecordingSnapshot;


/**
 * Server -> Client packet returning the frozen recorder snapshot.
 */
public class PacketMultiblockRecordingCapture implements IMessage {

    @Nullable
    private byte[] snapshotPayload;
    @Nullable
    private MultiblockRecordingSnapshot snapshot;
    private String errorKey;

    public PacketMultiblockRecordingCapture() {
    }

    public PacketMultiblockRecordingCapture(@Nullable NBTTagCompound snapshotTag, String errorKey) {
        this.snapshot = snapshotTag == null ? null : MultiblockRecordingSnapshot.fromNBT(snapshotTag);
        this.snapshotPayload = compressSnapshotTag(snapshotTag);
        this.errorKey = errorKey;

        if (snapshotTag != null && snapshotPayload == null && (this.errorKey == null || this.errorKey.isEmpty())) {
            this.errorKey = "message.machineryassembler.recorder.preview_failed";
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf.readBoolean()) {
            int payloadLength = buf.readInt();
            if (payloadLength > 0) {
                snapshotPayload = new byte[payloadLength];
                buf.readBytes(snapshotPayload);
                NBTTagCompound tag = decompressSnapshotTag(snapshotPayload);
                snapshot = tag == null ? null : MultiblockRecordingSnapshot.fromNBT(tag);
            }
        }

        errorKey = ByteBufUtils.readUTF8String(buf);

        if (snapshotPayload != null && snapshot == null && (errorKey == null || errorKey.isEmpty())) {
            errorKey = "message.machineryassembler.recorder.preview_failed";
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (snapshotPayload == null) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeInt(snapshotPayload.length);
            buf.writeBytes(snapshotPayload);
        }

        ByteBufUtils.writeUTF8String(buf, errorKey == null ? "" : errorKey);
    }

    @Nullable
    private static byte[] compressSnapshotTag(@Nullable NBTTagCompound snapshotTag) {
        if (snapshotTag == null) return null;

        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            CompressedStreamTools.writeCompressed(snapshotTag, stream);
            return stream.toByteArray();
        } catch (IOException exception) {
            MachineryAssembler.LOGGER.warn("Failed to encode recorder snapshot: {}", exception.getMessage());
            return null;
        }
    }

    @Nullable
    private static NBTTagCompound decompressSnapshotTag(byte[] payload) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(payload)) {
            return CompressedStreamTools.readCompressed(stream);
        } catch (IOException exception) {
            MachineryAssembler.LOGGER.warn("Failed to decode recorder snapshot: {}", exception.getMessage());
            return null;
        }
    }

    public static class Handler implements IMessageHandler<PacketMultiblockRecordingCapture, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketMultiblockRecordingCapture message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() ->
                MultiblockRecordingClientController.handleCaptureResponse(message.snapshot, message.errorKey));
            return null;
        }
    }
}
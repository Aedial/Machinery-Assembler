package com.machineryassembler.client.recording;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;

import com.machineryassembler.client.gui.GuiMultiblockRecorder;
import com.machineryassembler.common.network.NetworkHandler;
import com.machineryassembler.common.network.PacketClearMultiblockRecordingSession;
import com.machineryassembler.common.network.PacketRequestMultiblockRecordingCapture;
import com.machineryassembler.common.recording.MultiblockRecordingExclusions;
import com.machineryassembler.common.recording.MultiblockRecordingSnapshot;


/**
 * Client-only selection flow for the multiblock recording tool.
 */
public final class MultiblockRecordingClientController {

    @Nullable
    private static BlockPos firstCorner;
    @Nullable
    private static BlockPos secondCorner;
    private static final MultiblockRecordingExclusions exclusions = new MultiblockRecordingExclusions();
    private static boolean captureRequestPending;

    private MultiblockRecordingClientController() {
    }

    public static void handleToolUse(EntityPlayer player) {
        if (player == null) return;

        if (player.isSneaking()) {
            resetSelection();
            return;
        }

        BlockPos feetPos = getFeetBlockPos(player);
        if (firstCorner == null) {
            firstCorner = feetPos;
            secondCorner = null;
            captureRequestPending = false;
            sendMessage("message.machineryassembler.recorder.first_corner", feetPos.getX(), feetPos.getY(), feetPos.getZ());
            return;
        }

        if (secondCorner == null) {
            secondCorner = feetPos;
            sendMessage("message.machineryassembler.recorder.second_corner",
                secondCorner.getX(), secondCorner.getY(), secondCorner.getZ());
            return;
        }

        if (captureRequestPending) return;

        captureRequestPending = true;
        sendMessage("message.machineryassembler.recorder.loading");
        NetworkHandler.INSTANCE.sendToServer(new PacketRequestMultiblockRecordingCapture(firstCorner, secondCorner));
    }

    public static void handleCaptureResponse(@Nullable MultiblockRecordingSnapshot snapshot, String errorKey) {
        captureRequestPending = false;

        if (firstCorner == null || secondCorner == null) return;

        if (snapshot == null) {
            sendMessage(errorKey == null || errorKey.isEmpty()
                ? "message.machineryassembler.recorder.empty"
                : errorKey);
            return;
        }

        Minecraft.getMinecraft().displayGuiScreen(new GuiMultiblockRecorder(snapshot, firstCorner, secondCorner, exclusions));
    }

    public static void resetSelection() {
        if (!hasSelection() && !captureRequestPending && exclusions.isEmpty()) return;

        NetworkHandler.INSTANCE.sendToServer(new PacketClearMultiblockRecordingSession());
        clearCaptureData();
        sendMessage("message.machineryassembler.recorder.reset");
    }

    public static void clearCaptureData() {
        firstCorner = null;
        secondCorner = null;
        captureRequestPending = false;
        exclusions.clear();
    }

    public static boolean hasSelection() {
        return firstCorner != null;
    }

    @Nullable
    public static BlockPos getFirstCorner() {
        return firstCorner;
    }

    @Nullable
    public static BlockPos getRenderSecondCorner(@Nullable EntityPlayer player) {
        if (firstCorner == null) return null;
        if (secondCorner != null) return secondCorner;
        if (player == null) return null;

        return getFeetBlockPos(player);
    }

    public static MultiblockRecordingExclusions getExclusions() {
        return exclusions;
    }

    private static BlockPos getFeetBlockPos(EntityPlayer player) {
        return new BlockPos(
            MathHelper.floor(player.posX),
            MathHelper.floor(player.posY),
            MathHelper.floor(player.posZ)
        );
    }

    private static void sendMessage(String translationKey, Object... args) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        mc.player.sendMessage(new TextComponentTranslation(translationKey, args));
    }
}
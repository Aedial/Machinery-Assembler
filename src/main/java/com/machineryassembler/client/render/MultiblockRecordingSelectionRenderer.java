package com.machineryassembler.client.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.client.recording.MultiblockRecordingClientController;


/**
 * Renders the current recorder selection box in the world.
 */
@SideOnly(Side.CLIENT)
public class MultiblockRecordingSelectionRenderer {

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        EntityPlayer player = net.minecraft.client.Minecraft.getMinecraft().player;
        if (player == null) return;
        if (!MultiblockRecordingClientController.hasSelection()) return;

        BlockPos firstCorner = MultiblockRecordingClientController.getFirstCorner();
        BlockPos secondCorner = MultiblockRecordingClientController.getRenderSecondCorner(player);
        if (firstCorner == null || secondCorner == null) return;

        BlockPos minPos = new BlockPos(
            Math.min(firstCorner.getX(), secondCorner.getX()),
            Math.min(firstCorner.getY(), secondCorner.getY()),
            Math.min(firstCorner.getZ(), secondCorner.getZ())
        );
        BlockPos maxPos = new BlockPos(
            Math.max(firstCorner.getX(), secondCorner.getX()),
            Math.max(firstCorner.getY(), secondCorner.getY()),
            Math.max(firstCorner.getZ(), secondCorner.getZ())
        );

        AxisAlignedBB bounds = new AxisAlignedBB(minPos, maxPos.add(1, 1, 1));
        float partialTicks = event.getPartialTicks();
        double cameraX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double cameraY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double cameraZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        );
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(2.0F);
        RenderGlobal.drawSelectionBoundingBox(bounds.offset(-cameraX, -cameraY, -cameraZ), 0.15F, 0.8F, 1.0F, 0.9F);
        GlStateManager.glLineWidth(1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
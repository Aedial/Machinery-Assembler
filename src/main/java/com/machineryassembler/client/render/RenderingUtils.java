// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/client/util/RenderingUtils.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.client.render;

import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Utility methods for rendering.
 */
@SideOnly(Side.CLIENT)
public class RenderingUtils {

    /**
     * Draw white translucent cubes at the specified positions.
     */
    public static void drawWhiteOutlineCubes(List<BlockPos> positions, float partialTicks) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1F, 1F, 1F, 0.4F);
        GlStateManager.disableTexture2D();
        GlStateManager.enableColorMaterial();
        GlStateManager.disableCull();

        Entity player = Minecraft.getMinecraft().getRenderViewEntity();
        if (player == null) player = Minecraft.getMinecraft().player;

        Tessellator tes = Tessellator.getInstance();
        BufferBuilder vb = tes.getBuffer();
        vb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);

        double dX = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double) partialTicks;
        double dY = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double) partialTicks;
        double dZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double) partialTicks;

        for (BlockPos pos : positions) {
            AxisAlignedBB box = Block.FULL_BLOCK_AABB.offset(pos).grow(0.002).offset(-dX, -dY, -dZ);

            // Bottom face
            vb.pos(box.minX, box.minY, box.minZ).endVertex();
            vb.pos(box.maxX, box.minY, box.minZ).endVertex();
            vb.pos(box.maxX, box.minY, box.maxZ).endVertex();
            vb.pos(box.minX, box.minY, box.maxZ).endVertex();

            // Top face
            vb.pos(box.minX, box.maxY, box.maxZ).endVertex();
            vb.pos(box.maxX, box.maxY, box.maxZ).endVertex();
            vb.pos(box.maxX, box.maxY, box.minZ).endVertex();
            vb.pos(box.minX, box.maxY, box.minZ).endVertex();

            // East face
            vb.pos(box.maxX, box.minY, box.minZ).endVertex();
            vb.pos(box.maxX, box.maxY, box.minZ).endVertex();
            vb.pos(box.maxX, box.maxY, box.maxZ).endVertex();
            vb.pos(box.maxX, box.minY, box.maxZ).endVertex();

            // West face
            vb.pos(box.minX, box.minY, box.maxZ).endVertex();
            vb.pos(box.minX, box.maxY, box.maxZ).endVertex();
            vb.pos(box.minX, box.maxY, box.minZ).endVertex();
            vb.pos(box.minX, box.minY, box.minZ).endVertex();

            // North face
            vb.pos(box.minX, box.maxY, box.minZ).endVertex();
            vb.pos(box.maxX, box.maxY, box.minZ).endVertex();
            vb.pos(box.maxX, box.minY, box.minZ).endVertex();
            vb.pos(box.minX, box.minY, box.minZ).endVertex();

            // South face
            vb.pos(box.minX, box.minY, box.maxZ).endVertex();
            vb.pos(box.maxX, box.minY, box.maxZ).endVertex();
            vb.pos(box.maxX, box.maxY, box.maxZ).endVertex();
            vb.pos(box.minX, box.maxY, box.maxZ).endVertex();
        }

        tes.draw();

        GlStateManager.enableCull();
        GlStateManager.disableColorMaterial();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    /**
     * Draw an item stack at the specified position.
     */
    public static void renderItemStack(ItemStack stack, int x, int y) {
        RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;

        GlStateManager.pushMatrix();
        RenderHelper.enableGUIStandardItemLighting();

        renderItem.renderItemAndEffectIntoGUI(stack, x, y);
        renderItem.renderItemOverlayIntoGUI(fontRenderer, stack, x, y, null);

        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    /**
     * Bind the block texture atlas.
     */
    public static void bindBlockTexture() {
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
    }

    /**
     * Get interpolated render position for an entity.
     */
    public static double[] getEntityRenderPos(Entity entity, float partialTicks) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;

        return new double[] {x, y, z};
    }
}

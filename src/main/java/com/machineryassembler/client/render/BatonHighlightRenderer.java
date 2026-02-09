// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.client.render;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.common.item.ItemAssemblerBaton;


/**
 * Renders block highlight outlines for autobuild obstructions and placement issues.
 * Highlights are only visible when holding the Assembler's Baton.
 */
@SideOnly(Side.CLIENT)
public class BatonHighlightRenderer {

    public enum HighlightType {
        OBSTRUCTION(1.0f, 0.2f, 0.2f),      // Red for obstructions
        WRONG_BLOCK(1.0f, 0.5f, 0.0f),       // Orange for wrong blocks
        CORRECT_EXTERNAL(0.2f, 1.0f, 0.2f),  // Green for correct external
        MISSING(1.0f, 1.0f, 0.0f);           // Yellow for missing

        public final float r, g, b;

        HighlightType(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    private static final Map<BlockPos, HighlightEntry> highlights = new HashMap<>();

    /**
     * Add a block to be highlighted.
     */
    public static void addHighlight(BlockPos pos, HighlightType type, long durationMs) {
        highlights.put(pos, new HighlightEntry(type, System.currentTimeMillis() + durationMs));
    }

    /**
     * Add multiple blocks with the same type and duration.
     */
    public static void addHighlights(Iterable<BlockPos> positions, HighlightType type, long durationMs) {
        long expireTime = System.currentTimeMillis() + durationMs;

        for (BlockPos pos : positions) {
            highlights.put(pos, new HighlightEntry(type, expireTime));
        }
    }

    /**
     * Add a block to be highlighted with no expiration.
     */
    public static void addHighlight(BlockPos pos, HighlightType type) {
        highlights.put(pos, new HighlightEntry(type));
    }

    /**
     * Add multiple blocks with the same type and no expiration.
     */
    public static void addHighlights(Iterable<BlockPos> positions, HighlightType type) {
        for (BlockPos pos : positions) highlights.put(pos, new HighlightEntry(type));
    }

    /**
     * Clear all highlights.
     */
    public static void clearHighlights() {
        highlights.clear();
    }

    /**
     * Check if player is holding the baton in either hand.
     */
    private static boolean isHoldingBaton(EntityPlayer player) {
        ItemStack mainHand = player.getHeldItem(EnumHand.MAIN_HAND);
        ItemStack offHand = player.getHeldItem(EnumHand.OFF_HAND);

        return mainHand.getItem() instanceof ItemAssemblerBaton ||
               offHand.getItem() instanceof ItemAssemblerBaton;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (highlights.isEmpty()) return;

        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return;

        // Only render when holding baton
        if (!isHoldingBaton(player)) return;

        long now = System.currentTimeMillis();

        // Remove expired highlights
        Iterator<Map.Entry<BlockPos, HighlightEntry>> iter = highlights.entrySet().iterator();
        while (iter.hasNext()) {
            long expireTime = iter.next().getValue().expireTime;
            if (expireTime != -1 && expireTime < now) iter.remove();
        }

        if (highlights.isEmpty()) return;

        // Render
        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks();
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.getPartialTicks();
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks();

        GlStateManager.pushMatrix();
        GlStateManager.translate(-playerX, -playerY, -playerZ);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(3.0F);

        for (Map.Entry<BlockPos, HighlightEntry> entry : highlights.entrySet()) {
            BlockPos pos = entry.getKey();
            HighlightEntry highlight = entry.getValue();

            // Calculate pulsing alpha
            float alpha = 0.5f + 0.4f * (float) Math.sin(now / 400.0);

            renderBlockOutline(pos, highlight.type.r, highlight.type.g, highlight.type.b, alpha);
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private void renderBlockOutline(BlockPos pos, float red, float green, float blue, float alpha) {
        AxisAlignedBB box = new AxisAlignedBB(pos).grow(0.002);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        // Bottom face
        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        tessellator.draw();

        // Top face
        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();
        tessellator.draw();

        // Vertical edges
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();

        buffer.pos(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();

        buffer.pos(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();

        buffer.pos(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();
        tessellator.draw();
    }

    private static class HighlightEntry {
        final HighlightType type;
        final long expireTime;

        HighlightEntry(HighlightType type, long expireTime) {
            this.type = type;
            this.expireTime = expireTime;
        }

        HighlightEntry(HighlightType type) {
            this(type, -1);  // no expiration
        }
    }
}

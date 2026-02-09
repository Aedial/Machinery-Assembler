// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.client.render;

import java.awt.image.BufferedImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Generates GUI textures programmatically.
 */
@SideOnly(Side.CLIENT)
public class GuiTextureGenerator {

    private static final int BORDER_DARK = 0xFF373737;
    private static final int BORDER_LIGHT = 0xFFFFFFFF;
    private static final int BACKGROUND = 0xFFC6C6C6;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_BG = 0xFF8B8B8B;

    private static ResourceLocation structurePreviewTexture = null;

    /**
     * Get the structure preview texture, generating it if needed.
     */
    public static ResourceLocation getStructurePreviewTexture() {
        if (structurePreviewTexture == null) {
            structurePreviewTexture = generateStructurePreviewTexture();
        }

        return structurePreviewTexture;
    }

    /**
     * Generate the structure preview GUI texture (184x232).
     * Layout: Title area (top 16px), Preview area (middle), Items area (bottom 48px for 2 rows).
     */
    private static ResourceLocation generateStructurePreviewTexture() {
        int width = 184;
        int height = 232;  // Increased for 2 rows of slots
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // Fill background
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) img.setRGB(x, y, BACKGROUND);
        }

        // Draw outer border (vanilla style: light top/left, dark bottom/right)
        drawVanillaBorder(img, 0, 0, width, height);

        // Draw title area separator (y = 14)
        drawHorizontalLine(img, 4, 14, width - 8, SLOT_DARK);
        drawHorizontalLine(img, 4, 15, width - 8, SLOT_LIGHT);

        // Draw preview area (center panel, inset) - larger area
        int previewTop = 18;
        int previewBottom = height - 50;  // Adjusted for 2 rows
        drawInsetPanel(img, 7, previewTop, width - 14, previewBottom - previewTop);

        // Draw bottom area for items - draw 2 rows of 9 slot backgrounds
        int slotSize = 18;
        int slotsBaseY = height - 44;  // Y position for first row
        int slotsStartX = 7;

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = slotsStartX + col * slotSize;
                int slotY = slotsBaseY + row * slotSize;
                drawSlot(img, slotX, slotY);
            }
        }

        // Create dynamic texture
        DynamicTexture dynamicTexture = new DynamicTexture(img);

        return Minecraft.getMinecraft().getTextureManager()
            .getDynamicTextureLocation("machineryassembler_structure_preview", dynamicTexture);
    }

    /**
     * Draw a single slot background (18x18).
     */
    private static void drawSlot(BufferedImage img, int x, int y) {
        int size = 18;

        // Fill with slot background
        for (int py = y; py < y + size; py++) {
            for (int px = x; px < x + size; px++) img.setRGB(px, py, SLOT_BG);
        }

        // Dark border on top and left
        for (int i = x; i < x + size; i++) img.setRGB(i, y, SLOT_DARK);
        for (int i = y; i < y + size; i++) img.setRGB(x, i, SLOT_DARK);

        // Light border on bottom and right
        for (int i = x; i < x + size; i++) img.setRGB(i, y + size - 1, SLOT_LIGHT);
        for (int i = y; i < y + size; i++) img.setRGB(x + size - 1, i, SLOT_LIGHT);
    }

    /**
     * Draw a vanilla-style raised border around a rectangle.
     */
    private static void drawVanillaBorder(BufferedImage img, int x, int y, int w, int h) {
        // Light border (top and left)
        for (int i = x; i < x + w; i++) {
            img.setRGB(i, y, BORDER_LIGHT);
            img.setRGB(i, y + 1, BORDER_LIGHT);
        }

        for (int i = y; i < y + h; i++) {
            img.setRGB(x, i, BORDER_LIGHT);
            img.setRGB(x + 1, i, BORDER_LIGHT);
        }

        // Dark border (bottom and right)
        for (int i = x; i < x + w; i++) {
            img.setRGB(i, y + h - 1, BORDER_DARK);
            img.setRGB(i, y + h - 2, BORDER_DARK);
        }

        for (int i = y; i < y + h; i++) {
            img.setRGB(x + w - 1, i, BORDER_DARK);
            img.setRGB(x + w - 2, i, BORDER_DARK);
        }

        // Corner cleanup (dark takes priority at corners)
        img.setRGB(x + w - 1, y, BORDER_DARK);
        img.setRGB(x + w - 2, y, BORDER_DARK);
        img.setRGB(x + w - 1, y + 1, BORDER_DARK);
    }

    /**
     * Draw an inset panel (like a slot background but larger).
     */
    private static void drawInsetPanel(BufferedImage img, int x, int y, int w, int h) {
        // Fill with slot background
        for (int py = y; py < y + h; py++) {
            for (int px = x; px < x + w; px++) img.setRGB(px, py, SLOT_BG);
        }

        // Dark border on top and left (inset effect)
        for (int i = x; i < x + w; i++) img.setRGB(i, y, SLOT_DARK);
        for (int i = y; i < y + h; i++) img.setRGB(x, i, SLOT_DARK);

        // Light border on bottom and right
        for (int i = x; i < x + w; i++) img.setRGB(i, y + h - 1, SLOT_LIGHT);
        for (int i = y; i < y + h; i++) img.setRGB(x + w - 1, i, SLOT_LIGHT);
    }

    /**
     * Draw a horizontal line.
     */
    private static void drawHorizontalLine(BufferedImage img, int x, int y, int w, int color) {
        for (int i = x; i < x + w; i++) img.setRGB(i, y, color);
    }

    /**
     * Clear cached textures (call on resource reload).
     */
    public static void clearCache() {
        structurePreviewTexture = null;
    }
}

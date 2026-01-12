package com.machineryassembler.client;

import net.minecraft.util.math.BlockPos;

/**
 * Holds the manual offset for the floating (unplaced) preview.
 * This allows moving the preview with arrow keys before placing it.
 */
public class PreviewOffsetHolder {

    private static BlockPos previewOffset = BlockPos.ORIGIN;

    /**
     * Sets the preview offset.
     */
    public static void setPreviewOffset(BlockPos offset) {
        previewOffset = offset;
    }

    /**
     * Gets the current preview offset.
     */
    public static BlockPos getPreviewOffset() {
        return previewOffset;
    }

    /**
     * Resets the preview offset to zero.
     */
    public static void resetPreviewOffset() {
        previewOffset = BlockPos.ORIGIN;
    }

    /**
     * Adds an offset in the given direction.
     */
    public static void addOffset(BlockPos delta) {
        previewOffset = previewOffset.add(delta);
    }
}

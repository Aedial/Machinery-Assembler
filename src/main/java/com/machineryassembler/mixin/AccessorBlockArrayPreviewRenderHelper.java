package com.machineryassembler.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.util.math.BlockPos;

import hellfirepvp.modularmachinery.client.util.BlockArrayPreviewRenderHelper;


/**
 * Accessor mixin to expose private fields from BlockArrayPreviewRenderHelper.
 * This allows us to read and modify the attached preview position.
 */
@Mixin(value = BlockArrayPreviewRenderHelper.class, remap = false)
public interface AccessorBlockArrayPreviewRenderHelper {

    @Accessor("attachedPosition")
    BlockPos getAttachedPosition();

    @Accessor("attachedPosition")
    void setAttachedPosition(BlockPos pos);
}

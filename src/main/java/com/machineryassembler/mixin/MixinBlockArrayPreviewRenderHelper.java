package com.machineryassembler.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.util.math.BlockPos;

import hellfirepvp.modularmachinery.client.util.BlockArrayPreviewRenderHelper;

import com.machineryassembler.client.PreviewOffsetHolder;


/**
 * Mixin to add a manual offset to the floating (unplaced) preview.
 * This allows moving the preview with arrow keys before placing it.
 */
@Mixin(value = BlockArrayPreviewRenderHelper.class, remap = false)
public class MixinBlockArrayPreviewRenderHelper {

    /**
     * Inject at the end of getRenderOffset to add our manual offset.
     */
    @Inject(method = "getRenderOffset", at = @At("RETURN"), cancellable = true)
    private void ma$addPreviewOffset(CallbackInfoReturnable<BlockPos> cir) {
        BlockPos original = cir.getReturnValue();
        BlockPos offset = PreviewOffsetHolder.getPreviewOffset();

        if (original != null && !offset.equals(BlockPos.ORIGIN)) cir.setReturnValue(original.add(offset));
    }

    /**
     * Reset offset when preview is unloaded.
     */
    @Inject(method = "unloadWorld", at = @At("HEAD"))
    private void ma$resetOffsetOnUnload(CallbackInfo ci) {
        PreviewOffsetHolder.resetPreviewOffset();
    }
}

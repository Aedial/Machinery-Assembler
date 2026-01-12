package com.machineryassembler.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import hellfirepvp.modularmachinery.client.util.DynamicMachineRenderContext;
import hellfirepvp.modularmachinery.common.util.BlockArray;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;

import net.minecraft.util.math.Vec3i;

import com.machineryassembler.common.structure.StructureDynamicMachineAdapter;


/**
 * Mixin to prevent controller block insertion in the in-world preview.
 * This intercepts the addControllerToBlockArray static method used by DynamicMachineRenderContext.
 */
@Mixin(value = DynamicMachineRenderContext.class, remap = false)
public class MixinDynamicMachineRenderContext {

    /**
     * Intercept addControllerToBlockArray and cancel it for our adapter.
     * This prevents the controller block from being added to the in-world preview.
     */
    @Inject(method = "addControllerToBlockArray", at = @At("HEAD"), cancellable = true)
    private static void onAddControllerToBlockArray(DynamicMachine machine, BlockArray copy, Vec3i moveOffset, CallbackInfo ci) {
        if (machine instanceof StructureDynamicMachineAdapter adapter && adapter.shouldSkipController()) {
            ci.cancel();
        }
    }
}

package com.machineryassembler.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import github.kasuminova.mmce.client.gui.widget.impl.preview.WorldSceneRendererWidget;

import hellfirepvp.modularmachinery.common.machine.DynamicMachine;

import com.machineryassembler.common.structure.StructureDynamicMachineAdapter;


/**
 * Mixin to prevent controller block insertion for StructureDynamicMachineAdapter.
 * This intercepts the addControllerToPattern method and cancels it if the machine
 * is our custom adapter that doesn't need a controller.
 */
@Mixin(value = WorldSceneRendererWidget.class, remap = false)
public class MixinWorldSceneRendererWidget {

    /**
     * Intercept addControllerToPattern and cancel it for our adapter.
     * This prevents the controller block from being added to the pattern in GUI preview.
     */
    @Inject(method = "addControllerToPattern", at = @At("HEAD"), cancellable = true)
    protected void onAddControllerToPattern(DynamicMachine machine, CallbackInfo ci) {
        if (machine instanceof StructureDynamicMachineAdapter adapter && adapter.shouldSkipController()) {
            ci.cancel();
        }
    }
}

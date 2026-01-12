package com.machineryassembler.common.structure;

import hellfirepvp.modularmachinery.common.machine.DynamicMachine;

import com.machineryassembler.MachineryAssembler;


/**
 * A DynamicMachine wrapper that allows our Structure to be displayed
 * using MMCE's preview infrastructure.
 *
 * This creates a "fake" DynamicMachine that has no controller or recipes,
 * but has the same pattern as our Structure for preview purposes.
 */
public class StructureDynamicMachineAdapter extends DynamicMachine {

    private final Structure wrappedStructure;
    private boolean skipController = true;

    public StructureDynamicMachineAdapter(Structure structure) {
        super(structure.getRegistryName().getPath());
        this.wrappedStructure = structure;

        // Copy the pattern from the structure
        this.getPattern().overwrite(structure.getPattern());

        // Set the localized name
        this.setLocalizedName(structure.getOriginalLocalizedName());

        // Set color
        this.setDefinedColor(structure.getColor());

        // No blueprint required (we don't have one)
        this.setRequiresBlueprint(false);

        // No factory
        this.setHasFactory(false);
    }

    public Structure getWrappedStructure() {
        return wrappedStructure;
    }

    /**
     * Check if the controller block should be skipped when rendering.
     * Used by the WorldSceneRendererWidget mixin to prevent controller insertion.
     */
    public boolean shouldSkipController() {
        return skipController;
    }

    public void setSkipController(boolean skipController) {
        this.skipController = skipController;
    }

    /**
     * Updates this adapter when the underlying structure changes (e.g., hot reload).
     */
    public void updateFromStructure() {
        this.getPattern().overwrite(wrappedStructure.getPattern());
        this.setLocalizedName(wrappedStructure.getOriginalLocalizedName());
        this.setDefinedColor(wrappedStructure.getColor());
    }
}

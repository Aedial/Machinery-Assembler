package com.machineryassembler.client.integration.jei;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.google.common.cache.Cache;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import github.kasuminova.mmce.client.preivew.PreviewPanels;

import hellfirepvp.modularmachinery.common.machine.DynamicMachine;

import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.common.structure.Structure;
import com.machineryassembler.common.structure.StructureDynamicMachineAdapter;


/**
 * JEI recipe wrapper for structure previews.
 * Extends MMCE's StructurePreviewWrapper so the mixin hooks work (scroll, drag, keyboard).
 * 
 * The controller block is prevented from being added via a mixin on WorldSceneRendererWidget.
 */
public class StructurePreviewWrapper extends hellfirepvp.modularmachinery.common.integration.preview.StructurePreviewWrapper {

    private static final Field MACHINE_FIELD;

    static {
        Field field = null;
        try {
            field = hellfirepvp.modularmachinery.common.integration.preview.StructurePreviewWrapper.class.getDeclaredField("machine");
            field.setAccessible(true);
        } catch (Exception e) {
            MachineryAssembler.LOGGER.error("Failed to access machine field in StructurePreviewWrapper", e);
        }
        MACHINE_FIELD = field;
    }

    private final Structure structure;
    private final StructureDynamicMachineAdapter machineAdapter;

    public StructurePreviewWrapper(Structure structure) {
        super(new StructureDynamicMachineAdapter(structure));
        this.structure = structure;
        this.machineAdapter = getMachineAdapter();
    }

    /**
     * Access the machine field via reflection since it's private in the parent.
     */
    private StructureDynamicMachineAdapter getMachineAdapter() {
        if (MACHINE_FIELD == null) return null;

        try {
            return (StructureDynamicMachineAdapter) MACHINE_FIELD.get(this);
        } catch (Exception e) {
            MachineryAssembler.LOGGER.error("Failed to get machine field value", e);
            return null;
        }
    }

    public Structure getStructure() {
        return structure;
    }

    @Override
    public void drawInfo(Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        // Let the parent handle the rendering
        // The mixin on WorldSceneRendererWidget prevents controller insertion
        super.drawInfo(minecraft, recipeWidth, recipeHeight, mouseX, mouseY);
    }

    @Override
    public void getIngredients(@Nonnull IIngredients ingredients) {
        if (machineAdapter == null) return;

        // Get the ingredient list from our structure's pattern (without controller)
        List<List<ItemStack>> ingredientList = machineAdapter.getPattern().getIngredientList();

        // No output items (we don't have a controller or blueprint)
        List<ItemStack> outputList = new ArrayList<>();

        ingredients.setInputLists(VanillaTypes.ITEM, ingredientList);
        ingredients.setOutputs(VanillaTypes.ITEM, outputList);
    }

    /**
     * Called when the structure is reloaded to update the adapter.
     */
    public void onStructureReloaded() {
        MachineryAssembler.LOGGER.info("[Machinery Assembler] onStructureReloaded called for {}", structure.getRegistryName());

        if (machineAdapter != null) machineAdapter.updateFromStructure();

        // Invalidate the cached panel for this machine
        invalidatePanelCache();

        // Reset the gui so it gets recreated
        resetGui();
    }

    /**
     * Reset the GUI so it gets recreated on next render.
     */
    private void resetGui() {
        try {
            Field guiField = hellfirepvp.modularmachinery.common.integration.preview.StructurePreviewWrapper.class.getDeclaredField("gui");
            guiField.setAccessible(true);
            guiField.set(this, null);
        } catch (Exception e) {
            MachineryAssembler.LOGGER.error("Failed to reset gui field", e);
        }
    }

    /**
     * Invalidate the panel cache for this machine adapter.
     */
    @SuppressWarnings("unchecked")
    private void invalidatePanelCache() {
        if (machineAdapter == null) {
            return;
        }

        try {
            Field cacheField = PreviewPanels.class.getDeclaredField("PANEL_CACHE");
            cacheField.setAccessible(true);
            com.google.common.cache.Cache<DynamicMachine, ?> cache =
                (Cache<DynamicMachine, ?>) cacheField.get(null);
            cache.invalidate(machineAdapter);
            MachineryAssembler.LOGGER.info("[Machinery Assembler] Invalidated panel cache for {}", structure.getRegistryName());
        } catch (Exception e) {
            MachineryAssembler.LOGGER.error("Failed to invalidate panel cache", e);
        }
    }
}

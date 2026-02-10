package com.machineryassembler.client.integration.jei;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;

import net.minecraft.util.ResourceLocation;

import mezz.jei.api.IJeiHelpers;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.common.structure.Structure;
import com.machineryassembler.common.structure.StructureRegistry;


/**
 * JEI plugin for Machinery Assembler.
 * Registers structure previews in JEI.
 */
@JEIPlugin
public class MAJEIPlugin implements IModPlugin {

    public static final String CATEGORY_STRUCTURE_PREVIEW = MachineryAssembler.MODID + ".structure_preview";
    public static final List<StructurePreviewWrapper> STRUCTURE_WRAPPERS = Lists.newArrayList();
    private static final Map<ResourceLocation, StructurePreviewWrapper> WRAPPER_MAP = new HashMap<>();

    public static IJeiHelpers jeiHelpers;
    public static IJeiRuntime jeiRuntime;
    private static boolean registered = false;

    /**
     * Called when structures are reloaded to update all wrappers.
     * Updates existing wrappers and registers new structures with JEI.
     */
    public static void onStructuresReloaded() {
        if (!registered || jeiRuntime == null) {
            MachineryAssembler.LOGGER.warn("[Machinery Assembler] onStructuresReloaded called but JEI not ready. registered={}, jeiRuntime={}", 
                registered, jeiRuntime != null);
            return;
        }

        MachineryAssembler.LOGGER.info("[Machinery Assembler] onStructuresReloaded called. registered={}, wrappers={}", 
            registered, STRUCTURE_WRAPPERS.size());

        int updated = 0;
        int newStructures = 0;

        // Update existing wrappers - JEI doesn't support adding recipes at runtime
        for (Structure structure : StructureRegistry.getLoadedStructures()) {
            ResourceLocation id = structure.getRegistryName();
            StructurePreviewWrapper existingWrapper = WRAPPER_MAP.get(id);

            if (existingWrapper != null) {
                existingWrapper.onStructureReloaded();
                updated++;
            } else {
                // TODO: JEI 4.x doesn't support adding recipes at runtime.
                // New structures require a game restart to appear in JEI.
                newStructures++;
            }
        }

        if (newStructures > 0) {
            MachineryAssembler.LOGGER.warn("[Machinery Assembler] {} new structure(s) detected. " +
                "A game restart is required for them to appear in JEI.", newStructures);
        }

        MachineryAssembler.LOGGER.info("[Machinery Assembler] Reload complete. Updated {}/{} wrappers.", 
            updated, STRUCTURE_WRAPPERS.size());
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registry) {
        jeiHelpers = registry.getJeiHelpers();
        registry.addRecipeCategories(new CategoryStructurePreview());
    }

    @Override
    public void register(IModRegistry registry) {
        jeiHelpers = registry.getJeiHelpers();

        // Register global GUI handler for message panel exclusion areas
        registry.addGlobalGuiHandlers(new MessagePanelGuiHandler());

        // Register structure preview wrappers
        for (Structure structure : StructureRegistry.getRegistry()) {
            StructurePreviewWrapper wrapper = new StructurePreviewWrapper(structure);
            STRUCTURE_WRAPPERS.add(wrapper);
            WRAPPER_MAP.put(structure.getRegistryName(), wrapper);
        }

        registry.addRecipes(STRUCTURE_WRAPPERS, CATEGORY_STRUCTURE_PREVIEW);
        registered = true;
        MachineryAssembler.LOGGER.info("[Machinery Assembler] Registered {} structure wrappers with JEI", STRUCTURE_WRAPPERS.size());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        MAJEIPlugin.jeiRuntime = jeiRuntime;
    }
}

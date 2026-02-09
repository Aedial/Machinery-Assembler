// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.client.integration.jei;

import java.lang.reflect.Field;
import java.util.List;

import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import mezz.jei.gui.recipes.RecipesGui;


/**
 * Handles mouse scroll events in JEI's recipe GUI for structure previews.
 * This intercepts scroll events when over a structure preview to allow
 * zooming and layer navigation instead of page switching.
 */
@SideOnly(Side.CLIENT)
public class JEIScrollHandler {

    private static Field recipeLayoutsField = null;
    private static boolean reflectionFailed = false;

    /**
     * Handle mouse input events to intercept scroll when over a structure preview.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        GuiScreen gui = event.getGui();
        if (!(gui instanceof RecipesGui)) return;

        int scroll = Mouse.getEventDWheel();
        if (scroll == 0) return;

        RecipesGui recipesGui = (RecipesGui) gui;

        // Try to get the current wrapper
        StructurePreviewWrapper wrapper = getCurrentStructureWrapper(recipesGui);
        if (wrapper == null) return;

        // Calculate mouse position relative to the GUI
        int mouseX = Mouse.getEventX() * gui.width / gui.mc.displayWidth;
        int mouseY = gui.height - Mouse.getEventY() * gui.height / gui.mc.displayHeight - 1;

        // Let the wrapper handle the scroll
        boolean consumed = wrapper.handleMouseScrolling(mouseX, mouseY, scroll);
        if (consumed) event.setCanceled(true);
    }

    /**
     * Try to get the current StructurePreviewWrapper from the RecipesGui.
     * Uses reflection since JEI doesn't expose this through the API.
     */
    private StructurePreviewWrapper getCurrentStructureWrapper(RecipesGui recipesGui) {
        if (reflectionFailed) return null;

        try {
            if (recipeLayoutsField == null) {
                recipeLayoutsField = RecipesGui.class.getDeclaredField("recipeLayouts");
                recipeLayoutsField.setAccessible(true);
            }

            @SuppressWarnings("unchecked")
            List<Object> layouts = (List<Object>) recipeLayoutsField.get(recipesGui);
            if (layouts == null || layouts.isEmpty()) return null;

            // Get the first layout's recipe wrapper
            for (Object layout : layouts) {
                Object wrapper = getRecipeWrapper(layout);
                if (wrapper instanceof StructurePreviewWrapper) return (StructurePreviewWrapper) wrapper;
            }
        } catch (Exception e) {
            // Reflection failed, disable future attempts
            reflectionFailed = true;
        }

        return null;
    }

    /**
     * Get the recipe wrapper from a RecipeLayout using reflection.
     */
    private Object getRecipeWrapper(Object layout) {
        try {
            Field wrapperField = layout.getClass().getDeclaredField("recipeWrapper");
            wrapperField.setAccessible(true);
            return wrapperField.get(layout);
        } catch (Exception e) {
            return null;
        }
    }
}

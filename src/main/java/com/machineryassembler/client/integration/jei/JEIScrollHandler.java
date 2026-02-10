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
 * Handles mouse scroll and click events in JEI's recipe GUI for structure previews.
 * This intercepts events when over a structure preview to allow
 * zooming, layer navigation, and message item click handling.
 */
@SideOnly(Side.CLIENT)
public class JEIScrollHandler {

    private static Field recipeLayoutsField = null;
    private static Field posXField = null;
    private static Field posYField = null;
    private static boolean reflectionFailed = false;

    /**
     * Handle mouse input events to intercept scroll and clicks when over a structure preview.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        GuiScreen gui = event.getGui();
        if (!(gui instanceof RecipesGui)) return;

        RecipesGui recipesGui = (RecipesGui) gui;

        // Try to get the current wrapper
        StructurePreviewWrapper wrapper = getCurrentStructureWrapper(recipesGui);
        if (wrapper == null) return;

        // Calculate mouse position relative to the screen
        int mouseScreenX = Mouse.getEventX() * gui.width / gui.mc.displayWidth;
        int mouseScreenY = gui.height - Mouse.getEventY() * gui.height / gui.mc.displayHeight - 1;

        // Get recipe-relative coordinates
        int[] recipeOffset = getRecipeOffset(recipesGui);
        int mouseRecipeX = mouseScreenX - recipeOffset[0];
        int mouseRecipeY = mouseScreenY - recipeOffset[1];

        // Handle scroll events
        int scroll = Mouse.getEventDWheel();
        if (scroll != 0) {
            boolean consumed = wrapper.handleMouseScrolling(mouseRecipeX, mouseRecipeY, scroll);
            if (consumed) {
                event.setCanceled(true);
                return;
            }
        }

        // Handle click events for message item slots
        if (Mouse.getEventButtonState()) {
            int button = Mouse.getEventButton();
            if (button == 0 || button == 1) {
                // Check if click is on a message item slot (outside recipe area, negative X)
                if (mouseRecipeX < 0) {
                    boolean consumed = wrapper.handleMessageItemClick(mouseRecipeX, mouseRecipeY, button == 1);
                    if (consumed) {
                        event.setCanceled(true);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Get the recipe area offset (top-left corner in screen coordinates).
     */
    private int[] getRecipeOffset(RecipesGui recipesGui) {
        if (reflectionFailed) return new int[]{0, 0};

        try {
            // Get the first recipe layout's position
            if (recipeLayoutsField == null) {
                recipeLayoutsField = RecipesGui.class.getDeclaredField("recipeLayouts");
                recipeLayoutsField.setAccessible(true);
            }

            @SuppressWarnings("unchecked")
            List<Object> layouts = (List<Object>) recipeLayoutsField.get(recipesGui);
            if (layouts == null || layouts.isEmpty()) return new int[]{0, 0};

            Object layout = layouts.get(0);

            if (posXField == null) {
                posXField = layout.getClass().getDeclaredField("posX");
                posXField.setAccessible(true);
            }
            if (posYField == null) {
                posYField = layout.getClass().getDeclaredField("posY");
                posYField.setAccessible(true);
            }

            int posX = posXField.getInt(layout);
            int posY = posYField.getInt(layout);

            return new int[]{posX, posY};
        } catch (Exception e) {
            // Fall back to estimation
            return new int[]{0, 0};
        }
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

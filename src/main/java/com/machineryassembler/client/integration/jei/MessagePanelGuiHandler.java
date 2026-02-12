// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.client.integration.jei;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import mezz.jei.api.gui.IGlobalGuiHandler;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.gui.recipes.IRecipeGuiLogic;
import mezz.jei.gui.recipes.RecipesGui;


/**
 * Global GUI handler that tells JEI about the message windows' screen-space positions.
 * This prevents JEI from drawing its ingredient list or bookmarks over the windows.
 */
@SideOnly(Side.CLIENT)
public class MessagePanelGuiHandler implements IGlobalGuiHandler {

    private static Field logicField = null;
    private static boolean reflectionFailed = false;

    @Override
    public Collection<Rectangle> getGuiExtraAreas() {
        // Only return exclusion areas if we're currently viewing the structure preview category
        if (!isStructurePreviewCategoryActive()) return Collections.emptyList();

        List<Rectangle> rects = StructurePreviewWrapper.getActiveMessagePanelRects();
        if (rects == null || rects.isEmpty()) return Collections.emptyList();

        return rects;
    }

    /**
     * Check if the current JEI screen is showing our structure preview category.
     */
    private boolean isStructurePreviewCategoryActive() {
        if (reflectionFailed) return true; // Fall back to always returning areas if reflection fails

        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (!(screen instanceof RecipesGui)) return false;

        try {
            if (logicField == null) {
                logicField = RecipesGui.class.getDeclaredField("logic");
                logicField.setAccessible(true);
            }

            IRecipeGuiLogic logic = (IRecipeGuiLogic) logicField.get(screen);
            if (logic == null) return false;

            IRecipeCategory category = logic.getSelectedRecipeCategory();
            if (category == null) return false;

            return MAJEIPlugin.CATEGORY_STRUCTURE_PREVIEW.equals(category.getUid());
        } catch (Exception e) {
            reflectionFailed = true;
            return true; // Fall back to always returning areas
        }
    }
}

package com.machineryassembler.client.integration.jei;

import javax.annotation.Nonnull;

import net.minecraft.client.resources.I18n;

import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategory;

import com.machineryassembler.MachineryAssembler;


/**
 * JEI category for structure previews.
 */
public class CategoryStructurePreview implements IRecipeCategory<StructurePreviewWrapper> {

    private final IDrawable background;
    private final String trTitle;

    public CategoryStructurePreview() {
        this.background = new DynamicBackgroundDrawable();
        this.trTitle = I18n.format("jei.category.machineryassembler.structure_preview");
    }

    @Nonnull
    @Override
    public String getUid() {
        return MAJEIPlugin.CATEGORY_STRUCTURE_PREVIEW;
    }

    @Nonnull
    @Override
    public String getTitle() {
        return trTitle;
    }

    @Nonnull
    @Override
    public String getModName() {
        return MachineryAssembler.NAME;
    }

    @Nonnull
    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, @Nonnull StructurePreviewWrapper recipeWrapper, IIngredients ingredients) {
        IGuiItemStackGroup group = recipeLayout.getItemStacks();

        // Hidden output slot
        group.init(0, false, -999999, -999999);

        // Position first 18 slots visible (2 rows of 9), rest hidden
        // Y_SIZE is now 232, slots start at Y_SIZE - 44 = 188
        int slotsBaseY = 232 - 44;
        int slotsStartX = 7;
        int slotSize = 18;
        int slotsPerRow = 9;
        int slotRows = 2;
        int slotsPerPage = slotsPerRow * slotRows;
        int totalSlots = Math.min(ingredients.getInputs(VanillaTypes.ITEM).size(), 81);

        for (int i = 0; i < totalSlots; i++) {
            if (i < slotsPerPage) {
                // Visible on first page - arrange in 2 rows
                int row = i / slotsPerRow;
                int col = i % slotsPerRow;
                int slotX = slotsStartX + col * slotSize;
                int slotY = slotsBaseY + row * slotSize;
                group.init(1 + i, true, slotX, slotY);
            } else {
                // Hidden
                group.init(1 + i, true, -999999, -999999);
            }
        }

        group.set(ingredients);

        // Pass the slot group and ingredients to the wrapper for dynamic repositioning
        recipeWrapper.setSlotGroup(group, totalSlots, ingredients);
    }
}

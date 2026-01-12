package com.machineryassembler.client.integration.jei;

import javax.annotation.Nonnull;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import hellfirepvp.modularmachinery.client.gui.GuiScreenBlueprint;

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
        // Use the same texture as MMCE's blueprint screen for consistency
        ResourceLocation location = new ResourceLocation("modularmachinery", "textures/gui/guiblueprint_new.png");
        this.background = MAJEIPlugin.jeiHelpers.getGuiHelper()
            .drawableBuilder(location, 0, 0, GuiScreenBlueprint.X_SIZE, GuiScreenBlueprint.Y_SIZE)
            .addPadding(0, 0, 0, 0)
            .build();
        this.trTitle = I18n.format("jei.category." + MachineryAssembler.MODID + ".structure_preview");
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

        // Hidden input slots for ingredients
        for (int i = 0; i < Math.min(ingredients.getInputs(VanillaTypes.ITEM).size(), 81); i++) {
            group.init(1 + i, true, -999999, -999999);
        }

        group.set(ingredients);
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.client.integration.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import mezz.jei.api.gui.IDrawable;

import com.machineryassembler.client.gui.GuiStructurePreview;
import com.machineryassembler.client.render.GuiTextureGenerator;


/**
 * Custom IDrawable that uses our dynamically generated texture.
 */
@SideOnly(Side.CLIENT)
public class DynamicBackgroundDrawable implements IDrawable {

    @Override
    public int getWidth() {
        return GuiStructurePreview.X_SIZE;
    }

    @Override
    public int getHeight() {
        return GuiStructurePreview.Y_SIZE;
    }

    @Override
    public void draw(Minecraft minecraft, int xOffset, int yOffset) {
        GlStateManager.color(1F, 1F, 1F, 1F);
        minecraft.getTextureManager().bindTexture(GuiTextureGenerator.getStructurePreviewTexture());
        Gui.drawModalRectWithCustomSizedTexture(xOffset, yOffset, 0, 0,
            GuiStructurePreview.X_SIZE, GuiStructurePreview.Y_SIZE,
            GuiStructurePreview.X_SIZE, GuiStructurePreview.Y_SIZE);
    }
}

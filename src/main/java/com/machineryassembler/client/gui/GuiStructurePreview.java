// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/client/gui/GuiScreenBlueprint.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.client.gui;

import java.io.IOException;
import java.util.Optional;

import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.client.ClientProxy;
import com.machineryassembler.client.render.GuiTextureGenerator;
import com.machineryassembler.client.render.StructureRenderContext;
import com.machineryassembler.client.render.StructureRenderHelper;
import com.machineryassembler.common.structure.Structure;


/**
 * GUI screen for viewing and interacting with structure previews.
 * Used by JEI integration for structure preview display.
 */
@SideOnly(Side.CLIENT)
public class GuiStructurePreview extends GuiScreen {

    public static final int X_SIZE = 184;
    public static final int Y_SIZE = 232;  // Increased to accommodate 2 rows of slots

    private static final int BUTTON_PREVIEW = 0;
    private static final int BUTTON_TOGGLE_MODE = 1;

    private final Structure structure;
    private final StructureRenderContext context;

    private int guiLeft;
    private int guiTop;

    // Dragging state
    private boolean isDragging = false;
    private int lastMouseX;
    private int lastMouseY;

    public GuiStructurePreview(Structure structure) {
        this.structure = structure;
        this.context = StructureRenderContext.createContext(structure);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.guiLeft = (width - X_SIZE) / 2;
        this.guiTop = (height - Y_SIZE) / 2;

        buttonList.clear();

        // Button to start in-world preview
        buttonList.add(new GuiButton(BUTTON_PREVIEW,
            guiLeft + X_SIZE - 80 - 4, guiTop + Y_SIZE - 24,
            80, 20,
            I18n.format("gui.machineryassembler.button.preview")));

        // Button to toggle 2D/3D mode
        buttonList.add(new GuiButton(BUTTON_TOGGLE_MODE,
            guiLeft + 4, guiTop + Y_SIZE - 24,
            60, 20,
            I18n.format("gui.machineryassembler.button.mode")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BUTTON_PREVIEW) {
            context.snapSamples();

            if (ClientProxy.previewRenderer.startPreview(context)) mc.displayGuiScreen(null);
        } else if (button.id == BUTTON_TOGGLE_MODE) {
            if (context.doesRender3D()) {
                context.setTo2D();
            } else {
                context.setTo3D();
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        // Draw background
        GlStateManager.color(1F, 1F, 1F, 1F);
        mc.getTextureManager().bindTexture(GuiTextureGenerator.getStructurePreviewTexture());
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, X_SIZE, Y_SIZE);

        // Draw title
        String title = structure.getLocalizedName();
        if (title == null || title.isEmpty()) title = structure.getRegistryName().toString();
        FontRenderer fr = mc.fontRenderer;
        fr.drawString(title, guiLeft + (X_SIZE - fr.getStringWidth(title)) / 2, guiTop + 4, 0x404040);

        // Draw 3D/2D mode indicator
        String modeText = context.doesRender3D()
            ? I18n.format("gui.machineryassembler.mode.3d")
            : I18n.format("gui.machineryassembler.mode.2d");
        fr.drawString(modeText, guiLeft + 8, guiTop + Y_SIZE - 14, 0x606060);

        // Render structure preview
        int previewX = guiLeft + X_SIZE / 2;
        int previewY = guiTop + Y_SIZE / 2 + 10;

        StructureRenderHelper render = context.getRender();
        if (context.doesRender3D()) {
            render.render3DGUI(previewX, previewY, context.getScale(), partialTicks);
        } else {
            render.render3DGUI(previewX, previewY, context.getScale(), partialTicks,
                Optional.of(context.getRenderSlice()));
        }

        // Draw layer indicator in 2D mode
        if (!context.doesRender3D()) {
            String layerText = I18n.format("gui.machineryassembler.layer",
                context.getRenderSlice() + 1,
                structure.getPattern().getSize().getY());
            fr.drawString(layerText, guiLeft + X_SIZE - 8 - fr.getStringWidth(layerText),
                guiTop + Y_SIZE - 14, 0x606060);
        }

        // Draw instructions
        drawInstructions();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawInstructions() {
        FontRenderer fr = mc.fontRenderer;
        int y = guiTop + 16;
        int color = 0x808080;
        int x = guiLeft + 8;

        fr.drawString(I18n.format("gui.machineryassembler.hint.drag"), x, y, color);
        y += 10;
        fr.drawString(I18n.format("gui.machineryassembler.hint.scroll"), x, y, color);

        if (!context.doesRender3D()) {
            y += 10;
            fr.drawString(I18n.format("gui.machineryassembler.hint.updown"), x, y, color);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton == 0) {
            isDragging = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        isDragging = false;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);

        if (isDragging && clickedMouseButton == 0) {
            int dx = mouseX - lastMouseX;
            int dy = mouseY - lastMouseY;

            StructureRenderHelper render = context.getRender();

            if (context.doesRender3D()) {
                // Negate dy so dragging up rotates the structure upward
                render.rotate(-dy * 0.5, dx * 0.5, 0);
            } else {
                render.translate(dx * 0.05, 0, -dy * 0.05);
            }

            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int scroll = Mouse.getEventDWheel();
        if (scroll != 0) {
            if (context.doesRender3D()) {
                // Zoom in/out
                if (scroll > 0) {
                    context.zoomIn();
                } else {
                    context.zoomOut();
                }
            } else {
                // Change slice
                if (scroll > 0 && context.hasSliceUp()) {
                    context.sliceUp();
                } else if (scroll < 0 && context.hasSliceDown()) {
                    context.sliceDown();
                }
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // Escape
            mc.displayGuiScreen(null);

            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    public Structure getStructure() {
        return structure;
    }

    public StructureRenderContext getContext() {
        return context;
    }
}

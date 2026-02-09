// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/common/integration/preview/StructurePreviewWrapper.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.client.integration.jei;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.client.ClientProxy;
import com.machineryassembler.client.render.StructureRenderContext;
import com.machineryassembler.client.render.StructureRenderHelper;
import com.machineryassembler.common.structure.Structure;
import com.machineryassembler.common.structure.StructurePattern;


/**
 * JEI recipe wrapper for structure previews.
 * Renders structure preview with buttons matching MMCE's behavior.
 */
@SideOnly(Side.CLIENT)
public class StructurePreviewWrapper implements IRecipeWrapper {

    // Arrow textures for pagination
    private static final ResourceLocation ARROW_UP = new ResourceLocation(MachineryAssembler.MODID, "textures/gui/arrow_up.png");
    private static final ResourceLocation ARROW_UP_HOVERED = new ResourceLocation(MachineryAssembler.MODID, "textures/gui/arrow_up_hovered.png");
    private static final ResourceLocation ARROW_UP_DISABLED = new ResourceLocation(MachineryAssembler.MODID, "textures/gui/arrow_up_disabled.png");
    private static final ResourceLocation ARROW_DOWN = new ResourceLocation(MachineryAssembler.MODID, "textures/gui/arrow_down.png");
    private static final ResourceLocation ARROW_DOWN_HOVERED = new ResourceLocation(MachineryAssembler.MODID, "textures/gui/arrow_down_hovered.png");
    private static final ResourceLocation ARROW_DOWN_DISABLED = new ResourceLocation(MachineryAssembler.MODID, "textures/gui/arrow_down_disabled.png");

    // Arrow button dimensions
    private static final int ARROW_SIZE = 8;

    // Button dimensions - small icon buttons
    private static final int BUTTON_SIZE = 12;
    private static final int BUTTON_MARGIN = 2;

    // Slot pagination - 2 rows of 9 slots each
    private static final int SLOTS_PER_ROW = 9;
    private static final int SLOT_ROWS = 2;
    private static final int SLOTS_PER_PAGE = SLOTS_PER_ROW * SLOT_ROWS;
    private int slotPage = 0;
    private int maxSlotPages = 1;

    // Cached ingredient list for rendering
    private List<List<ItemStack>> ingredientLists = new ArrayList<>();
    private long sampleTick = 0;

    private final Structure structure;
    private StructureRenderContext context;

    // Dragging state
    private boolean isDragging = false;
    private int lastMouseX;
    private int lastMouseY;

    // Button positions (calculated in drawInfo)
    private int btnLayerX, btnLayerY;
    private int btnPreviewX, btnPreviewY;
    private int btnResetX, btnResetY;
    private int btnSlotUpX, btnSlotUpY;
    private int btnSlotDownX, btnSlotDownY;

    // JEI slot group for dynamic repositioning
    private IGuiItemStackGroup slotGroup;
    private IIngredients storedIngredients;
    private int recipeWidth, recipeHeight;

    // Slot area bounds for contextual scroll (2 rows * 18px + padding)
    private static final int SLOT_AREA_HEIGHT = 50;

    // Total number of ingredient slots
    private int totalSlots = 0;
    private int lastSlotPage = 0;

    public StructurePreviewWrapper(Structure structure) {
        this.structure = structure;
        this.context = StructureRenderContext.createContext(structure);
    }

    public Structure getStructure() {
        return structure;
    }

    /**
     * Called when the structure is reloaded to update the context.
     */
    public void onStructureReloaded() {
        this.context = StructureRenderContext.createContext(structure);
    }

    /**
     * Called by CategoryStructurePreview to pass the slot group for dynamic repositioning.
     */
    public void setSlotGroup(IGuiItemStackGroup group, int totalSlots, IIngredients ingredients) {
        this.slotGroup = group;
        this.totalSlots = totalSlots;
        this.storedIngredients = ingredients;
        this.lastSlotPage = slotPage;
    }

    @Override
    public void getIngredients(@Nonnull IIngredients ingredients) {
        StructurePattern pattern = structure.getPattern();
        // Use validated ingredient list to exclude items with missing models/textures
        ingredientLists = pattern.getIngredientList(true);

        // Calculate max pages
        maxSlotPages = (ingredientLists.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
        if (maxSlotPages < 1) maxSlotPages = 1;
        if (slotPage >= maxSlotPages) slotPage = maxSlotPages - 1;

        // No outputs for structure preview (we don't have blueprints)
        List<ItemStack> outputList = new ArrayList<>();

        ingredients.setInputLists(VanillaTypes.ITEM, ingredientLists);
        ingredients.setOutputs(VanillaTypes.ITEM, outputList);
    }

    @Override
    public void drawInfo(Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        // Store dimensions for slot repositioning
        this.recipeWidth = recipeWidth;
        this.recipeHeight = recipeHeight;

        // Reposition JEI slots based on current page
        repositionSlots();

        // Handle input (dragging and scrolling)
        handleInput(mouseX, mouseY, recipeHeight);

        FontRenderer fr = minecraft.fontRenderer;

        // Draw title
        String title = structure.getLocalizedName();
        if (title == null || title.isEmpty()) title = structure.getRegistryName().getPath();
        int titleX = (recipeWidth - fr.getStringWidth(title)) / 2;
        fr.drawString(title, titleX, 3, 0x404040);

        // Preview area bounds (match texture layout)
        int previewTop = 18;
        int previewBottom = recipeHeight - 50;  // Above 2 rows of slots (44px) + padding
        int previewRight = recipeWidth - 7;

        // Calculate button positions - bottom-right corner of preview area
        btnResetX = previewRight - BUTTON_SIZE - BUTTON_MARGIN;
        btnResetY = previewBottom - BUTTON_SIZE - BUTTON_MARGIN;

        btnPreviewX = btnResetX - BUTTON_SIZE - BUTTON_MARGIN;
        btnPreviewY = btnResetY;

        btnLayerX = btnPreviewX - BUTTON_SIZE - BUTTON_MARGIN;
        btnLayerY = btnResetY;

        // Render structure preview - center in preview area
        int previewX = recipeWidth / 2;
        int previewY = previewTop + (previewBottom - previewTop) / 2;

        StructureRenderHelper render = context.getRender();

        if (context.doesRender3D()) {
            render.render3DGUI(previewX, previewY, context.getScale(), 0);
        } else {
            render.render3DGUI(previewX, previewY, context.getScale(), 0,
                Optional.of(context.getRenderSlice()));
        }

        // Draw buttons
        GlStateManager.color(1F, 1F, 1F, 1F);
        drawButtons(minecraft, mouseX, mouseY, recipeWidth, recipeHeight);

        // Draw layer indicator in 2D mode
        if (!context.doesRender3D()) {
            // Calculate layer number relative to structure (1-indexed from bottom)
            StructurePattern pattern = structure.getPattern();
            int minY = pattern.getMin().getY() + context.getMoveOffset().getY();
            int layerNum = context.getRenderSlice() - minY + 1;
            int totalLayers = pattern.getSize().getY();

            String layerText = I18n.format("gui.machineryassembler.layer",
                layerNum, totalLayers);
            int layerTextX = recipeWidth - BUTTON_MARGIN - fr.getStringWidth(layerText);
            fr.drawString(layerText, layerTextX, recipeHeight - 10, 0x606060);
        }

        // Draw slot pagination arrows (always visible, grayed when disabled)
        drawSlotPagination(minecraft, mouseX, mouseY, recipeWidth, recipeHeight);

        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    /**
     * Reposition JEI slots based on current page.
     * Only repositions when page actually changes to avoid performance issues.
     */
    private void repositionSlots() {
        if (slotGroup == null || totalSlots == 0 || storedIngredients == null) return;
        if (slotPage == lastSlotPage) return;

        lastSlotPage = slotPage;

        int slotsBaseY = recipeHeight - 44;
        int slotsStartX = 7;
        int slotSize = 18;

        int startIndex = slotPage * SLOTS_PER_PAGE;

        for (int i = 0; i < totalSlots; i++) {
            int slotIndex = i - startIndex;

            if (slotIndex >= 0 && slotIndex < SLOTS_PER_PAGE) {
                // Visible on current page - arrange in 2 rows
                int row = slotIndex / SLOTS_PER_ROW;
                int col = slotIndex % SLOTS_PER_ROW;
                int slotX = slotsStartX + col * slotSize;
                int slotY = slotsBaseY + row * slotSize;
                slotGroup.init(1 + i, true, slotX, slotY);
            } else {
                // Hidden (not on current page)
                slotGroup.init(1 + i, true, -999999, -999999);
            }
        }

        // Re-set ingredients to apply new positions
        slotGroup.set(storedIngredients);
    }

    /**
     * Draw vertical pagination arrows for slots.
     * Layout: [up arrow] [page X/Y] [down arrow] vertically, to the right of the slot area.
     */
    private void drawSlotPagination(Minecraft minecraft, int mouseX, int mouseY, int recipeWidth, int recipeHeight) {
        if (maxSlotPages <= 1) return;

        FontRenderer fr = minecraft.fontRenderer;
        int slotsBaseY = recipeHeight - 44;

        // Position arrows and page text to the right of the 9-slot row
        // Slots end at X = 7 + 9*18 = 169, so arrows start at X = 170
        int arrowX = 170;

        // Vertical layout: up arrow at top of first row, page text in middle, down arrow at bottom of second row
        btnSlotUpX = arrowX;
        btnSlotUpY = slotsBaseY + 2;
        btnSlotDownX = arrowX;
        btnSlotDownY = slotsBaseY + 26;

        boolean upEnabled = slotPage > 0;
        boolean downEnabled = slotPage < maxSlotPages - 1;
        boolean upHovered = upEnabled && isInArrowButton(mouseX, mouseY, btnSlotUpX, btnSlotUpY);
        boolean downHovered = downEnabled && isInArrowButton(mouseX, mouseY, btnSlotDownX, btnSlotDownY);

        // Draw up arrow
        ResourceLocation upTexture = upEnabled ? (upHovered ? ARROW_UP_HOVERED : ARROW_UP) : ARROW_UP_DISABLED;
        drawArrowTexture(minecraft, btnSlotUpX, btnSlotUpY, upTexture);

        // Draw page indicator between arrows
        String pageText = (slotPage + 1) + "/" + maxSlotPages;
        int pageTextX = arrowX + (ARROW_SIZE - fr.getStringWidth(pageText)) / 2;
        int pageTextY = slotsBaseY + 14;
        fr.drawString(pageText, pageTextX, pageTextY, 0x404040);

        // Draw down arrow
        ResourceLocation downTexture = downEnabled ? (downHovered ? ARROW_DOWN_HOVERED : ARROW_DOWN) : ARROW_DOWN_DISABLED;
        drawArrowTexture(minecraft, btnSlotDownX, btnSlotDownY, downTexture);
    }

    /**
     * Draw an arrow texture at the specified position.
     */
    private void drawArrowTexture(Minecraft minecraft, int x, int y, ResourceLocation texture) {
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1F, 1F, 1F, 1F);

        minecraft.getTextureManager().bindTexture(texture);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);

        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, ARROW_SIZE, ARROW_SIZE, ARROW_SIZE, ARROW_SIZE);

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    /**
     * Check if mouse is in an arrow button area.
     */
    private boolean isInArrowButton(int mouseX, int mouseY, int btnX, int btnY) {
        return mouseX >= btnX && mouseX < btnX + ARROW_SIZE &&
               mouseY >= btnY && mouseY < btnY + ARROW_SIZE;
    }

    private void drawButtons(Minecraft minecraft, int mouseX, int mouseY, int recipeWidth, int recipeHeight) {
        FontRenderer fr = minecraft.fontRenderer;

        // TODO: Add proper icons for buttons instead of letters

        // Layer toggle button
        boolean layerHovered = isInButton(mouseX, mouseY, btnLayerX, btnLayerY);
        String layerLabel = context.doesRender3D() ? "3" : "2";
        drawVanillaButton(btnLayerX, btnLayerY, BUTTON_SIZE, BUTTON_SIZE, layerHovered);
        int layerTextX = btnLayerX + (BUTTON_SIZE - fr.getStringWidth(layerLabel)) / 2;
        int layerTextY = btnLayerY + (BUTTON_SIZE - 8) / 2;
        fr.drawString(layerLabel, layerTextX, layerTextY, layerHovered ? 0xFFFFA0 : 0xE0E0E0);

        // Place preview button
        boolean previewHovered = isInButton(mouseX, mouseY, btnPreviewX, btnPreviewY);
        String previewLabel = "W";
        drawVanillaButton(btnPreviewX, btnPreviewY, BUTTON_SIZE, BUTTON_SIZE, previewHovered);
        int previewTextX = btnPreviewX + (BUTTON_SIZE - fr.getStringWidth(previewLabel)) / 2;
        int previewTextY = btnPreviewY + (BUTTON_SIZE - 8) / 2;
        fr.drawString(previewLabel, previewTextX, previewTextY, previewHovered ? 0xFFFFA0 : 0xE0E0E0);

        // Reset center button
        boolean resetHovered = isInButton(mouseX, mouseY, btnResetX, btnResetY);
        String resetLabel = "R";
        drawVanillaButton(btnResetX, btnResetY, BUTTON_SIZE, BUTTON_SIZE, resetHovered);
        int resetTextX = btnResetX + (BUTTON_SIZE - fr.getStringWidth(resetLabel)) / 2;
        int resetTextY = btnResetY + (BUTTON_SIZE - 8) / 2;
        fr.drawString(resetLabel, resetTextX, resetTextY, resetHovered ? 0xFFFFA0 : 0xE0E0E0);

        // Draw tooltips
        if (layerHovered) {
            drawTooltip(minecraft, mouseX, mouseY, context.doesRender3D()
                ? I18n.format("gui.machineryassembler.button.layer.to2d")
                : I18n.format("gui.machineryassembler.button.layer.to3d"));
        } else if (previewHovered) {
            drawTooltip(minecraft, mouseX, mouseY,
                I18n.format("gui.machineryassembler.button.world_preview"));
        } else if (resetHovered) {
            drawTooltip(minecraft, mouseX, mouseY,
                I18n.format("gui.machineryassembler.button.reset"));
        }
    }

    /**
     * Draw a vanilla-style button.
     */
    private void drawVanillaButton(int x, int y, int w, int h, boolean hovered) {
        // Button background colors (vanilla style)
        int bgColor = hovered ? 0xFF6060B0 : 0xFF707070;
        int lightBorder = hovered ? 0xFF9090E0 : 0xFFA0A0A0;
        int darkBorder = hovered ? 0xFF303060 : 0xFF404040;

        // Fill background
        Gui.drawRect(x + 1, y + 1, x + w - 1, y + h - 1, bgColor);

        // Top and left borders (light)
        Gui.drawRect(x, y, x + w - 1, y + 1, lightBorder);
        Gui.drawRect(x, y, x + 1, y + h - 1, lightBorder);

        // Bottom and right borders (dark)
        Gui.drawRect(x + 1, y + h - 1, x + w, y + h, darkBorder);
        Gui.drawRect(x + w - 1, y + 1, x + w, y + h, darkBorder);
    }

    private void drawTooltip(Minecraft minecraft, int mouseX, int mouseY, String text) {
        FontRenderer fr = minecraft.fontRenderer;
        int width = fr.getStringWidth(text) + 6;
        int x = mouseX + 8;
        int y = mouseY - 12;

        Gui.drawRect(x - 2, y - 2, x + width, y + 10, 0xE0000000);
        fr.drawString(text, x, y, 0xFFFFFF);
    }

    private boolean isInButton(int mouseX, int mouseY, int btnX, int btnY) {
        return mouseX >= btnX && mouseX < btnX + BUTTON_SIZE &&
               mouseY >= btnY && mouseY < btnY + BUTTON_SIZE;
    }

    @Override
    public boolean handleClick(Minecraft minecraft, int mouseX, int mouseY, int mouseButton) {
        // Right-click anywhere in the preview area starts the world preview
        if (mouseButton == 1) {
            context.snapSamples();
            if (ClientProxy.previewRenderer.startPreview(context)) {
                minecraft.displayGuiScreen(null);
            }

            return true;
        }

        if (mouseButton != 0) return false;

        // Check button clicks
        if (isInButton(mouseX, mouseY, btnLayerX, btnLayerY)) {
            // Toggle 2D/3D
            if (context.doesRender3D()) {
                context.setTo2D();
            } else {
                context.setTo3D();
            }

            return true;
        }

        if (isInButton(mouseX, mouseY, btnPreviewX, btnPreviewY)) {
            // Start in-world preview
            context.snapSamples();
            if (ClientProxy.previewRenderer.startPreview(context)) {
                minecraft.displayGuiScreen(null);
            }

            return true;
        }

        if (isInButton(mouseX, mouseY, btnResetX, btnResetY)) {
            // Reset view
            context.getRender().resetRotation();
            context.resetScale();

            return true;
        }

        // Check slot page arrows (vertical)
        if (maxSlotPages > 1) {
            if (slotPage > 0 && isInArrowButton(mouseX, mouseY, btnSlotUpX, btnSlotUpY)) {
                slotPage--;
                return true;
            }

            if (slotPage < maxSlotPages - 1 && isInArrowButton(mouseX, mouseY, btnSlotDownX, btnSlotDownY)) {
                slotPage++;
                return true;
            }
        }

        // Start dragging
        isDragging = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        return false;
    }

    /**
     * Called each frame to handle dragging and scrolling.
     */
    private void handleInput(int mouseX, int mouseY, int recipeHeight) {
        // Handle dragging - only apply rotation if we're actively tracking the mouse
        if (!Mouse.isButtonDown(0)) {
            // Mouse released, stop dragging
            isDragging = false;
        } else if (isDragging) {
            int dx = mouseX - lastMouseX;
            int dy = mouseY - lastMouseY;

            // Only apply rotation if the delta is reasonable (prevents jumps when recipe changes)
            if (Math.abs(dx) < 50 && Math.abs(dy) < 50) {
                StructureRenderHelper render = context.getRender();

                if (context.doesRender3D()) {
                    // Negate dy for intuitive vertical drag, keep dx positive for intuitive horizontal
                    render.rotate(-dy * 0.5, dx * 0.5, 0);
                } else {
                    render.translate(dx * 0.05, 0, -dy * 0.05);
                }
            }

            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }

        // Note: scroll handling is done via handleMouseScrolling() which JEI calls
    }

    /**
     * Handle mouse scroll events from JEI.
     * This is called by JEI when the mouse wheel is scrolled over the recipe area.
     * 
     * @return true if we consumed the scroll event, false to let JEI handle it
     */
    public boolean handleMouseScrolling(int mouseX, int mouseY, int scrollDelta) {
        // Check if mouse is in slot area (bottom of GUI)
        boolean inSlotArea = mouseY >= recipeHeight - SLOT_AREA_HEIGHT;

        if (inSlotArea && maxSlotPages > 1) {
            // Scroll through slot pages
            if (scrollDelta > 0 && slotPage > 0) {
                slotPage--;
                return true;
            } else if (scrollDelta < 0 && slotPage < maxSlotPages - 1) {
                slotPage++;
                return true;
            }
        } else if (context.doesRender3D()) {
            // Zoom in/out
            if (scrollDelta > 0) {
                context.zoomIn();
            } else {
                context.zoomOut();
            }

            return true;
        } else {
            // Layer up/down in 2D mode
            if (scrollDelta > 0 && context.hasSliceUp()) {
                context.sliceUp();
                return true;
            } else if (scrollDelta < 0 && context.hasSliceDown()) {
                context.sliceDown();
                return true;
            }
        }

        return false;
    }

    public StructureRenderContext getContext() {
        return context;
    }

    public int getSlotPage() {
        return slotPage;
    }

    public int getSlotsPerPage() {
        return SLOTS_PER_PAGE;
    }
}

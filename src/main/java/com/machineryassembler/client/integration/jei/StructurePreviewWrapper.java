// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/common/integration/preview/StructurePreviewWrapper.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.client.integration.jei;

import javax.annotation.Nonnull;
import java.awt.Rectangle;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeWrapper;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.client.ClientProxy;
import com.machineryassembler.client.render.StructureRenderContext;
import com.machineryassembler.client.render.StructureRenderHelper;
import com.machineryassembler.common.structure.Structure;
import com.machineryassembler.common.structure.StructureMessage;
import com.machineryassembler.common.structure.StructureOutput;
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

    // Message level icon textures
    private static final ResourceLocation ICON_INFO = new ResourceLocation(MachineryAssembler.MODID, "textures/gui/icon_info.png");
    private static final ResourceLocation ICON_WARNING = new ResourceLocation(MachineryAssembler.MODID, "textures/gui/icon_warning.png");
    private static final ResourceLocation ICON_ERROR = new ResourceLocation(MachineryAssembler.MODID, "textures/gui/icon_error.png");

    // Message panel dimensions
    private static final int MESSAGE_PANEL_MAX_WIDTH = 240;  // Maximum width when plenty of space available
    private static final int MESSAGE_PANEL_MIN_WIDTH = 80;   // Minimum width to keep text readable
    private static final int MESSAGE_PANEL_MIN_MARGIN = 8;   // Minimum margin between panel and screen edge
    private static final int MESSAGE_PANEL_PADDING = 4;
    private static final int MESSAGE_PANEL_GAP = 4;   // Gap between message windows and JEI border
    private static final int MESSAGE_PANEL_JEI_BORDER = 7; // JEI's own border extends this far left of recipe X=0
    private static final int MESSAGE_WINDOW_GAP = 2;   // Gap between individual message windows
    private static final int MESSAGE_ICON_SIZE = 10;
    private static final int MESSAGE_LINE_HEIGHT = 10;

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

    // Message item slots - tracked separately for positioning
    private int messageItemSlotStartIndex = 0;
    private List<ItemStack> messageItemStacks = new ArrayList<>();

    // Screen-space rectangles of the currently-visible message windows, for JEI exclusion areas.
    // Updated each frame in drawInfo(); empty when no messages are visible.
    private static volatile List<Rectangle> activeMessagePanelRects = new ArrayList<>();

    // Pending tooltip data for deferred rendering in DrawScreenEvent.Post phase.
    // JEI renders slots after drawInfo(), so we must defer tooltip rendering to appear on top.
    private static volatile ItemStack pendingTooltipStack = ItemStack.EMPTY;
    private static volatile int pendingTooltipX = 0;
    private static volatile int pendingTooltipY = 0;

    // Message item slot positions (recipe-relative coordinates) for tooltip/click handling
    // Index corresponds to messageItemStacks index (messages with empty stacks have null entries)
    private List<Rectangle> messageItemSlotRects = new ArrayList<>();

    // Currently hovered message item index (-1 if none)
    private int hoveredMessageItemIndex = -1;

    // Current calculated panel width (updated each frame in drawMessagePanel)
    private int currentPanelWidth = MESSAGE_PANEL_MAX_WIDTH;

    /**
     * Returns the screen-space rectangles of the active message windows.
     * Used by the JEI global GUI handler to report exclusion areas.
     * The handler checks if our category is active before calling this.
     */
    public static List<Rectangle> getActiveMessagePanelRects() {
        return activeMessagePanelRects;
    }

    /**
     * Returns pending tooltip data for deferred rendering.
     * Called by JEIScrollHandler in DrawScreenEvent.Post to render above JEI slots.
     * @return Array of [ItemStack, mouseX, mouseY] or null if no pending tooltip
     */
    public static Object[] consumePendingTooltip() {
        ItemStack stack = pendingTooltipStack;
        if (stack.isEmpty()) return null;

        // Clear pending data after consuming
        pendingTooltipStack = ItemStack.EMPTY;
        return new Object[]{stack, pendingTooltipX, pendingTooltipY};
    }

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

        // Build message item stacks from messages that have items
        messageItemStacks.clear();
        List<StructureMessage> messages = structure.getMessages();
        if (messages != null) {
            for (StructureMessage message : messages) {
                ItemStack stack = getMessageItemStack(message);
                messageItemStacks.add(stack); // Add even if empty to maintain index alignment
            }
        }

        messageItemSlotStartIndex = ingredientLists.size();

        List<List<ItemStack>> allInputs = new ArrayList<>(ingredientLists);

        // Output item from structure definition
        List<ItemStack> outputList = new ArrayList<>();
        StructureOutput output = structure.getOutput();
        if (output != null && output.isValid()) outputList.add(output.getItemStack());

        ingredients.setInputLists(VanillaTypes.ITEM, allInputs);
        ingredients.setOutputs(VanillaTypes.ITEM, outputList);
    }

    /**
     * Gets the ItemStack for a message's item field.
     */
    private ItemStack getMessageItemStack(StructureMessage message) {
        String itemId = message.getItemId();
        if (itemId == null) return ItemStack.EMPTY;

        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        if (item == null) return ItemStack.EMPTY;

        return new ItemStack(item, message.getItemCount(), message.getItemMeta());
    }

    /**
     * Checks if this structure has an output item defined.
     */
    public boolean hasOutput() {
        StructureOutput output = structure.getOutput();
        // TODO: should warn if output is defined but invalid (e.g. item doesn't exist)
        return output != null && output.isValid();
    }

    /**
     * Gets the number of message item slots needed.
     */
    public int getMessageItemSlotCount() {
        int count = 0;
        for (ItemStack stack : messageItemStacks) {
            if (!stack.isEmpty()) count++;
        }
        return count;
    }

    /**
     * Gets the starting slot index for message item slots.
     */
    public int getMessageItemSlotStartIndex() {
        return messageItemSlotStartIndex;
    }

    /**
     * Gets the list of message item stacks (includes empty stacks to maintain index alignment).
     */
    public List<ItemStack> getMessageItemStacks() {
        return messageItemStacks;
    }

    @Override
    public void drawInfo(Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        // Clear the active panel rects; drawMessagePanel will populate if this recipe has messages
        activeMessagePanelRects = new ArrayList<>();

        // Clear any pending tooltip from previous frame
        pendingTooltipStack = ItemStack.EMPTY;

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

        // Draw message panel on the left side (if there are messages)
        drawMessagePanel(minecraft, recipeHeight, mouseX, mouseY);

        // Draw tooltip for hovered message item LAST and at high z-level to appear above JEI slots.
        // We can't use getTooltipStrings() because the message panel is outside JEI's recipe bounds.
        drawMessageItemTooltip(minecraft, mouseX, mouseY);

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

        // FIXME: doesn't render
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
     * Draw individual message windows on the left side of the JEI window.
     * Each message gets its own bordered window, stacking from the bottom of the GUI upward.
     * Items are rendered manually with proper overlays and hover handling.
     */
    private void drawMessagePanel(Minecraft minecraft, int recipeHeight, int mouseX, int mouseY) {
        List<StructureMessage> messages = structure.getMessages();
        if (messages == null || messages.isEmpty()) {
            messageItemSlotRects.clear();
            hoveredMessageItemIndex = -1;
            return;
        }

        FontRenderer fr = minecraft.fontRenderer;
        RenderItem renderItem = minecraft.getRenderItem();

        // The 3D structure renderer uses raw GL11 calls with glPushAttrib/glPopAttrib,
        // which restores GL state but desynchronizes GlStateManager's internal cache.
        // We must forcefully reset the relevant GL state here.
        GlStateManager.pushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1F, 1F, 1F, 1F);

        // Get screen-space origin for exclusion area tracking and dynamic width calculation
        java.nio.FloatBuffer modelview = org.lwjgl.BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview);
        int originScreenX = (int) modelview.get(12);
        int originScreenY = (int) modelview.get(13);

        // Calculate dynamic panel width based on available screen space
        // JEI window starts at originScreenX, so available space = originScreenX - margin
        int availableSpace = originScreenX - MESSAGE_PANEL_JEI_BORDER - MESSAGE_PANEL_GAP - MESSAGE_PANEL_MIN_MARGIN;
        currentPanelWidth = Math.max(MESSAGE_PANEL_MIN_WIDTH, Math.min(MESSAGE_PANEL_MAX_WIDTH, availableSpace));

        // Pre-calculate each message window's height (bottom-up, so we need total first)
        int textWidth = currentPanelWidth - MESSAGE_PANEL_PADDING * 2 - MESSAGE_ICON_SIZE - 4;
        List<MessageWindowMetrics> windowMetrics = new ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            StructureMessage message = messages.get(i);
            String text = I18n.format(message.getKey());
            List<String> lines = wrapText(fr, text, textWidth);
            int textHeight = lines.size() * MESSAGE_LINE_HEIGHT;

            ItemStack itemStack = i < messageItemStacks.size() ? messageItemStacks.get(i) : ItemStack.EMPTY;
            boolean hasItem = !itemStack.isEmpty();

            // Content height: icon row (with text beside it), then item slot row below if present
            int contentHeight = Math.max(MESSAGE_ICON_SIZE, textHeight);
            if (hasItem) contentHeight += 20; // 18px slot + 2px gap

            int windowHeight = contentHeight + MESSAGE_PANEL_PADDING * 2;
            windowMetrics.add(new MessageWindowMetrics(lines, windowHeight, hasItem));
        }

        // JEI's own window border extends ~7px left of the recipe content area (X=0).
        // MESSAGE_PANEL_GAP provides the visual gap between the JEI border and our windows.
        int windowRight = -MESSAGE_PANEL_JEI_BORDER - MESSAGE_PANEL_GAP;
        int windowLeft = windowRight - currentPanelWidth;

        // Stack windows from the bottom of the recipe area upward
        int y = recipeHeight; // Start at bottom
        List<Rectangle> exclusionRects = new ArrayList<>();

        // Reset item slot tracking
        messageItemSlotRects = new ArrayList<>(Collections.nCopies(messageItemStacks.size(), null));
        hoveredMessageItemIndex = -1;

        for (int i = messages.size() - 1; i >= 0; i--) {
            StructureMessage message = messages.get(i);
            MessageWindowMetrics metrics = windowMetrics.get(i);

            // Move up by this window's height + gap between windows
            y -= metrics.windowHeight;

            // Draw window background (dark semi-transparent)
            Gui.drawRect(windowLeft, y, windowRight, y + metrics.windowHeight, 0xE0101010);

            // Draw border
            Gui.drawRect(windowLeft, y, windowRight, y + 1, 0xFF404040);  // top
            Gui.drawRect(windowLeft, y + metrics.windowHeight - 1, windowRight, y + metrics.windowHeight, 0xFF404040);  // bottom
            Gui.drawRect(windowLeft, y, windowLeft + 1, y + metrics.windowHeight, 0xFF404040);  // left
            Gui.drawRect(windowRight - 1, y, windowRight, y + metrics.windowHeight, 0xFF404040);  // right

            // Draw level icon
            int contentX = windowLeft + MESSAGE_PANEL_PADDING;
            int contentY = y + MESSAGE_PANEL_PADDING;
            ResourceLocation icon = getIconForLevel(message.getLevel());
            drawIconTexture(minecraft, contentX, contentY, icon);

            // Draw wrapped text lines beside the icon
            int wrapTextX = contentX + MESSAGE_ICON_SIZE + 4;
            int textColor = getColorForLevel(message.getLevel());
            int lineY = contentY;

            for (String line : metrics.wrappedLines) {
                fr.drawString(line, wrapTextX, lineY + 1, textColor);
                lineY += MESSAGE_LINE_HEIGHT;
            }

            // Draw item slot and item (if present)
            if (metrics.hasItem && i < messageItemStacks.size()) {
                ItemStack itemStack = messageItemStacks.get(i);
                if (!itemStack.isEmpty()) {
                    int slotBgX = contentX - 1; // Slot background is 18x18, item is 16x16 centered
                    int slotBgY = contentY + Math.max(MESSAGE_ICON_SIZE, metrics.wrappedLines.size() * MESSAGE_LINE_HEIGHT) + 2 - 1;

                    // Track slot position for click/tooltip handling (recipe-relative coords)
                    messageItemSlotRects.set(i, new Rectangle(slotBgX, slotBgY, 18, 18));

                    // Check if this slot is hovered
                    boolean isHovered = mouseX >= slotBgX && mouseX < slotBgX + 18 &&
                                        mouseY >= slotBgY && mouseY < slotBgY + 18;
                    if (isHovered) {
                        hoveredMessageItemIndex = i;
                    }

                    // Draw slot background
                    drawSlotBackground(slotBgX, slotBgY);

                    // Draw hover highlight if applicable
                    if (isHovered) {
                        GlStateManager.disableLighting();
                        GlStateManager.disableDepth();
                        GlStateManager.colorMask(true, true, true, false);
                        Gui.drawRect(slotBgX + 1, slotBgY + 1, slotBgX + 17, slotBgY + 17, 0x80FFFFFF);
                        GlStateManager.colorMask(true, true, true, true);
                        GlStateManager.enableDepth();
                    }

                    // Render the item
                    int itemX = slotBgX + 1;
                    int itemY = slotBgY + 1;
                    GlStateManager.enableDepth();
                    RenderHelper.enableGUIStandardItemLighting();
                    renderItem.renderItemAndEffectIntoGUI(itemStack, itemX, itemY);

                    // Render item overlay (count) with shadow - mimics vanilla but uses drawStringWithShadow
                    renderItemOverlayWithShadow(fr, itemStack, itemX, itemY);

                    RenderHelper.disableStandardItemLighting();
                    GlStateManager.disableDepth();
                }
            }

            // Track exclusion area for this window
            exclusionRects.add(new Rectangle(
                originScreenX + windowLeft, originScreenY + y,
                currentPanelWidth, metrics.windowHeight
            ));

            // Gap between windows
            y -= MESSAGE_WINDOW_GAP;
        }

        activeMessagePanelRects = exclusionRects;

        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    /**
     * Render item overlay (count/durability) with shadowed text like standard JEI slots.
     */
    private void renderItemOverlayWithShadow(FontRenderer fr, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();

        // Item count
        if (stack.getCount() != 1) {
            String countText = String.valueOf(stack.getCount());
            // Position at bottom-right corner of slot, same as vanilla
            int textX = x + 17 - fr.getStringWidth(countText);
            int textY = y + 9;
            fr.drawStringWithShadow(countText, textX, textY, 0xFFFFFF);
        }

        // Durability bar (if item is damageable and damaged)
        if (stack.isItemDamaged()) {
            int damage = stack.getItemDamage();
            int maxDamage = stack.getMaxDamage();
            int durabilityWidth = Math.round(13.0F * (1.0F - (float) damage / (float) maxDamage));
            int durabilityColor = getDurabilityColor(damage, maxDamage);

            Gui.drawRect(x + 2, y + 13, x + 15, y + 15, 0xFF000000);
            Gui.drawRect(x + 2, y + 13, x + 2 + durabilityWidth, y + 14, durabilityColor);
        }

        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
    }

    /**
     * Get durability bar color based on damage (green to red gradient).
     */
    private int getDurabilityColor(int damage, int maxDamage) {
        float ratio = 1.0F - (float) damage / (float) maxDamage;
        int r = (int) (255 * (1.0F - ratio));
        int g = (int) (255 * ratio);
        return 0xFF000000 | (r << 16) | (g << 8);
    }

    /**
     * Get tooltip strings for hovered message items.
     * Note: This won't be called by JEI for message items since they're outside the recipe bounds.
     * Tooltip rendering is handled manually in drawMessageItemTooltip().
     */
    @Override
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
        return Collections.emptyList();
    }

    /**
     * Store pending tooltip data for deferred rendering.
     * The actual tooltip is rendered in DrawScreenEvent.Post by JEIScrollHandler
     * to ensure it appears above JEI's slot rendering.
     */
    private void drawMessageItemTooltip(Minecraft minecraft, int mouseX, int mouseY) {
        if (hoveredMessageItemIndex < 0 || hoveredMessageItemIndex >= messageItemStacks.size()) return;

        ItemStack stack = messageItemStacks.get(hoveredMessageItemIndex);
        if (stack.isEmpty()) return;

        // Get the current GL modelview matrix to convert recipe-relative coords to screen coords
        FloatBuffer modelview = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview);
        int originScreenX = (int) modelview.get(12);
        int originScreenY = (int) modelview.get(13);

        // Store pending tooltip data for deferred rendering
        pendingTooltipStack = stack;
        pendingTooltipX = originScreenX + mouseX;
        pendingTooltipY = originScreenY + mouseY;
    }

    /**
     * Draw a vanilla-style slot background (the dark inset square behind items).
     */
    private void drawSlotBackground(int x, int y) {
        // Standard MC slot background: 18x18 with dark inset borders
        // Outer border (dark)
        Gui.drawRect(x, y, x + 18, y + 1, 0xFF373737);       // top
        Gui.drawRect(x, y, x + 1, y + 18, 0xFF373737);       // left
        // Inner border (lighter)
        Gui.drawRect(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);  // bottom
        Gui.drawRect(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);  // right
        // Fill (dark gray)
        Gui.drawRect(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    /**
     * Holds pre-calculated metrics for a single message window.
     */
    private static class MessageWindowMetrics {
        final List<String> wrappedLines;
        final int windowHeight;
        final boolean hasItem;

        MessageWindowMetrics(List<String> wrappedLines, int windowHeight, boolean hasItem) {
            this.wrappedLines = wrappedLines;
            this.windowHeight = windowHeight;
            this.hasItem = hasItem;
        }
    }

    /**
     * Calculate the height of a single message window.
     * Uses MESSAGE_PANEL_MAX_WIDTH as width for initial layout estimation.
     */
    static int calculateMessageWindowHeight(FontRenderer fr, StructureMessage message, boolean hasItem) {
        return calculateMessageWindowHeight(fr, message, hasItem, MESSAGE_PANEL_MAX_WIDTH);
    }

    /**
     * Calculate the height of a single message window with specified width.
     */
    static int calculateMessageWindowHeight(FontRenderer fr, StructureMessage message, boolean hasItem, int panelWidth) {
        int textWidth = panelWidth - MESSAGE_PANEL_PADDING * 2 - MESSAGE_ICON_SIZE - 4;
        String text = I18n.format(message.getKey());
        // Approximate line count (same logic as wrapText but just counting)
        int textHeight = fr.listFormattedStringToWidth(text, textWidth).size() * MESSAGE_LINE_HEIGHT;
        int contentHeight = Math.max(MESSAGE_ICON_SIZE, textHeight);
        if (hasItem) contentHeight += 20;

        return contentHeight + MESSAGE_PANEL_PADDING * 2;
    }

    /**
     * Wrap text to fit within the specified width.
     * Breaks on spaces first, and force-breaks long words character-by-character.
     */
    private List<String> wrapText(FontRenderer fr, String text, int maxWidth) {
        if (maxWidth <= 0) return Collections.singletonList(text);

        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            // If the word itself is too long, force-break it character by character
            if (fr.getStringWidth(word) > maxWidth) {
                // Flush current line first
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }

                // Break the long word into fitting chunks
                StringBuilder chunk = new StringBuilder();
                for (int ci = 0; ci < word.length(); ci++) {
                    char ch = word.charAt(ci);
                    if (fr.getStringWidth(chunk.toString() + ch) > maxWidth && chunk.length() > 0) {
                        lines.add(chunk.toString());
                        chunk = new StringBuilder();
                    }
                    chunk.append(ch);
                }

                // Remaining chunk becomes the new current line
                currentLine = chunk;
                continue;
            }

            String testLine = currentLine.length() > 0 ? currentLine + " " + word : word;

            if (fr.getStringWidth(testLine) <= maxWidth) {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(word);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines.isEmpty() ? Collections.singletonList(text) : lines;
    }

    /**
     * Get the icon texture for a message level.
     */
    private ResourceLocation getIconForLevel(StructureMessage.Level level) {
        switch (level) {
            case WARNING:
                return ICON_WARNING;
            case ERROR:
                return ICON_ERROR;
            case INFO:
            default:
                return ICON_INFO;
        }
    }

    /**
     * Get the text color for a message level.
     */
    private int getColorForLevel(StructureMessage.Level level) {
        switch (level) {
            case WARNING:
                return 0xFFAA00;
            case ERROR:
                return 0xFF4444;
            case INFO:
            default:
                return 0xAAAAFF;
        }
    }

    /**
     * Draw an icon texture at the specified position.
     */
    private void drawIconTexture(Minecraft minecraft, int x, int y, ResourceLocation texture) {
        GlStateManager.color(1F, 1F, 1F, 1F);
        minecraft.getTextureManager().bindTexture(texture);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);

        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, MESSAGE_ICON_SIZE, MESSAGE_ICON_SIZE, MESSAGE_ICON_SIZE, MESSAGE_ICON_SIZE);

        GlStateManager.disableBlend();
    }

    /**
     * Get the exclusion area rectangles for JEI.
     * This prevents JEI from rendering tooltips/bookmarks over our message windows.
     */
    public List<Rectangle> getExclusionAreas(int guiLeft, int guiTop) {
        // The real exclusion areas are tracked via activeMessagePanelRects (screen-space),
        // populated each frame in drawMessagePanel(). Return them as-is.
        return activeMessagePanelRects;
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
            // First check if right-clicking a message item slot (show usages)
            if (handleMessageItemClick(mouseX, mouseY, true)) return true;

            context.snapSamples();
            if (ClientProxy.previewRenderer.startPreview(context)) {
                minecraft.displayGuiScreen(null);
            }

            return true;
        }

        if (mouseButton != 0) return false;

        // Check message item slot clicks (left-click = show recipes)
        if (handleMessageItemClick(mouseX, mouseY, false)) return true;

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
     * Handle click on message item slots.
     * Called from both handleClick (for clicks inside recipe area) and JEIScrollHandler (for clicks outside).
     * @param mouseX Mouse X position (recipe-relative)
     * @param mouseY Mouse Y position (recipe-relative)
     * @param showUsages If true, show usages (right-click); if false, show recipes (left-click)
     * @return true if a message item was clicked and handled
     */
    public boolean handleMessageItemClick(int mouseX, int mouseY, boolean showUsages) {
        for (int i = 0; i < messageItemSlotRects.size(); i++) {
            Rectangle rect = messageItemSlotRects.get(i);
            if (rect == null) continue;

            if (mouseX >= rect.x && mouseX < rect.x + rect.width &&
                mouseY >= rect.y && mouseY < rect.y + rect.height) {

                ItemStack stack = messageItemStacks.get(i);
                if (stack.isEmpty()) continue;

                // Trigger JEI recipe/usage lookup
                if (MAJEIPlugin.jeiRuntime != null) {
                    IFocus.Mode mode = showUsages ? IFocus.Mode.OUTPUT : IFocus.Mode.INPUT;
                    MAJEIPlugin.jeiRuntime.getRecipesGui().show(
                        MAJEIPlugin.jeiRuntime.getRecipeRegistry().createFocus(mode, stack));
                    return true;
                }
            }
        }

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

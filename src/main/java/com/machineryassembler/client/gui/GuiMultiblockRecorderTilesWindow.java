package com.machineryassembler.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.client.config.GuiUtils;

import com.machineryassembler.common.recording.MultiblockRecordingExclusions;
import com.machineryassembler.common.recording.MultiblockRecordingSnapshot;
import com.machineryassembler.common.recording.MultiblockRecordingSnapshot.TileSummary;
import com.machineryassembler.common.recording.MultiblockRecordingSnapshot.TileTagSummary;
import com.machineryassembler.common.util.BlockStackUtils;


/**
 * Scrollable tile-tag toggle window for the recorder.
 */
public class GuiMultiblockRecorderTilesWindow {

    private static final int HEADER_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 16;
    private static final int PADDING = 6;
    private static final int ICON_SIZE = 16;
    private static final int CARD_SPACING = 6;
    private static final int ROW_HEIGHT = 14;
    private static final int KEY_COLUMN_WIDTH = 86;

    private final GuiScreen parent;
    private final MultiblockRecordingSnapshot snapshot;
    private final MultiblockRecordingExclusions exclusions;

    private boolean visible;
    private int windowX;
    private int windowY;
    private int windowW;
    private int windowH;
    private int scrollOffset;
    private int maxScroll;

    private TileSummary hoveredIconTile;
    private TileSummary hoveredContentTile;
    private TileTagSummary hoveredTag;
    private String hoveredContent;

    public GuiMultiblockRecorderTilesWindow(GuiScreen parent, MultiblockRecordingSnapshot snapshot,
            MultiblockRecordingExclusions exclusions) {
        this.parent = parent;
        this.snapshot = snapshot;
        this.exclusions = exclusions;
    }

    public void show() {
        visible = true;
        calculateLayout();
    }

    public void hide() {
        visible = false;
        hoveredIconTile = null;
        hoveredContentTile = null;
        hoveredTag = null;
        hoveredContent = null;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (!visible) return false;

        if (!isMouseOver(mouseX, mouseY)) {
            hide();
            return true;
        }

        if (hoveredTag != null) {
            boolean included = exclusions.isTileTagIncluded(hoveredTag.getTileKey(), hoveredTag.getKey());
            exclusions.setTileTagIncluded(hoveredTag.getTileKey(), hoveredTag.getKey(), !included);
            return true;
        }

        return true;
    }

    public boolean handleKey(int keyCode) {
        if (!visible) return false;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            hide();
            return true;
        }

        return true;
    }

    public boolean handleMouseInput(int mouseX, int mouseY, int wheel) {
        if (!visible || wheel == 0) return false;
        if (!isMouseOver(mouseX, mouseY)) return false;

        scrollOffset = clamp(scrollOffset - Integer.signum(wheel) * 16, 0, maxScroll);
        return true;
    }

    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        List<TileSummary> tiles = getVisibleTiles();
        updateMaxScroll(tiles);

        hoveredIconTile = null;
        hoveredContentTile = null;
        hoveredTag = null;
        hoveredContent = null;

        Gui.drawRect(windowX - 1, windowY - 1, windowX + windowW + 1, windowY + windowH + 1, 0xFF303030);
        Gui.drawRect(windowX, windowY, windowX + windowW, windowY + windowH, 0xFF1A1A1A);
        font.drawString(I18n.format("gui.machineryassembler.recorder.tiles.title"), windowX + 6, windowY + 6, 0xFFFFFF);

        int contentX = windowX + PADDING;
        int contentY = windowY + HEADER_HEIGHT + PADDING;
        int contentW = windowW - PADDING * 2;
        int contentBottom = windowY + windowH - FOOTER_HEIGHT - PADDING;

        if (tiles.isEmpty()) {
            font.drawString(I18n.format("gui.machineryassembler.recorder.tiles.empty"), contentX, contentY + 6, 0xFF7777);
        } else {
            drawTiles(font, tiles, mouseX, mouseY, contentX, contentY, contentW, contentBottom);
        }

        String footer = I18n.format(
            "gui.machineryassembler.recorder.tiles.footer",
            tiles.size(),
            snapshot.getEnabledTileTagCount(exclusions),
            snapshot.getVisibleTileTagCount(exclusions)
        );
        font.drawString(footer, windowX + 6, windowY + windowH - FOOTER_HEIGHT + 2, 0xCCCCCC);
    }

    public void drawTooltips(int mouseX, int mouseY) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen currentScreen = mc.currentScreen;
        int screenWidth = currentScreen != null ? currentScreen.width : mc.displayWidth;
        int screenHeight = currentScreen != null ? currentScreen.height : mc.displayHeight;

        if (hoveredIconTile != null) {
            ItemStack stack = getDisplayStack(hoveredIconTile);
            List<String> tooltipLines = new ArrayList<>();

            if (!stack.isEmpty()) {
                ITooltipFlag.TooltipFlags tooltipFlag = mc.gameSettings.advancedItemTooltips
                    ? ITooltipFlag.TooltipFlags.ADVANCED
                    : ITooltipFlag.TooltipFlags.NORMAL;
                tooltipLines.addAll(stack.getTooltip(mc.player, tooltipFlag));
            }

            if (tooltipLines.isEmpty()) tooltipLines.add(hoveredIconTile.getBlockKey());

            GuiUtils.drawHoveringText(tooltipLines, mouseX, mouseY, screenWidth, screenHeight, -1, mc.fontRenderer);
            return;
        }

        if (hoveredTag != null) {
            boolean excluded = exclusions.isTileTagExcluded(hoveredTag.getTileKey(), hoveredTag.getKey());
            List<String> tooltipLines = new ArrayList<>();
            tooltipLines.add(hoveredTag.getKey());
            tooltipLines.add(I18n.format(excluded
                ? "gui.machineryassembler.recorder.tiles.tag_tooltip.excluded"
                : "gui.machineryassembler.recorder.tiles.tag_tooltip.included"));
            GuiUtils.drawHoveringText(tooltipLines, mouseX, mouseY, screenWidth, screenHeight, -1, mc.fontRenderer);
            return;
        }

        if (hoveredContent != null) {
            GuiUtils.drawHoveringText(Collections.singletonList(hoveredContent), mouseX, mouseY, screenWidth, screenHeight, -1, mc.fontRenderer);
        }
    }

    private void calculateLayout() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        int screenW = resolution.getScaledWidth();
        int screenH = resolution.getScaledHeight();

        windowW = Math.min(372, screenW - 40);
        windowH = Math.min(252, screenH - 40);
        windowX = (screenW - windowW) / 2;
        windowY = (screenH - windowH) / 2;
        scrollOffset = 0;
        maxScroll = 0;
    }

    private void updateMaxScroll(List<TileSummary> tiles) {
        int totalHeight = 0;
        for (TileSummary tile : tiles) {
            totalHeight += getTileCardHeight(tile) + CARD_SPACING;
        }

        int availableHeight = windowH - HEADER_HEIGHT - FOOTER_HEIGHT - PADDING * 2;
        maxScroll = Math.max(0, totalHeight - availableHeight);
        scrollOffset = clamp(scrollOffset, 0, maxScroll);
    }

    private void drawTiles(FontRenderer font, List<TileSummary> tiles, int mouseX, int mouseY,
            int contentX, int contentY, int contentW, int contentBottom) {
        int contentHeight = Math.max(0, contentBottom - contentY);
        if (contentHeight <= 0) return;

        GlStateManager.pushMatrix();
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        enableScissor(contentX, contentY, contentW, contentHeight);

        int cardY = contentY - scrollOffset;
        for (TileSummary tile : tiles) {
            int cardH = getTileCardHeight(tile);
            if (cardY + cardH <= contentY) {
                cardY += cardH + CARD_SPACING;
                continue;
            }
            if (cardY >= contentBottom) break;

            drawTileCard(font, tile, mouseX, mouseY, contentX, cardY, contentW, cardH, contentY, contentBottom);
            cardY += cardH + CARD_SPACING;
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
    }

    private void drawTileCard(FontRenderer font, TileSummary tile, int mouseX, int mouseY,
            int cardX, int cardY, int cardW, int cardH, int clipTop, int clipBottom) {
        Gui.drawRect(cardX, cardY, cardX + cardW, cardY + cardH, 0xFF202020);
        Gui.drawRect(cardX, cardY, cardX + 1, cardY + cardH, 0xFF77C7B1);
        Gui.drawRect(cardX, cardY, cardX + cardW, cardY + 1, 0xFF77C7B1);

        ItemStack stack = getDisplayStack(tile);
        int iconX = cardX + PADDING;
        int iconY = cardY + PADDING;
        if (!stack.isEmpty()) {
            net.minecraft.client.Minecraft.getMinecraft().getRenderItem().renderItemIntoGUI(stack, iconX, iconY);
        }

        if (mouseX >= iconX && mouseX < iconX + ICON_SIZE
                && mouseY >= Math.max(iconY, clipTop) && mouseY < Math.min(iconY + ICON_SIZE, clipBottom)) {
            hoveredIconTile = tile;
        }

        if (tile.getCount() > 1) {
            String countText = "x" + tile.getCount();
            font.drawString(countText, iconX, iconY + ICON_SIZE + 2, 0xCCCCCC);
        }

        int rowsX = iconX + ICON_SIZE + 10;
        int contentX = rowsX + KEY_COLUMN_WIDTH + 8;
        int contentW = cardX + cardW - contentX - PADDING;
        int rowY = cardY + PADDING;

        for (TileTagSummary tag : tile.getVisibleTags()) {
            if (rowY + ROW_HEIGHT > clipTop && rowY < clipBottom) {
                boolean excluded = exclusions.isTileTagExcluded(tag.getTileKey(), tag.getKey());
                int keyColor = excluded ? 0xFF989898 : 0xFFE1C89C;
                int contentColor = excluded ? 0xFF777777 : 0xFFB8B8B8;
                String keyText = font.trimStringToWidth(tag.getKey(), KEY_COLUMN_WIDTH - 4);
                font.drawString(keyText, rowsX, rowY, keyColor);

                String contentText = shortenMiddleToWidth(font, tag.getContent(), contentW);
                font.drawString(contentText, contentX, rowY, contentColor);

                if (mouseX >= rowsX && mouseX <= rowsX + KEY_COLUMN_WIDTH
                        && mouseY >= Math.max(rowY, clipTop) && mouseY < Math.min(rowY + ROW_HEIGHT, clipBottom)) {
                    hoveredTag = tag;
                } else if (mouseX >= contentX && mouseX <= contentX + contentW
                        && mouseY >= Math.max(rowY, clipTop) && mouseY < Math.min(rowY + ROW_HEIGHT, clipBottom)) {
                    hoveredContentTile = tile;
                    hoveredContent = tag.getContent();
                }
            }

            rowY += ROW_HEIGHT;
        }
    }

    private List<TileSummary> getVisibleTiles() {
        List<TileSummary> tiles = new ArrayList<>();
        for (TileSummary tileSummary : snapshot.getTileSummaries()) {
            if (exclusions.isBlockExcluded(tileSummary.getBlockKey())) continue;
            tiles.add(tileSummary);
        }

        return tiles;
    }

    private ItemStack getDisplayStack(TileSummary tile) {
        return BlockStackUtils.getStackFromBlockState(tile.getState(), snapshot.getFilteredTileData(tile, exclusions));
    }

    private int getTileCardHeight(TileSummary tile) {
        int rowsHeight = Math.max(ICON_SIZE, tile.getVisibleTags().size() * ROW_HEIGHT);
        return rowsHeight + PADDING * 2;
    }

    private boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= windowX && mouseX <= windowX + windowW
            && mouseY >= windowY && mouseY <= windowY + windowH;
    }

    private static void enableScissor(int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        int scaleFactor = resolution.getScaleFactor();

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
            x * scaleFactor,
            mc.displayHeight - (y + height) * scaleFactor,
            width * scaleFactor,
            height * scaleFactor
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String shortenMiddleToWidth(FontRenderer font, String text, int maxWidth) {
        if (font.getStringWidth(text) <= maxWidth) return text;
        if (maxWidth <= font.getStringWidth("...")) return "...";

        int left = text.length() / 2;
        int right = left;
        String shortened = text;

        while (left > 1 && right < text.length() - 1) {
            left--;
            right++;
            shortened = text.substring(0, left) + "..." + text.substring(right);
            if (font.getStringWidth(shortened) <= maxWidth) return shortened;
        }

        return font.trimStringToWidth(text, maxWidth);
    }
}
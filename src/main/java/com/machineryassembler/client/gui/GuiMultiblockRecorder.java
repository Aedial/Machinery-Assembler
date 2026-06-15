package com.machineryassembler.client.gui;

import java.io.IOException;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import com.machineryassembler.common.network.NetworkHandler;
import com.machineryassembler.common.network.PacketRequestMultiblockRecordingSave;
import com.machineryassembler.common.recording.MultiblockRecordingExclusions;
import com.machineryassembler.common.recording.MultiblockRecordingSnapshot;
import com.machineryassembler.common.recording.MultiblockRecordingSnapshot.ExportBounds;


/**
 * Main multiblock recording review screen.
 */
public class GuiMultiblockRecorder extends GuiScreen {

    private static final int BUTTON_BLOCKS = 1;
    private static final int BUTTON_TILES = 2;
    private static final int BUTTON_PREVIEW = 3;
    private static final int BUTTON_SAVE = 4;
    private static final int BUTTON_CANCEL = 5;

    private static final int PANEL_PADDING = 12;
    private static final int HEADER_HEIGHT = 28;
    private static final int SIZE_BAND_HEIGHT = 22;
    private static final int CORNER_CARD_HEIGHT = 38;
    private static final int SECTION_CARD_HEIGHT = 72;
    private static final int ACTION_BUTTON_HEIGHT = 18;
    private static final int FOOTER_LINE_SPACING = 11;
    private static final int STAT_BUTTON_HEIGHT = 16;
    private static final int STAT_BUTTON_SIDE_MARGIN = 6;
    private static final int CARD_GAP = 8;

    private final MultiblockRecordingSnapshot snapshot;
    private final BlockPos firstCorner;
    private final BlockPos secondCorner;
    private final MultiblockRecordingExclusions exclusions;

    private GuiMultiblockRecorderBlocksWindow blocksWindow;
    private GuiMultiblockRecorderTilesWindow tilesWindow;
    private GuiMultiblockRecorderPreviewWindow previewWindow;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public GuiMultiblockRecorder(MultiblockRecordingSnapshot snapshot, BlockPos firstCorner, BlockPos secondCorner,
            MultiblockRecordingExclusions exclusions) {
        this.snapshot = snapshot;
        this.firstCorner = firstCorner;
        this.secondCorner = secondCorner;
        this.exclusions = exclusions;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        calculateLayout();

        int statsButtonY = getStatsY() + SECTION_CARD_HEIGHT - STAT_BUTTON_HEIGHT - STAT_BUTTON_SIDE_MARGIN;
        int statsButtonWidth = getStatCardWidth() - STAT_BUTTON_SIDE_MARGIN * 2;
        int actionButtonWidth = (panelWidth - PANEL_PADDING * 4) / 3;
        int actionButtonY = getActionY();

        addStatButton(BUTTON_BLOCKS, 0, statsButtonY, statsButtonWidth, 0xC02B3244, 0xE03B4660, 0xFF5E6C90, 0xFF8CA3D9);
        addStatButton(BUTTON_TILES, 1, statsButtonY, statsButtonWidth, 0xC0283F3A, 0xE0385951, 0xFF4E8A7C, 0xFF77C7B1);

        addActionButton(BUTTON_PREVIEW, 0, actionButtonY, actionButtonWidth, "gui.machineryassembler.recorder.preview",
            0xC0233656, 0xE0334E7B, 0xFF5F81C2, 0xFF96B5EB);
        addActionButton(BUTTON_SAVE, 1, actionButtonY, actionButtonWidth, "gui.machineryassembler.recorder.save",
            0xC0275134, 0xE03B7348, 0xFF61A06E, 0xFF95D59E);
        addActionButton(BUTTON_CANCEL, 2, actionButtonY, actionButtonWidth, "gui.machineryassembler.recorder.cancel",
            0xC05A2A26, 0xE07B3833, 0xFFB56B63, 0xFFF0A79B);

        blocksWindow = new GuiMultiblockRecorderBlocksWindow(this, snapshot.getBlockSummaries(), exclusions);
        tilesWindow = new GuiMultiblockRecorderTilesWindow(this, snapshot, exclusions);
        previewWindow = new GuiMultiblockRecorderPreviewWindow(snapshot, exclusions);
        updateButtonState();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_BLOCKS) {
            blocksWindow.show();
            return;
        }

        if (button.id == BUTTON_TILES) {
            tilesWindow.show();
            return;
        }

        if (button.id == BUTTON_PREVIEW) {
            if (!hasExportContent()) {
                sendRecorderMessage("message.machineryassembler.recorder.empty_after_exclusions");
                return;
            }

            previewWindow.show();
            return;
        }

        if (button.id == BUTTON_SAVE) {
            if (!hasExportContent()) {
                sendRecorderMessage("message.machineryassembler.recorder.empty_after_exclusions");
                return;
            }

            NetworkHandler.INSTANCE.sendToServer(new PacketRequestMultiblockRecordingSave(firstCorner, secondCorner, exclusions.copy()));
            mc.displayGuiScreen(null);
            return;
        }

        if (button.id == BUTTON_CANCEL) mc.displayGuiScreen(null);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (previewWindow.isVisible() && previewWindow.handleKey(keyCode)) return;
        if (blocksWindow.isVisible() && blocksWindow.handleKey(keyCode)) return;
        if (tilesWindow.isVisible() && tilesWindow.handleKey(keyCode)) return;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (previewWindow.isVisible() && previewWindow.handleClick(mouseX, mouseY, mouseButton)) return;
        if (blocksWindow.isVisible() && blocksWindow.handleClick(mouseX, mouseY, mouseButton)) return;
        if (tilesWindow.isVisible() && tilesWindow.handleClick(mouseX, mouseY, mouseButton)) return;

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (previewWindow.isVisible() && previewWindow.handleRelease(mouseX, mouseY, state)) return;

        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (previewWindow.isVisible() && previewWindow.handleDrag(mouseX, mouseY, clickedMouseButton)) return;

        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int wheel = Mouse.getEventDWheel();

        if (previewWindow.isVisible()) {
            previewWindow.handleMouseInput(mouseX, mouseY, wheel);
            return;
        }

        if (blocksWindow.isVisible()) return;
        if (tilesWindow.isVisible() && tilesWindow.handleMouseInput(mouseX, mouseY, wheel)) return;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGradientRect(0, 0, width, height, 0x60110D08, 0x78000000);
        updateButtonState();

        boolean previewVisible = previewWindow.isVisible();
        boolean modalVisible = previewVisible || blocksWindow.isVisible() || tilesWindow.isVisible();
        int effectiveMouseX = modalVisible ? -1 : mouseX;
        int effectiveMouseY = modalVisible ? -1 : mouseY;

        drawCapturePanel();
        super.drawScreen(effectiveMouseX, effectiveMouseY, partialTicks);

        if (previewVisible) {
            previewWindow.draw(mouseX, mouseY, partialTicks);
            previewWindow.drawTooltips(mouseX, mouseY);
            return;
        }

        if (blocksWindow.isVisible()) {
            blocksWindow.draw(mouseX, mouseY, partialTicks);
            blocksWindow.drawTooltips(mouseX, mouseY);
        }

        if (tilesWindow.isVisible()) {
            tilesWindow.draw(mouseX, mouseY, partialTicks);
            tilesWindow.drawTooltips(mouseX, mouseY);
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        previewWindow.release();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void updateButtonState() {
        int excludedBlocks = getExcludedBlockCount();
        int excludedTileTags = getExcludedTileTagCount();
        boolean hasContent = hasExportContent();

        for (GuiButton button : buttonList) {
            if (button.id == BUTTON_BLOCKS) {
                button.displayString = I18n.format("gui.machineryassembler.recorder.blocks_button", excludedBlocks);
                continue;
            }

            if (button.id == BUTTON_TILES) {
                button.displayString = I18n.format("gui.machineryassembler.recorder.tiles_button", excludedTileTags);
                continue;
            }

            if (button.id == BUTTON_PREVIEW || button.id == BUTTON_SAVE) {
                button.enabled = hasContent;
            }
        }
    }

    private void calculateLayout() {
        panelWidth = Math.min(382, width - 28);
        panelHeight = Math.min(238, height - 24);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
    }

    private void drawCapturePanel() {
        Gui.drawRect(panelX - 3, panelY - 3, panelX + panelWidth + 3, panelY + panelHeight + 3, 0x50000000);
        Gui.drawRect(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0xFF4F3820);
        Gui.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE014120F);
        drawGradientRect(panelX, panelY, panelX + panelWidth, panelY + HEADER_HEIGHT, 0xF0372B18, 0xF01E1811);
        Gui.drawRect(panelX, panelY + HEADER_HEIGHT, panelX + panelWidth, panelY + HEADER_HEIGHT + 1, 0xFFC89254);

        fontRenderer.drawStringWithShadow(I18n.format("gui.machineryassembler.recorder.title"), panelX + PANEL_PADDING, panelY + 10, 0xFFF5EEE4);

        drawSizeSummaryBand();
        drawCornerSummaryCards();
        drawStatSummaryCards();
        drawFooterSummaryText();
    }

    private void drawSizeSummaryBand() {
        ExportBounds bounds = snapshot.getExportBounds(exclusions);
        String text = bounds == null
            ? I18n.format("gui.machineryassembler.recorder.empty_after_exclusions_label")
            : I18n.format("gui.machineryassembler.recorder.size", bounds.getSizeX(), bounds.getSizeY(), bounds.getSizeZ());

        drawInfoBand(panelX + PANEL_PADDING, getSizeBandY(), panelWidth - PANEL_PADDING * 2, SIZE_BAND_HEIGHT,
            0xD019241D, 0xFF4E8A64, text, bounds == null ? 0xFFE6A4A4 : 0xFFB8F3C6);
    }

    private void drawCornerSummaryCards() {
        int cornerY = getCornerCardsY();
        int cardWidth = getCornerCardWidth();

        drawInfoCard(getCornerCardX(0, cardWidth), cornerY, cardWidth, CORNER_CARD_HEIGHT, 0xC01A1917, 0xFF8A6330,
            I18n.format("gui.machineryassembler.recorder.corner_a.short"), formatCorner(firstCorner));
        drawInfoCard(getCornerCardX(1, cardWidth), cornerY, cardWidth, CORNER_CARD_HEIGHT, 0xC01A1917, 0xFF8A6330,
            I18n.format("gui.machineryassembler.recorder.corner_b.short"), formatCorner(secondCorner));
    }

    private void drawStatSummaryCards() {
        int statsY = getStatsY();

        drawSectionCard(getStatCardX(0), statsY, getStatCardWidth(), SECTION_CARD_HEIGHT, 0xC01A1919, 0xFF8CA3D9,
            I18n.format("gui.machineryassembler.recorder.blocks.short"),
            I18n.format("gui.machineryassembler.recorder.block_types", snapshot.getBlockSummaries().size()),
            I18n.format("gui.machineryassembler.recorder.total_blocks", snapshot.getTotalBlockCount()));

        drawSectionCard(getStatCardX(1), statsY, getStatCardWidth(), SECTION_CARD_HEIGHT, 0xC0161D1B, 0xFF77C7B1,
            I18n.format("gui.machineryassembler.recorder.tiles.short"),
            I18n.format("gui.machineryassembler.recorder.tile_groups", snapshot.getVisibleTileGroupCount(exclusions)),
            I18n.format("gui.machineryassembler.recorder.tile_tags", snapshot.getVisibleTileTagCount(exclusions)));
    }

    private void drawFooterSummaryText() {
        int footerY = getFooterY();
        drawFooterLine(footerY, "gui.machineryassembler.recorder.output_folder", 0xFFD6BC93);
        drawFooterLine(footerY + FOOTER_LINE_SPACING, "gui.machineryassembler.recorder.hint", 0xFFAAA39A);
    }

    private void drawFooterLine(int y, String translationKey, int color) {
        fontRenderer.drawString(fontRenderer.trimStringToWidth(I18n.format(translationKey), panelWidth - PANEL_PADDING * 2),
            panelX + PANEL_PADDING, y, color);
    }

    private void addStatButton(int buttonId, int index, int y, int width, int baseColor, int hoveredColor,
            int borderColor, int accentColor) {
        buttonList.add(new RecorderButton(buttonId, getStatButtonX(index), y, width, STAT_BUTTON_HEIGHT, "",
            baseColor, hoveredColor, borderColor, accentColor));
    }

    private void addActionButton(int buttonId, int index, int y, int width, String labelKey,
            int baseColor, int hoveredColor, int borderColor, int accentColor) {
        buttonList.add(new RecorderButton(buttonId, getActionButtonX(index, width), y, width, ACTION_BUTTON_HEIGHT,
            I18n.format(labelKey), baseColor, hoveredColor, borderColor, accentColor));
    }

    private void drawInfoBand(int x, int y, int width, int height, int backgroundColor, int borderColor,
            String text, int textColor) {
        Gui.drawRect(x - 1, y - 1, x + width + 1, y + height + 1, borderColor);
        Gui.drawRect(x, y, x + width, y + height, backgroundColor);
        Gui.drawRect(x, y, x + width, y + 1, borderColor);
        fontRenderer.drawString(text, x + 8, y + 7, textColor);
    }

    private void drawInfoCard(int x, int y, int width, int height, int backgroundColor, int accentColor,
            String title, String value) {
        Gui.drawRect(x, y, x + width, y + height, backgroundColor);
        Gui.drawRect(x, y, x + 2, y + height, accentColor);
        fontRenderer.drawString(title, x + 8, y + 6, 0xFFE8D8BE);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(value, width - 14), x + 8, y + 20, 0xFFD7D0C7);
    }

    private void drawSectionCard(int x, int y, int width, int height, int backgroundColor, int accentColor,
            String title, String lineOne, String lineTwo) {
        Gui.drawRect(x, y, x + width, y + height, backgroundColor);
        Gui.drawRect(x, y, x + 2, y + height, accentColor);
        Gui.drawRect(x, y, x + width, y + 1, accentColor);

        fontRenderer.drawString(title, x + 8, y + 6, 0xFFF5EEE4);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(lineOne, width - 14), x + 8, y + 22, 0xFFE1C89C);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(lineTwo, width - 14), x + 8, y + 34, 0xFFB8B8B8);
    }

    private int getExcludedBlockCount() {
        int count = 0;
        for (MultiblockRecordingSnapshot.BlockSummary blockSummary : snapshot.getBlockSummaries()) {
            if (exclusions.isBlockExcluded(blockSummary.getKey())) count++;
        }

        return count;
    }

    private int getExcludedTileTagCount() {
        int count = 0;
        for (MultiblockRecordingSnapshot.TileSummary tileSummary : snapshot.getTileSummaries()) {
            if (exclusions.isBlockExcluded(tileSummary.getBlockKey())) continue;

            for (MultiblockRecordingSnapshot.TileTagSummary tagSummary : tileSummary.getVisibleTags()) {
                if (exclusions.isTileTagExcluded(tagSummary.getTileKey(), tagSummary.getKey())) count++;
            }
        }

        return count;
    }

    private boolean hasExportContent() {
        return snapshot.getExportBounds(exclusions) != null;
    }

    private String formatCorner(BlockPos corner) {
        return corner.getX() + ", " + corner.getY() + ", " + corner.getZ();
    }

    private void sendRecorderMessage(String translationKey) {
        if (mc == null || mc.player == null) return;
        if (translationKey == null || translationKey.isEmpty()) return;
        mc.player.sendMessage(new TextComponentTranslation(translationKey));
    }

    private int getSizeBandY() {
        return panelY + HEADER_HEIGHT + 6;
    }

    private int getCornerCardsY() {
        return getSizeBandY() + SIZE_BAND_HEIGHT + CARD_GAP;
    }

    private int getStatsY() {
        return getCornerCardsY() + CORNER_CARD_HEIGHT + CARD_GAP;
    }

    private int getFooterY() {
        return getActionY() - 24;
    }

    private int getActionY() {
        return panelY + panelHeight - PANEL_PADDING - ACTION_BUTTON_HEIGHT;
    }

    private int getActionButtonX(int index, int buttonWidth) {
        return panelX + PANEL_PADDING + index * (buttonWidth + PANEL_PADDING);
    }

    private int getCornerCardWidth() {
        return (panelWidth - PANEL_PADDING * 3) / 2;
    }

    private int getCornerCardX(int index, int cardWidth) {
        return panelX + PANEL_PADDING + index * (cardWidth + PANEL_PADDING);
    }

    private int getStatCardWidth() {
        return (panelWidth - PANEL_PADDING * 3) / 2;
    }

    private int getStatCardX(int index) {
        return panelX + PANEL_PADDING + index * (getStatCardWidth() + PANEL_PADDING);
    }

    private int getStatButtonX(int index) {
        return getStatCardX(index) + STAT_BUTTON_SIDE_MARGIN;
    }

    private static class RecorderButton extends GuiButton {
        private final int baseColor;
        private final int hoveredColor;
        private final int borderColor;
        private final int accentColor;

        private RecorderButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText,
                int baseColor, int hoveredColor, int borderColor, int accentColor) {
            super(buttonId, x, y, widthIn, heightIn, buttonText);
            this.baseColor = baseColor;
            this.hoveredColor = hoveredColor;
            this.borderColor = borderColor;
            this.accentColor = accentColor;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!visible) return;

            hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;

            int backgroundColor;
            int textColor;
            int edgeColor;

            if (!enabled) {
                backgroundColor = 0x66222222;
                textColor = 0x777777;
                edgeColor = 0x88333333;
            } else {
                backgroundColor = hovered ? hoveredColor : baseColor;
                textColor = 0xFFF3ECE2;
                edgeColor = hovered ? 0xFFF0E2C6 : borderColor;
            }

            Gui.drawRect(x - 1, y - 1, x + width + 1, y + height + 1, edgeColor);
            Gui.drawRect(x, y, x + width, y + height, backgroundColor);
            Gui.drawRect(x, y, x + width, y + 1, accentColor);

            String text = mc.fontRenderer.trimStringToWidth(displayString, width - 8);
            int textX = x + (width - mc.fontRenderer.getStringWidth(text)) / 2;
            int textY = y + (height - mc.fontRenderer.FONT_HEIGHT) / 2;
            mc.fontRenderer.drawString(text, textX, textY, textColor);
        }
    }
}
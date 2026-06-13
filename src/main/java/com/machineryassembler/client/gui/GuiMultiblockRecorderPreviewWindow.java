package com.machineryassembler.client.gui;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;

import com.machineryassembler.client.render.StructureRenderContext;
import com.machineryassembler.common.recording.MultiblockRecordingExclusions;
import com.machineryassembler.common.recording.MultiblockRecordingSnapshot;
import com.machineryassembler.common.structure.Structure;


/**
 * Modal preview window using Machinery Assembler's preview renderer.
 */
public class GuiMultiblockRecorderPreviewWindow extends Gui {

    private static final int HEADER_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 22;
    private static final int PADDING = 10;

    private final MultiblockRecordingSnapshot snapshot;
    private final MultiblockRecordingExclusions exclusions;

    private boolean visible;
    private int windowX;
    private int windowY;
    private int windowW;
    private int windowH;
    private int previewX;
    private int previewY;
    private int previewWidth;
    private int previewHeight;
    private boolean dragging;
    private int lastMouseX;
    private int lastMouseY;

    private StructureRenderContext context;

    public GuiMultiblockRecorderPreviewWindow(MultiblockRecordingSnapshot snapshot,
            MultiblockRecordingExclusions exclusions) {
        this.snapshot = snapshot;
        this.exclusions = exclusions;
    }

    public void show() {
        visible = true;
        dragging = false;
        calculateLayout();
        rebuildContext();
    }

    public void hide() {
        visible = false;
        dragging = false;
    }

    public void release() {
        context = null;
        dragging = false;
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

        if (mouseButton == 0 && isMouseOverPreview(mouseX, mouseY)) {
            dragging = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }

        return true;
    }

    public boolean handleRelease(int mouseX, int mouseY, int mouseButton) {
        if (!visible) return false;

        if (mouseButton == 0) dragging = false;

        return true;
    }

    public boolean handleDrag(int mouseX, int mouseY, int mouseButton) {
        if (!visible) return false;
        if (!dragging || mouseButton != 0 || context == null) return true;

        int deltaX = mouseX - lastMouseX;
        int deltaY = mouseY - lastMouseY;
        context.getRender().rotate(-deltaY * 0.5, deltaX * 0.5, 0);

        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return true;
    }

    public boolean handleKey(int keyCode) {
        if (!visible) return false;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            hide();
            return true;
        }

        return false;
    }

    public boolean handleMouseInput(int mouseX, int mouseY, int wheel) {
        if (!visible || wheel == 0 || context == null) return false;
        if (!isMouseOverPreview(mouseX, mouseY)) return false;

        if (wheel > 0) {
            context.zoomIn();
        } else {
            context.zoomOut();
        }

        return true;
    }

    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;

        drawRect(windowX - 2, windowY - 2, windowX + windowW + 2, windowY + windowH + 2, 0xFF4F3820);
        drawRect(windowX, windowY, windowX + windowW, windowY + windowH, 0xE014120F);

        drawGradientRect(windowX, windowY, windowX + windowW, windowY + HEADER_HEIGHT, 0xF0372B18, 0xF01E1811);
        drawRect(windowX, windowY + HEADER_HEIGHT, windowX + windowW, windowY + HEADER_HEIGHT + 1, 0xFFC89254);
        font.drawStringWithShadow(I18n.format("gui.machineryassembler.recorder.preview.title"), windowX + 8, windowY + 6, 0xFFF5EEE4);

        previewX = windowX + PADDING;
        previewY = windowY + HEADER_HEIGHT + PADDING;
        previewWidth = windowW - PADDING * 2;
        previewHeight = windowH - HEADER_HEIGHT - FOOTER_HEIGHT - PADDING * 2;

        drawRect(previewX - 1, previewY - 1, previewX + previewWidth + 1, previewY + previewHeight + 1, 0xFF333333);
        drawRect(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0xFF1A1A1A);

        int footerY = windowY + windowH - FOOTER_HEIGHT + 3;
        font.drawString(I18n.format("gui.machineryassembler.hint.drag"), windowX + 8, footerY, 0xFFAAA39A);
        font.drawString(I18n.format("gui.machineryassembler.hint.scroll"), windowX + 8, footerY + 10, 0xFFAAA39A);

        if (context == null) {
            String noPreview = I18n.format("gui.machineryassembler.recorder.preview.unavailable");
            int textX = previewX + (previewWidth - font.getStringWidth(noPreview)) / 2;
            int textY = previewY + previewHeight / 2 - 4;
            font.drawString(noPreview, textX, textY, 0x888888);
            return;
        }

        context.getRender().render3DGUI(previewX + previewWidth / 2.0, previewY + previewHeight / 2.0,
            context.getScale(), partialTicks);
    }

    public void drawTooltips(int mouseX, int mouseY) {
    }

    private boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= windowX && mouseX <= windowX + windowW
            && mouseY >= windowY && mouseY <= windowY + windowH;
    }

    private boolean isMouseOverPreview(int mouseX, int mouseY) {
        return mouseX >= previewX && mouseX <= previewX + previewWidth
            && mouseY >= previewY && mouseY <= previewY + previewHeight;
    }

    private void rebuildContext() {
        Structure structure = snapshot.buildStructure("preview", exclusions);
        context = structure == null ? null : StructureRenderContext.createContext(structure);
    }

    private void calculateLayout() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        int screenW = scaledResolution.getScaledWidth();
        int screenH = scaledResolution.getScaledHeight();
        int maxSize = Math.min(screenW - 40, screenH - 40);

        windowW = maxSize;
        windowH = maxSize;
        windowX = (screenW - windowW) / 2;
        windowY = (screenH - windowH) / 2;
    }
}
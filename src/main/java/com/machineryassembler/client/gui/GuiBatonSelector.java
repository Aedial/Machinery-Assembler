// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.client.autobuild.AutobuildHandler;
import com.machineryassembler.client.render.StructureRenderContext;
import com.machineryassembler.client.render.StructureRenderHelper;
import com.machineryassembler.common.item.ItemAssemblerBaton;
import com.machineryassembler.common.structure.BlockRequirement;
import com.machineryassembler.common.structure.Structure;
import com.machineryassembler.common.structure.StructureRegistry;


/**
 * GUI for selecting a structure for autobuild with the Assembler's Baton.
 *
 * Layout: 1/4 left (filter + list), 3/4 right (preview).
 * Double-click to select structure for autobuild.
 */
@SideOnly(Side.CLIENT)
public class GuiBatonSelector extends GuiScreen {

    private static final int BUTTON_I18N_TOGGLE = 0;

    private final ItemStack batonStack;
    private final IBlockState anchorState;
    private final BlockPos anchorPos;

    // Left panel
    private GuiTextField filterField;
    private StructureListWidget listWidget;
    private int leftPanelWidth;

    // Right panel - preview
    private StructureRenderContext previewContext = null;
    private ResourceLocation selectedStructure = null;
    private boolean isDragging = false;
    private int lastMouseX;
    private int lastMouseY;

    // Double-click detection
    private long lastClickTime = 0L;
    private ResourceLocation lastClickId = null;
    private static final long DOUBLE_CLICK_TIME = 500L;

    // I18n toggle state
    private boolean useI18nNames = true;

    public GuiBatonSelector(ItemStack batonStack, @Nullable IBlockState anchorState) {
        this.batonStack = batonStack;
        this.anchorState = anchorState;
        this.anchorPos = ItemAssemblerBaton.getLastAnchorPos(batonStack);
    }

    @Override
    public void initGui() {
        super.initGui();

        leftPanelWidth = width / 4;

        // Filter field
        filterField = new GuiTextField(0, fontRenderer, 10, 10, leftPanelWidth - 20, 14);
        filterField.setMaxStringLength(64);

        // Structure list widget
        listWidget = new StructureListWidget(10, 30, leftPanelWidth - 20, height - 70);

        // I18n toggle button
        buttonList.clear();
        buttonList.add(new GuiButton(BUTTON_I18N_TOGGLE, 10, height - 30, leftPanelWidth - 20, 20, getI18nButtonText()));
    }

    private String getI18nButtonText() {
        return useI18nNames
            ? I18n.format("gui.machineryassembler.baton.i18n.on")
            : I18n.format("gui.machineryassembler.baton.i18n.off");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BUTTON_I18N_TOGGLE) {
            useI18nNames = !useI18nNames;
            button.displayString = getI18nButtonText();
        }
    }

    @Override
    public void updateScreen() {
        filterField.updateCursorCounter();
        listWidget.updateFilter(filterField.getText());
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (filterField.textboxKeyTyped(typedChar, keyCode)) return;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Right-click on filter field clears it
        if (mouseButton == 1 && isInBounds(mouseX, mouseY, filterField.x, filterField.y, filterField.width, 14)) {
            filterField.setText("");
            return;
        }

        filterField.mouseClicked(mouseX, mouseY, mouseButton);

        // Check list widget click
        if (mouseButton == 0) {
            ResourceLocation clicked = listWidget.getStructureAt(mouseX, mouseY);

            if (clicked != null) {
                // Check for double-click
                long now = System.currentTimeMillis();

                if (clicked.equals(lastClickId) && now - lastClickTime < DOUBLE_CLICK_TIME) {
                    // Double-click - select for autobuild
                    selectForAutobuild(clicked);
                    return;
                }

                lastClickTime = now;
                lastClickId = clicked;

                // Single click - update preview
                selectStructure(clicked);
            }

            // Check if clicking in preview area - start dragging
            if (isInPreviewArea(mouseX, mouseY)) {
                isDragging = true;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        isDragging = false;
        listWidget.onMouseReleased();
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);

        if (isDragging && clickedMouseButton == 0 && previewContext != null) {
            int dx = mouseX - lastMouseX;
            int dy = mouseY - lastMouseY;

            StructureRenderHelper render = previewContext.getRender();

            if (previewContext.doesRender3D()) {
                render.rotate(-dy * 0.5, dx * 0.5, 0);
            } else {
                render.translate(dx * 0.05, 0, -dy * 0.05);
            }

            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }

        listWidget.onMouseDrag(mouseY);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int wheel = Mouse.getDWheel();

        if (wheel != 0) {
            if (isInLeftPanel(mouseX)) {
                listWidget.handleScroll(wheel);
            } else if (isInPreviewArea(mouseX, mouseY) && previewContext != null) {
                if (previewContext.doesRender3D()) {
                    if (wheel > 0) {
                        previewContext.zoomIn();
                    } else {
                        previewContext.zoomOut();
                    }
                } else {
                    if (wheel > 0 && previewContext.hasSliceUp()) {
                        previewContext.sliceUp();
                    } else if (wheel < 0 && previewContext.hasSliceDown()) {
                        previewContext.sliceDown();
                    }
                }
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        // Left panel background
        Gui.drawRect(0, 0, leftPanelWidth, height, 0x80000000);

        // Filter field
        filterField.drawTextBox();

        // Structure list
        listWidget.draw(mouseX, mouseY);

        // Right panel - preview
        drawPreviewPanel(mouseX, mouseY, partialTicks);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPreviewPanel(int mouseX, int mouseY, float partialTicks) {
        int panelX = leftPanelWidth + 10;
        int panelY = 10;
        int panelW = width - leftPanelWidth - 20;
        int panelH = height - 60;

        // Panel background
        Gui.drawRect(panelX, panelY, panelX + panelW, panelY + panelH, 0x80000000);

        if (previewContext == null || selectedStructure == null) {
            // No selection - show hint
            String hint = I18n.format("gui.machineryassembler.baton.no_selection");
            int hintX = panelX + (panelW - fontRenderer.getStringWidth(hint)) / 2;
            int hintY = panelY + panelH / 2;
            fontRenderer.drawString(hint, hintX, hintY, 0x808080);
        } else {
            // Draw structure name
            Structure structure = StructureRegistry.getRegistry().getStructure(selectedStructure);

            if (structure != null) {
                String name = useI18nNames ? structure.getLocalizedName() : selectedStructure.toString();
                int nameX = panelX + (panelW - fontRenderer.getStringWidth(name)) / 2;
                fontRenderer.drawString(name, nameX, panelY + 6, 0xFFFFFF);
            }

            // Draw preview (TODO: add a button to open JEI for the structure)
            int previewX = panelX + panelW / 2;
            int previewY = panelY + panelH / 2;

            StructureRenderHelper render = previewContext.getRender();

            if (previewContext.doesRender3D()) {
                render.render3DGUI(previewX, previewY, previewContext.getScale(), partialTicks);
            } else {
                render.render3DGUI(previewX, previewY, previewContext.getScale(), partialTicks,
                    Optional.of(previewContext.getRenderSlice()));
            }

            // Draw layer indicator in 2D mode
            if (!previewContext.doesRender3D() && structure != null) {
                String layerText = I18n.format("gui.machineryassembler.layer",
                    previewContext.getRenderSlice() + 1,
                    structure.getPattern().getSize().getY());
                fontRenderer.drawString(layerText, panelX + panelW - 8 - fontRenderer.getStringWidth(layerText),
                    panelY + panelH - 16, 0x606060);
            }
        }

        // Footer with instruction
        int footerY = panelY + panelH + 10;
        String footerText = I18n.format("gui.machineryassembler.baton.double_click_hint");
        int footerX = panelX + (panelW - fontRenderer.getStringWidth(footerText)) / 2;
        fontRenderer.drawString(footerText, footerX, footerY, 0xAAAAAA);
    }

    private void selectStructure(ResourceLocation structureId) {
        this.selectedStructure = structureId;
        Structure structure = StructureRegistry.getRegistry().getStructure(structureId);

        if (structure != null) {
            this.previewContext = StructureRenderContext.createContext(structure);
        } else {
            this.previewContext = null;
        }
    }

    private void selectForAutobuild(ResourceLocation structureId) {
        AutobuildHandler.selectStructure(batonStack, structureId, anchorState, anchorPos);
        mc.displayGuiScreen(null);
    }

    private boolean isInBounds(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private boolean isInLeftPanel(int mouseX) {
        return mouseX < leftPanelWidth;
    }

    private boolean isInPreviewArea(int mouseX, int mouseY) {
        int panelX = leftPanelWidth + 10;
        int panelY = 10;
        int panelW = width - leftPanelWidth - 20;
        int panelH = height - 60;

        return isInBounds(mouseX, mouseY, panelX, panelY, panelW, panelH);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * Inner class for the structure list widget with scrolling.
     */
    private class StructureListWidget {
        private final int x, y, w, h;
        private final int entryHeight = 16;

        private List<ResourceLocation> allStructures;
        private List<ResourceLocation> filteredStructures;
        private String currentFilter = "";

        private int scrollOffset = 0;
        private boolean isDraggingScrollbar = false;
        private int scrollbarDragStart = 0;
        private int scrollOffsetDragStart = 0;

        public StructureListWidget(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            loadStructures();
        }

        private void loadStructures() {
            allStructures = new ArrayList<>();

            for (Structure structure : StructureRegistry.getLoadedStructures()) {
                // If anchor state is set, filter to only structures containing that block
                if (anchorState != null) {
                    if (!structureContainsBlock(structure, anchorState)) continue;
                }

                allStructures.add(structure.getRegistryName());
            }

            // Sort by name
            allStructures.sort(Comparator.comparing(ResourceLocation::toString));
            filteredStructures = new ArrayList<>(allStructures);
        }

        private boolean structureContainsBlock(Structure structure, IBlockState state) {
            for (BlockRequirement req : structure.getPattern().getPattern().values()) {
                for (IBlockState sample : req.getSamples()) {
                    if (sample.getBlock() == state.getBlock()) {
                        int sampleMeta = sample.getBlock().getMetaFromState(sample);
                        int targetMeta = state.getBlock().getMetaFromState(state);

                        if (sampleMeta == targetMeta) return true;
                    }
                }
            }

            return false;
        }

        public void updateFilter(String filter) {
            if (filter.equals(currentFilter)) return;

            currentFilter = filter;
            String lowerFilter = filter.toLowerCase();

            if (filter.isEmpty()) {
                filteredStructures = new ArrayList<>(allStructures);
            } else {
                filteredStructures = allStructures.stream()
                    .filter(id -> {
                        // Match against ID
                        if (id.toString().toLowerCase().contains(lowerFilter)) return true;

                        // Match against localized name
                        if (useI18nNames) {
                            Structure s = StructureRegistry.getRegistry().getStructure(id);
                            if (s != null && s.getLocalizedName().toLowerCase().contains(lowerFilter)) {
                                return true;
                            }
                        }

                        return false;
                    })
                    .collect(Collectors.toList());
            }

            scrollOffset = 0;
        }

        public void draw(int mouseX, int mouseY) {
            // Clip to widget bounds
            int visibleEntries = h / entryHeight;
            int maxScroll = Math.max(0, filteredStructures.size() - visibleEntries);
            scrollOffset = Math.min(scrollOffset, maxScroll);

            // Draw entries
            for (int i = 0; i < visibleEntries && i + scrollOffset < filteredStructures.size(); i++) {
                ResourceLocation id = filteredStructures.get(i + scrollOffset);
                int entryY = y + i * entryHeight;

                // Highlight if selected
                boolean selected = id.equals(selectedStructure);
                boolean hovered = isInBounds(mouseX, mouseY, x, entryY, w - 10, entryHeight);

                if (selected) {
                    Gui.drawRect(x, entryY, x + w - 10, entryY + entryHeight, 0x60FFFFFF);
                } else if (hovered) {
                    Gui.drawRect(x, entryY, x + w - 10, entryY + entryHeight, 0x40FFFFFF);
                }

                // Draw name
                String displayName;

                if (useI18nNames) {
                    Structure s = StructureRegistry.getRegistry().getStructure(id);
                    displayName = s != null ? s.getLocalizedName() : id.getPath();
                } else {
                    displayName = id.toString();
                }

                // Truncate if too long
                if (fontRenderer.getStringWidth(displayName) > w - 16) {
                    while (fontRenderer.getStringWidth(displayName + "...") > w - 16 && displayName.length() > 0) {
                        displayName = displayName.substring(0, displayName.length() - 1);
                    }
                    displayName += "...";
                }

                fontRenderer.drawString(displayName, x + 2, entryY + 4, selected ? 0xFFFFFF : 0xCCCCCC);
            }

            // Draw scrollbar if needed
            if (filteredStructures.size() > visibleEntries) {
                int scrollbarHeight = Math.max(20, (int) ((float) visibleEntries / filteredStructures.size() * h));
                int scrollbarY = y + (int) ((float) scrollOffset / maxScroll * (h - scrollbarHeight));

                Gui.drawRect(x + w - 8, y, x + w, y + h, 0x40000000);
                Gui.drawRect(x + w - 6, scrollbarY, x + w - 2, scrollbarY + scrollbarHeight, 0xC0FFFFFF);
            }
        }

        @Nullable
        public ResourceLocation getStructureAt(int mouseX, int mouseY) {
            if (!isInBounds(mouseX, mouseY, x, y, w - 10, h)) return null;

            int index = (mouseY - y) / entryHeight + scrollOffset;
            if (index >= 0 && index < filteredStructures.size()) return filteredStructures.get(index);

            return null;
        }

        public void handleScroll(int wheel) {
            int visibleEntries = h / entryHeight;
            int maxScroll = Math.max(0, filteredStructures.size() - visibleEntries);

            if (wheel > 0) {
                scrollOffset = Math.max(0, scrollOffset - 3);
            } else {
                scrollOffset = Math.min(maxScroll, scrollOffset + 3);
            }
        }

        public void onMouseDrag(int mouseY) {
            // Could implement scrollbar dragging here
        }

        public void onMouseReleased() {
            isDraggingScrollbar = false;
        }
    }
}

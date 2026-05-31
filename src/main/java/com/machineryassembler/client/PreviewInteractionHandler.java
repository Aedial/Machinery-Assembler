// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.client;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.client.autobuild.AutobuildHandler;
import com.machineryassembler.client.gui.GuiWrenchSelector;
import com.machineryassembler.client.render.WrenchHighlightRenderer;
import com.machineryassembler.client.render.InWorldPreviewRenderer;
import com.machineryassembler.common.item.ItemAssemblerWrench;


/**
 * Global client-side interaction handler for in-world preview.
 * Intercepts right-click events at HIGH priority to prevent block activation
 * (e.g. container GUIs opening) when a preview is active.
 *
 * This fires BEFORE Block.onBlockActivated in MC 1.12's interaction pipeline:
 *   1. PlayerInteractEvent.RightClickBlock (this handler)
 *   2. Item.onItemUseFirst
 *   3. Block.onBlockActivated  <-- too late if we only use onItemUse
 *   4. Item.onItemUse
 *
 * By cancelling at step 1, we prevent containers from opening.
 * This also handles right-click interactions for JEI previews regardless of held item,
 * since JEI previews shouldn't require holding the wrench.
 */
@SideOnly(Side.CLIENT)
public class PreviewInteractionHandler {

    /**
     * Intercept right-click on blocks when a preview is active.
     * Prevents block interactions (container GUIs) and handles preview actions instead.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld() == null || !event.getWorld().isRemote) return;

        InWorldPreviewRenderer previewRenderer = ClientProxy.previewRenderer;
        if (!previewRenderer.hasActivePreview()) return;

        EntityPlayer player = event.getEntityPlayer();
        ItemStack mainHand = player.getHeldItemMainhand();

        // Cancel the event to prevent block activation (container GUIs, etc.)
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);

        // Determine what to do based on whether the wrench has a selection
        ResourceLocation selected = null;
        ItemStack wrenchStack = ItemStack.EMPTY;

        if (mainHand.getItem() instanceof ItemAssemblerWrench) {
            wrenchStack = mainHand;
            selected = ItemAssemblerWrench.getSelectedStructure(mainHand);
        } else {
            ItemStack offHand = player.getHeldItemOffhand();
            if (offHand.getItem() instanceof ItemAssemblerWrench) {
                wrenchStack = offHand;
                selected = ItemAssemblerWrench.getSelectedStructure(offHand);
            }
        }

        if (player.isSneaking()) {
            handleShiftRightClick(player, previewRenderer, wrenchStack, event);

            return;
        }

        // Check if this preview is in autobuild mode (started from wrench selection)
        // vs JEI layer-by-layer guided mode (started from JEI preview)
        if (previewRenderer.isAutobuildMode() && selected != null) {
            // Wrench-selected autobuild: fix preview or trigger autobuild
            attemptAutobuildOrFix(player, wrenchStack);
        } else {
            // JEI preview or preview without wrench: just fix the preview for layer guidance
            fixPreview(previewRenderer);
        }
    }

    /**
     * Intercept right-click in air when a preview is active.
     * Handles the same logic as right-click on blocks but for empty air clicks.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getWorld() == null || !event.getWorld().isRemote) return;

        InWorldPreviewRenderer previewRenderer = ClientProxy.previewRenderer;
        if (!previewRenderer.hasActivePreview()) return;

        EntityPlayer player = event.getEntityPlayer();

        // For JEI previews (non-autobuild mode), handle regardless of held item
        if (!previewRenderer.isAutobuildMode()) {
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.SUCCESS);

            // Shift right-click cancels the preview
            if (player.isSneaking()) {
                WrenchHighlightRenderer.clearHighlights();
                previewRenderer.cancelPreview();

                return;
            }

            fixPreview(previewRenderer);

            return;
        }

        // Autobuild mode: let the wrench handle it if player is holding one
        ItemStack mainHand = player.getHeldItemMainhand();

        if (mainHand.getItem() instanceof ItemAssemblerWrench) return;

        ItemStack offHand = player.getHeldItemOffhand();
        if (offHand.getItem() instanceof ItemAssemblerWrench) return;

        // Autobuild mode but no wrench - shouldn't happen normally, just fix preview
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
        fixPreview(previewRenderer);
    }

    /**
     * Intercept right-click with empty hand when a preview is active.
     * This is needed because RightClickItem only fires when holding an item.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (event.getWorld() == null || !event.getWorld().isRemote) return;

        InWorldPreviewRenderer previewRenderer = ClientProxy.previewRenderer;
        if (!previewRenderer.hasActivePreview()) return;

        // For JEI previews, handle empty hand right-click to fix or cancel preview
        if (!previewRenderer.isAutobuildMode()) {
            EntityPlayer player = event.getEntityPlayer();

            // Shift right-click cancels the preview
            if (player.isSneaking()) {
                WrenchHighlightRenderer.clearHighlights();
                previewRenderer.cancelPreview();

                return;
            }

            fixPreview(previewRenderer);
        }
    }

    /**
     * Handle shift+right-click: cancel preview and open wrench GUI if holding wrench.
     */
    private void handleShiftRightClick(EntityPlayer player, InWorldPreviewRenderer previewRenderer,
                                       ItemStack wrenchStack, PlayerInteractEvent.RightClickBlock event) {
        // Clear any existing highlights from previous autobuild attempts
        WrenchHighlightRenderer.clearHighlights();

        if (!wrenchStack.isEmpty()) {
            // Cancel preview and open GUI with clicked block as anchor
            previewRenderer.cancelPreview();
            AutobuildHandler.clearSelection(wrenchStack);

            IBlockState state = event.getWorld().getBlockState(event.getPos());
            ItemAssemblerWrench.setLastAnchorBlock(wrenchStack, state, event.getPos());
            Minecraft.getMinecraft().displayGuiScreen(new GuiWrenchSelector(wrenchStack, state));
        } else {
            // No wrench: just cancel the preview
            previewRenderer.cancelPreview();
        }
    }

    /**
     * Fix the preview and trigger autobuild if wrench has a selection.
     */
    private void attemptAutobuildOrFix(EntityPlayer player, ItemStack wrenchStack) {
        InWorldPreviewRenderer previewRenderer = ClientProxy.previewRenderer;
        if (!previewRenderer.isFixed()) previewRenderer.fixPreview();

        AutobuildHandler.attemptAutobuild(player, null, wrenchStack);
    }

    /**
     * Fix the preview for layer-by-layer guidance (JEI preview mode).
     */
    private void fixPreview(InWorldPreviewRenderer previewRenderer) {
        if (!previewRenderer.isFixed()) previewRenderer.fixPreview();
    }
}

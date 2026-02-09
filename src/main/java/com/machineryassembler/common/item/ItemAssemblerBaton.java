// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.MachineryAssembler;


/**
 * Assembler's Baton - Used to select and autobuild multiblock structures.
 *
 * Right-click in air while sneaking: Opens GUI with no anchor filter.
 * Right-click in air (no sneak): Opens GUI with previous anchor filter.
 * Right-click on block: Opens GUI with that block as anchor filter.
 * Right-click after selection: Autobuilds structure at position.
 */
public class ItemAssemblerBaton extends Item {

    private static final String NBT_SELECTED_STRUCTURE = "SelectedStructure";
    private static final String NBT_FOCUS_BLOCK = "FocusBlock";
    private static final String NBT_FOCUS_META = "FocusMeta";
    private static final String NBT_LAST_ANCHOR_BLOCK = "LastAnchorBlock";
    private static final String NBT_LAST_ANCHOR_META = "LastAnchorMeta";
    private static final String NBT_LAST_ANCHOR_POS = "LastAnchorPos";

    public ItemAssemblerBaton() {
        setRegistryName(MachineryAssembler.MODID, "assembler_baton");
        setTranslationKey(MachineryAssembler.MODID + ".assembler_baton");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.TOOLS);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (!world.isRemote) return new ActionResult<>(EnumActionResult.SUCCESS, stack);

        // If shift is held while preview is active, cancel preview and open GUI
        ResourceLocation selected = getSelectedStructure(stack);
        if (hasActivePreviewClient()) {
            if (player.isSneaking()) {
                // Shift+right-click: Cancel preview and open GUI
                cancelPreviewClient(stack);
                openGui(stack, null, player);

                return new ActionResult<>(EnumActionResult.SUCCESS, stack);
            }

            // Check if the preview is in autobuild mode (started by baton selection)
            // vs JEI layer-by-layer mode (started from JEI preview)
            if (isAutobuildModeClient() && selected != null) {
                // Baton-started autobuild preview: fix and trigger autobuild
                attemptAutobuildOrFix(player, stack);
            } else {
                // JEI preview (layer guidance mode): just fix the preview
                fixPreviewClient();
            }

            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }

        // No active preview - open GUI
        if (player.isSneaking()) {
            // Shift+right-click: Clear any stored anchor/selection and open fresh GUI
            clearLastAnchorBlock(stack);
            clearSelectedStructure(stack);
            openGui(stack, null, player);
        } else {
            // Right-click in air: Open GUI with previous anchor (if any)
            IBlockState lastAnchor = getLastAnchorBlock(stack);
            openGui(stack, lastAnchor, player);
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);

        if (!world.isRemote) return EnumActionResult.SUCCESS;

        // Preview interactions are handled by PreviewInteractionHandler (via event),
        // which fires BEFORE this method. If we reach here with an active preview,
        // it means the event handler already dealt with it and we should not double-process.
        if (hasActivePreviewClient()) return EnumActionResult.SUCCESS;

        // Shift + right-click on block: open GUI with that block as anchor filter
        if (player.isSneaking()) {
            IBlockState state = world.getBlockState(pos);
            setLastAnchorBlock(stack, state, pos);
            openGui(stack, state, player);

            return EnumActionResult.SUCCESS;
        }

        // Regular right-click on block without selection: let block handle it normally
        return EnumActionResult.PASS;
    }
    /**
     * Check if there's an active preview (client-side only).
     */
    @SideOnly(Side.CLIENT)
    private boolean hasActivePreviewClient() {
        return com.machineryassembler.client.ClientProxy.previewRenderer.hasActivePreview();
    }

    /**
     * Check if the current preview is in autobuild mode (client-side only).
     * Autobuild mode means the preview was started by baton selection.
     * Non-autobuild mode means the preview was started from JEI for layer guidance.
     */
    @SideOnly(Side.CLIENT)
    private boolean isAutobuildModeClient() {
        return com.machineryassembler.client.ClientProxy.previewRenderer.isAutobuildMode();
    }

    /**
     * Cancel the current preview (client-side only).
     */
    @SideOnly(Side.CLIENT)
    private void cancelPreviewClient(ItemStack stack) {
        com.machineryassembler.client.ClientProxy.previewRenderer.cancelPreview();
        com.machineryassembler.client.autobuild.AutobuildHandler.clearSelection(stack);
    }

    /**
     * Try to fix the preview and trigger autobuild.
     * If the preview is floating, it gets fixed and autobuild is sent in one action.
     * If the preview is already fixed, autobuild is sent immediately.
     */
    @SideOnly(Side.CLIENT)
    private void attemptAutobuildOrFix(EntityPlayer player, ItemStack stack) {
        com.machineryassembler.client.render.InWorldPreviewRenderer previewRenderer = com.machineryassembler.client.ClientProxy.previewRenderer;

        if (!previewRenderer.isFixed()) {
            // Preview is floating, fix it at current position
            previewRenderer.fixPreview();
        }

        // Immediately trigger autobuild (fixPreview guarantees fixedPosition is set)
        com.machineryassembler.client.autobuild.AutobuildHandler.attemptAutobuild(player, null, stack);
    }

    /**
     * Fix the preview for layer-by-layer guidance (used for JEI previews without baton selection).
     */
    @SideOnly(Side.CLIENT)
    private void fixPreviewClient() {
        com.machineryassembler.client.render.InWorldPreviewRenderer previewRenderer = com.machineryassembler.client.ClientProxy.previewRenderer;

        if (!previewRenderer.isFixed()) previewRenderer.fixPreview();
    }

    /**
     * Opens the baton selector GUI.
     * Called on client side only.
     */
    @SideOnly(Side.CLIENT)
    private void openGui(ItemStack stack, @Nullable IBlockState anchorState, EntityPlayer player) {
        // Defer import to avoid class loading issues on server
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
            new com.machineryassembler.client.gui.GuiBatonSelector(stack, anchorState));
    }

    // ----- NBT Accessors -----

    @Nullable
    public static ResourceLocation getSelectedStructure(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_SELECTED_STRUCTURE)) return null;

        String structureId = tag.getString(NBT_SELECTED_STRUCTURE);
        if (structureId.isEmpty()) return null;

        return new ResourceLocation(structureId);
    }

    public static void setSelectedStructure(ItemStack stack, @Nullable ResourceLocation structureId) {
        NBTTagCompound tag = stack.getTagCompound();

        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        if (structureId == null) {
            tag.removeTag(NBT_SELECTED_STRUCTURE);
        } else {
            tag.setString(NBT_SELECTED_STRUCTURE, structureId.toString());
        }
    }

    public static void clearSelectedStructure(ItemStack stack) {
        setSelectedStructure(stack, null);
    }

    @Nullable
    public static IBlockState getFocusBlock(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_FOCUS_BLOCK)) return null;

        String blockId = tag.getString(NBT_FOCUS_BLOCK);
        Block block = Block.getBlockFromName(blockId);
        if (block == null) return null;

        int meta = tag.getInteger(NBT_FOCUS_META);

        return block.getStateFromMeta(meta);
    }

    public static void setFocusBlock(ItemStack stack, @Nullable IBlockState state) {
        NBTTagCompound tag = stack.getTagCompound();

        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        if (state == null) {
            tag.removeTag(NBT_FOCUS_BLOCK);
            tag.removeTag(NBT_FOCUS_META);
        } else {
            tag.setString(NBT_FOCUS_BLOCK, state.getBlock().getRegistryName().toString());
            tag.setInteger(NBT_FOCUS_META, state.getBlock().getMetaFromState(state));
        }
    }

    @Nullable
    public static IBlockState getLastAnchorBlock(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_LAST_ANCHOR_BLOCK)) return null;

        String blockId = tag.getString(NBT_LAST_ANCHOR_BLOCK);
        Block block = Block.getBlockFromName(blockId);
        if (block == null) return null;

        int meta = tag.getInteger(NBT_LAST_ANCHOR_META);

        return block.getStateFromMeta(meta);
    }

    public static void setLastAnchorBlock(ItemStack stack, @Nullable IBlockState state, @Nullable BlockPos pos) {
        NBTTagCompound tag = stack.getTagCompound();

        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        if (state == null) {
            tag.removeTag(NBT_LAST_ANCHOR_BLOCK);
            tag.removeTag(NBT_LAST_ANCHOR_META);
            tag.removeTag(NBT_LAST_ANCHOR_POS);
        } else {
            tag.setString(NBT_LAST_ANCHOR_BLOCK, state.getBlock().getRegistryName().toString());
            tag.setInteger(NBT_LAST_ANCHOR_META, state.getBlock().getMetaFromState(state));
            if (pos != null) tag.setLong(NBT_LAST_ANCHOR_POS, pos.toLong());
        }
    }

    /**
     * Clear the stored anchor block (convenience method).
     */
    public static void clearLastAnchorBlock(ItemStack stack) {
        setLastAnchorBlock(stack, null, null);
    }

    /**
     * Clear only the stored anchor position, keeping the anchor block type.
     * Used after autobuild so the focus filter remains for the next GUI open,
     * but the position no longer causes the preview to fix immediately.
     */
    public static void clearLastAnchorPos(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            tag.removeTag(NBT_LAST_ANCHOR_POS);
        }
    }

    @Nullable
    public static BlockPos getLastAnchorPos(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_LAST_ANCHOR_POS)) return null;

        return BlockPos.fromLong(tag.getLong(NBT_LAST_ANCHOR_POS));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        ResourceLocation selectedStructure = getSelectedStructure(stack);

        if (selectedStructure != null) {
            tooltip.add(I18n.format("item.machineryassembler.assembler_baton.selected", selectedStructure.toString()));
            tooltip.add(I18n.format("item.machineryassembler.assembler_baton.clear_hint"));
        } else {
            tooltip.add(I18n.format("item.machineryassembler.assembler_baton.no_selection"));
        }

        IBlockState focusBlock = getFocusBlock(stack);
        if (focusBlock != null) {
            String blockName = focusBlock.getBlock().getLocalizedName();
            tooltip.add(I18n.format("item.machineryassembler.assembler_baton.focus", blockName));
        }
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return getSelectedStructure(stack) != null;
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.client.render;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Utility class for validating block states for rendering.
 * Checks if a block state has a valid model/texture and can be rendered properly.
 */
@SideOnly(Side.CLIENT)
public final class BlockStateRenderValidator {

    private static IBakedModel missingModel = null;

    private BlockStateRenderValidator() {}

    /**
     * Check if a block state can be rendered properly (has a valid model).
     *
     * @param state The block state to check
     * @return true if the state has a valid model, false if it would render as missing texture
     */
    public static boolean canRender(IBlockState state) {
        if (state == null || state.getBlock() == Blocks.AIR) return false;

        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getBlockRendererDispatcher() == null) return true;

            IBakedModel model = mc.getBlockRendererDispatcher().getModelForState(state);
            if (model == null) return false;

            // Check if this is the missing model
            if (missingModel == null) {
                missingModel = mc.getBlockRendererDispatcher().getBlockModelShapes()
                    .getModelManager().getMissingModel();
            }

            return model != missingModel;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if a block state can be converted to a valid item stack for display.
     *
     * @param state The block state to check
     * @return true if the state can be represented as an item, false otherwise
     */
    public static boolean hasValidItem(IBlockState state) {
        if (state == null || state.getBlock() == Blocks.AIR) return false;

        Block block = state.getBlock();
        Item item = Item.getItemFromBlock(block);

        return item != null;
    }

    /**
     * Check if an item stack can be rendered properly (has a valid model).
     *
     * @param stack The item stack to check
     * @return true if the stack has a valid model, false otherwise
     */
    public static boolean canRenderItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getRenderItem() == null) return true;

            IBakedModel model = mc.getRenderItem().getItemModelWithOverrides(stack, null, null);
            if (model == null) return false;

            // Check if this is the missing model
            if (missingModel == null && mc.getBlockRendererDispatcher() != null) {
                missingModel = mc.getBlockRendererDispatcher().getBlockModelShapes()
                    .getModelManager().getMissingModel();
            }

            return model != missingModel;
        } catch (Exception e) {
            return false;
        }
    }
}

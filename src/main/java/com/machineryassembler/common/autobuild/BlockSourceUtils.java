// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;


/**
 * Utility methods for BlockSource implementations.
 */
public class BlockSourceUtils {

    /**
     * Creates a key string for a block state (registry:meta format).
     */
    public static String stateToKey(IBlockState state) {
        Block block = state.getBlock();
        ResourceLocation registryName = block.getRegistryName();
        if (registryName == null) return "minecraft:air@0";

        int meta = block.getMetaFromState(state);

        return registryName.toString() + "@" + meta;
    }

    /**
     * Parses a key string back to a block state.
     *
     * @return The block state, or null if parsing fails
     */
    @Nullable
    public static IBlockState keyToState(String key) {
        int atIndex = key.lastIndexOf('@');

        if (atIndex < 0) {
            // No meta specified, assume 0
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(key));
            if (block == null || block == Blocks.AIR) return null;

            return block.getDefaultState();
        }

        String blockId = key.substring(0, atIndex);
        String metaStr = key.substring(atIndex + 1);

        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockId));
        if (block == null || block == Blocks.AIR) return null;

        try {
            int meta = Integer.parseInt(metaStr);

            return block.getStateFromMeta(meta);
        } catch (NumberFormatException e) {
            return block.getDefaultState();
        }
    }

    /**
     * Gets a display name for a block key.
     * Uses ItemStack with correct metadata to get the meta-specific name
     * (e.g. "Oak Planks" vs "Spruce Planks" instead of generic "Wooden Planks").
     */
    public static String getDisplayName(String key) {
        IBlockState state = keyToState(key);
        if (state == null) return key;

        Block block = state.getBlock();
        int meta = block.getMetaFromState(state);
        Item item = Item.getItemFromBlock(block);

        // Some blocks don't have corresponding items (e.g. flowing water)
        if (item == null) return block.getLocalizedName();

        ItemStack stack = new ItemStack(item, 1, meta);

        return stack.getDisplayName();
    }
}

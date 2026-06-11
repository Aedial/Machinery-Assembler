// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import com.machineryassembler.common.structure.BlockRequirement;
import com.machineryassembler.common.util.nbt.NBTMatchingHelper;


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
    @SuppressWarnings("deprecation")
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
     * Creates a key string for an item stack (registry:meta|nbt format).
     */
    public static String stackToKey(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() == Items.AIR) return "minecraft:air@0";

        ResourceLocation registryName = stack.getItem().getRegistryName();
        if (registryName == null) return "minecraft:air@0";

        String baseKey = registryName.toString() + "@" + stack.getMetadata();
        if (!stack.hasTagCompound()) return baseKey;

        return baseKey + "|" + stack.getTagCompound().toString();
    }

    public static String requirementToKey(BlockRequirement requirement) {
        return stackToKey(requirement.getRequiredStack());
    }

    public static ItemStack keyToStack(String key) {
        int tagSeparator = key.indexOf('|');
        String baseKey = tagSeparator < 0 ? key : key.substring(0, tagSeparator);
        int atIndex = baseKey.lastIndexOf('@');
        String itemId = atIndex < 0 ? baseKey : baseKey.substring(0, atIndex);

        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;

        int meta = 0;
        if (atIndex >= 0) {
            try {
                meta = Integer.parseInt(baseKey.substring(atIndex + 1));
            } catch (NumberFormatException ignored) {
            }
        }

        ItemStack stack = new ItemStack(item, 1, meta);
        if (tagSeparator < 0 || tagSeparator == key.length() - 1) return stack;

        try {
            stack.setTagCompound(JsonToNBT.getTagFromJson(key.substring(tagSeparator + 1)));
        } catch (NBTException ignored) {
        }

        return stack;
    }

    public static int getKeySpecificity(String key) {
        ItemStack stack = keyToStack(key);
        if (stack.isEmpty() || !stack.hasTagCompound()) return 0;

        return stack.getTagCompound().toString().length();
    }

    /**
     * Checks if the available stack matches the required stack inclusively
     * (i.e. available stack can have extra NBT but must match all required NBT).
     * @param availableStack The stack that is available.
     * @param requiredStack The stack that is required.
     * @return True if the available stack matches the required stack, false otherwise.
     */
    public static boolean matchesRequiredStack(ItemStack availableStack, ItemStack requiredStack) {
        if (availableStack.isEmpty() || requiredStack.isEmpty()) return false;

        return matchesRequiredComponents(
            availableStack.getItem(),
            availableStack.getMetadata(),
            availableStack.getTagCompound(),
            requiredStack.getItem(),
            requiredStack.getMetadata(),
            requiredStack.getTagCompound());
    }

    static boolean matchesRequiredComponents(@Nullable Item availableItem, int availableMetadata,
                                             @Nullable NBTTagCompound availableTag, @Nullable Item requiredItem,
                                             int requiredMetadata, @Nullable NBTTagCompound requiredTag) {
        if (availableItem == null || requiredItem == null) return false;
        if (availableItem != requiredItem) return false;
        if (availableMetadata != requiredMetadata) return false;
        if (requiredTag == null) return true;
        if (availableTag == null) return false;

        return NBTMatchingHelper.matchNBTCompound(requiredTag, availableTag);
    }

    /**
     * Gets a display name for a block key.
     * Uses ItemStack with correct metadata to get the meta-specific name
     * (e.g. "Oak Planks" vs "Spruce Planks" instead of generic "Wooden Planks").
     */
    public static String getDisplayName(String key) {
        ItemStack keyedStack = keyToStack(key);
        if (!keyedStack.isEmpty()) return keyedStack.getDisplayName();

        IBlockState state = keyToState(key);
        if (state == null) return key;

        Block block = state.getBlock();
        int meta = block.getMetaFromState(state);
        Item item = Item.getItemFromBlock(block);

        // Some blocks don't have corresponding items (e.g. flowing water)
        if (item == null) return block.getLocalizedName();

        ItemStack displayStack = new ItemStack(item, 1, meta);

        return displayStack.getDisplayName();
    }
}

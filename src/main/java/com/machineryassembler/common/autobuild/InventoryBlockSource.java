// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import com.machineryassembler.common.config.AutobuildConfig;
import com.machineryassembler.common.util.BlockStackUtils;
import com.machineryassembler.common.util.nbt.NBTMatchingHelper;


/**
 * Block source that pulls blocks from player's inventory.
 */
public class InventoryBlockSource implements BlockSource {

    public static final InventoryBlockSource INSTANCE = new InventoryBlockSource();

    private InventoryBlockSource() {
    }

    @Override
    public boolean canProvide(IBlockState state, EntityPlayer player) {
        // Creative mode with no consumption always can provide
        if (player.isCreative() && !AutobuildConfig.consumeBlocksInCreative) return true;

        ItemStack requiredStack = BlockStackUtils.getStackFromBlockState(state);
        if (requiredStack.isEmpty()) return false;

        return findMatchingSlot(requiredStack, player.inventory) >= 0;
    }

    @Override
    public int countAvailable(IBlockState state, EntityPlayer player) {
        // Creative mode with no consumption has infinite blocks
        if (player.isCreative() && !AutobuildConfig.consumeBlocksInCreative) return Integer.MAX_VALUE;

        ItemStack requiredStack = BlockStackUtils.getStackFromBlockState(state);
        if (requiredStack.isEmpty()) return 0;

        return countAvailable(requiredStack, player);
    }

    @Override
    @Nullable
    public ItemStack extract(IBlockState state, EntityPlayer player, boolean simulate) {
        ItemStack requiredStack = BlockStackUtils.getStackFromBlockState(state);
        if (requiredStack.isEmpty()) return null;

        // Creative mode with no consumption - return a fake stack
        if (player.isCreative() && !AutobuildConfig.consumeBlocksInCreative) {
            return requiredStack.copy();
        }

        int slot = findMatchingSlot(requiredStack, player.inventory);
        if (slot < 0) return null;

        if (simulate) {
            ItemStack stack = player.inventory.getStackInSlot(slot);

            return stack.copy().splitStack(1);
        }

        ItemStack stack = player.inventory.getStackInSlot(slot);
        ItemStack extracted = stack.splitStack(1);

        if (stack.isEmpty()) player.inventory.setInventorySlotContents(slot, ItemStack.EMPTY);

        return extracted;
    }

    @Override
    public Map<String, Integer> checkAvailability(Map<String, Integer> requirements, EntityPlayer player) {
        Map<String, Integer> available = new HashMap<>();

        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            ItemStack requiredStack = BlockSourceUtils.keyToStack(entry.getKey());
            if (requiredStack.isEmpty()) {
                available.put(entry.getKey(), 0);
                continue;
            }

            available.put(entry.getKey(), countAvailable(requiredStack, player));
        }

        return available;
    }

    @Override
    public Map<String, Integer> batchExtract(Map<String, Integer> requirements, EntityPlayer player, boolean simulate) {
        Map<String, Integer> remainder = new HashMap<>();

        // Creative mode with no consumption - everything succeeds
        if (player.isCreative() && !AutobuildConfig.consumeBlocksInCreative) {
            return remainder; // Empty remainder = all extracted
        }

        // Create working copy of inventory if simulating
        ItemStack[] inventoryCopy = null;

        if (simulate) {
            inventoryCopy = new ItemStack[player.inventory.getSizeInventory()];

            for (int i = 0; i < inventoryCopy.length; i++) {
                ItemStack stack = player.inventory.getStackInSlot(i);
                inventoryCopy[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            }
        }

        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            String key = entry.getKey();
            int needed = entry.getValue();
            ItemStack requiredStack = BlockSourceUtils.keyToStack(key);

            if (requiredStack.isEmpty()) {
                remainder.put(key, needed);
                continue;
            }

            int extracted = 0;
            ItemStack[] workingInventory = simulate ? inventoryCopy : null;

            for (int i = 0; i < player.inventory.getSizeInventory() && extracted < needed; i++) {
                ItemStack stack = simulate ? workingInventory[i] : player.inventory.getStackInSlot(i);

                if (stack.isEmpty()) continue;
                if (!matchesRequiredStack(stack, requiredStack)) continue;

                int toExtract = Math.min(stack.getCount(), needed - extracted);
                if (simulate) {
                    workingInventory[i] = stack.getCount() == toExtract ? ItemStack.EMPTY : stack.splitStack(stack.getCount() - toExtract);
                } else {
                    stack.shrink(toExtract);

                    if (stack.isEmpty()) {
                        player.inventory.setInventorySlotContents(i, ItemStack.EMPTY);
                    }
                }

                extracted += toExtract;
            }

            if (extracted < needed) remainder.put(key, needed - extracted);
        }

        return remainder;
    }

    @Override
    public String getName() {
        return "Player Inventory";
    }

    @Override
    public int getPriority() {
        return 0;
    }

    /**
     * Finds a slot in the inventory containing a block matching the required state.
     *
     * @return The slot index, or -1 if not found
     */
    private int findMatchingSlot(ItemStack requiredStack, InventoryPlayer inventory) {
        // Search main inventory and hotbar
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);

            if (stack.isEmpty()) continue;
            if (matchesRequiredStack(stack, requiredStack)) return i;
        }

        return -1;
    }

    private int countAvailable(ItemStack requiredStack, EntityPlayer player) {
        if (player.isCreative() && !AutobuildConfig.consumeBlocksInCreative) return Integer.MAX_VALUE;

        int total = 0;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (matchesRequiredStack(stack, requiredStack)) total += stack.getCount();
        }

        return total;
    }

    /**
     * Checks if an ItemStack represents the given block/meta.
     */
    private boolean matchesRequiredStack(ItemStack stack, ItemStack requiredStack) {
        if (stack.getItem() != requiredStack.getItem()) return false;
        if (stack.getMetadata() != requiredStack.getMetadata()) return false;
        if (!requiredStack.hasTagCompound()) return true;
        if (!stack.hasTagCompound()) return false;

        return NBTMatchingHelper.matchNBTCompound(requiredStack.getTagCompound(), stack.getTagCompound());
    }
}

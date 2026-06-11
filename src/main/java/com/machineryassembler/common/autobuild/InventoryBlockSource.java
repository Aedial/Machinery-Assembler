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


/**
 * Block source that pulls blocks from player's inventory.
 */
public class InventoryBlockSource implements BlockSource {

    public static final InventoryBlockSource INSTANCE = new InventoryBlockSource();

    private InventoryBlockSource() {
    }

    @Override
    public boolean canProvide(IBlockState state, BlockSourceContext context) {
        EntityPlayer player = context.getPlayer();

        // Creative mode with no consumption always can provide
        if (player.isCreative() && !AutobuildConfig.consumeBlocksInCreative) return true;

        ItemStack requiredStack = BlockStackUtils.getStackFromBlockState(state);
        if (requiredStack.isEmpty()) return false;

        return findMatchingSlot(requiredStack, player.inventory) >= 0;
    }

    @Override
    public int countAvailable(IBlockState state, BlockSourceContext context) {
        EntityPlayer player = context.getPlayer();

        // Creative mode with no consumption has infinite blocks
        if (player.isCreative() && !AutobuildConfig.consumeBlocksInCreative) return Integer.MAX_VALUE;

        ItemStack requiredStack = BlockStackUtils.getStackFromBlockState(state);
        if (requiredStack.isEmpty()) return 0;

        return countAvailable(requiredStack, player);
    }

    @Override
    @Nullable
    public ItemStack extract(IBlockState state, BlockSourceContext context, boolean simulate) {
        EntityPlayer player = context.getPlayer();
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
    public BlockExtractionResult batchExtractDetailed(Map<String, Integer> requirements, BlockSourceContext context,
                                                      boolean simulate) {
        EntityPlayer player = context.getPlayer();
        BlockExtractionResult result = new BlockExtractionResult();

        // Creative mode with no consumption - everything succeeds
        if (player.isCreative() && !AutobuildConfig.consumeBlocksInCreative) {
            return result;
        }

        ItemStack[] workingInventory = null;
        if (simulate) {
            workingInventory = new ItemStack[player.inventory.getSizeInventory()];

            for (int i = 0; i < workingInventory.length; i++) {
                ItemStack stack = player.inventory.getStackInSlot(i);
                workingInventory[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            }
        }

        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            String key = entry.getKey();
            int needed = entry.getValue();
            ItemStack requiredStack = BlockSourceUtils.keyToStack(key);

            if (requiredStack.isEmpty()) {
                result.addRemainder(key, needed);
                continue;
            }

            int extracted = 0;
            for (int i = 0; i < player.inventory.getSizeInventory() && extracted < needed; i++) {
                ItemStack stack = simulate ? workingInventory[i] : player.inventory.getStackInSlot(i);

                if (stack.isEmpty()) continue;
                if (!BlockSourceUtils.matchesRequiredStack(stack, requiredStack)) continue;

                int toExtract = Math.min(stack.getCount(), needed - extracted);
                if (toExtract <= 0) continue;

                if (simulate) {
                    stack.shrink(toExtract);
                    if (stack.isEmpty()) workingInventory[i] = ItemStack.EMPTY;
                } else {
                    ItemStack extractedStack = stack.copy();
                    extractedStack.setCount(toExtract);
                    result.addExtracted(extractedStack, toExtract);

                    stack.shrink(toExtract);
                    if (stack.isEmpty()) player.inventory.setInventorySlotContents(i, ItemStack.EMPTY);
                }

                extracted += toExtract;
            }

            if (extracted < needed) result.addRemainder(key, needed - extracted);
        }

        return result;
    }

    @Override
    public Map<String, Integer> checkAvailability(Map<String, Integer> requirements, BlockSourceContext context) {
        EntityPlayer player = context.getPlayer();
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
    public String getName() {
        return "Player Inventory";
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
            if (BlockSourceUtils.matchesRequiredStack(stack, requiredStack)) return i;
        }

        return -1;
    }

    private int countAvailable(ItemStack requiredStack, EntityPlayer player) {
        if (player.isCreative() && !AutobuildConfig.consumeBlocksInCreative) return Integer.MAX_VALUE;

        int total = 0;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (BlockSourceUtils.matchesRequiredStack(stack, requiredStack)) total += stack.getCount();
        }

        return total;
    }
}

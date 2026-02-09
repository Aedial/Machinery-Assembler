// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import com.machineryassembler.common.config.AutobuildConfig;


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

        return findMatchingSlot(state, player.inventory) >= 0;
    }

    @Override
    public int countAvailable(IBlockState state, EntityPlayer player) {
        // Creative mode with no consumption has infinite blocks
        if (player.isCreative() && !AutobuildConfig.consumeBlocksInCreative) return Integer.MAX_VALUE;

        Block targetBlock = state.getBlock();
        int targetMeta = targetBlock.getMetaFromState(state);
        int total = 0;

        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (matchesState(stack, targetBlock, targetMeta)) total += stack.getCount();
        }

        return total;
    }

    @Override
    @Nullable
    public ItemStack extract(IBlockState state, EntityPlayer player, boolean simulate) {
        // Creative mode with no consumption - return a fake stack
        if (player.isCreative() && !AutobuildConfig.consumeBlocksInCreative) {
            Item item = Item.getItemFromBlock(state.getBlock());
            if (item == null) return null;

            return new ItemStack(item, 1, state.getBlock().getMetaFromState(state));
        }

        int slot = findMatchingSlot(state, player.inventory);
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
            IBlockState state = BlockSourceUtils.keyToState(key);

            if (state == null) {
                remainder.put(key, needed);
                continue;
            }

            Block targetBlock = state.getBlock();
            int targetMeta = targetBlock.getMetaFromState(state);

            int extracted = 0;
            ItemStack[] workingInventory = simulate ? inventoryCopy : null;

            for (int i = 0; i < player.inventory.getSizeInventory() && extracted < needed; i++) {
                ItemStack stack = simulate ? workingInventory[i] : player.inventory.getStackInSlot(i);

                if (stack.isEmpty()) continue;
                if (!matchesState(stack, targetBlock, targetMeta)) continue;

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
    private int findMatchingSlot(IBlockState state, InventoryPlayer inventory) {
        Block targetBlock = state.getBlock();
        int targetMeta = targetBlock.getMetaFromState(state);

        // Search main inventory and hotbar
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);

            if (stack.isEmpty()) continue;
            if (matchesState(stack, targetBlock, targetMeta)) return i;
        }

        return -1;
    }

    /**
     * Checks if an ItemStack represents the given block/meta.
     */
    private boolean matchesState(ItemStack stack, Block targetBlock, int targetMeta) {
        Item item = stack.getItem();
        if (!(item instanceof ItemBlock)) return false;

        Block itemBlock = ((ItemBlock) item).getBlock();
        if (itemBlock != targetBlock) return false;

        int stackMeta = stack.getMetadata();
        return stackMeta == targetMeta;
    }
}

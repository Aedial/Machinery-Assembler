// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;


/**
 * Interface for block sources used during autobuild.
 * Implementations can pull blocks from different sources (inventory, AE2 network, etc.).
 */
public interface BlockSource {

    /**
     * Checks if this source can provide a block matching the required state.
     *
     * @param state The block state required
     * @param player The player (for context, e.g., network access)
     * @return true if the source can provide this block
     */
    boolean canProvide(IBlockState state, EntityPlayer player);

    /**
     * Counts how many of the given block state this source can provide.
     *
     * @param state The block state required
     * @param player The player
     * @return The count available, or Integer.MAX_VALUE if unlimited
     */
    int countAvailable(IBlockState state, EntityPlayer player);

    /**
     * Checks availability of multiple block types at once.
     * Returns a map of block key -> available count.
     *
     * @param requirements Map of block key (registry:meta) -> required count
     * @param player The player
     * @return Map of block key -> available count
     */
    default Map<String, Integer> checkAvailability(Map<String, Integer> requirements, EntityPlayer player) {
        Map<String, Integer> available = new HashMap<>();

        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            IBlockState state = BlockSourceUtils.keyToState(entry.getKey());

            if (state != null) {
                available.put(entry.getKey(), countAvailable(state, player));
            } else {
                available.put(entry.getKey(), 0);
            }
        }

        return available;
    }

    /**
     * Extracts a single block from this source.
     * This should only be called after {@link #canProvide} returns true.
     *
     * @param state The block state required
     * @param player The player
     * @param simulate If true, don't actually extract, just check
     * @return The extracted ItemStack, or null if extraction failed
     */
    @Nullable
    ItemStack extract(IBlockState state, EntityPlayer player, boolean simulate);

    /**
     * Batch extract multiple blocks from this source.
     * Extracts as many as possible and returns the remainder that couldn't be extracted.
     *
     * @param requirements Map of block key (registry:meta) -> required count
     * @param player The player
     * @param simulate If true, don't actually extract, just check
     * @return Map of block key -> count that could NOT be extracted (remainder)
     */
    Map<String, Integer> batchExtract(Map<String, Integer> requirements, EntityPlayer player, boolean simulate);

    /**
     * Returns a descriptive name for this source (for logging/debugging).
     */
    String getName();

    /**
     * Priority of this source. Lower values are checked first.
     * Default: 0 for inventory, 100 for external systems like AE2.
     */
    default int getPriority() {
        return 0;
    }
}

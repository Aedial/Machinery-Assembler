// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
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
     * @param context Runtime context for this autobuild request
     * @return true if the source can provide this block
     */
    boolean canProvide(IBlockState state, BlockSourceContext context);

    /**
     * Counts how many of the given block state this source can provide.
     *
     * @param state The block state required
     * @param context Runtime context for this autobuild request
     * @return The count available, or Integer.MAX_VALUE if unlimited
     */
    int countAvailable(IBlockState state, BlockSourceContext context);

    /**
     * Refuses all requirements, returning them as the remainder.
     * This is a helper for sources that can't provide anything.
     *
     * @param requirements Map of block key (registry:meta) -> required count
     * @return The same requirements as the remainder
     */
    default BlockExtractionResult refuse(Map<String, Integer> requirements) {
        Map<String, Integer> remainder = new LinkedHashMap<>();
        remainder.putAll(requirements);
        return BlockExtractionResult.remainderOnly(remainder);
    }

    /**
     * Checks availability of multiple block types at once.
     * Returns a map of block key -> available count.
     *
     * @param requirements Map of block key (registry:meta) -> required count
     * @param context Runtime context for this autobuild request
     * @return Map of block key -> available count
     */
    default Map<String, Integer> checkAvailability(Map<String, Integer> requirements, BlockSourceContext context) {
        Map<String, Integer> available = new HashMap<>();

        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            IBlockState state = BlockSourceUtils.keyToState(entry.getKey());

            if (state != null) {
                available.put(entry.getKey(), countAvailable(state, context));
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
     * @param context Runtime context for this autobuild request
     * @param simulate If true, don't actually extract, just check
     * @return The extracted ItemStack, or null if extraction failed
     */
    @Nullable
    ItemStack extract(IBlockState state, BlockSourceContext context, boolean simulate);

    /**
     * Batch extract multiple blocks from this source while retaining the exact extracted stack keys.
     *
     * @param requirements Map of block key (registry:meta|nbt) -> required count
     * @param context Runtime context for this autobuild request
     * @param simulate If true, don't actually extract, just check
     * @return The result of the batch extraction, including extracted items and remainder
     */
    BlockExtractionResult batchExtractDetailed(Map<String, Integer> requirements, BlockSourceContext context,
                                               boolean simulate);

    /**
     * Returns a descriptive name for this source (for logging/debugging).
     */
    String getName();
}

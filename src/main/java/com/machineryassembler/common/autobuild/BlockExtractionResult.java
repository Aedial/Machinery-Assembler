// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;


/**
 * Result of a batch extraction attempt.
 * Tracks both the remainder per requirement key and the exact extracted stack keys that were consumed.
 */
public class BlockExtractionResult {

    private final Map<String, Integer> remainder = new LinkedHashMap<>();
    private final Map<String, Integer> extracted = new LinkedHashMap<>();
    private final Map<String, Map<BlockSourceProviderId, Integer>> extractedBySource = new LinkedHashMap<>();

    public BlockExtractionResult() {
    }

    public BlockExtractionResult(Map<String, Integer> remainder, Map<String, Integer> extracted) {
        this.remainder.putAll(remainder);
        this.extracted.putAll(extracted);
    }

    public static BlockExtractionResult remainderOnly(Map<String, Integer> remainder) {
        return new BlockExtractionResult(remainder, new LinkedHashMap<>());
    }

    public Map<String, Integer> getRemainder() {
        return remainder;
    }

    public Map<String, Integer> getExtracted() {
        return extracted;
    }

    public Map<String, Map<BlockSourceProviderId, Integer>> getExtractedBySource() {
        return extractedBySource;
    }

    public void addRemainder(String key, int count) {
        if (count <= 0) return;

        remainder.put(key, count);
    }

    public void addExtracted(ItemStack stack, int count) {
        if (count <= 0 || stack.isEmpty()) return;

        addExtractedKey(BlockSourceUtils.stackToKey(stack), count);
    }

    public void addExtractedKey(String key, int count) {
        if (count <= 0) return;

        extracted.merge(key, count, Integer::sum);
    }

    public void addSourceContribution(String key, BlockSourceProviderId providerId, int count) {
        if (count <= 0) return;

        Map<BlockSourceProviderId, Integer> sourceCounts = extractedBySource.computeIfAbsent(
            key,
            ignored -> new EnumMap<>(BlockSourceProviderId.class));
        sourceCounts.merge(providerId, count, Integer::sum);
    }
}
// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Ordered router for all configured autobuild block sources.
 */
public class BlockSourceManager {

    private final Map<BlockSourceProviderId, BlockSource> sources = new EnumMap<>(BlockSourceProviderId.class);

    private static class InstanceHolder {

        private static final BlockSourceManager INSTANCE = new BlockSourceManager(createDefaultSources());
    }

    public static BlockSourceManager getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private static Map<BlockSourceProviderId, BlockSource> createDefaultSources() {
        Map<BlockSourceProviderId, BlockSource> sources = new EnumMap<>(BlockSourceProviderId.class);

        sources.put(BlockSourceProviderId.INVENTORY, InventoryBlockSource.INSTANCE);

        if (BlockSourceProviderId.EMC.isModAvailable()) {
            sources.put(BlockSourceProviderId.EMC, ProjectEBlockSource.INSTANCE);
        }

        if (BlockSourceProviderId.AE2.isModAvailable()) {
            sources.put(BlockSourceProviderId.AE2, AE2BlockSource.INSTANCE);
        }

        return sources;
    }

    BlockSourceManager(Map<BlockSourceProviderId, BlockSource> sources) {
        this.sources.putAll(sources);
    }

    public Map<String, Integer> checkAvailability(Map<String, Integer> requirements, BlockSourceContext context) {
        Map<String, Integer> combinedAvailable = new HashMap<>();
        Map<String, Integer> remainingRequirements = new LinkedHashMap<>(requirements);

        for (BlockSourceProviderId providerId : context.getSettings().getProviderOrder()) {
            if (!context.getSettings().isEnabled(providerId)) continue;
            if (!providerId.isModAvailable()) continue;

            BlockSource source = sources.get(providerId);
            if (source == null) continue;

            Map<String, Integer> availableFromSource = source.checkAvailability(remainingRequirements, context);
            if (availableFromSource.isEmpty()) continue;

            Map<String, Integer> nextRemainingRequirements = new LinkedHashMap<>();

            for (Map.Entry<String, Integer> entry : remainingRequirements.entrySet()) {
                String key = entry.getKey();
                int required = entry.getValue();
                int provided = Math.min(required, availableFromSource.getOrDefault(key, 0));

                if (provided > 0) {
                    combinedAvailable.merge(key, provided, Integer::sum);
                }

                int remaining = required - provided;
                if (remaining > 0) nextRemainingRequirements.put(key, remaining);
            }

            remainingRequirements = nextRemainingRequirements;
            if (remainingRequirements.isEmpty()) break;
        }

        return combinedAvailable;
    }

    public BlockExtractionResult batchExtractDetailed(Map<String, Integer> requirements, BlockSourceContext context,
                                                      boolean simulate) {
        Map<String, Integer> remainingRequirements = new LinkedHashMap<>(requirements);
        BlockExtractionResult result = new BlockExtractionResult();

        for (BlockSourceProviderId providerId : context.getSettings().getProviderOrder()) {
            if (!context.getSettings().isEnabled(providerId)) continue;
            if (!providerId.isModAvailable()) continue;

            BlockSource source = sources.get(providerId);
            if (source == null) continue;

            BlockExtractionResult sourceResult = source.batchExtractDetailed(remainingRequirements, context, simulate);
            remainingRequirements = new LinkedHashMap<>(sourceResult.getRemainder());

            for (Map.Entry<String, Integer> entry : sourceResult.getExtracted().entrySet()) {
                result.addExtractedKey(entry.getKey(), entry.getValue());
                result.addSourceContribution(entry.getKey(), providerId, entry.getValue());
            }

            if (remainingRequirements.isEmpty()) break;
        }

        for (Map.Entry<String, Integer> entry : remainingRequirements.entrySet()) {
            result.addRemainder(entry.getKey(), entry.getValue());
        }

        return result;
    }
}
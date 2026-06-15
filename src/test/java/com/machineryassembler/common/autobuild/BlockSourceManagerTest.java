// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;


class BlockSourceManagerTest {

    @Test
    void batchExtractDetailedPreservesOrderedRequirementsAndExactExtractedKeys() {
        RecordingBlockSource recordingSource = new RecordingBlockSource();
        recordingSource.result.addRemainder("generic", 1);
        recordingSource.result.addExtractedKey("specific|{owner:\"Alice\"}", 1);
        recordingSource.result.addExtractedKey("generic|{energy:4000}", 2);

        Map<BlockSourceProviderId, BlockSource> sources = new EnumMap<>(BlockSourceProviderId.class);
        sources.put(BlockSourceProviderId.INVENTORY, recordingSource);

        BlockSourceManager manager = new BlockSourceManager(sources);

        BlockSourceSettings settings = BlockSourceSettings.defaults();
        settings.setEnabled(BlockSourceProviderId.EMC, false);
        settings.setEnabled(BlockSourceProviderId.AE2, false);

        Map<String, Integer> requirements = new LinkedHashMap<>();
        requirements.put("specific", 1);
        requirements.put("generic", 3);

        BlockExtractionResult result = manager.batchExtractDetailed(
            requirements,
            new BlockSourceContext(null, settings),
            false);

        Assertions.assertEquals(Arrays.asList("specific", "generic"), recordingSource.recordedKeys);
        Assertions.assertEquals(recordingSource.result.getRemainder(), result.getRemainder());
        Assertions.assertEquals(recordingSource.result.getExtracted(), result.getExtracted());
    }

    private static class RecordingBlockSource implements BlockSource {

        private final BlockExtractionResult result = new BlockExtractionResult();
        private final List<String> recordedKeys = new ArrayList<>();

        @Override
        public boolean canProvide(IBlockState state, BlockSourceContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int countAvailable(IBlockState state, BlockSourceContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Integer> checkAvailability(Map<String, Integer> requirements, BlockSourceContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ItemStack extract(IBlockState state, BlockSourceContext context, boolean simulate) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BlockExtractionResult batchExtractDetailed(Map<String, Integer> requirements, BlockSourceContext context,
                                                          boolean simulate) {
            recordedKeys.clear();
            recordedKeys.addAll(requirements.keySet());
            return result;
        }

        @Override
        public String getName() {
            return "Recording";
        }
    }
}
// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


class BlockExtractionResultTest {

    @Test
    void addExtractedKeyMergesCountsAndKeepsInsertionOrder() {
        BlockExtractionResult result = new BlockExtractionResult();

        result.addExtractedKey("specific", 1);
        result.addExtractedKey("generic", 2);
        result.addExtractedKey("specific", 3);

        Assertions.assertEquals(Arrays.asList("specific", "generic"), new ArrayList<>(result.getExtracted().keySet()));
        Assertions.assertEquals(4, result.getExtracted().get("specific"));
        Assertions.assertEquals(2, result.getExtracted().get("generic"));
    }

    @Test
    void addRemainderIgnoresNonPositiveCounts() {
        BlockExtractionResult result = new BlockExtractionResult();

        result.addRemainder("keep", 2);
        result.addRemainder("ignore-zero", 0);
        result.addRemainder("ignore-negative", -1);

        Assertions.assertEquals(1, result.getRemainder().size());
        Assertions.assertEquals(2, result.getRemainder().get("keep"));
        Assertions.assertFalse(result.getRemainder().containsKey("ignore-zero"));
        Assertions.assertFalse(result.getRemainder().containsKey("ignore-negative"));
    }

    @Test
    void addSourceContributionMergesCountsPerProvider() {
        BlockExtractionResult result = new BlockExtractionResult();

        result.addSourceContribution("specific", BlockSourceProviderId.INVENTORY, 1);
        result.addSourceContribution("specific", BlockSourceProviderId.INVENTORY, 2);
        result.addSourceContribution("specific", BlockSourceProviderId.AE2, 4);

        Assertions.assertEquals(3, result.getExtractedBySource().get("specific").get(BlockSourceProviderId.INVENTORY));
        Assertions.assertEquals(4, result.getExtractedBySource().get("specific").get(BlockSourceProviderId.AE2));
    }
}
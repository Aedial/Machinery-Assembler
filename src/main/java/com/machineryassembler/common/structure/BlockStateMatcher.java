// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/common/util/IBlockStateDescriptor.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.common.structure;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.Rotation;
import net.minecraftforge.fluids.BlockFluidBase;


/**
 * Describes one or more valid block states that can match at a structure position.
 * Handles state variants, rotation, and tile entity detection.
 */
public class BlockStateMatcher {

    private final List<IBlockState> applicable;

    public BlockStateMatcher(IBlockState state) {
        this.applicable = Collections.singletonList(state);
    }

    /**
     * Create a matcher for a block using only its default state (metadata 0).
     * We intentionally do NOT iterate over all metadata values because:
     * 1. Many blocks share the same ID with different metadata (e.g., wood types)
     * 2. This causes incorrect items to show in the ingredients list
     * 3. For structure matching, the parser specifies the exact state needed
     */
    public BlockStateMatcher(Block block) {
        // Always use only the default state - no metadata iteration
        this.applicable = Collections.singletonList(block.getDefaultState());
    }

    protected BlockStateMatcher(List<IBlockState> applicable) {
        this.applicable = applicable;
    }

    public static BlockStateMatcher of(IBlockState state) {
        return new BlockStateMatcher(state);
    }

    public static BlockStateMatcher of(Block block) {
        return new BlockStateMatcher(block);
    }

    public BlockStateMatcher copy() {
        return applicable.size() == 1
            ? new BlockStateMatcher(applicable.get(0))
            : new BlockStateMatcher(new ReferenceArrayList<>(applicable));
    }

    public BlockStateMatcher copyRotateYCCW(final AtomicBoolean rotated) {
        List<IBlockState> applicable = new ReferenceArrayList<>();

        for (IBlockState state : this.applicable) {
            IBlockState rotatedState = state.withRotation(Rotation.COUNTERCLOCKWISE_90);
            if (state != rotatedState) rotated.set(true);
            applicable.add(rotatedState);
        }

        return applicable.size() == 1
            ? new BlockStateMatcher(applicable.get(0))
            : new BlockStateMatcher(applicable);
    }

    public BlockStateMatcher copyRotateYCW(final AtomicBoolean rotated) {
        List<IBlockState> applicable = new ReferenceArrayList<>();

        for (IBlockState state : this.applicable) {
            IBlockState rotatedState = state.withRotation(Rotation.CLOCKWISE_90);
            if (state != rotatedState) rotated.set(true);
            applicable.add(rotatedState);
        }

        return applicable.size() == 1
            ? new BlockStateMatcher(applicable.get(0))
            : new BlockStateMatcher(applicable);
    }

    public boolean hasTileEntity() {
        for (IBlockState state : applicable) {
            if (state.getBlock().hasTileEntity(state)) return true;
        }

        return false;
    }

    public List<IBlockState> getApplicable() {
        return applicable;
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof BlockStateMatcher && applicable.equals(((BlockStateMatcher) obj).applicable);
    }

    @Override
    public int hashCode() {
        return applicable.hashCode();
    }
}

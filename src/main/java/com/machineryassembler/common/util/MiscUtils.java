// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/common/util/MiscUtils.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.common.util;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import com.machineryassembler.common.structure.StructurePattern;


/**
 * Utility methods for position rotation and other misc operations.
 */
public class MiscUtils {

    /**
     * Rotates a BlockPos counter-clockwise around the Y axis.
     */
    public static BlockPos rotateYCCW(BlockPos pos) {
        return new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
    }

    /**
     * Rotates a BlockPos clockwise around the Y axis.
     */
    public static BlockPos rotateYCW(BlockPos pos) {
        return new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
    }

    /**
     * Rotates a BlockPos counter-clockwise from NORTH until it faces the specified direction.
     */
    public static BlockPos rotateYCCWNorthUntil(BlockPos at, EnumFacing dir) {
        EnumFacing currentFacing = EnumFacing.NORTH;
        BlockPos pos = at;

        while (currentFacing != dir) {
            currentFacing = currentFacing.rotateYCCW();
            pos = new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
        }

        return pos;
    }

    /**
     * Rotates a StructurePattern counter-clockwise from NORTH until it faces the specified direction.
     */
    public static StructurePattern rotateYCCWNorthUntil(StructurePattern array, EnumFacing dir) {
        EnumFacing currentFacing = EnumFacing.NORTH;
        StructurePattern rot = array;

        while (currentFacing != dir) {
            currentFacing = currentFacing.rotateYCCW();
            rot = rot.rotateYCCW();
        }

        return rot;
    }
}

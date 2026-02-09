// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/common/util/nbt/NBTComparableShort.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.common.util.nbt;

import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagShort;


/**
 * A comparable short NBT tag.
 */
public class NBTComparableShort extends NBTTagShort implements NBTComparableNumber {

    private final ComparisonMode mode;

    public NBTComparableShort(ComparisonMode mode, short data) {
        super(data);
        this.mode = mode;
    }

    @Override
    public boolean test(NBTPrimitive numberTag) {
        return mode.testShort(this.getShort(), numberTag.getShort());
    }

    @Override
    public NBTComparableShort copy() {
        return new NBTComparableShort(this.mode, this.getShort());
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/common/util/nbt/NBTComparableFloat.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.common.util.nbt;

import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagFloat;


/**
 * A comparable float NBT tag.
 */
public class NBTComparableFloat extends NBTTagFloat implements NBTComparableNumber {

    private final ComparisonMode mode;

    public NBTComparableFloat(ComparisonMode mode, float data) {
        super(data);
        this.mode = mode;
    }

    @Override
    public boolean test(NBTPrimitive numberTag) {
        return mode.testFloat(this.getFloat(), numberTag.getFloat());
    }

    @Override
    public NBTComparableFloat copy() {
        return new NBTComparableFloat(this.mode, this.getFloat());
    }
}

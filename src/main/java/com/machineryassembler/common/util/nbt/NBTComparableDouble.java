// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/common/util/nbt/NBTComparableDouble.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.common.util.nbt;

import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagDouble;


/**
 * A comparable double NBT tag.
 */
public class NBTComparableDouble extends NBTTagDouble implements NBTComparableNumber {

    private final ComparisonMode mode;

    public NBTComparableDouble(ComparisonMode mode, double data) {
        super(data);
        this.mode = mode;
    }

    @Override
    public boolean test(NBTPrimitive numberTag) {
        return mode.testDouble(this.getDouble(), numberTag.getDouble());
    }

    @Override
    public NBTComparableDouble copy() {
        return new NBTComparableDouble(this.mode, this.getDouble());
    }
}

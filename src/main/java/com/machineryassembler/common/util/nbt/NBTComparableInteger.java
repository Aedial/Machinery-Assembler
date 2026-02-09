// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/common/util/nbt/NBTComparableInteger.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.common.util.nbt;

import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagInt;


/**
 * A comparable integer NBT tag.
 */
public class NBTComparableInteger extends NBTTagInt implements NBTComparableNumber {

    private final ComparisonMode mode;

    public NBTComparableInteger(ComparisonMode mode, int data) {
        super(data);
        this.mode = mode;
    }

    @Override
    public boolean test(NBTPrimitive numberTag) {
        return mode.testInt(this.getInt(), numberTag.getInt());
    }

    @Override
    public NBTComparableInteger copy() {
        return new NBTComparableInteger(this.mode, this.getInt());
    }
}

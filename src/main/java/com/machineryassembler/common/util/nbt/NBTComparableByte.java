// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/common/util/nbt/NBTComparableByte.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.common.util.nbt;

import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagByte;


/**
 * A comparable byte NBT tag.
 */
public class NBTComparableByte extends NBTTagByte implements NBTComparableNumber {

    private final ComparisonMode mode;

    public NBTComparableByte(ComparisonMode mode, byte data) {
        super(data);
        this.mode = mode;
    }

    @Override
    public boolean test(NBTPrimitive numberTag) {
        return mode.testByte(this.getByte(), numberTag.getByte());
    }

    @Override
    public NBTComparableByte copy() {
        return new NBTComparableByte(this.mode, this.getByte());
    }
}

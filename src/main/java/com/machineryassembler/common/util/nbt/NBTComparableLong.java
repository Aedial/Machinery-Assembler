// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/common/util/nbt/NBTComparableLong.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.common.util.nbt;

import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagLong;


/**
 * A comparable long NBT tag.
 */
public class NBTComparableLong extends NBTTagLong implements NBTComparableNumber {

    private final ComparisonMode mode;

    public NBTComparableLong(ComparisonMode mode, long data) {
        super(data);
        this.mode = mode;
    }

    @Override
    public boolean test(NBTPrimitive numberTag) {
        return mode.testLong(this.getLong(), numberTag.getLong());
    }

    @Override
    public NBTComparableLong copy() {
        return new NBTComparableLong(this.mode, this.getLong());
    }
}

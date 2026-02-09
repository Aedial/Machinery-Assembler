// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/common/util/nbt/NBTPatternString.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.common.util.nbt;

import java.util.regex.Pattern;

import net.minecraft.nbt.NBTTagString;


/**
 * A string NBT tag that supports pattern matching via regex.
 */
public class NBTPatternString extends NBTTagString {

    private final Pattern strPattern;

    public NBTPatternString(String data) {
        this(data, Pattern.compile(data, Pattern.CASE_INSENSITIVE));
    }

    private NBTPatternString(String data, Pattern strPattern) {
        super(data);
        this.strPattern = strPattern;
    }

    @Override
    public NBTPatternString copy() {
        return new NBTPatternString(this.getString(), this.strPattern);
    }

    public boolean testString(String toTest) {
        return strPattern.matcher(toTest).matches();
    }
}

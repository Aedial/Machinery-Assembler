// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/common/util/nbt/NBTComparableNumber.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.common.util.nbt;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTPrimitive;


/**
 * Interface for NBT tags that can compare numbers with various comparison modes.
 */
public interface NBTComparableNumber {

    boolean test(NBTPrimitive numberTag);

    enum ComparisonMode {
        LESS_EQUAL("<="),
        EQUAL("=="),
        GREATER_EQUAL(">="),
        LESS("<"),
        GREATER(">");

        private final String identifier;

        ComparisonMode(String identifier) {
            this.identifier = identifier;
        }

        @Nullable
        public static ComparisonMode peekMode(String strModeAndValue) {
            lblModes:
            for (ComparisonMode mode : values()) {
                char[] charArray = mode.identifier.toCharArray();
                for (int i = 0; i < charArray.length; i++) {
                    char c = charArray[i];
                    if (strModeAndValue.charAt(i) != c) continue lblModes;
                }

                return mode;
            }

            return null;
        }

        public String getIdentifier() {
            return identifier;
        }

        public boolean testByte(byte original, byte toTest) {
            switch (this) {
                case LESS:
                    return toTest < original;
                case LESS_EQUAL:
                    return toTest <= original;
                case EQUAL:
                    return toTest == original;
                case GREATER_EQUAL:
                    return toTest >= original;
                case GREATER:
                    return toTest > original;
                default:
                    return false;
            }
        }

        public boolean testInt(int original, int toTest) {
            switch (this) {
                case LESS:
                    return toTest < original;
                case LESS_EQUAL:
                    return toTest <= original;
                case EQUAL:
                    return toTest == original;
                case GREATER_EQUAL:
                    return toTest >= original;
                case GREATER:
                    return toTest > original;
                default:
                    return false;
            }
        }

        public boolean testShort(short original, short toTest) {
            switch (this) {
                case LESS:
                    return toTest < original;
                case LESS_EQUAL:
                    return toTest <= original;
                case EQUAL:
                    return toTest == original;
                case GREATER_EQUAL:
                    return toTest >= original;
                case GREATER:
                    return toTest > original;
                default:
                    return false;
            }
        }

        public boolean testLong(long original, long toTest) {
            switch (this) {
                case LESS:
                    return toTest < original;
                case LESS_EQUAL:
                    return toTest <= original;
                case EQUAL:
                    return toTest == original;
                case GREATER_EQUAL:
                    return toTest >= original;
                case GREATER:
                    return toTest > original;
                default:
                    return false;
            }
        }

        public boolean testFloat(float original, float toTest) {
            switch (this) {
                case LESS:
                    return toTest < original;
                case LESS_EQUAL:
                    return toTest <= original;
                case EQUAL:
                    return Float.compare(toTest, original) == 0;
                case GREATER_EQUAL:
                    return toTest >= original;
                case GREATER:
                    return toTest > original;
                default:
                    return false;
            }
        }

        public boolean testDouble(double original, double toTest) {
            switch (this) {
                case LESS:
                    return toTest < original;
                case LESS_EQUAL:
                    return toTest <= original;
                case EQUAL:
                    return Double.compare(toTest, original) == 0;
                case GREATER_EQUAL:
                    return toTest >= original;
                case GREATER:
                    return toTest > original;
                default:
                    return false;
            }
        }
    }
}

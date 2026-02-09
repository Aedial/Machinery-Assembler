// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.structure;

import javax.annotation.Nullable;


/**
 * Represents a message to display in JEI for a structure.
 */
public class StructureMessage {

    public enum Level {
        INFO,
        WARNING,
        ERROR
    }

    private final String key;
    private final Level level;
    @Nullable
    private final String item;

    public StructureMessage(String key, Level level, @Nullable String item) {
        this.key = key;
        this.level = level;
        this.item = item;
    }

    public String getKey() {
        return key;
    }

    public Level getLevel() {
        return level;
    }

    @Nullable
    public String getItem() {
        return item;
    }

    /**
     * Parses the item string to extract the item ID.
     * Format: "modid:itemid" or "modid:itemid@meta" or "modid:itemid*count"
     * 
     * @return The item ID portion (modid:itemid)
     */
    @Nullable
    public String getItemId() {
        if (item == null) return null;

        // Strip count suffix
        String id = item;
        int countIndex = id.indexOf('*');
        if (countIndex != -1) id = id.substring(0, countIndex);

        // Strip meta suffix
        int metaIndex = id.indexOf('@');
        if (metaIndex != -1) id = id.substring(0, metaIndex);

        return id;
    }

    /**
     * Parses the item string to extract the metadata.
     * 
     * @return The metadata value, or 0 if not specified
     */
    public int getItemMeta() {
        if (item == null) return 0;

        // Strip count suffix first
        String work = item;
        int countIndex = work.indexOf('*');
        if (countIndex != -1) work = work.substring(0, countIndex);

        int metaIndex = work.indexOf('@');
        if (metaIndex == -1 || metaIndex == work.length() - 1) return 0;

        try {
            return Integer.parseInt(work.substring(metaIndex + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Parses the item string to extract the count.
     * 
     * @return The count value, or 1 if not specified
     */
    public int getItemCount() {
        if (item == null) return 1;

        int countIndex = item.indexOf('*');
        if (countIndex == -1 || countIndex == item.length() - 1) return 1;

        try {
            return Integer.parseInt(item.substring(countIndex + 1));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}

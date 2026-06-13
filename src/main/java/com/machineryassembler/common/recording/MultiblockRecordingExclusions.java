// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.recording;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;


/**
 * Mutable exclusion set for recorder block groups and tile tag toggles.
 */
public class MultiblockRecordingExclusions {

    private final Set<String> excludedBlockKeys = new HashSet<>();
    private final Set<String> excludedTileTagKeys = new HashSet<>();

    public boolean isBlockExcluded(@Nullable String blockKey) {
        return blockKey != null && excludedBlockKeys.contains(blockKey);
    }

    public void setBlockExcluded(@Nullable String blockKey, boolean excluded) {
        if (blockKey == null) return;

        if (excluded) {
            excludedBlockKeys.add(blockKey);
            return;
        }

        excludedBlockKeys.remove(blockKey);
    }

    public boolean isTileTagExcluded(@Nullable String tileKey, @Nullable String tagKey) {
        if (tileKey == null || tagKey == null) return false;

        return excludedTileTagKeys.contains(createTileTagKey(tileKey, tagKey));
    }

    public void setTileTagExcluded(@Nullable String tileKey, @Nullable String tagKey, boolean excluded) {
        if (tileKey == null || tagKey == null) return;

        String combinedKey = createTileTagKey(tileKey, tagKey);
        if (excluded) {
            excludedTileTagKeys.add(combinedKey);
            return;
        }

        excludedTileTagKeys.remove(combinedKey);
    }

    public void clear() {
        excludedBlockKeys.clear();
        excludedTileTagKeys.clear();
    }

    public boolean isEmpty() {
        return excludedBlockKeys.isEmpty() && excludedTileTagKeys.isEmpty();
    }

    public MultiblockRecordingExclusions copy() {
        MultiblockRecordingExclusions copy = new MultiblockRecordingExclusions();
        copy.excludedBlockKeys.addAll(excludedBlockKeys);
        copy.excludedTileTagKeys.addAll(excludedTileTagKeys);
        return copy;
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();

        NBTTagList blockList = new NBTTagList();
        for (String blockKey : excludedBlockKeys) {
            blockList.appendTag(new NBTTagString(blockKey));
        }

        NBTTagList tileTagList = new NBTTagList();
        for (String tileTagKey : excludedTileTagKeys) {
            tileTagList.appendTag(new NBTTagString(tileTagKey));
        }

        tag.setTag("blocks", blockList);
        tag.setTag("tileTags", tileTagList);
        return tag;
    }

    public static MultiblockRecordingExclusions fromNBT(@Nullable NBTTagCompound tag) {
        MultiblockRecordingExclusions exclusions = new MultiblockRecordingExclusions();
        if (tag == null) return exclusions;

        NBTTagList blockList = tag.getTagList("blocks", Constants.NBT.TAG_STRING);
        for (int index = 0; index < blockList.tagCount(); index++) {
            exclusions.excludedBlockKeys.add(blockList.getStringTagAt(index));
        }

        NBTTagList tileTagList = tag.getTagList("tileTags", Constants.NBT.TAG_STRING);
        for (int index = 0; index < tileTagList.tagCount(); index++) {
            exclusions.excludedTileTagKeys.add(tileTagList.getStringTagAt(index));
        }

        return exclusions;
    }

    private static String createTileTagKey(String tileKey, String tagKey) {
        return tileKey + "\n" + tagKey;
    }
}
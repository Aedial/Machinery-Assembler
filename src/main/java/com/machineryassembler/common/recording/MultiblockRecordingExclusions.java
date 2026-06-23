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

    private static final String TILE_TAGS_NBT_KEY = "tileTags";

    private final Set<String> excludedBlockKeys = new HashSet<>();
    private final Set<String> includedTileTagKeys = new HashSet<>();

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

    public boolean isTileTagIncluded(@Nullable String tileKey, @Nullable String tagKey) {
        if (tileKey == null || tagKey == null) return false;

        return includedTileTagKeys.contains(createTileTagKey(tileKey, tagKey));
    }

    public boolean isTileTagExcluded(@Nullable String tileKey, @Nullable String tagKey) {
        return !isTileTagIncluded(tileKey, tagKey);
    }

    public void setTileTagIncluded(@Nullable String tileKey, @Nullable String tagKey, boolean included) {
        if (tileKey == null || tagKey == null) return;

        String combinedKey = createTileTagKey(tileKey, tagKey);
        if (included) {
            includedTileTagKeys.add(combinedKey);
            return;
        }

        includedTileTagKeys.remove(combinedKey);
    }

    public void setTileTagExcluded(@Nullable String tileKey, @Nullable String tagKey, boolean excluded) {
        setTileTagIncluded(tileKey, tagKey, !excluded);
    }

    public void clear() {
        excludedBlockKeys.clear();
        includedTileTagKeys.clear();
    }

    public boolean isEmpty() {
        return excludedBlockKeys.isEmpty() && includedTileTagKeys.isEmpty();
    }

    public MultiblockRecordingExclusions copy() {
        MultiblockRecordingExclusions copy = new MultiblockRecordingExclusions();
        copy.excludedBlockKeys.addAll(excludedBlockKeys);
        copy.includedTileTagKeys.addAll(includedTileTagKeys);
        return copy;
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();

        NBTTagList blockList = new NBTTagList();
        for (String blockKey : excludedBlockKeys) {
            blockList.appendTag(new NBTTagString(blockKey));
        }

        NBTTagList tileTagList = new NBTTagList();
        for (String tileTagKey : includedTileTagKeys) {
            tileTagList.appendTag(new NBTTagString(tileTagKey));
        }

        tag.setTag("blocks", blockList);
        tag.setTag(TILE_TAGS_NBT_KEY, tileTagList);
        return tag;
    }

    public static MultiblockRecordingExclusions fromNBT(@Nullable NBTTagCompound tag) {
        MultiblockRecordingExclusions exclusions = new MultiblockRecordingExclusions();
        if (tag == null) return exclusions;

        NBTTagList blockList = tag.getTagList("blocks", Constants.NBT.TAG_STRING);
        for (int index = 0; index < blockList.tagCount(); index++) {
            exclusions.excludedBlockKeys.add(blockList.getStringTagAt(index));
        }

        NBTTagList tileTagList = tag.getTagList(TILE_TAGS_NBT_KEY, Constants.NBT.TAG_STRING);
        for (int index = 0; index < tileTagList.tagCount(); index++) {
            exclusions.includedTileTagKeys.add(tileTagList.getStringTagAt(index));
        }

        return exclusions;
    }

    private static String createTileTagKey(String tileKey, String tagKey) {
        return tileKey + "\n" + tagKey;
    }
}
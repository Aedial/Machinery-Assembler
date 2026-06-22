// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.recording;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import com.machineryassembler.common.autobuild.BlockSourceUtils;
import com.machineryassembler.common.structure.BlockRequirement;
import com.machineryassembler.common.structure.BlockStateMatcher;
import com.machineryassembler.common.structure.Structure;


/**
 * Frozen server-side capture that drives both recorder preview and JSON export.
 */
public class MultiblockRecordingSnapshot {

    private static final List<String> MANDATORY_TAGS = Collections.singletonList("id");

    private final int dimension;
    private final BlockPos selectionMinPos;
    private final BlockPos selectionMaxPos;
    private final Map<Long, CapturedBlock> blocks;

    @Nullable
    private List<BlockSummary> blockSummaries;
    @Nullable
    private List<TileSummary> tileSummaries;

    public MultiblockRecordingSnapshot(int dimension, BlockPos selectionMinPos, BlockPos selectionMaxPos,
            Map<Long, CapturedBlock> blocks) {
        this.dimension = dimension;
        this.selectionMinPos = selectionMinPos;
        this.selectionMaxPos = selectionMaxPos;
        this.blocks = new LinkedHashMap<>(blocks);
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public boolean matches(World world, BlockPos firstCorner, BlockPos secondCorner) {
        NormalizedBounds selection = NormalizedBounds.fromCorners(firstCorner, secondCorner);
        if (dimension != world.provider.getDimension()) return false;
        if (!selectionMinPos.equals(selection.minPos)) return false;
        return selectionMaxPos.equals(selection.maxPos);
    }

    public List<BlockSummary> getBlockSummaries() {
        if (blockSummaries != null) return blockSummaries;

        Map<String, BlockAccumulator> byKey = new LinkedHashMap<>();

        for (CapturedBlock block : blocks.values()) {
            String blockKey = createBlockKey(block.state);
            BlockAccumulator accumulator = byKey.computeIfAbsent(blockKey,
                key -> new BlockAccumulator(key, block.state));
            accumulator.count++;
        }

        List<BlockSummary> summaries = new ArrayList<>();
        for (BlockAccumulator accumulator : byKey.values()) {
            summaries.add(new BlockSummary(accumulator.key, accumulator.state, accumulator.count));
        }

        summaries.sort(Comparator
            .comparingInt(BlockSummary::getCount)
            .reversed()
            .thenComparing(BlockSummary::getKey));

        blockSummaries = Collections.unmodifiableList(summaries);
        return blockSummaries;
    }

    public List<TileSummary> getTileSummaries() {
        if (tileSummaries != null) return tileSummaries;

        Map<String, TileAccumulator> byKey = new LinkedHashMap<>();

        for (CapturedBlock block : blocks.values()) {
            if (block.tileData == null || !hasVisibleTileTags(block.tileData)) continue;

            String tileKey = createTileKey(block.state, block.tileData);
            TileAccumulator accumulator = byKey.computeIfAbsent(tileKey,
                key -> new TileAccumulator(tileKey, createBlockKey(block.state), block.state, block.tileData));
            accumulator.count++;
        }

        List<TileSummary> summaries = new ArrayList<>();
        for (TileAccumulator accumulator : byKey.values()) {
            summaries.add(new TileSummary(
                accumulator.key,
                accumulator.blockKey,
                accumulator.state,
                accumulator.count,
                accumulator.tileData.copy(),
                buildVisibleTagSummaries(accumulator.key, accumulator.tileData)
            ));
        }

        summaries.sort(Comparator
            .comparingInt(TileSummary::getCount)
            .reversed()
            .thenComparing(TileSummary::getKey));

        tileSummaries = Collections.unmodifiableList(summaries);
        return tileSummaries;
    }

    public int getTotalBlockCount() {
        return blocks.size();
    }

    public int getVisibleTileGroupCount(MultiblockRecordingExclusions exclusions) {
        int count = 0;
        for (TileSummary tileSummary : getTileSummaries()) {
            if (exclusions.isBlockExcluded(tileSummary.getBlockKey())) continue;
            count++;
        }

        return count;
    }

    public int getVisibleTileTagCount(MultiblockRecordingExclusions exclusions) {
        int count = 0;
        for (TileSummary tileSummary : getTileSummaries()) {
            if (exclusions.isBlockExcluded(tileSummary.getBlockKey())) continue;

            for (TileTagSummary tagSummary : tileSummary.getVisibleTags()) {
                if (tagSummary.isMandatory()) continue;
                count++;
            }
        }

        return count;
    }

    public int getEnabledTileTagCount(MultiblockRecordingExclusions exclusions) {
        int count = 0;
        for (TileSummary tileSummary : getTileSummaries()) {
            if (exclusions.isBlockExcluded(tileSummary.getBlockKey())) continue;

            for (TileTagSummary tagSummary : tileSummary.getVisibleTags()) {
                if (tagSummary.isMandatory()) continue;
                if (exclusions.isTileTagExcluded(tagSummary.getTileKey(), tagSummary.getKey())) continue;
                count++;
            }
        }

        return count;
    }

    @Nullable
    public ExportBounds getExportBounds(MultiblockRecordingExclusions exclusions) {
        ContentBounds bounds = collectContentBounds(exclusions);
        if (!bounds.hasContent()) return null;

        return new ExportBounds(bounds.getSizeX(), bounds.getSizeY(), bounds.getSizeZ());
    }

    @Nullable
    public Structure buildStructure(String id, MultiblockRecordingExclusions exclusions) {
        ContentBounds bounds = collectContentBounds(exclusions);
        if (!bounds.hasContent()) return null;

        Structure structure = new Structure(id);

        for (CapturedBlock block : blocks.values()) {
            String blockKey = createBlockKey(block.state);
            if (exclusions.isBlockExcluded(blockKey)) continue;

            BlockRequirement requirement = new BlockRequirement(Collections.singletonList(BlockStateMatcher.of(block.state)));
            NBTTagCompound filteredTileData = filterTileData(block, exclusions);
            if (filteredTileData != null && !filteredTileData.getKeySet().isEmpty()) {
                requirement.setMatchingTag(filteredTileData.copy());
                requirement.setPreviewTag(filteredTileData.copy());
            }

            BlockPos relativePos = block.worldPos.subtract(bounds.minPos);
            structure.getPattern().addBlock(relativePos, requirement);
        }

        return structure;
    }

    @Nullable
    public NBTTagCompound getFilteredTileData(TileSummary tileSummary, MultiblockRecordingExclusions exclusions) {
        if (tileSummary == null) return null;

        NBTTagCompound filtered = new NBTTagCompound();
        NBTTagCompound tileData = tileSummary.getTileData();
        List<String> keys = new ArrayList<>(tileData.getKeySet());
        keys.sort(String::compareTo);

        for (String key : keys) {
            if (isMandatoryTileTag(key)) {
                filtered.setTag(key, tileData.getTag(key).copy());
                continue;
            }

            if (exclusions.isTileTagExcluded(tileSummary.getKey(), key)) continue;
            filtered.setTag(key, tileData.getTag(key).copy());
        }

        return filtered.getKeySet().isEmpty() ? null : filtered;
    }

    @Nullable
    public String buildStructureJson(String id, MultiblockRecordingExclusions exclusions) {
        Structure structure = buildStructure(id, exclusions);
        if (structure == null || structure.getPattern().isEmpty()) return null;

        return MultiblockRecordingJsonExporter.export(id, structure);
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("dimension", dimension);
        tag.setLong("selectionMinPos", selectionMinPos.toLong());
        tag.setLong("selectionMaxPos", selectionMaxPos.toLong());

        NBTTagList blockList = new NBTTagList();
        for (CapturedBlock block : blocks.values()) {
            blockList.appendTag(block.toNBT());
        }

        tag.setTag("blocks", blockList);
        return tag;
    }

    public static MultiblockRecordingSnapshot fromNBT(@Nullable NBTTagCompound tag) {
        if (tag == null) {
            return new MultiblockRecordingSnapshot(0, BlockPos.ORIGIN, BlockPos.ORIGIN, Collections.emptyMap());
        }

        Map<Long, CapturedBlock> blocks = new LinkedHashMap<>();
        NBTTagList blockList = tag.getTagList("blocks", Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < blockList.tagCount(); index++) {
            CapturedBlock block = CapturedBlock.fromNBT(blockList.getCompoundTagAt(index));
            blocks.put(block.worldPos.toLong(), block);
        }

        return new MultiblockRecordingSnapshot(
            tag.getInteger("dimension"),
            BlockPos.fromLong(tag.getLong("selectionMinPos")),
            BlockPos.fromLong(tag.getLong("selectionMaxPos")),
            blocks
        );
    }

    private ContentBounds collectContentBounds(MultiblockRecordingExclusions exclusions) {
        ContentBounds bounds = new ContentBounds();

        for (CapturedBlock block : blocks.values()) {
            if (exclusions.isBlockExcluded(createBlockKey(block.state))) continue;
            bounds.include(block.worldPos);
        }

        return bounds;
    }

    @Nullable
    private NBTTagCompound filterTileData(CapturedBlock block, MultiblockRecordingExclusions exclusions) {
        if (block.tileData == null || block.tileData.getKeySet().isEmpty()) return null;

        NBTTagCompound filtered = new NBTTagCompound();
        String tileKey = createTileKey(block.state, block.tileData);

        List<String> keys = new ArrayList<>(block.tileData.getKeySet());
        keys.sort(String::compareTo);

        for (String key : keys) {
            if (isMandatoryTileTag(key)) {
                filtered.setTag(key, block.tileData.getTag(key).copy());
                continue;
            }

            if (exclusions.isTileTagExcluded(tileKey, key)) continue;
            filtered.setTag(key, block.tileData.getTag(key).copy());
        }

        return filtered.getKeySet().isEmpty() ? null : filtered;
    }

    private static List<TileTagSummary> buildVisibleTagSummaries(String tileKey, NBTTagCompound tileData) {
        List<TileTagSummary> summaries = new ArrayList<>();
        List<String> keys = new ArrayList<>(tileData.getKeySet());
        keys.sort(String::compareTo);

        for (String key : keys) {
            if (isMandatoryTileTag(key)) continue;

            NBTBase tag = tileData.getTag(key);
            summaries.add(new TileTagSummary(tileKey, key, tag.toString(), false));
        }

        return Collections.unmodifiableList(summaries);
    }

    private static boolean hasVisibleTileTags(NBTTagCompound tileData) {
        for (String key : tileData.getKeySet()) {
            if (isMandatoryTileTag(key)) continue;
            return true;
        }

        return false;
    }

    private static boolean isMandatoryTileTag(String key) {
        return MANDATORY_TAGS.contains(key);
    }

    public static String createBlockKey(IBlockState state) {
        return BlockSourceUtils.stateToKey(state);
    }

    public static String createTileKey(IBlockState state, NBTTagCompound tileData) {
        return createBlockKey(state) + "|" + tileData.toString();
    }

    public static final class ExportBounds {
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;

        private ExportBounds(int sizeX, int sizeY, int sizeZ) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
        }

        public int getSizeX() {
            return sizeX;
        }

        public int getSizeY() {
            return sizeY;
        }

        public int getSizeZ() {
            return sizeZ;
        }
    }

    public static final class BlockSummary {
        private final String key;
        private final IBlockState state;
        private final int count;

        private BlockSummary(String key, IBlockState state, int count) {
            this.key = key;
            this.state = state;
            this.count = count;
        }

        public String getKey() {
            return key;
        }

        public IBlockState getState() {
            return state;
        }

        public int getCount() {
            return count;
        }
    }

    public static final class TileSummary {
        private final String key;
        private final String blockKey;
        private final IBlockState state;
        private final int count;
        private final NBTTagCompound tileData;
        private final List<TileTagSummary> visibleTags;

        private TileSummary(String key, String blockKey, IBlockState state, int count, NBTTagCompound tileData,
                List<TileTagSummary> visibleTags) {
            this.key = key;
            this.blockKey = blockKey;
            this.state = state;
            this.count = count;
            this.tileData = tileData;
            this.visibleTags = visibleTags;
        }

        public String getKey() {
            return key;
        }

        public String getBlockKey() {
            return blockKey;
        }

        public IBlockState getState() {
            return state;
        }

        public int getCount() {
            return count;
        }

        public NBTTagCompound getTileData() {
            return tileData.copy();
        }

        public List<TileTagSummary> getVisibleTags() {
            return visibleTags;
        }
    }

    public static final class TileTagSummary {
        private final String tileKey;
        private final String key;
        private final String content;
        private final boolean mandatory;

        private TileTagSummary(String tileKey, String key, String content, boolean mandatory) {
            this.tileKey = tileKey;
            this.key = key;
            this.content = content;
            this.mandatory = mandatory;
        }

        public String getTileKey() {
            return tileKey;
        }

        public String getKey() {
            return key;
        }

        public String getContent() {
            return content;
        }

        public boolean isMandatory() {
            return mandatory;
        }
    }

    public static final class CapturedBlock {
        private final BlockPos worldPos;
        private final IBlockState state;
        @Nullable
        private final NBTTagCompound tileData;

        public CapturedBlock(BlockPos worldPos, IBlockState state, @Nullable NBTTagCompound tileData) {
            this.worldPos = worldPos;
            this.state = state;
            this.tileData = tileData == null ? null : tileData.copy();
        }

        private NBTTagCompound toNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("pos", worldPos.toLong());
            tag.setTag("state", NBTUtil.writeBlockState(new NBTTagCompound(), state));

            if (tileData != null && !tileData.getKeySet().isEmpty()) {
                tag.setTag("tile", tileData.copy());
            }

            return tag;
        }

        private static CapturedBlock fromNBT(NBTTagCompound tag) {
            return new CapturedBlock(
                BlockPos.fromLong(tag.getLong("pos")),
                NBTUtil.readBlockState(tag.getCompoundTag("state")),
                tag.hasKey("tile", Constants.NBT.TAG_COMPOUND) ? tag.getCompoundTag("tile") : null
            );
        }
    }

    private static final class BlockAccumulator {
        private final String key;
        private final IBlockState state;
        private int count;

        private BlockAccumulator(String key, IBlockState state) {
            this.key = key;
            this.state = state;
        }
    }

    private static final class TileAccumulator {
        private final String key;
        private final String blockKey;
        private final IBlockState state;
        private final NBTTagCompound tileData;
        private int count;

        private TileAccumulator(String key, String blockKey, IBlockState state, NBTTagCompound tileData) {
            this.key = key;
            this.blockKey = blockKey;
            this.state = state;
            this.tileData = tileData.copy();
        }
    }

    private static final class NormalizedBounds {
        private final BlockPos minPos;
        private final BlockPos maxPos;

        private NormalizedBounds(BlockPos minPos, BlockPos maxPos) {
            this.minPos = minPos;
            this.maxPos = maxPos;
        }

        private static NormalizedBounds fromCorners(BlockPos firstCorner, BlockPos secondCorner) {
            BlockPos minPos = new BlockPos(
                Math.min(firstCorner.getX(), secondCorner.getX()),
                Math.min(firstCorner.getY(), secondCorner.getY()),
                Math.min(firstCorner.getZ(), secondCorner.getZ())
            );
            BlockPos maxPos = new BlockPos(
                Math.max(firstCorner.getX(), secondCorner.getX()),
                Math.max(firstCorner.getY(), secondCorner.getY()),
                Math.max(firstCorner.getZ(), secondCorner.getZ())
            );
            return new NormalizedBounds(minPos, maxPos);
        }
    }

    private static final class ContentBounds {
        @Nullable
        private BlockPos minPos;
        @Nullable
        private BlockPos maxPos;

        private void include(BlockPos pos) {
            if (minPos == null || maxPos == null) {
                minPos = pos;
                maxPos = pos;
                return;
            }

            minPos = new BlockPos(
                Math.min(minPos.getX(), pos.getX()),
                Math.min(minPos.getY(), pos.getY()),
                Math.min(minPos.getZ(), pos.getZ())
            );
            maxPos = new BlockPos(
                Math.max(maxPos.getX(), pos.getX()),
                Math.max(maxPos.getY(), pos.getY()),
                Math.max(maxPos.getZ(), pos.getZ())
            );
        }

        private boolean hasContent() {
            return minPos != null && maxPos != null;
        }

        private int getSizeX() {
            return minPos == null || maxPos == null ? 0 : maxPos.getX() - minPos.getX() + 1;
        }

        private int getSizeY() {
            return minPos == null || maxPos == null ? 0 : maxPos.getY() - minPos.getY() + 1;
        }

        private int getSizeZ() {
            return minPos == null || maxPos == null ? 0 : maxPos.getZ() - minPos.getZ() + 1;
        }
    }
}
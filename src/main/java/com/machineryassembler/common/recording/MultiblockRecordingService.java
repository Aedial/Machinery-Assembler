// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.recording;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.machineryassembler.common.CommonProxy;
import com.machineryassembler.common.recording.MultiblockRecordingSnapshot.CapturedBlock;
import com.machineryassembler.common.recording.MultiblockRecordingSnapshot.ExportBounds;
import com.machineryassembler.common.structure.StructureRegistry;


/**
 * Server-side capture builder for recorder previews and saved multiblock JSON files.
 */
public final class MultiblockRecordingService {

    private static final Map<UUID, MultiblockRecordingSnapshot> FROZEN_CAPTURES = new LinkedHashMap<>();

    private MultiblockRecordingService() {
    }

    @Nullable
    public static MultiblockRecordingSnapshot captureSnapshot(UUID playerId, World world, BlockPos firstCorner,
            BlockPos secondCorner) {
        MultiblockRecordingSnapshot snapshot = captureSnapshot(world, firstCorner, secondCorner);
        if (snapshot.isEmpty()) {
            clearFrozenCapture(playerId);
            return null;
        }

        FROZEN_CAPTURES.put(playerId, snapshot);
        return snapshot;
    }

    public static void clearFrozenCapture(UUID playerId) {
        FROZEN_CAPTURES.remove(playerId);
    }

    @Nullable
    public static SaveResult saveCapture(UUID playerId, World world, BlockPos firstCorner, BlockPos secondCorner,
            MultiblockRecordingExclusions exclusions) throws IOException {
        MultiblockRecordingSnapshot snapshot = getOrCreateSnapshot(playerId, world, firstCorner, secondCorner);

        File outputDirectory = CommonProxy.dataHolder.getStructuresSaveDirectory();
        String structureId = createStructureId(outputDirectory);
        String json = snapshot.buildStructureJson(structureId, exclusions);
        if (json == null || json.trim().isEmpty()) return null;

        ExportBounds bounds = snapshot.getExportBounds(exclusions);
        if (bounds == null) return null;

        File outputFile = new File(outputDirectory, structureId + ".json");
        Files.write(outputFile.toPath(), json.getBytes(StandardCharsets.UTF_8));

        StructureRegistry.reloadStructures(null);
        return new SaveResult(outputFile, structureId, bounds.getSizeX(), bounds.getSizeY(), bounds.getSizeZ());
    }

    private static MultiblockRecordingSnapshot getOrCreateSnapshot(UUID playerId, World world, BlockPos firstCorner,
            BlockPos secondCorner) {
        MultiblockRecordingSnapshot snapshot = FROZEN_CAPTURES.get(playerId);
        if (snapshot != null && snapshot.matches(world, firstCorner, secondCorner)) return snapshot;

        snapshot = captureSnapshot(world, firstCorner, secondCorner);
        FROZEN_CAPTURES.put(playerId, snapshot);
        return snapshot;
    }

    private static MultiblockRecordingSnapshot captureSnapshot(World world, BlockPos firstCorner, BlockPos secondCorner) {
        Map<Long, CapturedBlock> blocks = new LinkedHashMap<>();
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

        for (BlockPos.MutableBlockPos mutablePos : BlockPos.getAllInBoxMutable(minPos, maxPos)) {
            IBlockState state = world.getBlockState(mutablePos);
            if (state.getBlock().isAir(state, world, mutablePos)) continue;

            BlockPos worldPos = new BlockPos(mutablePos);
            NBTTagCompound tileData = captureTileData(world.getTileEntity(worldPos));
            blocks.put(worldPos.toLong(), new CapturedBlock(worldPos, state, tileData));
        }

        return new MultiblockRecordingSnapshot(world.provider.getDimension(), minPos, maxPos, blocks);
    }

    @Nullable
    private static NBTTagCompound captureTileData(@Nullable TileEntity tileEntity) {
        if (tileEntity == null) return null;

        NBTTagCompound tileData = tileEntity.writeToNBT(new NBTTagCompound());
        trimTileData(tileData);
        return tileData.getKeySet().isEmpty() ? null : tileData;
    }

    /**
     * Removes tags that are related the world instead of the block's state.
     * These tags should not be used by anything, so it is safe to remove them to reduce bloat.
     */
    private static void trimTileData(NBTTagCompound tileData) {
        tileData.removeTag("x");
        tileData.removeTag("y");
        tileData.removeTag("z");
        tileData.removeTag("ForgeCaps");
        tileData.removeTag("Lock");
        tileData.removeTag("LootTable");
        tileData.removeTag("LootTableSeed");

        // Empty ForgeData tags are common and usually noise, so remove them to reduce bloat
        if (tileData.hasKey("ForgeData") && tileData.getTag("ForgeData").isEmpty()) {
            tileData.removeTag("ForgeData");
        }
    }

    private static String createStructureId(File directory) {
        String baseId = "0_machinery_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date());
        String candidate = baseId;
        int suffix = 1;

        while (new File(directory, candidate + ".json").exists()) {
            candidate = baseId + "_" + suffix;
            suffix++;
        }

        return candidate;
    }

    public static final class SaveResult {
        private final File file;
        private final String id;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;

        private SaveResult(File file, String id, int sizeX, int sizeY, int sizeZ) {
            this.file = file;
            this.id = id;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
        }

        public File getFile() {
            return file;
        }

        public String getId() {
            return id;
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
}
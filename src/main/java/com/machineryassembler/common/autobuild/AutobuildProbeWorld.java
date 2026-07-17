// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.structure.template.TemplateManager;
import net.minecraft.world.storage.IPlayerFileData;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;


/**
 * In-memory world used to probe autobuild placement footprints without touching the live world.
 */
class AutobuildProbeWorld extends World {

    private static final WorldSettings DEFAULT_SETTINGS = new WorldSettings(
        1L,
        GameType.CREATIVE,
        false,
        false,
        WorldType.FLAT
    );

    private final Set<BlockPos> knownBlocks = new HashSet<>();
    private final Map<BlockPos, TileEntity> tileEntities = new HashMap<>();

    AutobuildProbeWorld() {
        super(
            new ProbeSaveHandler(),
            new WorldInfo(DEFAULT_SETTINGS, "AutobuildProbeWorld"),
            new WorldProviderSurface(),
            new Profiler(),
            false
        );

        provider.setDimension(Integer.MAX_VALUE - 2048);
        int dimension = provider.getDimension();
        provider.setWorld(this);
        provider.setDimension(dimension);
        chunkProvider = createChunkProvider();
        getWorldBorder().setSize(30000000);
    }

    @Override
    protected void initCapabilities() {
        // Skip Forge capability setup. Probe worlds should stay isolated from normal world hooks.
    }

    void clearAll() {
        clearTileEntities();
        knownBlocks.clear();

        if (chunkProvider instanceof ProbeChunkProvider) {
            ((ProbeChunkProvider) chunkProvider).clear();
        }
    }

    private void clearTileEntities() {
        if (tileEntities.isEmpty()) return;

        Set<BlockPos> positions = new HashSet<>(tileEntities.keySet());

        for (BlockPos pos : positions) removeTileEntity(pos);
    }

    @Override
    public boolean setBlockState(@Nonnull BlockPos pos, @Nonnull IBlockState newState, int flags) {
        if (newState.getBlock() == Blocks.AIR) {
            knownBlocks.remove(pos);
            removeTileEntity(pos);
        } else {
            knownBlocks.add(pos);
        }

        return super.setBlockState(pos, newState, flags);
    }

    @Nonnull
    @Override
    public IBlockState getBlockState(@Nonnull BlockPos pos) {
        if (!knownBlocks.contains(pos)) return Blocks.AIR.getDefaultState();

        return super.getBlockState(pos);
    }

    @Nonnull
    @Override
    protected IChunkProvider createChunkProvider() {
        return new ProbeChunkProvider(this);
    }

    @Override
    protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
        return chunkProvider.isChunkGeneratedAt(x, z);
    }

    @Override
    public boolean checkLightFor(@Nonnull EnumSkyBlock lightType, @Nonnull BlockPos pos) {
        return true;
    }

    @Override
    public int getLightFromNeighborsFor(@Nonnull EnumSkyBlock type, @Nonnull BlockPos pos) {
        return 15;
    }

    @Override
    public int getCombinedLight(@Nonnull BlockPos pos, int lightValue) {
        return 15 << 20 | 15 << 4;
    }

    @Nullable
    @Override
    public TileEntity getTileEntity(@Nonnull BlockPos pos) {
        return tileEntities.get(pos);
    }

    @Override
    public void setTileEntity(BlockPos pos, @Nullable TileEntity tileEntityIn) {
        removeTileEntity(pos);
        if (tileEntityIn == null) return;

        tileEntityIn.setWorld(this);
        tileEntityIn.setPos(pos);
        tileEntities.put(pos, tileEntityIn);
        loadedTileEntityList.add(tileEntityIn);

        if (tileEntityIn instanceof ITickable) tickableTileEntities.add(tileEntityIn);
    }

    @Override
    public void removeTileEntity(BlockPos pos) {
        TileEntity tileEntity = tileEntities.remove(pos);
        if (tileEntity == null) return;

        loadedTileEntityList.remove(tileEntity);
        tickableTileEntities.remove(tileEntity);
    }

    @Override
    public void notifyBlockUpdate(@Nonnull BlockPos pos,
                                  @Nonnull IBlockState oldState,
                                  @Nonnull IBlockState newState,
                                  int flags) {
        // No-op
    }

    @Override
    public void markBlockRangeForRenderUpdate(@Nonnull BlockPos rangeMin, @Nonnull BlockPos rangeMax) {
        // No-op
    }

    @Override
    public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
        // No-op
    }

    private static class ProbeChunkProvider implements IChunkProvider {

        private final World world;
        private final Map<Long, Chunk> loadedChunks = new HashMap<>();

        private ProbeChunkProvider(World world) {
            this.world = world;
        }

        @Nullable
        @Override
        public Chunk getLoadedChunk(int x, int z) {
            return loadedChunks.get(getChunkKey(x, z));
        }

        @Nonnull
        @Override
        public Chunk provideChunk(int x, int z) {
            long chunkKey = getChunkKey(x, z);
            Chunk existingChunk = loadedChunks.get(chunkKey);
            if (existingChunk != null) return existingChunk;

            Chunk chunk = new Chunk(world, x, z);
            loadedChunks.put(chunkKey, chunk);

            return chunk;
        }

        @Override
        public boolean tick() {
            return !loadedChunks.isEmpty();
        }

        @Nonnull
        @Override
        public String makeString() {
            return "AutobuildProbeChunkProvider";
        }

        @Override
        public boolean isChunkGeneratedAt(int x, int z) {
            return true;
        }

        private void clear() {
            loadedChunks.clear();
        }

        private long getChunkKey(int x, int z) {
            return ((long) x & 4294967295L) << 32 | (long) z & 4294967295L;
        }
    }

    private static class ProbeSaveHandler implements ISaveHandler, IPlayerFileData, IChunkLoader {

        @Override
        public WorldInfo loadWorldInfo() {
            return null;
        }

        @Override
        public void checkSessionLock() {
        }

        @Nonnull
        @Override
        public IChunkLoader getChunkLoader(@Nonnull WorldProvider provider) {
            return this;
        }

        @Nonnull
        @Override
        public IPlayerFileData getPlayerNBTManager() {
            return this;
        }

        @Nonnull
        @Override
        public TemplateManager getStructureTemplateManager() {
            return new TemplateManager("", new DataFixer(0));
        }

        @Override
        public void saveWorldInfoWithPlayer(@Nonnull WorldInfo worldInformation,
                                            @Nonnull NBTTagCompound tagCompound) {
        }

        @Override
        public void saveWorldInfo(@Nonnull WorldInfo worldInformation) {
        }

        @Nonnull
        @Override
        public File getWorldDirectory() {
            return new File(".");
        }

        @Nonnull
        @Override
        public File getMapFileFromName(@Nonnull String mapName) {
            return new File(".", mapName);
        }

        @Nullable
        @Override
        public Chunk loadChunk(@Nonnull World worldIn, int x, int z) {
            return null;
        }

        @Override
        public void saveChunk(@Nonnull World worldIn, @Nonnull Chunk chunkIn) {
        }

        @Override
        public void saveExtraChunkData(@Nonnull World worldIn, @Nonnull Chunk chunkIn) {
        }

        @Override
        public void chunkTick() {
        }

        @Override
        public void flush() {
        }

        @Override
        public boolean isChunkGeneratedAt(int x, int z) {
            return false;
        }

        @Override
        public void writePlayerData(@Nonnull EntityPlayer player) {
        }

        @Nullable
        @Override
        public NBTTagCompound readPlayerData(@Nonnull EntityPlayer player) {
            return null;
        }

        @Nonnull
        @Override
        public String[] getAvailablePlayerDat() {
            return new String[0];
        }
    }
}
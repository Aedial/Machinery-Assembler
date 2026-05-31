// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.client.render;

import java.lang.reflect.Field;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Lists;

import sun.misc.Unsafe;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.pathfinding.PathWorldListener;
import net.minecraft.profiler.Profiler;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.IntHashMap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.GameType;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.structure.template.TemplateManager;
import net.minecraft.world.storage.IPlayerFileData;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.common.util.BlockPos2ValueMap;


/**
 * Minimal client-only world used to back preview tile entities and state queries.
 */
@SuppressWarnings("removal")
@SideOnly(Side.CLIENT)
public class PreviewWorld extends World {

    private static final WorldSettings DEFAULT_SETTINGS =
        new WorldSettings(1L, GameType.CREATIVE, false, false, WorldType.DEFAULT);
    private static final int PREVIEW_DIMENSION = Integer.MAX_VALUE - 1024;
    private static final long INVALID_FIELD_OFFSET = -1L;
    private static final Unsafe UNSAFE = getUnsafe();

    // World fields are MCP-named in the dev environment and SRG-named in production.
    private static final long LOADED_ENTITY_LIST_OFFSET = getFieldOffset(World.class, "loadedEntityList", "field_72996_f");
    private static final long UNLOADED_ENTITY_LIST_OFFSET = getFieldOffset(World.class, "unloadedEntityList", "field_72997_g");
    private static final long LOADED_TILE_ENTITY_LIST_OFFSET = getFieldOffset(World.class, "loadedTileEntityList", "field_147482_g");
    private static final long TICKABLE_TILE_ENTITIES_OFFSET = getFieldOffset(World.class, "tickableTileEntities", "field_175730_i");
    private static final long PLAYER_ENTITIES_OFFSET = getFieldOffset(World.class, "playerEntities", "field_73010_i");
    private static final long WEATHER_EFFECTS_OFFSET = getFieldOffset(World.class, "weatherEffects", "field_73007_j");
    private static final long ENTITIES_BY_ID_OFFSET = getFieldOffset(World.class, "entitiesById", "field_175729_l");
    private static final long UPDATE_LCG_OFFSET = getFieldOffset(World.class, "updateLCG", "field_73005_l");
    private static final long RAND_OFFSET = getFieldOffset(World.class, "rand", "field_73012_v");
    private static final long PROVIDER_OFFSET = getFieldOffset(World.class, "provider", "field_73011_w");
    private static final long PATH_LISTENER_OFFSET = getFieldOffset(World.class, "pathListener", "field_184152_t");
    private static final long EVENT_LISTENERS_OFFSET = getFieldOffset(World.class, "eventListeners", "field_73021_x");
    private static final long CHUNK_PROVIDER_OFFSET = getFieldOffset(World.class, "chunkProvider", "field_73020_y");
    private static final long SAVE_HANDLER_OFFSET = getFieldOffset(World.class, "saveHandler", "field_73019_z");
    private static final long WORLD_INFO_OFFSET = getFieldOffset(World.class, "worldInfo", "field_72986_A");
    private static final long MAP_STORAGE_OFFSET = getFieldOffset(World.class, "mapStorage", "field_72988_C");
    private static final long PROFILER_OFFSET = getFieldOffset(World.class, "profiler", "field_72984_F");
    private static final long WORLD_SCOREBOARD_OFFSET = getFieldOffset(World.class, "worldScoreboard", "field_96442_D");
    private static final long IS_REMOTE_OFFSET = getFieldOffset(World.class, "isRemote", "field_72995_K");
    private static final long SPAWN_HOSTILE_MOBS_OFFSET = getFieldOffset(World.class, "spawnHostileMobs", "field_72985_G");
    private static final long SPAWN_PEACEFUL_MOBS_OFFSET = getFieldOffset(World.class, "spawnPeacefulMobs", "field_72992_H");
    private static final long CALENDAR_OFFSET = getFieldOffset(World.class, "calendar", "field_83016_L");
    private static final long WORLD_BORDER_OFFSET = getFieldOffset(World.class, "worldBorder", "field_175728_M");
    private static final long LIGHT_UPDATE_BLOCK_LIST_OFFSET = getFieldOffset(World.class, "lightUpdateBlockList", "field_72994_J");
    private static final long PER_WORLD_STORAGE_OFFSET = getOptionalFieldOffset(World.class, "perWorldStorage");
    private static final long CAPTURED_BLOCK_SNAPSHOTS_OFFSET = getOptionalFieldOffset(World.class, "capturedBlockSnapshots");

    private Map<BlockPos, IBlockState> previewStates;
    private Map<BlockPos, TileEntity> previewTileEntities;
    private Set<BlockPos> previewPositions;

    private PreviewWorld() {
        super(null, null, null, null, true);
        throw new UnsupportedOperationException("Use PreviewWorld.create()");
    }

    public static PreviewWorld create() {
        PreviewWorld previewWorld = allocatePreviewWorld();
        previewWorld.initialize();

        return previewWorld;
    }

    private static PreviewWorld allocatePreviewWorld() {
        try {
            return (PreviewWorld) UNSAFE.allocateInstance(PreviewWorld.class);
        } catch (InstantiationException e) {
            throw new IllegalStateException("Failed to allocate PreviewWorld", e);
        }
    }

    private void initialize() {
        previewStates = new BlockPos2ValueMap<>();
        previewTileEntities = new BlockPos2ValueMap<>();
        previewPositions = new HashSet<>();

        PreviewSaveHandler previewSaveHandler = new PreviewSaveHandler();
        WorldInfo previewInfo = new WorldInfo(DEFAULT_SETTINGS, "MachineryAssemblerPreview");
        WorldProviderSurface previewProvider = new WorldProviderSurface();
        PathWorldListener previewPathListener = new PathWorldListener();
        List<IWorldEventListener> previewEventListeners = Lists.newArrayList(previewPathListener);
        Random previewRandom = new Random();
        MapStorage previewMapStorage = new MapStorage((ISaveHandler) null);
        WorldBorder previewBorder = previewProvider.createWorldBorder();

        setObjectField(this, LOADED_ENTITY_LIST_OFFSET, new ArrayList<>());
        setObjectField(this, UNLOADED_ENTITY_LIST_OFFSET, new ArrayList<>());
        setObjectField(this, LOADED_TILE_ENTITY_LIST_OFFSET, new ArrayList<>());
        setObjectField(this, TICKABLE_TILE_ENTITIES_OFFSET, new ArrayList<>());
        setObjectField(this, PLAYER_ENTITIES_OFFSET, new ArrayList<>());
        setObjectField(this, WEATHER_EFFECTS_OFFSET, new ArrayList<>());
        setObjectField(this, ENTITIES_BY_ID_OFFSET, new IntHashMap<>());
        setIntField(this, UPDATE_LCG_OFFSET, previewRandom.nextInt());
        setObjectField(this, RAND_OFFSET, previewRandom);
        setObjectField(this, PROVIDER_OFFSET, previewProvider);
        setObjectField(this, PATH_LISTENER_OFFSET, previewPathListener);
        setObjectField(this, EVENT_LISTENERS_OFFSET, previewEventListeners);
        setObjectField(this, SAVE_HANDLER_OFFSET, previewSaveHandler);
        setObjectField(this, WORLD_INFO_OFFSET, previewInfo);
        setObjectField(this, MAP_STORAGE_OFFSET, previewMapStorage);
        setObjectField(this, PROFILER_OFFSET, new Profiler());
        setObjectField(this, WORLD_SCOREBOARD_OFFSET, new Scoreboard());
        setBooleanField(this, IS_REMOTE_OFFSET, true);
        setBooleanField(this, SPAWN_HOSTILE_MOBS_OFFSET, true);
        setBooleanField(this, SPAWN_PEACEFUL_MOBS_OFFSET, true);
        setObjectField(this, CALENDAR_OFFSET, Calendar.getInstance());
        setObjectField(this, WORLD_BORDER_OFFSET, previewBorder);
        setObjectField(this, LIGHT_UPDATE_BLOCK_LIST_OFFSET, new int[32768]);
        setOptionalObjectField(this, PER_WORLD_STORAGE_OFFSET, new MapStorage((ISaveHandler) null));
        setOptionalObjectField(this, CAPTURED_BLOCK_SNAPSHOTS_OFFSET, new ArrayList<>());

        previewProvider.setDimension(PREVIEW_DIMENSION);
        previewProvider.setWorld(this);

        setObjectField(this, CHUNK_PROVIDER_OFFSET, createChunkProvider());
        getWorldBorder().setSize(30000000);
    }

    public void clearPreview() {
        if (previewPositions == null || previewPositions.isEmpty()) return;

        Set<BlockPos> positions = new HashSet<>(previewPositions);
        for (BlockPos pos : positions) removePreviewState(pos);
    }

    public void setPreviewState(BlockPos pos, @Nullable IBlockState state,
                                @Nullable TileEntity tileEntity) {
        removePreviewState(pos);

        if (state == null || state.getBlock() == Blocks.AIR) return;

        previewPositions.add(pos);
        previewStates.put(pos, state);

        if (tileEntity == null) return;

        setTileEntity(pos, tileEntity);
    }

    private void removePreviewState(BlockPos pos) {
        previewPositions.remove(pos);
        previewStates.remove(pos);
        removeTileEntity(pos);
    }

    @Override
    protected void initCapabilities() {
        // Skip forge capability events for the preview world.
    }

    @Override
    public Biome getBiome(@Nonnull BlockPos pos) {
        World world = Minecraft.getMinecraft().world;
        if (world != null) return world.getBiome(pos);

        return Biomes.PLAINS;
    }

    @Nonnull
    @Override
    public IBlockState getBlockState(@Nonnull BlockPos pos) {
        IBlockState state = previewStates.get(pos);
        return state != null ? state : Blocks.AIR.getDefaultState();
    }

    @Nullable
    @Override
    public TileEntity getTileEntity(@Nonnull BlockPos pos) {
        return previewTileEntities.get(pos);
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        IBlockState state = previewStates.get(pos);
        return state == null || state.getBlock() == Blocks.AIR;
    }

    @Override
    public boolean isBlockLoaded(BlockPos pos) {
        return true;
    }

    @Override
    public boolean isBlockLoaded(BlockPos pos, boolean allowEmpty) {
        return true;
    }

    @Override
    public Chunk getChunk(BlockPos pos) {
        return getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Override
    public Chunk getChunk(int chunkX, int chunkZ) {
        return chunkProvider.provideChunk(chunkX, chunkZ);
    }

    @Override
    public boolean setBlockState(@Nonnull BlockPos pos, @Nonnull IBlockState newState, int flags) {
        if (newState.getBlock() == Blocks.AIR) {
            previewStates.remove(pos);
            return true;
        }

        previewStates.put(pos, newState);
        previewPositions.add(pos);
        return true;
    }

    @Override
    public void setTileEntity(BlockPos pos, @Nullable TileEntity tileEntityIn) {
        removeTileEntity(pos);

        if (tileEntityIn == null) return;

        tileEntityIn.setWorld(this);
        tileEntityIn.setPos(pos);
        previewTileEntities.put(pos, tileEntityIn);
        loadedTileEntityList.add(tileEntityIn);

        if (tileEntityIn instanceof ITickable) tickableTileEntities.add(tileEntityIn);
    }

    @Override
    public void removeTileEntity(BlockPos pos) {
        TileEntity tileEntity = previewTileEntities.remove(pos);
        if (tileEntity == null) return;

        loadedTileEntityList.remove(tileEntity);
        tickableTileEntities.remove(tileEntity);
    }

    @Override
    public void notifyNeighborsRespectDebug(@Nonnull BlockPos pos, @Nonnull Block blockType,
                                            boolean updateObservers) {
    }

    @Override
    public void notifyNeighborsOfStateChange(@Nonnull BlockPos pos, @Nonnull Block blockType,
                                             boolean updateObservers) {
    }

    @Override
    public void notifyNeighborsOfStateExcept(@Nonnull BlockPos pos, @Nonnull Block blockType,
                                             @Nonnull EnumFacing skipSide) {
    }

    @Override
    public void markAndNotifyBlock(@Nonnull BlockPos pos, @Nullable Chunk chunk,
                                   @Nonnull IBlockState oldState,
                                   @Nonnull IBlockState newState, int flags) {
    }

    @Override
    public void notifyBlockUpdate(@Nonnull BlockPos pos,
                                  @Nonnull IBlockState oldState,
                                  @Nonnull IBlockState newState, int flags) {
    }

    @Override
    public void markBlockRangeForRenderUpdate(@Nonnull BlockPos rangeMin, @Nonnull BlockPos rangeMax) {
    }

    @Override
    public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
    }

    @Override
    public void updateObservingBlocksAt(@Nonnull BlockPos pos, @Nonnull Block blockType) {
    }

    @Nonnull
    @Override
    protected IChunkProvider createChunkProvider() {
        return new PreviewChunkProvider(this);
    }

    @Override
    protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
        return true;
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

    @Nonnull
    @Override
    public WorldType getWorldType() {
        World world = Minecraft.getMinecraft().world;
        if (world != null) return world.getWorldType();

        return WorldType.DEFAULT;
    }

    @Override
    public long getWorldTime() {
        World world = Minecraft.getMinecraft().world;
        return world != null ? world.getWorldTime() : super.getWorldTime();
    }

    @Override
    public long getTotalWorldTime() {
        World world = Minecraft.getMinecraft().world;
        return world != null ? world.getTotalWorldTime() : super.getTotalWorldTime();
    }

    private static Unsafe getUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to access Unsafe", e);
        }
    }

    private static long getFieldOffset(Class<?> owner, String... fieldNames) {
        Field field = findField(owner, fieldNames);
        if (field != null) return UNSAFE.objectFieldOffset(field);

        throw new IllegalStateException("Failed to access fields " + owner.getName() + "." + String.join(", ", fieldNames));
    }

    private static long getOptionalFieldOffset(Class<?> owner, String... fieldNames) {
        Field field = findField(owner, fieldNames);
        return field != null ? UNSAFE.objectFieldOffset(field) : INVALID_FIELD_OFFSET;
    }

    @Nullable
    private static Field findField(Class<?> owner, String... fieldNames) {
        for (String fieldName : fieldNames) {
            try {
                Field field = owner.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return null;
    }

    private static void setObjectField(Object target, long offset, Object value) {
        UNSAFE.putObject(target, offset, value);
    }

    private static void setOptionalObjectField(Object target, long offset, Object value) {
        if (offset == INVALID_FIELD_OFFSET) return;

        setObjectField(target, offset, value);
    }

    private static void setBooleanField(Object target, long offset, boolean value) {
        UNSAFE.putBoolean(target, offset, value);
    }

    private static void setIntField(Object target, long offset, int value) {
        UNSAFE.putInt(target, offset, value);
    }

    private static class PreviewChunkProvider implements IChunkProvider {

        private final World world;
        private final Map<Long, Chunk> loadedChunks = new HashMap<>();

        private PreviewChunkProvider(World world) {
            this.world = world;
        }

        @Nullable
        @Override
        public Chunk getLoadedChunk(int x, int z) {
            return loadedChunks.get(ChunkPos.asLong(x, z));
        }

        @Nonnull
        @Override
        public Chunk provideChunk(int x, int z) {
            long chunkKey = ChunkPos.asLong(x, z);
            Chunk chunk = loadedChunks.get(chunkKey);
            if (chunk != null) return chunk;

            chunk = new Chunk(world, x, z);
            loadedChunks.put(chunkKey, chunk);
            return chunk;
        }

        @Override
        public boolean tick() {
            for (Chunk chunk : loadedChunks.values()) chunk.onTick(false);
            return !loadedChunks.isEmpty();
        }

        @Nonnull
        @Override
        public String makeString() {
            return "Preview";
        }

        @Override
        public boolean isChunkGeneratedAt(int x, int z) {
            return true;
        }
    }

    private static class PreviewSaveHandler implements ISaveHandler, IPlayerFileData, IChunkLoader {

        private final File rootDirectory = new File(".");

        @Nullable
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
            return rootDirectory;
        }

        @Nonnull
        @Override
        public File getMapFileFromName(@Nonnull String mapName) {
            return new File(rootDirectory, mapName);
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
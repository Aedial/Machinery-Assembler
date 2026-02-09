// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/client/util/BlockArrayRenderHelper.java (AirBlockRenderWorld) from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.client.render;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.common.util.BlockPos2ValueMap;


/**
 * A fake IBlockAccess for rendering structures without an actual world.
 * Stores block states and tile entities at specific positions.
 */
@SideOnly(Side.CLIENT)
public class DummyBlockAccess implements IBlockAccess {

    protected final Map<BlockPos, IBlockState> states = new BlockPos2ValueMap<>();
    protected final Map<BlockPos, TileEntity> tileEntities = new BlockPos2ValueMap<>();
    protected final WorldType worldType;

    public DummyBlockAccess() {
        this(WorldType.DEFAULT);
    }

    public DummyBlockAccess(WorldType worldType) {
        this.worldType = worldType;
    }

    public void setBlockState(BlockPos pos, IBlockState state) {
        states.put(pos, state);
    }

    public void setTileEntity(BlockPos pos, TileEntity te) {
        tileEntities.put(pos, te);
    }

    public void clear() {
        states.clear();
        tileEntities.clear();
    }

    public boolean hasBlockAt(BlockPos pos) {
        return states.containsKey(pos);
    }

    @Nullable
    @Override
    public TileEntity getTileEntity(@Nonnull BlockPos pos) {
        return tileEntities.get(pos);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getCombinedLight(@Nonnull BlockPos pos, int lightValue) {
        // Return max brightness for preview
        return 15 << 20 | 15 << 4;
    }

    @Nonnull
    @Override
    public IBlockState getBlockState(@Nonnull BlockPos pos) {
        IBlockState state = states.get(pos);
        return state != null ? state : Blocks.AIR.getDefaultState();
    }

    @Override
    public boolean isAirBlock(@Nonnull BlockPos pos) {
        IBlockState state = states.get(pos);
        return state == null || state.getBlock() == Blocks.AIR;
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public Biome getBiome(@Nonnull BlockPos pos) {
        return Biomes.PLAINS;
    }

    @Override
    public int getStrongPower(@Nonnull BlockPos pos, @Nonnull EnumFacing direction) {
        return 0;
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public WorldType getWorldType() {
        return worldType;
    }

    @Override
    public boolean isSideSolid(@Nonnull BlockPos pos, @Nonnull EnumFacing side, boolean _default) {
        if (!hasBlockAt(pos)) return _default;

        return getBlockState(pos).isSideSolid(this, pos, side);
    }
}

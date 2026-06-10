// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.client.render;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Translates local render coordinates to a backing preview world position.
 */
@SideOnly(Side.CLIENT)
public class OffsetBlockAccess implements IBlockAccess {

    private final IBlockAccess delegate;
    private final BlockPos offset;

    public OffsetBlockAccess(IBlockAccess delegate, BlockPos offset) {
        this.delegate = delegate;
        this.offset = offset;
    }

    private BlockPos resolve(BlockPos pos) {
        return pos.add(offset);
    }

    @Nullable
    @Override
    public TileEntity getTileEntity(@Nonnull BlockPos pos) {
        return delegate.getTileEntity(resolve(pos));
    }

    @Override
    public int getCombinedLight(@Nonnull BlockPos pos, int lightValue) {
        return delegate.getCombinedLight(resolve(pos), lightValue);
    }

    @Nonnull
    @Override
    public IBlockState getBlockState(@Nonnull BlockPos pos) {
        return delegate.getBlockState(resolve(pos));
    }

    @Override
    public boolean isAirBlock(@Nonnull BlockPos pos) {
        return delegate.isAirBlock(resolve(pos));
    }

    @Nonnull
    @Override
    public Biome getBiome(@Nonnull BlockPos pos) {
        return delegate.getBiome(resolve(pos));
    }

    @Override
    public int getStrongPower(@Nonnull BlockPos pos, @Nonnull EnumFacing direction) {
        return delegate.getStrongPower(resolve(pos), direction);
    }

    @Nonnull
    @Override
    public WorldType getWorldType() {
        return delegate.getWorldType();
    }

    @Override
    public boolean isSideSolid(@Nonnull BlockPos pos, @Nonnull EnumFacing side, boolean defaultValue) {
        return delegate.isSideSolid(resolve(pos), side, defaultValue);
    }
}
// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.client.render;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Resolves actual and extended states against the preview access before rendering.
 */
@SideOnly(Side.CLIENT)
public final class PreviewRenderStateResolver {

    private PreviewRenderStateResolver() {
    }

    @Nullable
    @SuppressWarnings("deprecation")
    public static IBlockState resolveActual(@Nullable IBlockState state, IBlockAccess access, BlockPos pos) {
        if (state == null || state.getBlock() == Blocks.AIR) return state;

        return state.getBlock().getActualState(state, access, pos);
    }

    @Nullable
    public static IBlockState resolve(@Nullable IBlockState state, IBlockAccess access, BlockPos pos) {
        IBlockState actualState = resolveActual(state, access, pos);
        if (actualState == null || actualState.getBlock() == Blocks.AIR) return actualState;

        return actualState.getBlock().getExtendedState(actualState, access, pos);
    }
}
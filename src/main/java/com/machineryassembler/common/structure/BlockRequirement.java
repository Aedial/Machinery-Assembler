// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/common/util/BlockArray.java (BlockInformation) from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.common.structure;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.client.render.BlockStateRenderValidator;
import com.machineryassembler.common.util.nbt.NBTMatchingHelper;


/**
 * Represents the block requirement at a single position in a structure.
 * Contains one or more valid BlockStateMatchers and optional NBT matching.
 */
public class BlockRequirement {

    public static final int CYCLE_TICK_SPEED = 30;

    private List<BlockStateMatcher> matchingStates = new ObjectArrayList<>();
    private List<IBlockState> samples = new ObjectArrayList<>();

    private boolean hasTileEntity;

    private NBTTagCompound matchingTag = null;
    private NBTTagCompound previewTag = null;

    public BlockRequirement(List<BlockStateMatcher> matching) {
        this.matchingStates.addAll(matching);

        for (BlockStateMatcher desc : matchingStates) {
            samples.addAll(desc.getApplicable());
            if (!hasTileEntity) hasTileEntity = desc.hasTileEntity();
        }
    }

    public void addMatchingStates(List<BlockStateMatcher> matching) {
        for (BlockStateMatcher desc : matching) {
            if (!matchingStates.contains(desc)) matchingStates.add(desc);

            for (IBlockState state : desc.getApplicable()) {
                if (!samples.contains(state)) samples.add(state);
            }

            if (!hasTileEntity) hasTileEntity = desc.hasTileEntity();
        }
    }

    public boolean hasTileEntity() {
        return hasTileEntity;
    }

    public NBTTagCompound getMatchingTag() {
        return matchingTag;
    }

    public void setMatchingTag(@Nullable NBTTagCompound matchingTag) {
        this.matchingTag = matchingTag;
    }

    public NBTTagCompound getPreviewTag() {
        return previewTag;
    }

    public void setPreviewTag(NBTTagCompound previewTag) {
        this.previewTag = previewTag;
    }

    /**
     * Get a sample state for this requirement.
     * On the server side, returns the first sample. On the client side, cycles through samples.
     */
    public IBlockState getSampleState() {
        // On server side, just return the first sample
        if (samples.isEmpty()) return Blocks.AIR.getDefaultState();

        return samples.get(0);
    }

    @SideOnly(Side.CLIENT)
    public IBlockState getSampleState(long snapTick) {
        if (samples.isEmpty()) return Blocks.AIR.getDefaultState();

        int p = (int) ((snapTick == -1 ? getClientTick() : snapTick) / CYCLE_TICK_SPEED);
        int part = p % samples.size();

        return samples.get(part);
    }

    @SideOnly(Side.CLIENT)
    private static long getClientTick() {
        return Minecraft.getMinecraft().world != null
            ? Minecraft.getMinecraft().world.getTotalWorldTime()
            : 0;
    }

    @SideOnly(Side.CLIENT)
    public ItemStack getDescriptiveStack(long snapTick) {
        return getStackFromBlockState(getSampleState(snapTick));
    }

    /**
     * Get the list of ingredient stacks for this requirement.
     * Uses default state filtering (no render validation).
     */
    public List<ItemStack> getIngredientList() {
        return getIngredientList(false);
    }

    /**
     * Get the list of ingredient stacks for this requirement.
     *
     * @param validateRendering If true and called on client side, excludes states that
     *                          have missing models/textures. Use false for server-side calls.
     */
    public List<ItemStack> getIngredientList(boolean validateRendering) {
        List<ItemStack> list = new ArrayList<>();

        for (IBlockState state : samples) {
            // Skip states with missing models if validation is requested
            if (validateRendering && !canStateRender(state)) continue;

            ItemStack stack = getStackFromBlockState(state);
            if (stack.isEmpty()) continue;

            boolean found = false;
            for (ItemStack existing : list) {
                if (stacksMatch(existing, stack)) {
                    found = true;
                    break;
                }
            }

            if (!found) list.add(stack);
        }

        return list;
    }

    /**
     * Check if a state can render properly. Only valid on client side.
     *
     * For blocks that convert to different items (like fluids -> buckets),
     * also checks if the item stack representation can render.
     *
     * This method must only be called on the client side, as it references
     * the @SideOnly(Side.CLIENT) BlockStateRenderValidator class. The JVM
     * will only load that class when this method is actually invoked, so
     * server-side code is safe as long as it never calls this method.
     */
    @SideOnly(Side.CLIENT)
    private boolean canStateRender(IBlockState state) {
        // First check if the block state itself can render
        if (BlockStateRenderValidator.canRender(state)) return true;

        // Block can't render directly (e.g., fluids), but check if the item representation can
        ItemStack stack = getStackFromBlockState(state);
        if (!stack.isEmpty()) return BlockStateRenderValidator.canRenderItem(stack);

        return false;
    }

    public BlockRequirement copyRotateYCCW() {
        List<BlockStateMatcher> newDescList = new ObjectArrayList<>();

        AtomicBoolean hasBlockRotated = new AtomicBoolean(false);
        for (BlockStateMatcher desc : this.matchingStates) {
            newDescList.add(desc.copyRotateYCCW(hasBlockRotated));
        }

        BlockRequirement bi;
        if (!hasBlockRotated.get()) {
            bi = new BlockRequirement(Collections.emptyList());
            bi.matchingStates = this.matchingStates;
            bi.samples = this.samples;
            bi.hasTileEntity = this.hasTileEntity;
        } else {
            bi = new BlockRequirement(newDescList);
        }
        bi.matchingTag = this.matchingTag;
        bi.previewTag = this.previewTag;

        return bi;
    }

    public BlockRequirement copyRotateYCW() {
        List<BlockStateMatcher> newDescList = new ObjectArrayList<>();

        AtomicBoolean hasBlockRotated = new AtomicBoolean(false);
        for (BlockStateMatcher desc : this.matchingStates) {
            newDescList.add(desc.copyRotateYCW(hasBlockRotated));
        }

        BlockRequirement bi;
        if (!hasBlockRotated.get()) {
            bi = new BlockRequirement(Collections.emptyList());
            bi.matchingStates = this.matchingStates;
            bi.samples = this.samples;
            bi.hasTileEntity = this.hasTileEntity;
        } else {
            bi = new BlockRequirement(newDescList);
        }
        bi.matchingTag = this.matchingTag;
        bi.previewTag = this.previewTag;

        return bi;
    }

    public BlockRequirement copy() {
        List<BlockStateMatcher> newDescList = new ObjectArrayList<>(this.matchingStates.size());
        for (BlockStateMatcher desc : this.matchingStates) newDescList.add(desc.copy());

        BlockRequirement bi = new BlockRequirement(newDescList);
        bi.matchingTag = this.matchingTag;
        bi.previewTag = this.previewTag;

        return bi;
    }

    public boolean matchesState(World world, BlockPos at, IBlockState state) {
        Block atBlock = state.getBlock();
        int atMeta = atBlock.getMetaFromState(state);

        for (BlockStateMatcher descriptor : matchingStates) {
            for (IBlockState applicable : descriptor.getApplicable()) {
                Block type = applicable.getBlock();
                int meta = type.getMetaFromState(applicable);

                if (!type.equals(atBlock) || meta != atMeta) continue;

                if (matchingTag != null) {
                    TileEntity te = world.getTileEntity(at);
                    if (te != null && matchingTag.getSize() > 0) {
                        NBTTagCompound cmp = new NBTTagCompound();
                        te.writeToNBT(cmp);

                        return NBTMatchingHelper.matchNBTCompound(matchingTag, cmp);
                    }
                }

                return true;
            }
        }

        return false;
    }

    public boolean matches(World world, BlockPos at, boolean default_) {
        if (!world.isBlockLoaded(at)) return default_;

        IBlockState state = world.getBlockState(at);
        return matchesState(world, at, state);
    }

    public List<BlockStateMatcher> getMatchingStates() {
        return matchingStates;
    }

    public List<IBlockState> getSamples() {
        return samples;
    }

    /**
     * Converts a block state to its corresponding ItemStack for display.
     * Uses a generalized approach that works for most blocks including fluids.
     */
    private static ItemStack getStackFromBlockState(IBlockState state) {
        Block block = state.getBlock();

        // Handle vanilla fluid blocks (water, lava) - FluidRegistry.lookupFluidForBlock doesn't work for these
        if (block == Blocks.WATER || block == Blocks.FLOWING_WATER) {
            Fluid water = FluidRegistry.WATER;
            if (water != null) {
                FluidStack fluidStack = new FluidStack(water, Fluid.BUCKET_VOLUME);
                ItemStack bucket = FluidUtil.getFilledBucket(fluidStack);
                if (!bucket.isEmpty()) return bucket;
            }
        }

        if (block == Blocks.LAVA || block == Blocks.FLOWING_LAVA) {
            Fluid lava = FluidRegistry.LAVA;
            if (lava != null) {
                FluidStack fluidStack = new FluidStack(lava, Fluid.BUCKET_VOLUME);
                ItemStack bucket = FluidUtil.getFilledBucket(fluidStack);
                if (!bucket.isEmpty()) return bucket;
            }
        }

        // Handle Forge fluid blocks (modded fluids)
        if (block instanceof IFluidBlock) {
            Fluid fluid = ((IFluidBlock) block).getFluid();
            if (fluid != null) {
                FluidStack fluidStack = new FluidStack(fluid, Fluid.BUCKET_VOLUME);
                ItemStack bucket = FluidUtil.getFilledBucket(fluidStack);
                if (!bucket.isEmpty()) return bucket;
            }
        }

        // Try getPickBlock for general block-to-item conversion
        // This handles special cases like tripwire -> string, crops -> seeds, etc.
        try {
            ItemStack pickStack = block.getPickBlock(
                state,
                new RayTraceResult(RayTraceResult.Type.BLOCK, Vec3d.ZERO, EnumFacing.UP, BlockPos.ORIGIN),
                null,  // World - some blocks don't need it
                BlockPos.ORIGIN,
                null   // Player - some blocks don't need it
            );

            if (!pickStack.isEmpty()) return pickStack;
        } catch (Exception ignored) {
            // getPickBlock can throw if it needs world/player
        }

        // Fallback: standard block-to-item conversion
        Item item = Item.getItemFromBlock(block);
        if (item == null) return ItemStack.EMPTY;

        int meta = block.getMetaFromState(state);

        return new ItemStack(item, 1, meta);
    }

    private static boolean stacksMatch(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem()) return false;

        return a.getMetadata() == b.getMetadata();
    }
}

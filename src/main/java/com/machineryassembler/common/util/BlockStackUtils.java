// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.util;

import java.util.Random;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.common.util.Constants;


/**
 * Shared block-state to ItemStack conversion used by previews and autobuild.
 */
public final class BlockStackUtils {

    private static final Random DROP_RANDOM = new Random(0L);

    private BlockStackUtils() {
    }

    public static ItemStack getStackFromBlockState(IBlockState state) {
        return getStackFromBlockState(state, null);
    }

    public static ItemStack getStackFromBlockState(IBlockState state, @Nullable NBTTagCompound previewTag) {
        if (state == null || state.getBlock() == Blocks.AIR) return ItemStack.EMPTY;

        Block block = state.getBlock();

        ItemStack specialCaseStack = getSpecialCaseStack(block, previewTag);
        if (!specialCaseStack.isEmpty()) return specialCaseStack;

        ItemStack fluidStack = getFluidStack(block, state);
        if (!fluidStack.isEmpty()) return fluidStack;

        Item item = block.getItemDropped(state, DROP_RANDOM, 0);
        if (item == null || item == Items.AIR) item = Item.getItemFromBlock(block);
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(item, 1, block.damageDropped(state));
        return applyPreviewData(block, stack, previewTag);
    }

    public static ItemStack applyPreviewData(Block block, ItemStack stack, @Nullable NBTTagCompound previewTag) {
        if (stack.isEmpty() || previewTag == null || previewTag.isEmpty()) return stack;

        if (block == Blocks.BED && stack.getItem() == Items.BED && previewTag.hasKey("color", 99)) {
            stack.setItemDamage(previewTag.getInteger("color"));
            return stack;
        }

        if (block == Blocks.SKULL && stack.getItem() == Items.SKULL) {
            if (previewTag.hasKey("SkullType", 99)) stack.setItemDamage(previewTag.getInteger("SkullType"));

            if (previewTag.hasKey("SkullOwner")) {
                NBTTagCompound stackTag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
                stackTag.setTag("SkullOwner", previewTag.getTag("SkullOwner").copy());
                stack.setTagCompound(stackTag);
            }
        }

        return stack;
    }

    private static ItemStack getSpecialCaseStack(Block block, @Nullable NBTTagCompound previewTag) {
        if (block == Blocks.BED) {
            int color = previewTag != null && previewTag.hasKey("color", Constants.NBT.TAG_ANY_NUMERIC)
                ? previewTag.getInteger("color")
                : 0;
            return new ItemStack(Items.BED, 1, color);
        }

        if (block == Blocks.SKULL) {
            int skullType = previewTag != null && previewTag.hasKey("SkullType", Constants.NBT.TAG_ANY_NUMERIC)
                ? previewTag.getInteger("SkullType")
                : 0;

            ItemStack stack = new ItemStack(Items.SKULL, 1, skullType);
            return applyPreviewData(block, stack, previewTag);
        }

        // TODO: May need to add more special cases

        return ItemStack.EMPTY;
    }

    private static ItemStack getFluidStack(Block block, IBlockState state) {
        if (block == Blocks.WATER || block == Blocks.FLOWING_WATER) {
            return getFilledBucket(FluidRegistry.WATER);
        }

        if (block == Blocks.LAVA || block == Blocks.FLOWING_LAVA) {
            return getFilledBucket(FluidRegistry.LAVA);
        }

        if (!(block instanceof IFluidBlock)) return ItemStack.EMPTY;

        Fluid fluid = ((IFluidBlock) block).getFluid();
        if (fluid == null) return ItemStack.EMPTY;

        ItemStack bucket = getFilledBucket(fluid);
        if (!bucket.isEmpty()) return bucket;

        if (block instanceof BlockLiquid && state.getMaterial().isLiquid()) {
            return new ItemStack(Item.getItemFromBlock(block), 1, block.damageDropped(state));
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack getFilledBucket(@Nullable Fluid fluid) {
        if (fluid == null) return ItemStack.EMPTY;

        ItemStack bucket = FluidUtil.getFilledBucket(new FluidStack(fluid, Fluid.BUCKET_VOLUME));
        return bucket.isEmpty() ? ItemStack.EMPTY : bucket;
    }
}
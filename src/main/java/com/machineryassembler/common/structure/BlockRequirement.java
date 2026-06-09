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
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.client.render.BlockStateRenderValidator;
import com.machineryassembler.client.render.PreviewWorld;
import com.machineryassembler.common.util.BlockStackUtils;
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

    public void applyPreviewTag(@Nullable TileEntity tileEntity) {
        if (tileEntity == null || previewTag == null || previewTag.isEmpty()) return;

        NBTTagCompound nbt = new NBTTagCompound();
        tileEntity.writeToNBT(nbt);

        for (String key : previewTag.getKeySet()) nbt.setTag(key, previewTag.getTag(key));

        tileEntity.readFromNBT(nbt);
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
        return getStackFromBlockState(getSampleState(snapTick), previewTag);
    }

    public ItemStack getRequiredStack() {
        return BlockStackUtils.getStackFromBlockState(getSampleState(), previewTag);
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

        for (List<ItemStack> ingredientGroup : getIngredientGroups(validateRendering)) {
            for (ItemStack stack : ingredientGroup) {
                if (stack.isEmpty() || containsMatchingStack(list, stack)) continue;

                list.add(stack);
            }
        }

        return list;
    }

    /**
     * Get the ingredient groups for this requirement.
     * Each inner list represents alternatives for a single required item slot.
     */
    public List<List<ItemStack>> getIngredientGroups(boolean validateRendering) {
        List<ItemStack> multipartStacks = getMultipartIngredientStacks(validateRendering);
        if (!multipartStacks.isEmpty()) {
            List<List<ItemStack>> ingredientGroups = new ArrayList<>();
            for (ItemStack stack : multipartStacks) ingredientGroups.add(Collections.singletonList(stack));

            return ingredientGroups;
        }

        List<ItemStack> list = new ArrayList<>();

        for (IBlockState state : samples) {
            // Skip states with missing models if validation is requested
            if (validateRendering && !canStateRender(state)) continue;

            ItemStack stack = getStackFromBlockState(state, previewTag);
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

        if (list.isEmpty()) return Collections.emptyList();

        List<List<ItemStack>> ingredientGroups = new ArrayList<>();
        ingredientGroups.add(list);
        return ingredientGroups;
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
        ItemStack stack = getStackFromBlockState(state, previewTag);
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
    private static ItemStack getStackFromBlockState(IBlockState state, @Nullable NBTTagCompound previewTag) {
        Block block = state.getBlock();

        // Try getPickBlock with proper world context for general block-to-item conversion
        // This handles special cases like tripwire -> string, crops -> seeds, skulls with types, etc.
        try {
            // Use the client world if available - some blocks need world context for getPickBlock
            World world = null;
            if (FMLCommonHandler.instance().getSide().isClient()) {
                world = createPreviewPickWorld(state, previewTag);
                if (world == null) world = Minecraft.getMinecraft().world;
            }

            ItemStack pickStack = block.getPickBlock(
                state,
                new RayTraceResult(RayTraceResult.Type.BLOCK, new Vec3d(0.5, 0.5, 0.5), EnumFacing.UP, BlockPos.ORIGIN),
                world,
                BlockPos.ORIGIN,
                null   // Player - some blocks don't need it
            );

            if (!pickStack.isEmpty()) return BlockStackUtils.applyPreviewData(block, pickStack, previewTag);
        } catch (Exception ignored) {
            // getPickBlock can throw if it needs specific world/player context
        }

        return BlockStackUtils.getStackFromBlockState(state, previewTag);
    }

    private List<ItemStack> getMultipartIngredientStacks(boolean validateRendering) {
        if (previewTag == null || previewTag.isEmpty()) return Collections.emptyList();

        List<ItemStack> multipartStacks = new ArrayList<>();

        for (String key : previewTag.getKeySet()) {
            if (!key.startsWith("def:")) continue;

            NBTTagCompound partTag = previewTag.getCompoundTag(key);
            if (partTag == null || partTag.isEmpty()) continue;

            ItemStack partStack = new ItemStack(partTag);
            if (partStack.isEmpty()) continue;
            if (validateRendering && !canStackRender(partStack)) continue;
            if (containsMatchingStack(multipartStacks, partStack)) continue;

            multipartStacks.add(partStack);
        }

        return multipartStacks;
    }

    @SideOnly(Side.CLIENT)
    private boolean canStackRender(ItemStack stack) {
        return BlockStateRenderValidator.canRenderItem(stack);
    }

    @Nullable
    @SideOnly(Side.CLIENT)
    private static World createPreviewPickWorld(IBlockState state, @Nullable NBTTagCompound previewTag) {
        if (previewTag == null || previewTag.isEmpty() || !state.getBlock().hasTileEntity(state)) return null;

        try {
            PreviewWorld previewWorld = PreviewWorld.create();
            TileEntity tileEntity = state.getBlock().createTileEntity(previewWorld, state);
            if (tileEntity == null) return null;

            tileEntity.setWorld(previewWorld);
            tileEntity.setPos(BlockPos.ORIGIN);
            applyPreviewTag(tileEntity, previewTag);
            previewWorld.setPreviewState(BlockPos.ORIGIN, state, tileEntity);
            return previewWorld;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void applyPreviewTag(TileEntity tileEntity, NBTTagCompound previewTag) {
        NBTTagCompound nbt = new NBTTagCompound();
        tileEntity.writeToNBT(nbt);

        for (String key : previewTag.getKeySet()) nbt.setTag(key, previewTag.getTag(key));

        tileEntity.readFromNBT(nbt);
    }

    private static boolean stacksMatch(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem()) return false;

        return a.getMetadata() == b.getMetadata();
    }

    private static boolean containsMatchingStack(List<ItemStack> stacks, ItemStack target) {
        for (ItemStack existing : stacks) {
            if (stacksMatch(existing, target)) return true;
        }

        return false;
    }
}

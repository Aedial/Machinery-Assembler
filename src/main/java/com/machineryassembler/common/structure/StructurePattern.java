// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/common/util/BlockArray.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.common.structure;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.common.util.BlockPos2ValueMap;
import com.machineryassembler.common.util.MiscUtils;


/**
 * Represents the complete spatial pattern of a multiblock structure.
 * Maps positions to their block requirements and handles matching, rotation, and serialization.
 */
public class StructurePattern {

    protected Map<BlockPos, BlockRequirement> pattern = new BlockPos2ValueMap<>();
    private BlockPos min = new BlockPos(0, 0, 0);
    private BlockPos max = new BlockPos(0, 0, 0);
    private BlockPos size = new BlockPos(0, 0, 0);

    public StructurePattern() {
    }

    public StructurePattern(StructurePattern other) {
        this.pattern = new BlockPos2ValueMap<>(other.pattern);
        this.min = new BlockPos(other.min.getX(), other.min.getY(), other.min.getZ());
        this.max = new BlockPos(other.max.getX(), other.max.getY(), other.max.getZ());
        this.size = new BlockPos(other.size.getX(), other.size.getY(), other.size.getZ());
    }

    public StructurePattern(StructurePattern other, BlockPos offset) {
        for (Map.Entry<BlockPos, BlockRequirement> otherEntry : other.pattern.entrySet()) {
            this.pattern.put(otherEntry.getKey().add(offset), otherEntry.getValue());
        }

        this.min = new BlockPos(
            offset.getX() + other.min.getX(),
            offset.getY() + other.min.getY(),
            offset.getZ() + other.min.getZ());
        this.max = new BlockPos(
            offset.getX() + other.max.getX(),
            offset.getY() + other.max.getY(),
            offset.getZ() + other.max.getZ());
        this.size = new BlockPos(other.size.getX(), other.size.getY(), other.size.getZ());
    }

    public void overwrite(StructurePattern other) {
        this.pattern = new BlockPos2ValueMap<>(other.pattern);
        this.min = new BlockPos(other.min.getX(), other.min.getY(), other.min.getZ());
        this.max = new BlockPos(other.max.getX(), other.max.getY(), other.max.getZ());
        this.size = new BlockPos(other.size.getX(), other.size.getY(), other.size.getZ());
    }

    public void addBlock(int x, int y, int z, @Nonnull BlockRequirement info) {
        addBlock(new BlockPos(x, y, z), info);
    }

    public void addBlock(BlockPos offset, @Nonnull BlockRequirement info) {
        pattern.put(offset, info);
        updateSize(offset);
    }

    public boolean hasBlockAt(BlockPos pos) {
        return pattern.containsKey(pos);
    }

    public boolean isEmpty() {
        return pattern.isEmpty();
    }

    public BlockPos getMax() {
        return max;
    }

    public BlockPos getMin() {
        return min;
    }

    public BlockPos getSize() {
        return size;
    }

    private void updateSize(BlockPos addedPos) {
        if (addedPos.getX() < min.getX()) min = new BlockPos(addedPos.getX(), min.getY(), min.getZ());
        if (addedPos.getX() > max.getX()) max = new BlockPos(addedPos.getX(), max.getY(), max.getZ());
        if (addedPos.getY() < min.getY()) min = new BlockPos(min.getX(), addedPos.getY(), min.getZ());
        if (addedPos.getY() > max.getY()) max = new BlockPos(max.getX(), addedPos.getY(), max.getZ());
        if (addedPos.getZ() < min.getZ()) min = new BlockPos(min.getX(), min.getY(), addedPos.getZ());
        if (addedPos.getZ() > max.getZ()) max = new BlockPos(max.getX(), max.getY(), addedPos.getZ());

        size = new BlockPos(
            max.getX() - min.getX() + 1,
            max.getY() - min.getY() + 1,
            max.getZ() - min.getZ() + 1);
    }

    public Map<BlockPos, BlockRequirement> getPattern() {
        return pattern;
    }

    public Map<BlockPos, BlockRequirement> getPatternSlice(int slice) {
        Map<BlockPos, BlockRequirement> copy = new BlockPos2ValueMap<>();

        for (BlockPos pos : pattern.keySet()) {
            if (pos.getY() == slice) copy.put(pos, pattern.get(pos));
        }

        return copy;
    }

    @SideOnly(Side.CLIENT)
    public List<ItemStack> getAsDescriptiveStacks(long snapSample) {
        List<ItemStack> out = new LinkedList<>();

        pattern.forEach((key, bi) -> {
            ItemStack s = bi.getDescriptiveStack(snapSample);
            if (s.isEmpty()) return;

            boolean found = false;
            for (ItemStack stack : out) {
                if (stack.getItem().getRegistryName().equals(s.getItem().getRegistryName()) &&
                    stack.getItemDamage() == s.getItemDamage()) {
                    stack.setCount(stack.getCount() + 1);
                    found = true;
                    break;
                }
            }

            if (!found) out.add(s);
        });

        return out;
    }

    /**
     * Get the list of ingredient lists for this pattern.
     * Uses default state filtering (no render validation).
     */
    public List<List<ItemStack>> getIngredientList() {
        return getIngredientList(false);
    }

    /**
     * Get the list of ingredient lists for this pattern.
     *
     * @param validateRendering If true and called on client side, excludes states that
     *                          have missing models/textures.
     */
    public List<List<ItemStack>> getIngredientList(boolean validateRendering) {
        List<List<ItemStack>> ingredient = new LinkedList<>();
        List<Integer> counts = new LinkedList<>();

        pattern.forEach((pos, info) -> {
            List<ItemStack> infoIngList = info.getIngredientList(validateRendering);
            if (infoIngList.isEmpty()) return;

            // Check if this exact ingredient list already exists (same items in same order)
            int index = 0;
            for (final List<ItemStack> existingList : ingredient) {
                if (ingredientListsMatch(infoIngList, existingList)) {
                    // Increment count for all items in the list
                    int count = counts.get(index);
                    counts.set(index, count + 1);

                    for (ItemStack stack : existingList) stack.setCount(count + 1);
                    return;
                }

                index++;
            }

            // New ingredient, add it with count 1
            List<ItemStack> copiedList = new LinkedList<>();
            for (ItemStack stack : infoIngList) {
                ItemStack copy = stack.copy();
                copy.setCount(1);
                copiedList.add(copy);
            }

            ingredient.add(copiedList);
            counts.add(1);
        });

        return ingredient;
    }

    /**
     * Check if two ingredient lists contain equivalent items (same oredict alternatives).
     */
    private static boolean ingredientListsMatch(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;

        for (int i = 0; i < a.size(); i++) {
            if (!matchStacks(a.get(i), b.get(i))) return false;
        }

        return true;
    }

    public boolean matches(World world, BlockPos center, boolean oldState) {
        for (Map.Entry<BlockPos, BlockRequirement> entry : pattern.entrySet()) {
            BlockPos at = center.add(entry.getKey());
            if (!entry.getValue().matches(world, at, oldState)) return false;
        }

        return true;
    }

    public BlockPos getRelativeMismatchPosition(World world, BlockPos center) {
        for (Map.Entry<BlockPos, BlockRequirement> entry : pattern.entrySet()) {
            BlockPos at = center.add(entry.getKey());
            if (!entry.getValue().matches(world, at, false)) return entry.getKey();
        }

        return null;
    }

    public StructurePattern rotateYCCW() {
        StructurePattern out = new StructurePattern();

        for (BlockPos pos : pattern.keySet()) {
            BlockRequirement info = pattern.get(pos);
            out.addBlock(MiscUtils.rotateYCCW(pos), info.copyRotateYCCW());
        }

        return out;
    }

    public StructurePattern rotateYCW() {
        StructurePattern out = new StructurePattern();

        for (BlockPos pos : pattern.keySet()) {
            BlockRequirement info = pattern.get(pos);
            out.addBlock(MiscUtils.rotateYCW(pos), info.copyRotateYCW());
        }

        return out;
    }

    /**
     * Serializes this pattern to JSON format.
     * 
     * @param id The structure ID for the output
     * @return JSON string in the new format
     */
    public String serializeAsJson(String id) {
        String newline = System.getProperty("line.separator");
        String indent = "  ";

        // Build reverse mapping: BlockRequirement -> character
        Map<String, Character> blockToChar = new LinkedHashMap<>();
        char nextChar = 'a';

        for (BlockRequirement req : pattern.values()) {
            String key = getBlockKey(req);
            if (!blockToChar.containsKey(key)) {
                blockToChar.put(key, nextChar++);
                if (nextChar > 'z') nextChar = 'A';
            }
        }

        // Build inputs section
        StringBuilder sb = new StringBuilder();
        sb.append("{").append(newline);
        sb.append(indent).append("\"id\": \"").append(id).append("\",").append(newline);
        sb.append(indent).append("\"inputs\": {").append(newline);

        Iterator<Map.Entry<String, Character>> inputIter = blockToChar.entrySet().iterator();
        while (inputIter.hasNext()) {
            Map.Entry<String, Character> entry = inputIter.next();
            sb.append(indent).append(indent).append("\"").append(entry.getValue()).append("\": \"").append(entry.getKey()).append("\"");

            if (inputIter.hasNext()) sb.append(",");
            sb.append(newline);
        }

        sb.append(indent).append("},").append(newline);

        // Build shape section
        // Iterate Y from top to bottom so first layer in array = top of structure
        sb.append(indent).append("\"shape\": [").append(newline);

        for (int y = max.getY(); y >= min.getY(); y--) {
            sb.append(indent).append(indent).append("[").append(newline);

            for (int z = min.getZ(); z <= max.getZ(); z++) {
                StringBuilder row = new StringBuilder();

                for (int x = min.getX(); x <= max.getX(); x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockRequirement req = pattern.get(pos);

                    if (req == null) {
                        row.append("_");
                    } else {
                        String key = getBlockKey(req);
                        row.append(blockToChar.get(key));
                    }
                }

                sb.append(indent).append(indent).append(indent).append("\"").append(row).append("\"");

                if (z < max.getZ()) sb.append(",");
                sb.append(newline);
            }

            sb.append(indent).append(indent).append("]");

            if (y < max.getY()) sb.append(",");
            sb.append(newline);
        }

        sb.append(indent).append("]").append(newline);
        sb.append("}");

        return sb.toString();
    }

    private String getBlockKey(BlockRequirement req) {
        List<IBlockState> samples = req.getSamples();
        if (samples.isEmpty()) return "minecraft:air";

        IBlockState state = samples.get(0);
        int meta = state.getBlock().getMetaFromState(state);
        String id = state.getBlock().getRegistryName().toString();

        if (meta == 0) return id;

        return id + "@" + meta;
    }

    private static boolean matchStacks(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem()) return false;

        return a.getMetadata() == b.getMetadata();
    }
}

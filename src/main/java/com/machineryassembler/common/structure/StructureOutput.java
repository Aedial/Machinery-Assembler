// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.structure;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;


/**
 * Represents an output item for a structure definition.
 * Stored as raw data to allow lazy ItemStack creation (items may not be registered during JSON parsing).
 */
public class StructureOutput {

    private final String itemId;
    private final int meta;
    private final int count;
    @Nullable
    private final NBTTagCompound nbt;

    // Cached ItemStack, created lazily
    @Nullable
    private ItemStack cachedStack = null;
    private boolean cacheAttempted = false;

    public StructureOutput(String itemId, int meta, int count, @Nullable NBTTagCompound nbt) {
        this.itemId = itemId;
        this.meta = meta;
        this.count = count;
        this.nbt = nbt;
    }

    public String getItemId() {
        return itemId;
    }

    public int getMeta() {
        return meta;
    }

    public int getCount() {
        return count;
    }

    @Nullable
    public NBTTagCompound getNbt() {
        return nbt;
    }

    /**
     * Gets the ItemStack for this output.
     * Creates and caches the stack on first call.
     * 
     * @return The ItemStack, or ItemStack.EMPTY if the item doesn't exist
     */
    public ItemStack getItemStack() {
        if (!cacheAttempted) {
            cacheAttempted = true;
            cachedStack = createItemStack();
        }

        return cachedStack != null ? cachedStack : ItemStack.EMPTY;
    }

    /**
     * Checks if this output has a valid item.
     */
    public boolean isValid() {
        return !getItemStack().isEmpty();
    }

    @Nullable
    private ItemStack createItemStack() {
        ResourceLocation res = new ResourceLocation(itemId);
        Item item = ForgeRegistries.ITEMS.getValue(res);
        if (item == null) return null;

        ItemStack stack = new ItemStack(item, count, meta);
        if (nbt != null) stack.setTagCompound(nbt.copy());

        return stack;
    }

    /**
     * Parses an item descriptor string.
     * Format: "modid:itemid" or "modid:itemid@meta" or "modid:itemid*count" or "modid:itemid@meta*count"
     * 
     * @param str The item descriptor string
     * @return A new StructureOutput instance
     */
    public static StructureOutput fromString(String str) {
        int meta = 0;
        int count = 1;

        // Extract count (after *)
        int countIndex = str.indexOf('*');
        if (countIndex != -1 && countIndex < str.length() - 1) {
            try {
                count = Integer.parseInt(str.substring(countIndex + 1));
            } catch (NumberFormatException e) {
                count = 1;
            }

            str = str.substring(0, countIndex);
        }

        // Extract meta (after @)
        int metaIndex = str.indexOf('@');
        if (metaIndex != -1 && metaIndex < str.length() - 1) {
            try {
                meta = Integer.parseInt(str.substring(metaIndex + 1));
            } catch (NumberFormatException e) {
                meta = 0;
            }

            str = str.substring(0, metaIndex);
        }

        return new StructureOutput(str, meta, count, null);
    }
}

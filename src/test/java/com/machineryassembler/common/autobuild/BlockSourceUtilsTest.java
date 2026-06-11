// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;


class BlockSourceUtilsTest {

    @Test
    void matchesRequiredStackAllowsExtraNbtOnAvailableStack() {
        Item item = new Item();

        NBTTagCompound requiredTag = new NBTTagCompound();
        NBTTagCompound requiredInventory = new NBTTagCompound();
        requiredInventory.setString("slot0", "diamond");
        requiredTag.setTag("inventory", requiredInventory);
        requiredTag.setString("owner", "Alice");

        NBTTagCompound availableTag = requiredTag.copy();
        availableTag.setInteger("energy", 4000);
        availableTag.getCompoundTag("inventory").setString("slot1", "emerald");

        Assertions.assertTrue(BlockSourceUtils.matchesRequiredComponents(item, 3, availableTag, item, 3, requiredTag));
    }

    @Test
    void matchesRequiredStackRejectsMissingOrConflictingNbt() {
        Item item = new Item();

        NBTTagCompound requiredTag = new NBTTagCompound();
        requiredTag.setString("owner", "Alice");

        NBTTagCompound conflictingTag = new NBTTagCompound();
        conflictingTag.setString("owner", "Bob");

        Assertions.assertFalse(BlockSourceUtils.matchesRequiredComponents(item, 1, null, item, 1, requiredTag));
        Assertions.assertFalse(BlockSourceUtils.matchesRequiredComponents(item, 1, conflictingTag, item, 1, requiredTag));
    }

    @Test
    void matchesRequiredStackStillRequiresSameItemAndMetadata() {
        Item requiredItem = new Item();
        Item otherItem = new Item();

        Assertions.assertFalse(BlockSourceUtils.matchesRequiredComponents(otherItem, 2, null, requiredItem, 2, null));
        Assertions.assertFalse(BlockSourceUtils.matchesRequiredComponents(requiredItem, 4, null, requiredItem, 2, null));
    }
}
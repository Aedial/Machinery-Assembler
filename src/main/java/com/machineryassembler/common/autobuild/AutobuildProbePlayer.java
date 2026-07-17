// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import com.mojang.authlib.GameProfile;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;


/**
 * Lightweight player surrogate for isolated autobuild probes.
 */
class AutobuildProbePlayer extends EntityPlayer {

    private final EntityPlayerMP sourcePlayer;

    AutobuildProbePlayer(World world, EntityPlayerMP sourcePlayer) {
        super(world, copyProfile(sourcePlayer));
        this.sourcePlayer = sourcePlayer;

        capabilities.allowEdit = sourcePlayer.capabilities.allowEdit;
        capabilities.isCreativeMode = sourcePlayer.capabilities.isCreativeMode;
        capabilities.allowFlying = sourcePlayer.capabilities.allowFlying;
        capabilities.disableDamage = sourcePlayer.capabilities.disableDamage;
        capabilities.isFlying = sourcePlayer.capabilities.isFlying;

        inventory.currentItem = sourcePlayer.inventory.currentItem;
        for (int slot = 0; slot < sourcePlayer.inventory.getSizeInventory(); slot++) {
            ItemStack sourceStack = sourcePlayer.inventory.getStackInSlot(slot);
            inventory.setInventorySlotContents(slot, sourceStack.isEmpty() ? ItemStack.EMPTY : sourceStack.copy());
        }

        setPositionAndRotation(0.0D, 0.0D, 0.0D, sourcePlayer.rotationYaw, sourcePlayer.rotationPitch);
        rotationYawHead = sourcePlayer.rotationYawHead;
        renderYawOffset = sourcePlayer.renderYawOffset;
    }

    private static GameProfile copyProfile(EntityPlayerMP sourcePlayer) {
        return sourcePlayer.getGameProfile();
    }

    @Override
    public boolean isSpectator() {
        return sourcePlayer.isSpectator();
    }

    @Override
    public boolean isCreative() {
        return sourcePlayer.isCreative();
    }

    @Override
    public EnumHandSide getPrimaryHand() {
        return sourcePlayer.getPrimaryHand();
    }

    @Override
    public Vec3d getPositionVector() {
        return new Vec3d(posX, posY, posZ);
    }
}
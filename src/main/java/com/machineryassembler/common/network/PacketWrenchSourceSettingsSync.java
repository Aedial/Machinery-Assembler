// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.machineryassembler.common.item.ItemAssemblerWrench;


/**
 * Client -> Server packet syncing wrench provider settings to the held item.
 */
public class PacketWrenchSourceSettingsSync implements IMessage {

    private EnumHand hand;
    private NBTTagCompound blockSourceSettings;

    public PacketWrenchSourceSettingsSync() {
    }

    public PacketWrenchSourceSettingsSync(EnumHand hand, NBTTagCompound blockSourceSettings) {
        this.hand = hand;
        this.blockSourceSettings = blockSourceSettings == null ? new NBTTagCompound() : blockSourceSettings.copy();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int handOrdinal = buf.readInt();

        if (handOrdinal < 0 || handOrdinal >= EnumHand.values().length) {
            hand = EnumHand.MAIN_HAND;
        } else {
            hand = EnumHand.values()[handOrdinal];
        }

        blockSourceSettings = ByteBufUtils.readTag(buf);

        if (blockSourceSettings == null) blockSourceSettings = new NBTTagCompound();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(hand.ordinal());
        ByteBufUtils.writeTag(buf, blockSourceSettings);
    }

    public EnumHand getHand() {
        return hand;
    }

    public NBTTagCompound getBlockSourceSettings() {
        return blockSourceSettings;
    }

    public static class Handler implements IMessageHandler<PacketWrenchSourceSettingsSync, IMessage> {
        @Override
        public IMessage onMessage(PacketWrenchSourceSettingsSync message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                ItemStack heldStack = player.getHeldItem(message.getHand());

                if (!(heldStack.getItem() instanceof ItemAssemblerWrench)) return;

                ItemAssemblerWrench.setBlockSourceSettingsTag(heldStack, message.getBlockSourceSettings());
                player.inventory.markDirty();
            });

            return null;
        }
    }
}
// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.network;

import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.client.autobuild.AutobuildHandler;


/**
 * Server -> Client packet indicating missing blocks for autobuild.
 * Contains a map of block registry name + meta -> missing count.
 */
public class PacketAutobuildMissingBlocks implements IMessage {

    private Map<String, Integer> missingBlocks;
    private boolean aborted;

    public PacketAutobuildMissingBlocks() {
        this.missingBlocks = new HashMap<>();
    }

    public PacketAutobuildMissingBlocks(Map<String, Integer> missingBlocks, boolean aborted) {
        this.missingBlocks = missingBlocks;
        this.aborted = aborted;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        aborted = buf.readBoolean();
        int count = buf.readInt();
        missingBlocks = new HashMap<>(count);

        for (int i = 0; i < count; i++) {
            String key = ByteBufUtils.readUTF8String(buf);
            int amount = buf.readInt();
            missingBlocks.put(key, amount);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(aborted);
        buf.writeInt(missingBlocks.size());

        for (Map.Entry<String, Integer> entry : missingBlocks.entrySet()) {
            ByteBufUtils.writeUTF8String(buf, entry.getKey());
            buf.writeInt(entry.getValue());
        }
    }

    public Map<String, Integer> getMissingBlocks() {
        return missingBlocks;
    }

    public boolean isAborted() {
        return aborted;
    }

    public static class Handler implements IMessageHandler<PacketAutobuildMissingBlocks, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketAutobuildMissingBlocks message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                AutobuildHandler.handleMissingBlocksResponse(message);
            });

            return null;
        }
    }
}

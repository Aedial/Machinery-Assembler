// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.client.autobuild.AutobuildHandler;


/**
 * Server -> Client packet indicating obstructions that prevent autobuild.
 * These blocks should be highlighted in red and the selection cleared.
 */
public class PacketAutobuildObstruction implements IMessage {

    private List<BlockPos> obstructedPositions;

    public PacketAutobuildObstruction() {
        this.obstructedPositions = new ArrayList<>();
    }

    public PacketAutobuildObstruction(List<BlockPos> obstructedPositions) {
        this.obstructedPositions = obstructedPositions;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        obstructedPositions = new ArrayList<>(count);

        for (int i = 0; i < count; i++) obstructedPositions.add(BlockPos.fromLong(buf.readLong()));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(obstructedPositions.size());

        for (BlockPos pos : obstructedPositions) buf.writeLong(pos.toLong());
    }

    public List<BlockPos> getObstructedPositions() {
        return obstructedPositions;
    }

    public static class Handler implements IMessageHandler<PacketAutobuildObstruction, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketAutobuildObstruction message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                AutobuildHandler.handleObstructionResponse(message);
            });

            return null;
        }
    }
}

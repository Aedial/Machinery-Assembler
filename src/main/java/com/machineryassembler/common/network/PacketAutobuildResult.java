// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.client.autobuild.AutobuildHandler;


/**
 * Server -> Client packet indicating autobuild result.
 */
public class PacketAutobuildResult implements IMessage {

    public enum ResultType {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILED
    }

    private ResultType resultType;
    private int blocksPlaced;
    private int blocksSkipped;
    private int blocksFailed;

    public PacketAutobuildResult() {
    }

    public PacketAutobuildResult(ResultType resultType, int blocksPlaced, int blocksSkipped, int blocksFailed) {
        this.resultType = resultType;
        this.blocksPlaced = blocksPlaced;
        this.blocksSkipped = blocksSkipped;
        this.blocksFailed = blocksFailed;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        resultType = ResultType.values()[buf.readByte()];
        blocksPlaced = buf.readInt();
        blocksSkipped = buf.readInt();
        blocksFailed = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(resultType.ordinal());
        buf.writeInt(blocksPlaced);
        buf.writeInt(blocksSkipped);
        buf.writeInt(blocksFailed);
    }

    public ResultType getResult() {
        return resultType;
    }

    public int getPlacedCount() {
        return blocksPlaced;
    }

    public int getSkippedCount() {
        return blocksSkipped;
    }

    public int getFailedCount() {
        return blocksFailed;
    }

    public static class Handler implements IMessageHandler<PacketAutobuildResult, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketAutobuildResult message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                AutobuildHandler.handleBuildResult(message);
            });

            return null;
        }
    }
}

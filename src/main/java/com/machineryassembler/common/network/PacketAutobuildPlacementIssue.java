// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.client.autobuild.AutobuildHandler;


/**
 * Server -> Client packet indicating placement issues during autobuild.
 * Used to report blocks that couldn't be placed due to external interference.
 */
public class PacketAutobuildPlacementIssue implements IMessage {

    public enum IssueType {
        /** External block was placed, but it's the wrong type */
        WRONG_BLOCK,
        /** External block was placed, and it happens to be correct */
        CORRECT_EXTERNAL,
        /** Block placement failed for unknown reason */
        PLACEMENT_FAILED
    }

    private List<PlacementIssue> issues;

    public PacketAutobuildPlacementIssue() {
        this.issues = new ArrayList<>();
    }

    public PacketAutobuildPlacementIssue(List<PlacementIssue> issues) {
        this.issues = issues;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        issues = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            IssueType type = IssueType.values()[buf.readByte()];
            BlockPos pos = BlockPos.fromLong(buf.readLong());
            String expectedBlock = ByteBufUtils.readUTF8String(buf);
            String actualBlock = ByteBufUtils.readUTF8String(buf);
            issues.add(new PlacementIssue(type, pos, expectedBlock, actualBlock));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(issues.size());

        for (PlacementIssue issue : issues) {
            buf.writeByte(issue.type.ordinal());
            buf.writeLong(issue.pos.toLong());
            ByteBufUtils.writeUTF8String(buf, issue.expectedBlock);
            ByteBufUtils.writeUTF8String(buf, issue.actualBlock);
        }
    }

    public List<PlacementIssue> getIssues() {
        return issues;
    }

    public static class PlacementIssue {
        private final IssueType type;
        private final BlockPos pos;
        private final String expectedBlock;
        private final String actualBlock;

        public PlacementIssue(IssueType type, BlockPos pos, String expectedBlock, String actualBlock) {
            this.type = type;
            this.pos = pos;
            this.expectedBlock = expectedBlock;
            this.actualBlock = actualBlock;
        }

        public IssueType getType() {
            return type;
        }

        public BlockPos getPos() {
            return pos;
        }

        public String getExpectedBlock() {
            return expectedBlock;
        }

        public String getActualBlock() {
            return actualBlock;
        }
    }

    public static class Handler implements IMessageHandler<PacketAutobuildPlacementIssue, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketAutobuildPlacementIssue message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                AutobuildHandler.handlePlacementIssues(message);
            });

            return null;
        }
    }
}

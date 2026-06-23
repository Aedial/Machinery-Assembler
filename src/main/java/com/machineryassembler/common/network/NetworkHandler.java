// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.Tags;


/**
 * Network handler for Machinery Assembler packets.
 */
public class NetworkHandler {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MODID);

    private static int packetId = 0;

    public static void init() {
        // Client -> Server
        INSTANCE.registerMessage(
            PacketAutobuildRequest.Handler.class,
            PacketAutobuildRequest.class,
            packetId++,
            Side.SERVER
        );

        INSTANCE.registerMessage(
            PacketWrenchSourceSettingsSync.Handler.class,
            PacketWrenchSourceSettingsSync.class,
            packetId++,
            Side.SERVER
        );

        INSTANCE.registerMessage(
            PacketRequestMultiblockRecordingCapture.Handler.class,
            PacketRequestMultiblockRecordingCapture.class,
            packetId++,
            Side.SERVER
        );

        INSTANCE.registerMessage(
            PacketRequestMultiblockRecordingSave.Handler.class,
            PacketRequestMultiblockRecordingSave.class,
            packetId++,
            Side.SERVER
        );

        INSTANCE.registerMessage(
            PacketClearMultiblockRecordingSession.Handler.class,
            PacketClearMultiblockRecordingSession.class,
            packetId++,
            Side.SERVER
        );

        // Server -> Client
        INSTANCE.registerMessage(
            PacketAutobuildObstruction.Handler.class,
            PacketAutobuildObstruction.class,
            packetId++,
            Side.CLIENT
        );

        INSTANCE.registerMessage(
            PacketAutobuildMissingBlocks.Handler.class,
            PacketAutobuildMissingBlocks.class,
            packetId++,
            Side.CLIENT
        );

        INSTANCE.registerMessage(
            PacketAutobuildResult.Handler.class,
            PacketAutobuildResult.class,
            packetId++,
            Side.CLIENT
        );

        INSTANCE.registerMessage(
            PacketAutobuildPlacementIssue.Handler.class,
            PacketAutobuildPlacementIssue.class,
            packetId++,
            Side.CLIENT
        );

        INSTANCE.registerMessage(
            PacketMultiblockRecordingCapture.Handler.class,
            PacketMultiblockRecordingCapture.class,
            packetId++,
            Side.CLIENT
        );

        MachineryAssembler.LOGGER.info("Registered {} network packets", packetId);
    }
}

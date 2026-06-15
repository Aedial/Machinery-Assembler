package com.machineryassembler.common.recording;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;


/**
 * Clears frozen recorder state when a player leaves the server.
 */
public class MultiblockRecordingSessionEvents {

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MultiblockRecordingService.clearFrozenCapture(event.player.getUniqueID());
    }
}
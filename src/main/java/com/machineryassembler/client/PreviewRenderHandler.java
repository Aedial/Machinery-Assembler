// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.client;

import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Handles render events for in-world structure preview.
 */
@SideOnly(Side.CLIENT)
public class PreviewRenderHandler {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        ClientProxy.previewRenderer.tick();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!ClientProxy.previewRenderer.hasActivePreview()) return;

        ClientProxy.previewRenderer.renderTranslucentBlocks();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) ClientProxy.previewRenderer.unloadWorld();
    }
}

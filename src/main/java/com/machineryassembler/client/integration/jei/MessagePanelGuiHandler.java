// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.client.integration.jei;

import java.awt.Rectangle;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import mezz.jei.api.gui.IGlobalGuiHandler;


/**
 * Global GUI handler that tells JEI about the message windows' screen-space positions.
 * This prevents JEI from drawing its ingredient list or bookmarks over the windows.
 */
@SideOnly(Side.CLIENT)
public class MessagePanelGuiHandler implements IGlobalGuiHandler {

    @Override
    public Collection<Rectangle> getGuiExtraAreas() {
        List<Rectangle> rects = StructurePreviewWrapper.getActiveMessagePanelRects();
        if (rects == null || rects.isEmpty()) return Collections.emptyList();

        return rects;
    }
}

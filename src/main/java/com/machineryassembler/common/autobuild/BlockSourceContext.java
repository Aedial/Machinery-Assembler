// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import net.minecraft.entity.player.EntityPlayer;


/**
 * Runtime context used by autobuild block providers.
 */
public class BlockSourceContext {

    private final EntityPlayer player;
    private final BlockSourceSettings settings;

    public BlockSourceContext(EntityPlayer player, BlockSourceSettings settings) {
        this.player = player;
        this.settings = settings;
    }

    public EntityPlayer getPlayer() {
        return player;
    }

    public BlockSourceSettings getSettings() {
        return settings;
    }
}
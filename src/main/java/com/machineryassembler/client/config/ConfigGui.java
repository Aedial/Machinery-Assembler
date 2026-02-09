// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.client.config;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.common.config.AutobuildConfig;


/**
 * In-game configuration GUI for Machinery Assembler.
 * Uses the traditional Configuration API for proper double value support.
 */
@SideOnly(Side.CLIENT)
public class ConfigGui extends GuiConfig {

    public ConfigGui(GuiScreen parentScreen) {
        super(
            parentScreen,
            getConfigElements(),
            MachineryAssembler.MODID,
            false,
            false,
            MachineryAssembler.NAME
        );
    }

    private static List<IConfigElement> getConfigElements() {
        List<IConfigElement> elements = new ArrayList<>();
        Configuration config = AutobuildConfig.getConfig();

        if (config != null) {
            elements.addAll(new ConfigElement(
                config.getCategory(AutobuildConfig.CATEGORY)).getChildElements());
        }

        return elements;
    }
}

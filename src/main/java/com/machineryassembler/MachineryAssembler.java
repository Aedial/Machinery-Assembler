// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
/*
 * Machinery Assembler
 *
 * A mod for displaying multiblock structures in JEI, utilizing MMCE's preview system.
 */

package com.machineryassembler;

import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import com.machineryassembler.common.CommonProxy;


/**
 * Machinery Assembler - A standalone mod for displaying multiblock structures in JEI.
 * Provides structure preview and building assistance without controller dependency.
 */
@Mod(
    modid = MachineryAssembler.MODID,
    name = MachineryAssembler.NAME,
    version = MachineryAssembler.VERSION,
    dependencies = "after:jei@[4.13.1.222,);",
    acceptedMinecraftVersions = "[1.12.2]",
    guiFactory = "com.machineryassembler.client.config.ConfigGuiFactory"
)
public class MachineryAssembler {

    public static final String MODID = "machineryassembler";
    public static final String NAME = "Machinery Assembler";
    public static final String VERSION = "0.2.1";
    public static final String CLIENT_PROXY = "com.machineryassembler.client.ClientProxy";
    public static final String COMMON_PROXY = "com.machineryassembler.common.CommonProxy";

    @Mod.Instance(MODID)
    public static MachineryAssembler instance;

    public static Logger LOGGER;

    @SidedProxy(clientSide = CLIENT_PROXY, serverSide = COMMON_PROXY)
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        CommonProxy.loadModData(event.getModConfigurationDirectory());
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit();
    }

    @Mod.EventHandler
    public void onServerStart(FMLServerStartingEvent event) {
        proxy.serverStart(event);
    }
}

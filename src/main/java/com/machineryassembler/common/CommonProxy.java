package com.machineryassembler.common;

import java.io.File;

import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import net.minecraftforge.common.ForgeChunkManager;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.common.command.CommandReloadStructures;
import com.machineryassembler.common.config.AutobuildConfig;
import com.machineryassembler.common.data.DataHolder;
import com.machineryassembler.common.network.NetworkHandler;
import com.machineryassembler.common.structure.StructureRegistry;


public class CommonProxy {

    public static final DataHolder dataHolder = new DataHolder();

    public static void loadModData(File configDir) {
        dataHolder.setup(configDir);

        // Initialize config
        File configFile = new File(configDir, MachineryAssembler.MODID + "/autobuild.cfg");
        AutobuildConfig.init(configFile);
    }

    public void preInit() {
        NetworkHandler.init();
        StructureRegistry.preloadStructures();

        // Register chunk loading callback for autobuild
        ForgeChunkManager.setForcedChunkLoadingCallback(MachineryAssembler.instance, (tickets, world) -> {
            // We don't need to reload tickets on world load since autobuild is one-shot
        });
    }

    public void init() {
    }

    public void postInit() {
        StructureRegistry.registerStructures(StructureRegistry.loadStructures(null));
        MachineryAssembler.LOGGER.info("[Machinery Assembler] Loaded {} structures.", StructureRegistry.getLoadedStructures().size());
    }

    public void serverStart(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandReloadStructures());
    }

    /**
     * Called when structures are reloaded.
     * Overridden on client to notify JEI.
     */
    public void onStructuresReloaded() {
        // Server side does nothing
    }

    /**
     * Schedule a client-side structure reload notification.
     * On dedicated server, does nothing.
     * On client or integrated server, schedules the notification on the client thread.
     */
    public void scheduleClientStructureReload() {
        // Server side does nothing - client overrides this
    }
}

package com.machineryassembler.common;

import java.io.File;

import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.common.command.CommandReloadStructures;
import com.machineryassembler.common.data.DataHolder;
import com.machineryassembler.common.structure.StructureRegistry;


public class CommonProxy {

    public static final DataHolder dataHolder = new DataHolder();

    public static void loadModData(File configDir) {
        dataHolder.setup(configDir);
    }

    public void preInit() {
        StructureRegistry.preloadStructures();
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

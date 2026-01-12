package com.machineryassembler.common.structure;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.common.CommonProxy;


/**
 * Registry for loaded structures with hot-reload support.
 */
public class StructureRegistry implements Iterable<Structure> {

    private static final StructureRegistry INSTANCE = new StructureRegistry();

    private static final Map<ResourceLocation, Tuple<Structure, String>> WAIT_FOR_LOAD_STRUCTURES = new HashMap<>();
    private static final Map<ResourceLocation, Structure> LOADED_STRUCTURES = new HashMap<>();

    private StructureRegistry() {
    }

    public static StructureRegistry getRegistry() {
        return INSTANCE;
    }

    /**
     * Preloads structures from files (first pass, registers names).
     */
    public static void preloadStructures() {
        File structuresDir = CommonProxy.dataHolder.getStructuresDirectory();
        List<File> candidates = StructureLoader.discoverDirectory(structuresDir);

        List<Tuple<Structure, String>> found = StructureLoader.registerStructures(candidates);

        Map<String, Exception> failures = StructureLoader.captureFailedAttempts();
        if (!failures.isEmpty()) {
            MachineryAssembler.LOGGER.warn("Encountered {} problems while registering structures!", failures.size());
            for (String fileName : failures.keySet()) {
                MachineryAssembler.LOGGER.warn("Couldn't load structure {}", fileName);
                failures.get(fileName).printStackTrace();
            }
        }

        for (Tuple<Structure, String> waitForRegistry : found) {
            WAIT_FOR_LOAD_STRUCTURES.put(waitForRegistry.getFirst().getRegistryName(), waitForRegistry);
        }
    }

    /**
     * Loads structures (second pass, full load).
     */
    public static Collection<Structure> loadStructures(@Nullable ICommandSender sender) {
        List<Structure> found = StructureLoader.loadStructures(WAIT_FOR_LOAD_STRUCTURES.values());
        WAIT_FOR_LOAD_STRUCTURES.clear();

        Map<String, Exception> failures = StructureLoader.captureFailedAttempts();
        if (!failures.isEmpty()) {
            MachineryAssembler.LOGGER.warn("Encountered {} problems while loading structures!", failures.size());
            for (String fileName : failures.keySet()) {
                MachineryAssembler.LOGGER.warn("Couldn't load structure {}", fileName);
                failures.get(fileName).printStackTrace();
            }

            if (sender != null) {
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "Failed to load " + failures.size() + " structures. Check log for details."));
            }
        }

        if (sender != null) {
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Loaded " + found.size() + " structures."));
        }

        return Collections.unmodifiableList(found);
    }

    /**
     * Registers loaded structures.
     */
    public static void registerStructures(Collection<Structure> structures) {
        for (Structure structure : structures) LOADED_STRUCTURES.put(structure.getRegistryName(), structure);
    }

    /**
     * Reloads structures from files, merging with existing ones or adding new ones.
     */
    public static void reloadStructures(@Nullable ICommandSender sender) {
        File structuresDir = CommonProxy.dataHolder.getStructuresDirectory();
        List<File> candidates = StructureLoader.discoverDirectory(structuresDir);

        List<Tuple<Structure, String>> found = StructureLoader.registerStructures(candidates);

        Map<String, Exception> failures = StructureLoader.captureFailedAttempts();
        if (!failures.isEmpty()) {
            MachineryAssembler.LOGGER.warn("Encountered {} problems while registering structures!", failures.size());
            for (String fileName : failures.keySet()) {
                MachineryAssembler.LOGGER.warn("Couldn't load structure {}", fileName);
                failures.get(fileName).printStackTrace();
            }

            if (sender != null) {
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "Failed to register " + failures.size() + " structures. Check log for details."));
            }
        }

        List<Structure> loadedStructures = StructureLoader.loadStructures(found);

        failures = StructureLoader.captureFailedAttempts();
        if (!failures.isEmpty()) {
            MachineryAssembler.LOGGER.warn("Encountered {} problems while loading structures!", failures.size());
            for (String fileName : failures.keySet()) {
                MachineryAssembler.LOGGER.warn("Couldn't load structure {}", fileName);
                failures.get(fileName).printStackTrace();
            }

            if (sender != null) {
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "Failed to load " + failures.size() + " structures. Check log for details."));
            }
        }

        // Merge or add structures
        for (Structure structure : loadedStructures) {
            Structure loaded = LOADED_STRUCTURES.get(structure.getRegistryName());
            if (loaded != null) {
                loaded.mergeFrom(structure);
            } else {
                LOADED_STRUCTURES.put(structure.getRegistryName(), structure);
            }
        }

        if (sender != null) {
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Reloaded " + loadedStructures.size() + " structures. Total: " + LOADED_STRUCTURES.size()));
        }

        MachineryAssembler.LOGGER.info("[Machinery Assembler] Reloaded {} structures. Total: {}", loadedStructures.size(), LOADED_STRUCTURES.size());

        // Notify JEI wrappers on client side
        notifyJEIReload();
    }

    /**
     * Notify JEI wrappers that structures have been reloaded.
     * This needs to run on the client side.
     */
    private static void notifyJEIReload() {
        // Use the proxy to handle client-side notification
        // The proxy knows how to schedule on the correct thread
        MachineryAssembler.proxy.scheduleClientStructureReload();
    }

    public static List<Structure> getLoadedStructures() {
        return Collections.unmodifiableList(new ArrayList<>(LOADED_STRUCTURES.values()));
    }

    @Nullable
    public Structure getStructure(@Nullable ResourceLocation name) {
        if (name == null) return null;

        return LOADED_STRUCTURES.get(name);
    }

    @Override
    public Iterator<Structure> iterator() {
        return LOADED_STRUCTURES.values().iterator();
    }
}

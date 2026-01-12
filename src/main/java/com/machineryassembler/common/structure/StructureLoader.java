package com.machineryassembler.common.structure;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.util.JsonUtils;
import net.minecraft.util.Tuple;

import com.machineryassembler.MachineryAssembler;


/**
 * Loads structure definitions from JSON files.
 */
public class StructureLoader {

    private static final Gson GSON = new GsonBuilder()
        .registerTypeHierarchyAdapter(Structure.class, new Structure.StructureDeserializer())
        .create();

    private static Map<String, Exception> failedAttempts = new HashMap<>();

    /**
     * Reads a file as a string.
     */
    public static String readFile(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * Discovers all JSON files in the given directory and subdirectories.
     */
    public static List<File> discoverDirectory(File directory) {
        List<File> candidates = new ArrayList<>();

        if (!directory.exists()) {
            return candidates;
        }

        LinkedList<File> directories = Lists.newLinkedList();
        directories.add(directory);

        while (!directories.isEmpty()) {
            File dir = directories.remove(0);
            File[] files = dir.listFiles();
            if (files == null) {
                continue;
            }

            for (File file : files) {
                if (file.isDirectory()) {
                    directories.addLast(file);
                } else if (file.getName().endsWith(".json") && !file.getName().endsWith(".var.json")) {
                    candidates.add(file);
                }
            }
        }

        return candidates;
    }

    /**
     * Registers structures from the given files (preload step).
     */
    public static List<Tuple<Structure, String>> registerStructures(Collection<File> structureCandidates) {
        List<Tuple<Structure, String>> registeredStructures = Lists.newArrayList();

        for (File file : structureCandidates) {
            try {
                String jsonString = readFile(file);
                Structure structure = JsonUtils.fromJson(GSON, jsonString, Structure.class, false);
                if (structure != null) registeredStructures.add(new Tuple<>(structure, jsonString));
            } catch (Exception exc) {
                failedAttempts.put(file.getPath(), exc);
            }
        }

        return registeredStructures;
    }

    /**
     * Loads structures from the preloaded data (full load step).
     */
    public static List<Structure> loadStructures(Collection<Tuple<Structure, String>> registeredStructureList) {
        List<Structure> loadedStructures = new ArrayList<>();

        for (Tuple<Structure, String> registryAndJsonStr : registeredStructureList) {
            Structure preloadStructure = registryAndJsonStr.getFirst();
            try {
                Structure loadedStructure = JsonUtils.fromJson(GSON, registryAndJsonStr.getSecond(), Structure.class, false);
                if (loadedStructure != null) {
                    preloadStructure.mergeFrom(loadedStructure);
                    loadedStructures.add(preloadStructure);
                }
            } catch (Exception exc) {
                MachineryAssembler.LOGGER.warn("Failed to load structure {}", preloadStructure.getRegistryName(), exc);
            }
        }

        return loadedStructures;
    }

    /**
     * Returns and clears the map of failed loading attempts.
     */
    public static Map<String, Exception> captureFailedAttempts() {
        Map<String, Exception> failed = failedAttempts;
        failedAttempts = new HashMap<>();

        return failed;
    }
}

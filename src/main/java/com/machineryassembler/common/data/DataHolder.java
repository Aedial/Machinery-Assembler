package com.machineryassembler.common.data;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.Tags;


public class DataHolder {

    private File altStructuresDirectory;
    private File structuresDirectory;
    private final List<File> structuresDirectories = new ArrayList<>();

    public void setup(File configDir) {
        // Alternative structures directory for temporary storage of recorded structures
        File minecraftDirectory = configDir.getParentFile();
        altStructuresDirectory = new File(minecraftDirectory == null ? configDir : minecraftDirectory, "multiblocks");
        if (!altStructuresDirectory.exists()) {
            altStructuresDirectory.mkdirs();
            MachineryAssembler.LOGGER.info("Created structures directory at {}", altStructuresDirectory.getAbsolutePath());
        }

        structuresDirectory = new File(new File(configDir, Tags.MODID), "structures");

        structuresDirectories.clear();
        structuresDirectories.add(altStructuresDirectory);

        if (!altStructuresDirectory.equals(structuresDirectory)) {
            structuresDirectories.add(structuresDirectory);
        }
    }

    /**
     * @return The directory where structures are saved when using the in-game recording tool.
     */
    public File getStructuresSaveDirectory() {
        return altStructuresDirectory;
    }

    public List<File> getStructuresDirectories() {
        return Collections.unmodifiableList(structuresDirectories);
    }
}

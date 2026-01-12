package com.machineryassembler.common.data;

import java.io.File;

import com.machineryassembler.MachineryAssembler;


public class DataHolder {

    private File structuresDirectory;

    public void setup(File configDir) {
        File modConfigDir = new File(configDir, MachineryAssembler.MODID);
        if (!modConfigDir.exists()) modConfigDir.mkdirs();

        structuresDirectory = new File(modConfigDir, "structures");
        if (!structuresDirectory.exists()) {
            structuresDirectory.mkdirs();
            MachineryAssembler.LOGGER.info("[Machinery Assembler] Created structures directory at {}", structuresDirectory.getAbsolutePath());
        }
    }

    public File getStructuresDirectory() {
        return structuresDirectory;
    }
}

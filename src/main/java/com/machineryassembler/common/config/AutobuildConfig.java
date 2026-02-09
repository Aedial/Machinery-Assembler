// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.config;

import java.io.File;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.machineryassembler.MachineryAssembler;


/**
 * Configuration for autobuild behavior.
 * Uses traditional Configuration API for proper GUI support with double values.
 */
public class AutobuildConfig {

    public static final String CATEGORY = "autobuild";

    // Config keys for localization
    public static final String KEY_CONSUME_CREATIVE = "config.machineryassembler.consumeBlocksInCreative";
    public static final String KEY_ALLOW_PARTIAL = "config.machineryassembler.allowPartialBuilds";
    public static final String KEY_BLOCKS_PER_TICK = "config.machineryassembler.blocksPerTick";
    public static final String KEY_MAX_DISTANCE = "config.machineryassembler.maxBuildDistance";
    public static final String KEY_DETAILED_REPORT = "config.machineryassembler.detailedMissingReport";

    private static Configuration config;

    public static boolean consumeBlocksInCreative = false;
    public static boolean allowPartialBuilds = true;
    public static double blocksPerTick = 0.5;
    public static int maxBuildDistance = 0;
    public static boolean detailedMissingReport = true;

    public static void init(File configFile) {
        if (config == null) {
            config = new Configuration(configFile);
            load();
        }
    }

    public static Configuration getConfig() {
        return config;
    }

    public static void load() {
        Property prop;

        prop = config.get(CATEGORY, "consumeBlocksInCreative", false);
        prop.setLanguageKey(KEY_CONSUME_CREATIVE);
        prop.setComment("Whether creative mode players should have blocks consumed during autobuild.");
        consumeBlocksInCreative = prop.getBoolean();

        prop = config.get(CATEGORY, "allowPartialBuilds", true);
        prop.setLanguageKey(KEY_ALLOW_PARTIAL);
        prop.setComment("Whether to allow partial builds when not all blocks are available. If false, autobuild will be aborted when blocks are missing.");
        allowPartialBuilds = prop.getBoolean();

        prop = config.get(CATEGORY, "blocksPerTick", 0.5);
        prop.setLanguageKey(KEY_BLOCKS_PER_TICK);
        prop.setComment("Number of blocks to place per tick during autobuild. Fractional values are allowed (e.g. 0.5 = one block every 2 ticks). Higher values build faster but may cause lag.");
        prop.setMinValue(0.05);
        prop.setMaxValue(64.0);
        blocksPerTick = prop.getDouble();

        prop = config.get(CATEGORY, "maxBuildDistance", 0);
        prop.setLanguageKey(KEY_MAX_DISTANCE);
        prop.setComment("Maximum distance from player for autobuild. Set to 0 for unlimited.");
        prop.setMinValue(0);
        prop.setMaxValue(1024);
        maxBuildDistance = prop.getInt();

        prop = config.get(CATEGORY, "detailedMissingReport", true);
        prop.setLanguageKey(KEY_DETAILED_REPORT);
        prop.setComment("Whether to report each missing block type in chat. If false, only shows total count.");
        detailedMissingReport = prop.getBoolean();

        if (config.hasChanged()) config.save();
    }

    /**
     * Check if blocks should be consumed for the given player.
     */
    public static boolean shouldConsumeBlocks(EntityPlayer player) {
        if (player.isCreative()) return consumeBlocksInCreative;

        return true;
    }

    @Mod.EventBusSubscriber(modid = MachineryAssembler.MODID)
    public static class ConfigSyncHandler {
        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(MachineryAssembler.MODID)) load();
        }
    }
}

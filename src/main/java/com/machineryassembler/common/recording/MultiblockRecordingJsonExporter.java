// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.recording;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;

import com.machineryassembler.common.structure.BlockRequirement;
import com.machineryassembler.common.structure.Structure;
import com.machineryassembler.common.structure.StructurePattern;


/**
 * Exports a recorder-built structure into Machinery Assembler's JSON format.
 */
public final class MultiblockRecordingJsonExporter {

    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    private static final char[] INPUT_SYMBOLS = (
        "abcdefghijklmnopqrstuvwxyz" +
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
        "0123456789" +
        "!#$%&()*+,-./:;<=>?@[]^{}~"
    ).toCharArray();

    private MultiblockRecordingJsonExporter() {
    }

    public static String export(String id, Structure structure) {
        StructurePattern pattern = structure.getPattern();
        JsonObject root = new JsonObject();
        root.addProperty("id", id);
        root.add("inputs", buildInputs(pattern));
        root.add("shape", buildShape(pattern));
        return GSON.toJson(root);
    }

    private static JsonObject buildInputs(StructurePattern pattern) {
        LinkedHashMap<String, Character> symbolsByKey = new LinkedHashMap<>();
        Map<String, InputDefinition> definitions = new LinkedHashMap<>();

        for (int y = pattern.getMax().getY(); y >= pattern.getMin().getY(); y--) {
            for (int z = pattern.getMin().getZ(); z <= pattern.getMax().getZ(); z++) {
                for (int x = pattern.getMin().getX(); x <= pattern.getMax().getX(); x++) {
                    BlockRequirement requirement = pattern.getPattern().get(new BlockPos(x, y, z));
                    InputDefinition definition = InputDefinition.from(requirement);
                    if (definition == null) continue;

                    String key = createRequirementKey(definition);
                    if (definitions.containsKey(key)) continue;

                    definitions.put(key, definition);
                    symbolsByKey.put(key, nextSymbol(symbolsByKey.size()));
                }
            }
        }

        JsonObject inputs = new JsonObject();
        for (Map.Entry<String, InputDefinition> entry : definitions.entrySet()) {
            Character symbol = symbolsByKey.get(entry.getKey());
            inputs.add(String.valueOf(symbol), toJson(entry.getValue()));
        }

        return inputs;
    }

    private static JsonArray buildShape(StructurePattern pattern) {
        LinkedHashMap<String, Character> symbolsByKey = new LinkedHashMap<>();
        JsonArray shape = new JsonArray();

        for (int y = pattern.getMax().getY(); y >= pattern.getMin().getY(); y--) {
            JsonArray layer = new JsonArray();

            for (int z = pattern.getMin().getZ(); z <= pattern.getMax().getZ(); z++) {
                StringBuilder row = new StringBuilder();

                for (int x = pattern.getMin().getX(); x <= pattern.getMax().getX(); x++) {
                    BlockRequirement requirement = pattern.getPattern().get(new BlockPos(x, y, z));
                    InputDefinition definition = InputDefinition.from(requirement);
                    // Treat holes, air, and invalid samples as empty in the exported shape.
                    if (definition == null) {
                        row.append('_');
                        continue;
                    }

                    String key = createRequirementKey(definition);
                    Character symbol = symbolsByKey.get(key);
                    if (symbol == null) {
                        symbol = nextSymbol(symbolsByKey.size());
                        symbolsByKey.put(key, symbol);
                    }

                    row.append(symbol.charValue());
                }

                layer.add(new JsonPrimitive(row.toString()));
            }

            shape.add(layer);
        }

        return shape;
    }

    private static JsonElement toJson(InputDefinition definition) {
        if (definition.meta == 0 && (definition.nbt == null || definition.nbt.getKeySet().isEmpty())) {
            return new JsonPrimitive(definition.id);
        }

        JsonObject object = new JsonObject();
        object.addProperty("id", definition.id);

        if (definition.meta != 0) {
            object.addProperty("meta", definition.meta);
        }

        if (definition.nbt != null && !definition.nbt.getKeySet().isEmpty()) {
            object.add("nbt", toJson(definition.nbt));
        }

        return object;
    }

    private static JsonElement toJson(NBTBase tag) {
        if (tag instanceof NBTTagCompound) {
            JsonObject object = new JsonObject();
            List<String> keys = new ArrayList<>(((NBTTagCompound) tag).getKeySet());
            Collections.sort(keys);

            for (String key : keys) {
                object.add(key, toJson(((NBTTagCompound) tag).getTag(key)));
            }

            return object;
        }

        if (tag instanceof NBTTagList) {
            JsonArray array = new JsonArray();
            NBTTagList list = (NBTTagList) tag;
            for (int index = 0; index < list.tagCount(); index++) {
                array.add(toJson(list.get(index)));
            }

            return array;
        }

        if (tag instanceof NBTTagByteArray) {
            JsonArray array = new JsonArray();
            for (byte value : ((NBTTagByteArray) tag).getByteArray()) {
                array.add(new JsonPrimitive(value + "b"));
            }

            return array;
        }

        if (tag instanceof NBTTagIntArray) {
            JsonArray array = new JsonArray();
            for (int value : ((NBTTagIntArray) tag).getIntArray()) {
                array.add(new JsonPrimitive(Integer.toString(value)));
            }

            return array;
        }

        if (tag instanceof NBTTagString) {
            return new JsonPrimitive(((NBTTagString) tag).getString());
        }

        if (tag instanceof NBTPrimitive) {
            return new JsonPrimitive(formatNumber((NBTPrimitive) tag));
        }

        return new JsonPrimitive(tag.toString());
    }

    private static String createRequirementKey(InputDefinition definition) {
        return definition.id + "@" + definition.meta + "|" + stableNbtKey(definition.nbt);
    }

    private static String stableNbtKey(@Nullable NBTTagCompound nbt) {
        if (nbt == null || nbt.getKeySet().isEmpty()) return "";

        return GSON.toJson(toJson(nbt));
    }

    private static char nextSymbol(int index) {
        if (index >= INPUT_SYMBOLS.length) {
            throw new IllegalStateException("Recorder export uses more unique block definitions than the JSON format can encode.");
        }

        return INPUT_SYMBOLS[index];
    }

    private static String formatNumber(NBTPrimitive primitive) {
        if (primitive instanceof NBTTagByte) {
            return primitive.getByte() + "b";
        }

        if (primitive instanceof NBTTagShort) {
            return primitive.getShort() + "s";
        }

        if (primitive instanceof NBTTagLong) {
            return primitive.getLong() + "l";
        }

        if (primitive instanceof NBTTagFloat) {
            return primitive.getFloat() + "f";
        }

        if (primitive instanceof NBTTagDouble) {
            return primitive.getDouble() + "d";
        }

        if (primitive instanceof NBTTagInt) {
            return Integer.toString(primitive.getInt());
        }

        return primitive.toString();
    }

    private static final class InputDefinition {
        private final String id;
        private final int meta;
        @Nullable
        private final NBTTagCompound nbt;

        private InputDefinition(String id, int meta, @Nullable NBTTagCompound nbt) {
            this.id = id;
            this.meta = meta;
            this.nbt = nbt;
        }

        @Nullable
        private static InputDefinition from(@Nullable BlockRequirement requirement) {
            IBlockState state = getExportState(requirement);
            if (state == null) return null;

            Block block = state.getBlock();
            String id = block.getRegistryName().toString();
            int meta = block.getMetaFromState(state);
            NBTTagCompound nbt = requirement.getMatchingTag();

            return new InputDefinition(id, meta, nbt == null ? null : nbt.copy());
        }

        @Nullable
        private static IBlockState getExportState(@Nullable BlockRequirement requirement) {
            if (requirement == null || requirement.getSamples().isEmpty()) return null;

            IBlockState state = requirement.getSamples().get(0);
            if (state == null) return null;

            Block block = state.getBlock();
            if (block == Blocks.AIR) return null;
            if (block.getRegistryName() == null) return null;

            return state;
        }
    }
}
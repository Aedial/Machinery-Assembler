// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.structure;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.common.util.nbt.NBTJsonDeserializer;

/**
 * Represents a multiblock structure for display in JEI.
 */
public class Structure {

    @Nonnull
    protected final ResourceLocation registryName;
    protected final StructurePattern pattern = new StructurePattern();

    protected boolean registerAsItem = false;
    protected List<StructureMessage> messages = new ArrayList<>();
    @Nullable
    protected StructureOutput output = null;

    public Structure(String registryName) {
        this.registryName = new ResourceLocation(MachineryAssembler.MODID, registryName);
    }

    public Structure(ResourceLocation registryName) {
        this.registryName = registryName;
    }

    @Nonnull
    public ResourceLocation getRegistryName() {
        return registryName;
    }

    public StructurePattern getPattern() {
        return pattern;
    }

    @SideOnly(Side.CLIENT)
    public String getLocalizedName() {
        String localizationKey = registryName.getNamespace() + "." + registryName.getPath();

        return I18n.hasKey(localizationKey) ? I18n.format(localizationKey) : localizationKey;
    }


    public boolean shouldRegisterAsItem() {
        return registerAsItem;
    }

    public void setRegisterAsItem(boolean registerAsItem) {
        this.registerAsItem = registerAsItem;
    }

    public List<StructureMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<StructureMessage> messages) {
        this.messages = messages;
    }

    @Nullable
    public StructureOutput getOutput() {
        return output;
    }

    public void setOutput(@Nullable StructureOutput output) {
        this.output = output;
    }

    public void mergeFrom(Structure another) {
        pattern.overwrite(another.pattern);
        registerAsItem = another.registerAsItem;
        messages = another.messages;
        output = another.output;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Structure structure = (Structure) o;

        return Objects.equals(registryName, structure.registryName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registryName);
    }

    /**
     * JSON deserializer for Structure.
     * 
     * Supports the following format:
     * - id: String for lang key, JEI, item registration
     * - register-as-item: Optional boolean
     * - output: Optional output item "id@meta*count" or {id, meta?, count?, nbt?}
     * - messages: Optional array of {key, level, item?}
     * - inputs: Mapping of characters to "id@meta" or {id, meta?, nbt?}
     * - shape: Array[y][z] where each z is a string representing x-axis blocks
     */
    public static class StructureDeserializer implements JsonDeserializer<Structure> {

        @Override
        public Structure deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject root = json.getAsJsonObject();

            String id = JsonUtils.getString(root, "id");
            if (id.isEmpty()) throw new JsonParseException("Invalid/Missing 'id'!");

            Structure structure = new Structure(id);

            // Optional register-as-item
            if (root.has("register-as-item")) {
                structure.setRegisterAsItem(JsonUtils.getBoolean(root, "register-as-item"));
            }

            // Optional messages
            if (root.has("messages")) {
                List<StructureMessage> messages = parseMessages(JsonUtils.getJsonArray(root, "messages"));
                structure.setMessages(messages);
            }

            // Optional output
            if (root.has("output")) {
                StructureOutput output = parseOutput(root.get("output"));
                structure.setOutput(output);
            }

            // Required inputs
            if (!root.has("inputs")) throw new JsonParseException("Missing 'inputs' mapping!");
            Map<Character, BlockRequirement> inputMap = parseInputs(root.getAsJsonObject("inputs"));

            // Required shape
            if (!root.has("shape")) throw new JsonParseException("Missing 'shape' array!");
            parseShape(JsonUtils.getJsonArray(root, "shape"), inputMap, structure.pattern);

            return structure;
        }

        private List<StructureMessage> parseMessages(JsonArray messagesArray) throws JsonParseException {
            List<StructureMessage> messages = new ArrayList<>();

            for (JsonElement element : messagesArray) {
                if (!element.isJsonObject()) throw new JsonParseException("Message must be an object!");

                JsonObject msgObj = element.getAsJsonObject();
                String key = JsonUtils.getString(msgObj, "key");
                String levelStr = JsonUtils.getString(msgObj, "level");
                String item = JsonUtils.getString(msgObj, "item", null);

                StructureMessage.Level level;
                try {
                    level = StructureMessage.Level.valueOf(levelStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new JsonParseException("Invalid message level: " + levelStr);
                }

                messages.add(new StructureMessage(key, level, item));
            }

            return messages;
        }

        private StructureOutput parseOutput(JsonElement value) throws JsonParseException {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                // Simple string: "modid:item" or "modid:item@meta" or "modid:item@meta*count"
                return StructureOutput.fromString(value.getAsString());
            }

            if (value.isJsonObject()) {
                // Object with id, optional meta, count, nbt
                JsonObject obj = value.getAsJsonObject();
                String id = JsonUtils.getString(obj, "id");
                int meta = 0;
                int count = 1;
                NBTTagCompound nbt = null;

                // Parse inline meta from id
                int metaIndex = id.indexOf('@');
                if (metaIndex != -1 && metaIndex < id.length() - 1) {
                    try {
                        meta = Integer.parseInt(id.substring(metaIndex + 1));
                    } catch (NumberFormatException e) {
                        throw new JsonParseException("Expected metadata number after @, got: " + id.substring(metaIndex + 1), e);
                    }

                    id = id.substring(0, metaIndex);
                }

                // Meta from object property overrides inline
                if (obj.has("meta")) meta = JsonUtils.getInt(obj, "meta");
                if (obj.has("count")) count = JsonUtils.getInt(obj, "count");

                if (obj.has("nbt")) {
                    try {
                        nbt = NBTJsonDeserializer.deserialize(obj.get("nbt").toString());
                    } catch (NBTException e) {
                        throw new JsonParseException("Error parsing output NBT: " + e.getMessage(), e);
                    }
                }

                return new StructureOutput(id, meta, count, nbt);
            }

            throw new JsonParseException("Output must be a string or object!");
        }

        private Map<Character, BlockRequirement> parseInputs(JsonObject inputsObj) throws JsonParseException {
            Map<Character, BlockRequirement> inputMap = new HashMap<>();

            for (Map.Entry<String, JsonElement> entry : inputsObj.entrySet()) {
                String key = entry.getKey();
                if (key.length() != 1) throw new JsonParseException("Input key must be a single character: " + key);

                char inputChar = key.charAt(0);
                JsonElement value = entry.getValue();

                BlockRequirement requirement = parseInputValue(value);
                inputMap.put(inputChar, requirement);
            }

            return inputMap;
        }

        private BlockRequirement parseInputValue(JsonElement value) throws JsonParseException {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                // Simple string: "modid:block" or "modid:block@meta"
                return new BlockRequirement(Lists.newArrayList(parseBlockDescriptor(value.getAsString(), null)));
            }

            if (value.isJsonObject()) {
                // Object with id, optional meta, optional nbt
                JsonObject obj = value.getAsJsonObject();
                String id = JsonUtils.getString(obj, "id");
                NBTTagCompound nbt = null;

                if (obj.has("nbt")) {
                    try {
                        nbt = NBTJsonDeserializer.deserialize(obj.get("nbt").toString());
                    } catch (NBTException e) {
                        throw new JsonParseException("Error parsing NBT: " + e.getMessage(), e);
                    }
                }

                BlockStateMatcher matcher = parseBlockDescriptor(id, obj.has("meta") ? obj.get("meta").getAsInt() : null);
                BlockRequirement requirement = new BlockRequirement(Lists.newArrayList(matcher));

                if (nbt != null) {
                    requirement.setMatchingTag(nbt);
                    requirement.setPreviewTag(nbt);
                }

                return requirement;
            }

            if (value.isJsonArray()) {
                // Array of alternatives (OR logic)
                List<BlockStateMatcher> matchers = new ArrayList<>();

                for (JsonElement element : value.getAsJsonArray()) {
                    if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                        throw new JsonParseException("Input array elements must be strings!");
                    }

                    matchers.add(parseBlockDescriptor(element.getAsString(), null));
                }

                if (matchers.isEmpty()) throw new JsonParseException("Input array cannot be empty!");

                return new BlockRequirement(matchers);
            }

            throw new JsonParseException("Invalid input value type!");
        }

        private BlockStateMatcher parseBlockDescriptor(String str, @Nullable Integer metaOverride) throws JsonParseException {
            int meta = -1;
            int indexMeta = str.indexOf('@');

            if (indexMeta != -1 && indexMeta != str.length() - 1) {
                try {
                    meta = Integer.parseInt(str.substring(indexMeta + 1));
                } catch (NumberFormatException e) {
                    throw new JsonParseException("Expected metadata number after @, got: " + str.substring(indexMeta + 1), e);
                }

                str = str.substring(0, indexMeta);
            }

            // Meta override from object key takes precedence
            if (metaOverride != null) meta = metaOverride;

            ResourceLocation res = new ResourceLocation(str);
            Block block = ForgeRegistries.BLOCKS.getValue(res);
            if (block == null) throw new JsonParseException("Couldn't find block: '" + res + "'");

            if (meta == -1) return BlockStateMatcher.of(block);

            return BlockStateMatcher.of(block.getStateFromMeta(meta));
        }

        private void parseShape(JsonArray shapeArray, Map<Character, BlockRequirement> inputMap, StructurePattern pattern) throws JsonParseException {
            int layerCount = shapeArray.size();

            for (int layerIndex = 0; layerIndex < layerCount; layerIndex++) {
                // First row in array = top of structure, so invert Y
                int y = layerCount - 1 - layerIndex;
                JsonElement layerElement = shapeArray.get(layerIndex);
                if (!layerElement.isJsonArray()) throw new JsonParseException("Shape layer must be an array of strings!");

                JsonArray layerArray = layerElement.getAsJsonArray();

                for (int z = 0; z < layerArray.size(); z++) {
                    JsonElement rowElement = layerArray.get(z);
                    if (!rowElement.isJsonPrimitive() || !rowElement.getAsJsonPrimitive().isString()) {
                        throw new JsonParseException("Shape row must be a string!");
                    }

                    String row = rowElement.getAsString();

                    for (int x = 0; x < row.length(); x++) {
                        char c = row.charAt(x);

                        // Skip air/empty positions
                        if (c == '_' || c == ' ') continue;

                        BlockRequirement requirement = inputMap.get(c);
                        if (requirement == null) {
                            throw new JsonParseException("Unknown input character '" + c + "' at position (" + x + "," + y + "," + z + ")");
                        }

                        pattern.addBlock(x, y, z, requirement);
                    }
                }
            }

            if (pattern.isEmpty()) throw new JsonParseException("Shape resulted in empty pattern!");
        }

        private static List<BlockPos> buildPermutations(List<Integer> avX, List<Integer> avY, List<Integer> avZ) {
            List<BlockPos> out = new ArrayList<>(avX.size() * avY.size() * avZ.size());

            for (int x : avX) {
                for (int y : avY) {
                    for (int z : avZ) out.add(new BlockPos(x, y, z));
                }
            }

            return out;
        }


    }
}

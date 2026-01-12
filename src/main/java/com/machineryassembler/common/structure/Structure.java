package com.machineryassembler.common.structure;

import javax.annotation.Nonnull;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import hellfirepvp.modularmachinery.common.machine.MachineLoader;
import hellfirepvp.modularmachinery.common.machine.TaggedPositionBlockArray;
import hellfirepvp.modularmachinery.common.util.BlockArray;
import hellfirepvp.modularmachinery.common.util.IBlockStateDescriptor;
import hellfirepvp.modularmachinery.common.util.nbt.NBTJsonDeserializer;

import com.machineryassembler.MachineryAssembler;


/**
 * Represents a multiblock structure for display in JEI.
 * Similar to DynamicMachine but without controller requirements.
 */
public class Structure {

    @Nonnull
    protected final ResourceLocation registryName;
    protected final TaggedPositionBlockArray pattern = new TaggedPositionBlockArray();

    protected String localizedName = "";
    protected int definedColor = 0xFFFFFF;

    public Structure(String registryName) {
        this.registryName = new ResourceLocation(MachineryAssembler.MODID, registryName);
    }

    @Nonnull
    public ResourceLocation getRegistryName() {
        return registryName;
    }

    public TaggedPositionBlockArray getPattern() {
        return pattern;
    }

    @SideOnly(Side.CLIENT)
    public String getLocalizedName() {
        String localizationKey = registryName.getNamespace() + "." + registryName.getPath();

        return I18n.hasKey(localizationKey) ? I18n.format(localizationKey) :
            localizedName != null ? localizedName : localizationKey;
    }

    public String getOriginalLocalizedName() {
        return localizedName;
    }

    public void setLocalizedName(String localizedName) {
        this.localizedName = localizedName;
    }

    public int getColor() {
        return definedColor;
    }

    public void setDefinedColor(int definedColor) {
        this.definedColor = definedColor;
    }

    public void mergeFrom(Structure another) {
        pattern.overwrite(another.pattern);
        localizedName = another.localizedName;
        definedColor = another.definedColor;
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
     * JSON deserializer for Structure, using the same format as MMCE's DynamicMachine
     * but without requiring a controller block.
     */
    public static class StructureDeserializer implements JsonDeserializer<Structure> {

        private static List<BlockPos> buildPermutations(List<Integer> avX, List<Integer> avY, List<Integer> avZ) {
            List<BlockPos> out = new ArrayList<>(avX.size() * avY.size() * avZ.size());
            for (int x : avX) {
                for (int y : avY) {
                    for (int z : avZ) out.add(new BlockPos(x, y, z));
                }
            }

            return out;
        }

        private static void addDescriptorWithPattern(TaggedPositionBlockArray pattern, BlockArray.BlockInformation information, JsonObject part) throws JsonParseException {
            List<Integer> avX = new ArrayList<>();
            List<Integer> avY = new ArrayList<>();
            List<Integer> avZ = new ArrayList<>();
            addCoordinates("x", part, avX);
            addCoordinates("y", part, avY);
            addCoordinates("z", part, avZ);

            for (BlockPos permutation : buildPermutations(avX, avY, avZ)) {
                pattern.addBlock(permutation, information);
            }
        }

        private static void addCoordinates(String key, JsonObject part, List<Integer> out) throws JsonParseException {
            if (!part.has(key)) {
                out.add(0);
                return;
            }

            JsonElement coordinateElement = part.get(key);
            if (coordinateElement.isJsonPrimitive() && coordinateElement.getAsJsonPrimitive().isNumber()) {
                out.add(coordinateElement.getAsInt());
            } else if (coordinateElement.isJsonArray() && coordinateElement.getAsJsonArray().size() > 0) {
                for (JsonElement element : coordinateElement.getAsJsonArray()) {
                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                        out.add(element.getAsInt());
                    } else {
                        throw new JsonParseException("Expected only numbers in JsonArray " + coordinateElement + " but found " + element);
                    }
                }
            }
        }

        private static void addParts(final JsonArray parts, final TaggedPositionBlockArray pattern) {
            for (int i = 0; i < parts.size(); i++) {
                JsonElement element = parts.get(i);
                if (!element.isJsonObject()) {
                    throw new JsonParseException("A part of 'parts' is not a compound object!");
                }
                JsonObject part = element.getAsJsonObject();
                NBTTagCompound matchNBT = null;
                NBTTagCompound previewNBT = null;

                if (part.has("nbt")) {
                    JsonElement je = part.get("nbt");
                    if (!je.isJsonObject()) {
                        throw new JsonParseException("The 'nbt' expects a json compound that defines the NBT tag!");
                    }
                    String jsonStr = je.toString();
                    try {
                        matchNBT = NBTJsonDeserializer.deserialize(jsonStr);
                    } catch (NBTException exc) {
                        throw new JsonParseException("Error trying to parse NBTTag! Rethrowing exception...", exc);
                    }
                }

                if (part.has("preview-nbt")) {
                    JsonElement je = part.get("preview-nbt");
                    if (!je.isJsonObject()) {
                        throw new JsonParseException("The 'preview-nbt' expects a json compound that defines the NBT tag!");
                    }
                    String jsonStr = je.toString();
                    try {
                        previewNBT = NBTJsonDeserializer.deserialize(jsonStr);
                    } catch (NBTException exc) {
                        throw new JsonParseException("Error trying to parse NBTTag! Rethrowing exception...", exc);
                    }
                }

                if (!part.has("elements")) throw new JsonParseException("Part contained empty element!");

                JsonElement partElement = part.get("elements");
                if (partElement.isJsonPrimitive() && partElement.getAsJsonPrimitive().isString()) {
                    String strDesc = partElement.getAsString();
                    BlockArray.BlockInformation descr = MachineLoader.VARIABLE_CONTEXT.get(strDesc);
                    if (descr == null) {
                        descr = new BlockArray.BlockInformation(Lists.newArrayList(BlockArray.BlockInformation.getDescriptor(partElement.getAsString())));
                    } else {
                        descr = descr.copy();
                    }

                    if (matchNBT != null) descr.setMatchingTag(matchNBT);
                    if (previewNBT != null) descr.setPreviewTag(previewNBT);

                    addDescriptorWithPattern(pattern, descr, part);
                } else if (partElement.isJsonArray()) {
                    JsonArray elementArray = partElement.getAsJsonArray();
                    List<IBlockStateDescriptor> descriptors = Lists.newArrayList();
                    for (int xx = 0; xx < elementArray.size(); xx++) {
                        JsonElement p = elementArray.get(xx);
                        if (!p.isJsonPrimitive() || !p.getAsJsonPrimitive().isString()) {
                            throw new JsonParseException("Part elements of 'elements' have to be blockstate descriptions!");
                        }
                        String prim = p.getAsString();
                        BlockArray.BlockInformation descr = MachineLoader.VARIABLE_CONTEXT.get(prim);
                        if (descr != null) {
                            descriptors.addAll(descr.copy().getMatchingStates());
                        } else {
                            descriptors.add(BlockArray.BlockInformation.getDescriptor(prim));
                        }
                    }

                    if (descriptors.isEmpty()) {
                        throw new JsonParseException("'elements' array didn't contain any blockstate descriptors!");
                    }

                    BlockArray.BlockInformation bi = new BlockArray.BlockInformation(descriptors);
                    if (matchNBT != null) bi.setMatchingTag(matchNBT);
                    if (previewNBT != null) bi.setPreviewTag(previewNBT);

                    addDescriptorWithPattern(pattern, bi, part);
                } else {
                    throw new JsonParseException("'elements' has to either be a blockstate description, variable or array of blockstate descriptions!");
                }
            }
        }

        @Override
        public Structure deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject root = json.getAsJsonObject();

            String registryName = JsonUtils.getString(root, "registryname", "");
            if (registryName.isEmpty()) {
                registryName = JsonUtils.getString(root, "registryName", "");
                if (registryName.isEmpty()) throw new JsonParseException("Invalid/Missing 'registryname'!");
            }

            String localized = JsonUtils.getString(root, "localizedname", "");
            if (localized.isEmpty()) throw new JsonParseException("Invalid/Missing 'localizedname'!");

            JsonArray parts = JsonUtils.getJsonArray(root, "parts", new JsonArray());
            if (parts.size() == 0) throw new JsonParseException("Empty/Missing 'parts'!");

            Structure structure = new Structure(registryName);
            structure.setLocalizedName(localized);

            // Color (optional)
            if (root.has("color")) {
                String hexColor = JsonUtils.getString(root, "color");
                structure.setDefinedColor(Integer.parseInt(hexColor, 16));
            }

            // Parts
            addParts(parts, structure.pattern);

            return structure;
        }
    }
}

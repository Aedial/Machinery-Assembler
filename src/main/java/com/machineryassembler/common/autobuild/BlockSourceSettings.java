// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;


/**
 * Serializable wrench-side configuration for autobuild block providers.
 */
public class BlockSourceSettings {

    private static final String NBT_PROVIDER_ORDER = "ProviderOrder";
    private static final String NBT_PROVIDER_STATES = "ProviderStates";
    private static final String NBT_AE2_ENCRYPTION_KEY = "encryptionKey";

    private final List<BlockSourceProviderId> providerOrder;
    private final Map<BlockSourceProviderId, Boolean> enabledProviders;

    private long encryptionKey = 0L;

    public BlockSourceSettings() {
        this.providerOrder = BlockSourceProviderId.createDefaultOrder();
        this.enabledProviders = new EnumMap<>(BlockSourceProviderId.class);

        for (BlockSourceProviderId providerId : BlockSourceProviderId.values()) {
            enabledProviders.put(providerId, true);
        }
    }

    public static BlockSourceSettings defaults() {
        return new BlockSourceSettings();
    }

    public static BlockSourceSettings fromTag(@Nullable NBTTagCompound tag) {
        BlockSourceSettings settings = new BlockSourceSettings();

        if (tag == null) return settings;

        settings.providerOrder.clear();

        NBTTagList providerOrder = tag.getTagList(NBT_PROVIDER_ORDER, Constants.NBT.TAG_STRING);
        for (int i = 0; i < providerOrder.tagCount(); i++) {
            BlockSourceProviderId providerId = BlockSourceProviderId.fromSerializedName(providerOrder.getStringTagAt(i));

            if (providerId != null && !settings.providerOrder.contains(providerId)) {
                settings.providerOrder.add(providerId);
            }
        }

        settings.normalizeProviderOrder();

        NBTTagCompound providerStates = tag.getCompoundTag(NBT_PROVIDER_STATES);
        for (BlockSourceProviderId providerId : BlockSourceProviderId.values()) {
            if (providerStates.hasKey(providerId.getSerializedName(), Constants.NBT.TAG_BYTE)) {
                settings.enabledProviders.put(providerId, providerStates.getBoolean(providerId.getSerializedName()));
            }
        }

        String rawEncryptionKey = tag.getString(NBT_AE2_ENCRYPTION_KEY);
        if (!rawEncryptionKey.isEmpty()) {
            settings.encryptionKey = Long.parseLong(rawEncryptionKey);
        }

        return settings;
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList providerOrderTag = new NBTTagList();

        normalizeProviderOrder();

        for (BlockSourceProviderId providerId : providerOrder) {
            providerOrderTag.appendTag(new NBTTagString(providerId.getSerializedName()));
        }

        tag.setTag(NBT_PROVIDER_ORDER, providerOrderTag);

        NBTTagCompound providerStates = new NBTTagCompound();
        for (BlockSourceProviderId providerId : BlockSourceProviderId.values()) {
            providerStates.setBoolean(providerId.getSerializedName(), isEnabled(providerId));
        }

        tag.setTag(NBT_PROVIDER_STATES, providerStates);
        if (encryptionKey != 0) tag.setString(NBT_AE2_ENCRYPTION_KEY, Long.toString(encryptionKey));

        return tag;
    }

    public List<BlockSourceProviderId> getProviderOrder() {
        normalizeProviderOrder();

        return Collections.unmodifiableList(providerOrder);
    }

    public boolean isEnabled(BlockSourceProviderId providerId) {
        return enabledProviders.getOrDefault(providerId, true);
    }

    public void setEnabled(BlockSourceProviderId providerId, boolean enabled) {
        enabledProviders.put(providerId, enabled);
    }

    public void moveUp(BlockSourceProviderId providerId) {
        int currentIndex = providerOrder.indexOf(providerId);
        if (currentIndex <= 0) return;

        Collections.swap(providerOrder, currentIndex, currentIndex - 1);
    }

    public void moveDown(BlockSourceProviderId providerId) {
        int currentIndex = providerOrder.indexOf(providerId);
        if (currentIndex < 0 || currentIndex >= providerOrder.size() - 1) return;

        Collections.swap(providerOrder, currentIndex, currentIndex + 1);
    }

    public List<BlockSourceProviderId> getAvailableProviderOrder() {
        normalizeProviderOrder();

        List<BlockSourceProviderId> availableProviders = new ArrayList<>();
        for (BlockSourceProviderId providerId : providerOrder) {
            if (providerId.isModAvailable()) availableProviders.add(providerId);
        }

        return availableProviders;
    }

    public long getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(long encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    private void normalizeProviderOrder() {
        for (BlockSourceProviderId providerId : BlockSourceProviderId.createDefaultOrder()) {
            if (!providerOrder.contains(providerId)) providerOrder.add(providerId);
        }
    }
}
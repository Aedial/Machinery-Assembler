// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraftforge.fml.common.Loader;


/**
 * Stable identifiers for configurable autobuild block providers.
 */
public enum BlockSourceProviderId {

    INVENTORY("inventory", "item.machineryassembler.assembler_wrench.provider.inventory", null),
    EMC("emc", "item.machineryassembler.assembler_wrench.provider.emc", "projecte"),
    AE2("ae2", "item.machineryassembler.assembler_wrench.provider.ae2", "appliedenergistics2");

    private final String serializedName;
    private final String translationKey;
    private final String requiredModId;

    BlockSourceProviderId(String serializedName, String translationKey, @Nullable String requiredModId) {
        this.serializedName = serializedName;
        this.translationKey = translationKey;
        this.requiredModId = requiredModId;
    }

    public String getSerializedName() {
        return serializedName;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    @Nullable
    public String getRequiredModId() {
        return requiredModId;
    }

    public boolean isModAvailable() {
        return requiredModId == null || Loader.isModLoaded(requiredModId);
    }

    @Nullable
    public static BlockSourceProviderId fromSerializedName(String serializedName) {
        for (BlockSourceProviderId providerId : values()) {
            if (providerId.serializedName.equals(serializedName)) return providerId;
        }

        return null;
    }

    public static List<BlockSourceProviderId> createDefaultOrder() {
        List<BlockSourceProviderId> defaultOrder = new ArrayList<>();

        defaultOrder.add(INVENTORY);
        defaultOrder.add(EMC);
        defaultOrder.add(AE2);

        return defaultOrder;
    }
}
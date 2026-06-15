// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.features.ILocatable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.helpers.PlayerSource;
import appeng.tile.misc.TileSecurityStation;

import com.machineryassembler.common.util.BlockStackUtils;


/**
 * Block source backed by a linked AE2 network host.
 */
public class AE2BlockSource implements BlockSource {

    public static final AE2BlockSource INSTANCE = new AE2BlockSource();

    private AE2BlockSource() {
    }

    @Override
    public boolean canProvide(IBlockState state, BlockSourceContext context) {
        return countAvailable(state, context) > 0;
    }

    @Override
    public int countAvailable(IBlockState state, BlockSourceContext context) {
        ItemStack requiredStack = BlockStackUtils.getStackFromBlockState(state);
        if (requiredStack.isEmpty()) return 0;

        return countAvailable(requiredStack, context);
    }

    @Override
    @Nullable
    public ItemStack extract(IBlockState state, BlockSourceContext context, boolean simulate) {
        ItemStack requiredStack = BlockStackUtils.getStackFromBlockState(state);
        if (requiredStack.isEmpty()) return null;

        BlockExtractionResult result = batchExtractDetailed(singleRequirement(requiredStack), context, simulate);
        if (!result.getRemainder().isEmpty()) return null;

        for (String extractedKey : result.getExtracted().keySet()) {
            ItemStack extractedStack = BlockSourceUtils.keyToStack(extractedKey);
            if (!extractedStack.isEmpty()) return extractedStack;
        }

        ItemStack extracted = requiredStack.copy();
        extracted.setCount(1);

        return extracted;
    }

    @Override
    public BlockExtractionResult batchExtractDetailed(Map<String, Integer> requirements, BlockSourceContext context,
                                                      boolean simulate) {
        if (!BlockSourceProviderId.AE2.isModAvailable()) return this.refuse(requirements);

        IMEMonitor<IAEItemStack> itemStorage = getItemStorage(context);
        PlayerSource actionSource = getActionSource(context);
        if (itemStorage == null || actionSource == null) return this.refuse(requirements);

        List<IAEItemStack> storedVariants = new ArrayList<>();
        for (IAEItemStack stored : itemStorage.getStorageList()) {
            if (stored == null) continue;

            storedVariants.add(stored.copy());
        }

        BlockExtractionResult result = new BlockExtractionResult();
        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            String key = entry.getKey();
            ItemStack requiredStack = BlockSourceUtils.keyToStack(key);
            int needed = entry.getValue();

            if (requiredStack.isEmpty()) {
                result.addRemainder(key, needed);
                continue;
            }

            int extractedCount = 0;
            for (IAEItemStack storedVariant : storedVariants) {
                if (extractedCount >= needed) break;

                ItemStack availableStack = storedVariant.createItemStack();
                if (!BlockSourceUtils.matchesRequiredStack(availableStack, requiredStack)) continue;

                long availableCount = storedVariant.getStackSize();
                if (availableCount <= 0) continue;

                int toExtract = (int) Math.min(needed - extractedCount, availableCount);
                if (toExtract <= 0) continue;

                if (simulate) {
                    storedVariant.setStackSize(availableCount - toExtract);
                    extractedCount += toExtract;
                    continue;
                }

                IAEItemStack request = storedVariant.copy();
                request.setStackSize(toExtract);

                IAEItemStack extracted = itemStorage.extractItems(request, Actionable.MODULATE, actionSource);
                if (extracted == null) continue;

                int extractedAmount = (int) Math.min(Integer.MAX_VALUE, extracted.getStackSize());
                if (extractedAmount <= 0) continue;

                result.addExtracted(extracted.createItemStack(), extractedAmount);
                storedVariant.setStackSize(Math.max(0L, availableCount - extractedAmount));
                extractedCount += extractedAmount;
            }

            if (extractedCount < needed) result.addRemainder(key, needed - extractedCount);
        }

        return result;
    }

    @Override
    public Map<String, Integer> checkAvailability(Map<String, Integer> requirements, BlockSourceContext context) {
        Map<String, Integer> available = new HashMap<>();

        if (!BlockSourceProviderId.AE2.isModAvailable()) return available;

        IMEMonitor<IAEItemStack> itemStorage = getItemStorage(context);
        if (itemStorage == null) return available;

        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            ItemStack requiredStack = BlockSourceUtils.keyToStack(entry.getKey());
            if (requiredStack.isEmpty()) {
                available.put(entry.getKey(), 0);
                continue;
            }

            long total = 0;
            for (IAEItemStack stored : itemStorage.getStorageList()) {
                if (stored == null) continue;

                ItemStack availableStack = stored.createItemStack();
                if (!BlockSourceUtils.matchesRequiredStack(availableStack, requiredStack)) continue;

                total += stored.getStackSize();
                if (total >= Integer.MAX_VALUE) {
                    total = Integer.MAX_VALUE;
                    break;
                }
            }

            available.put(entry.getKey(), (int) total);
        }

        return available;
    }

    @Override
    public String getName() {
        return "AE2";
    }

    private int countAvailable(ItemStack requiredStack, BlockSourceContext context) {
        Map<String, Integer> availability = checkAvailability(singleRequirement(requiredStack), context);

        return availability.values().stream().findFirst().orElse(0);
    }

    private Map<String, Integer> singleRequirement(ItemStack stack) {
        Map<String, Integer> requirements = new HashMap<>();
        requirements.put(BlockSourceUtils.stackToKey(stack), 1);

        return requirements;
    }

    @Nullable
    @Optional.Method(modid = "appliedenergistics2")
    private IMEMonitor<IAEItemStack> getItemStorage(BlockSourceContext context) {
        TileSecurityStation securityStation = getLinkedSecurityStation(context);
        if (securityStation == null) return null;

        IGridNode node = securityStation.getActionableNode();
        if (node == null || !node.isActive()) return null;

        IGrid grid = node.getGrid();
        IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
        if (storageGrid == null) return null;

        IItemStorageChannel channel = getItemChannel();
        if (channel == null) return null;

        return storageGrid.getInventory(channel);
    }

    @Nullable
    @Optional.Method(modid = "appliedenergistics2")
    private PlayerSource getActionSource(BlockSourceContext context) {
        TileSecurityStation securityStation = getLinkedSecurityStation(context);
        if (securityStation == null) return null;

        return new PlayerSource(context.getPlayer(), securityStation);
    }

    @Nullable
    @Optional.Method(modid = "appliedenergistics2")
    private IItemStorageChannel getItemChannel() {
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    @Nullable
    @Optional.Method(modid = "appliedenergistics2")
    private TileSecurityStation getLinkedSecurityStation(BlockSourceContext context) {
        long encryptionKey = context.getSettings().getEncryptionKey();
        if (encryptionKey == 0L) return null;

        ILocatable locatable = AEApi.instance().registries().locatable().getLocatableBy(encryptionKey);
        if (!(locatable instanceof TileSecurityStation)) return null;

        return (TileSecurityStation) locatable;
    }
}
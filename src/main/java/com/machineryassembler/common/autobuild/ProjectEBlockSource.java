// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;

import com.machineryassembler.common.util.BlockStackUtils;

import moze_intel.projecte.api.ProjectEAPI;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.ITransmutationProxy;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.ItemHelper;
import moze_intel.projecte.utils.NBTWhitelist;


/**
 * Block source backed by the player's ProjectE EMC and transmutation knowledge.
 */
public class ProjectEBlockSource implements BlockSource {

    public static final ProjectEBlockSource INSTANCE = new ProjectEBlockSource();

    private ProjectEBlockSource() {
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
    public Map<String, Integer> checkAvailability(Map<String, Integer> requirements, BlockSourceContext context) {
        Map<String, Integer> available = new HashMap<>();

        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            ItemStack requiredStack = BlockSourceUtils.keyToStack(entry.getKey());
            if (requiredStack.isEmpty()) {
                available.put(entry.getKey(), 0);
                continue;
            }

            available.put(entry.getKey(), countAvailable(requiredStack, context));
        }

        return available;
    }

    @Override
    public BlockExtractionResult batchExtractDetailed(Map<String, Integer> requirements, BlockSourceContext context,
                                                      boolean simulate) {
        if (!BlockSourceProviderId.EMC.isModAvailable()) this.refuse(requirements);

        IKnowledgeProvider knowledgeProvider = getKnowledgeProvider(context.getPlayer());
        if (knowledgeProvider == null) this.refuse(requirements);

        long remainingEmc = knowledgeProvider.getEmc();
        boolean extractedAny = false;

        BlockExtractionResult result = new BlockExtractionResult();
        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            String key = entry.getKey();
            ItemStack requiredStack = BlockSourceUtils.keyToStack(key);
            int needed = entry.getValue();

            if (requiredStack.isEmpty()) {
                result.addRemainder(key, needed);
                continue;
            }

            ItemStack normalizedStack = normalizeTransmutationStack(requiredStack);
            ItemStack providedStack = getProvidedStack(knowledgeProvider, requiredStack, normalizedStack);

            if (providedStack.isEmpty()) {
                result.addRemainder(key, needed);
                continue;
            }

            long emcValue = EMCHelper.getEmcValue(providedStack);
            if (emcValue <= 0) {
                result.addRemainder(key, needed);
                continue;
            }

            int available = (int) Math.min(Integer.MAX_VALUE, remainingEmc / emcValue);
            int extracted = Math.min(available, needed);

            if (extracted < needed) result.addRemainder(key, needed - extracted);
            if (extracted == 0) continue;

            extractedAny = true;

            if (!simulate) {
                result.addExtracted(providedStack, extracted);
                remainingEmc -= emcValue * extracted;
            }
        }

        if (!simulate && extractedAny) {
            knowledgeProvider.setEmc(Math.max(0, remainingEmc));

            if (context.getPlayer() instanceof EntityPlayerMP) {
                knowledgeProvider.sync((EntityPlayerMP) context.getPlayer());
            }
        }

        return result;
    }

    @Override
    public String getName() {
        return "ProjectE";
    }

    private int countAvailable(ItemStack requiredStack, BlockSourceContext context) {
        if (!BlockSourceProviderId.EMC.isModAvailable()) return 0;

        IKnowledgeProvider knowledgeProvider = getKnowledgeProvider(context.getPlayer());
        if (knowledgeProvider == null) return 0;

        ItemStack normalizedStack = normalizeTransmutationStack(requiredStack);
        ItemStack providedStack = getProvidedStack(knowledgeProvider, requiredStack, normalizedStack);
        if (providedStack.isEmpty()) return 0;

        long emcValue = EMCHelper.getEmcValue(providedStack);
        if (emcValue <= 0) return 0;

        return (int) Math.min(Integer.MAX_VALUE, knowledgeProvider.getEmc() / emcValue);
    }

    private Map<String, Integer> singleRequirement(ItemStack stack) {
        Map<String, Integer> requirements = new HashMap<>();
        requirements.put(BlockSourceUtils.stackToKey(stack), 1);

        return requirements;
    }

    /**
     * Normalizes an ItemStack for transmutation checks. See ProjectEXUtils.fixOutput for reference.
     * @param stack The stack to normalize
     * @return The stack that should be provided by ProjectE if the required stack is available.
     */
    private ItemStack normalizeTransmutationStack(ItemStack stack) {
        ItemStack normalized = ItemHelper.getNormalizedStack(stack);

        if (ItemHelper.isDamageable(normalized)) normalized.setItemDamage(0);
        if (normalized.hasTagCompound() && !NBTWhitelist.shouldDupeWithNBT(normalized)) normalized.setTagCompound(null);

        return normalized;
    }

    private ItemStack getProvidedStack(IKnowledgeProvider knowledgeProvider, ItemStack requiredStack, ItemStack normalizedStack) {
        ItemStack bestMatch = ItemStack.EMPTY;
        String bestKey = null;
        int bestSpecificity = Integer.MAX_VALUE;

        for (ItemStack knowledgeStack : knowledgeProvider.getKnowledge()) {
            ItemStack normalizedKnowledge = normalizeTransmutationStack(knowledgeStack);
            if (!BlockSourceUtils.matchesRequiredStack(normalizedKnowledge, requiredStack)) continue;

            String knowledgeKey = BlockSourceUtils.stackToKey(normalizedKnowledge);
            int specificity = BlockSourceUtils.getKeySpecificity(knowledgeKey);
            if (specificity > bestSpecificity) continue;
            if (specificity == bestSpecificity && bestKey != null && knowledgeKey.compareTo(bestKey) >= 0) continue;

            bestMatch = normalizedKnowledge.copy();
            bestMatch.setCount(1);
            bestKey = knowledgeKey;
            bestSpecificity = specificity;
        }

        if (!bestMatch.isEmpty()) return bestMatch;

        if (!requiredStack.hasTagCompound() && knowledgeProvider.hasKnowledge(normalizedStack)) {
            ItemStack providedStack = normalizedStack.copy();
            providedStack.setCount(1);
            return providedStack;
        }

        return ItemStack.EMPTY;
    }

    @Nullable
    @Optional.Method(modid = "projecte")
    private IKnowledgeProvider getKnowledgeProvider(EntityPlayer player) {
        ITransmutationProxy transmutationProxy = ProjectEAPI.getTransmutationProxy();
        if (transmutationProxy == null) return null;

        return transmutationProxy.getKnowledgeProviderFor(player.getUniqueID());
    }

}
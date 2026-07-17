// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.common.config.AutobuildConfig;
import com.machineryassembler.common.network.NetworkHandler;
import com.machineryassembler.common.network.PacketAutobuildMissingBlocks;
import com.machineryassembler.common.network.PacketAutobuildObstruction;
import com.machineryassembler.common.network.PacketAutobuildPlacementIssue;
import com.machineryassembler.common.network.PacketAutobuildPlacementIssue.IssueType;
import com.machineryassembler.common.network.PacketAutobuildPlacementIssue.PlacementIssue;
import com.machineryassembler.common.network.PacketAutobuildResult;
import com.machineryassembler.common.network.PacketAutobuildResult.ResultType;
import com.machineryassembler.common.structure.BlockRequirement;
import com.machineryassembler.common.structure.BlockStateMatcher;
import com.machineryassembler.common.structure.Structure;
import com.machineryassembler.common.structure.StructurePattern;
import com.machineryassembler.common.structure.StructureRegistry;
import com.machineryassembler.common.util.nbt.NBTMatchingHelper;


/**
 * Server-side handler for autobuild requests.
 * Manages chunk loading, obstruction detection, block extraction, and throttled placement.
 * Placement is spread across multiple ticks based on {@link AutobuildConfig#blocksPerTick}.
 */
public class ServerAutobuildHandler {

    private static final Map<String, SizedBlockPlacementInfo> sizedPlacementCache = new HashMap<>();
    private static final AutobuildProbeWorld probeWorld = new AutobuildProbeWorld();
    // 7x5x7 area for block size testing. This is for a single block
    private static final int SIZE_PROBE_RADIUS = 3;
    private static final int SIZE_PROBE_HEIGHT = 5;
    private static final BlockPos SIZE_PROBE_ORIGIN = new BlockPos(0, 64, 0);
    private static final int PLACEMENT_SCAN_MIN_Y = -1;
    private static final int PLACEMENT_SCAN_MAX_Y = SIZE_PROBE_HEIGHT;
    private static final int MAX_DEFERRED_PLACEMENT_ATTEMPTS = 2;
    private static final String VERBOSE_AUTOBUILD_LOG_PREFIX = "[Autobuild/Verbose]";

    /**
     * Handle an autobuild request from a client.
     */
    public static void handleAutobuildRequest(EntityPlayerMP player, ResourceLocation structureId, BlockPos origin,
                                              BlockSourceSettings blockSourceSettings) {
        WorldServer world = player.getServerWorld();
        Structure structure = StructureRegistry.getRegistry().getStructure(structureId);
        BlockSourceContext sourceContext = new BlockSourceContext(player, blockSourceSettings);
        BlockSourceManager sourceManager = BlockSourceManager.getInstance();

        if (structure == null) {
            MachineryAssembler.LOGGER.warn("Player {} requested autobuild for unknown structure: {}",
                player.getName(), structureId);
            NetworkHandler.INSTANCE.sendTo(
                new PacketAutobuildResult(ResultType.FAILED, 0, 0, 0), player);

            return;
        }

        // Check distance
        double distance = player.getDistanceSq(origin);
        if (AutobuildConfig.maxBuildDistance != 0 && distance > AutobuildConfig.maxBuildDistance * AutobuildConfig.maxBuildDistance) {
            MachineryAssembler.LOGGER.warn("Player {} attempted autobuild too far away: {} blocks",
                player.getName(), Math.sqrt(distance));
            NetworkHandler.INSTANCE.sendTo(
                new PacketAutobuildResult(ResultType.FAILED, 0, 0, 0), player);

            return;
        }

        StructurePattern pattern = structure.getPattern();
        ForgeChunkManager.Ticket ticket = loadChunks(world, pattern, origin);
        if (ticket == null) {
            NetworkHandler.INSTANCE.sendTo(
                new PacketAutobuildResult(ResultType.FAILED, 0, 0, 0), player);

            return;
        }

        List<Map.Entry<BlockPos, BlockRequirement>> sortedBlocks = prepareSortedBlocks(pattern);
        Map<BlockPos, PlacementAction> plannedActions = new HashMap<>();
        List<BlockPos> unresolvedPlanningPositions = new ArrayList<>();

        // TODO: Benchmark and improve performance for large structures.
        //       A big one may wait for up to half a minute before the player can see blocks.
        //       It seems that blocks are placed fine, just that we observe a huge lag spike at the start.
        // TODO: Sanity-check the NBT matching

        // Phase 1: Check for obstructions and plan placements in the fake world
        logPlanningStart(player, structureId, origin, sortedBlocks);

        Set<BlockPos> blockedPositions = new HashSet<>();
        Set<BlockPos> allObstructed = new LinkedHashSet<>();
        Map<String, Integer> required = new HashMap<>();

        while (true) {
            plannedActions.clear();
            unresolvedPlanningPositions.clear();
            required = orderRequirementsForInclusiveMatching(
                collectRequiredBlocks(
                    world,
                    origin,
                    sortedBlocks,
                    player,
                    plannedActions,
                    blockedPositions,
                    unresolvedPlanningPositions));

            if (!unresolvedPlanningPositions.isEmpty()) break;

            ResolvedObstructionResult obstructionResult = checkObstructions(
                world,
                pattern,
                origin,
                plannedActions,
                blockedPositions
            );
            allObstructed.addAll(obstructionResult.obstructedPositions);

            if (obstructionResult.blockedStructurePositions.isEmpty()) break;
            if (!AutobuildConfig.allowPartialBuilds) break;
            if (!blockedPositions.addAll(obstructionResult.blockedStructurePositions)) {
                logPlanningStall(structureId, origin, blockedPositions, obstructionResult, plannedActions);
                break;
            }
        }

        logPlanningEnd(structureId, plannedActions, required);

        if (!allObstructed.isEmpty()) {
            boolean aborted = !AutobuildConfig.allowPartialBuilds;
            NetworkHandler.INSTANCE.sendTo(new PacketAutobuildObstruction(new ArrayList<>(allObstructed)), player);

            if (aborted) {
                NetworkHandler.INSTANCE.sendTo(
                    new PacketAutobuildResult(ResultType.FAILED, 0, 0, allObstructed.size()), player);
                if (ticket != null) ForgeChunkManager.releaseTicket(ticket);

                return;
            }
        }

        if (!unresolvedPlanningPositions.isEmpty()) {
            logPlanningFailureDetails(
                structureId,
                origin,
                unresolvedPlanningPositions,
                blockedPositions,
                plannedActions);
            MachineryAssembler.LOGGER.warn(
                "Autobuild planning left {} unresolved positions for {} at {}. Continuing with {} planned actions and reserving generic items so unresolved positions can retry later when support appears.",
                unresolvedPlanningPositions.size(),
                structureId,
                formatBlockPos(origin),
                plannedActions.size());
        }

        // Phase 2: Simulate extraction in NBT-specific order so stricter requirements reserve
        // matching blocks before more generic requirements can consume them.
        Map<String, Integer> missing = sourceManager.batchExtractDetailed(required, sourceContext, true).getRemainder();

        if (!missing.isEmpty()) {
            boolean aborted = !AutobuildConfig.allowPartialBuilds;
            NetworkHandler.INSTANCE.sendTo(
                new PacketAutobuildMissingBlocks(missing, aborted), player);

            if (aborted) {
                NetworkHandler.INSTANCE.sendTo(
                    new PacketAutobuildResult(ResultType.FAILED, 0, 0, 0), player);
                if (ticket != null) ForgeChunkManager.releaseTicket(ticket);

                return;
            }
        }

        // Phase 3: Extract blocks
        // TODO: handle fluids, we need to check buckets and containers, extract, and insert back (at the feet if necessary)
        Map<String, Integer> toExtract = new HashMap<>(required);

        // Subtract missing blocks if partial builds allowed
        for (Map.Entry<String, Integer> entry : missing.entrySet()) {
            String key = entry.getKey();
            int avail = toExtract.getOrDefault(key, 0) - entry.getValue();

            if (avail <= 0) {
                toExtract.remove(key);
            } else {
                toExtract.put(key, avail);
            }
        }

        // Build the extracted map (toExtract - remainder)
        logExtractionStart(structureId, toExtract);
        BlockExtractionResult extractionResult = sourceManager.batchExtractDetailed(toExtract, sourceContext, false);
        Map<String, Integer> extractedCounts = extractionResult.getExtracted();
        logExtractionDetails(extractionResult);
        logExtractionEnd(structureId, extractionResult);
        logUnexpectedExtractionRemainder(structureId, toExtract, extractionResult);

        logPlacementStart(structureId, plannedActions, extractedCounts);

        // Phase 4: Start throttled placement with item-driven actions.
        ThrottledPlacementTask task = new ThrottledPlacementTask(
            world,
            origin,
            sortedBlocks,
            extractedCounts,
            missing,
            plannedActions,
            unresolvedPlanningPositions,
            player,
            ticket,
            structureId);
        task.start();
    }

    /**
     * Load all chunks needed for the structure.
     */
    private static ForgeChunkManager.Ticket loadChunks(WorldServer world, StructurePattern pattern, BlockPos origin) {
        ForgeChunkManager.Ticket ticket = ForgeChunkManager.requestTicket(
            MachineryAssembler.instance, world, ForgeChunkManager.Type.NORMAL);

        if (ticket == null) {
            MachineryAssembler.LOGGER.warn("Could not obtain chunk loading ticket");

            return null;
        }

        BlockPos min = pattern.getMin().add(origin);
        BlockPos max = pattern.getMax().add(origin);

        int minChunkX = min.getX() >> 4;
        int maxChunkX = max.getX() >> 4;
        int minChunkZ = min.getZ() >> 4;
        int maxChunkZ = max.getZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                ForgeChunkManager.forceChunk(ticket, new ChunkPos(cx, cz));
            }
        }

        return ticket;
    }

    /**
     * Check for obstructions - blocks that are non-air and don't match the structure.
     */
    private static ResolvedObstructionResult checkObstructions(WorldServer world,
                                                               StructurePattern pattern,
                                                               BlockPos origin,
                                                               Map<BlockPos, PlacementAction> plannedActions,
                                                               Set<BlockPos> blockedPositions) {
        Set<BlockPos> obstructed = new LinkedHashSet<>();
        Set<BlockPos> blockedStructurePositions = new HashSet<>();
        Set<BlockPos> checkedWorldPositions = new HashSet<>();

        for (Map.Entry<BlockPos, BlockRequirement> entry : pattern.getPattern().entrySet()) {
            BlockPos relPos = entry.getKey();
            if (blockedPositions.contains(relPos)) continue;

            BlockPos worldPos = origin.add(relPos);
            BlockRequirement requirement = entry.getValue();

            checkedWorldPositions.add(worldPos);

            IBlockState currentState = world.getBlockState(worldPos);

            // Skip if already correct
            if (requirement.matches(world, worldPos, false)) continue;

            // Check if there's an obstruction (non-air, non-replaceable block)
            if (isObstructed(world, worldPos, currentState)) {
                obstructed.add(worldPos);
                blockedStructurePositions.add(relPos);
            }
        }

        for (PlacementAction action : plannedActions.values()) {
            for (BlockPos offset : action.footprintOffsets) {
                BlockPos structureRelPos = action.attempt.targetRelPos.add(offset);
                BlockPos worldPos = origin.add(structureRelPos);

                if (checkedWorldPositions.contains(worldPos)) continue;
                checkedWorldPositions.add(worldPos);

                IBlockState currentState = world.getBlockState(worldPos);
                if (!isObstructed(world, worldPos, currentState)) continue;

                obstructed.add(worldPos);
                blockedStructurePositions.addAll(action.coveredPositions);
            }
        }

        return new ResolvedObstructionResult(new ArrayList<>(obstructed), blockedStructurePositions);
    }

    /**
     * Sort stricter NBT requirements first so simulated and real extraction reserve the most
     * specific blocks before generic requirements can claim them.
     */
    private static Map<String, Integer> orderRequirementsForInclusiveMatching(Map<String, Integer> requirements) {
        List<Map.Entry<String, Integer>> orderedEntries = new ArrayList<>(requirements.entrySet());
        orderedEntries.sort((left, right) -> {
            int specificityComparison = Integer.compare(BlockSourceUtils.getKeySpecificity(right.getKey()), BlockSourceUtils.getKeySpecificity(left.getKey()));
            if (specificityComparison != 0) return specificityComparison;

            return left.getKey().compareTo(right.getKey());
        });

        Map<String, Integer> orderedRequirements = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : orderedEntries) {
            orderedRequirements.put(entry.getKey(), entry.getValue());
        }

        return orderedRequirements;
    }

    /**
     * Prepare the sorted block list for placement (bottom to top, then X, then Z).
     */
    private static List<Map.Entry<BlockPos, BlockRequirement>> prepareSortedBlocks(StructurePattern pattern) {
        List<Map.Entry<BlockPos, BlockRequirement>> sortedBlocks = new ArrayList<>(pattern.getPattern().entrySet());
        sortedBlocks.sort(Comparator
            .comparingInt((Map.Entry<BlockPos, BlockRequirement> e) -> e.getKey().getY())
            .thenComparingInt(e -> e.getKey().getX())
            .thenComparingInt(e -> e.getKey().getZ()));

        return sortedBlocks;
    }

    /**
     * Collect required items by planning placements instead of counting pattern cells.
     * One successful item use can satisfy multiple structure positions.
     */
    private static Map<String, Integer> collectRequiredBlocks(WorldServer world, BlockPos origin,
                                                              List<Map.Entry<BlockPos, BlockRequirement>> sortedBlocks,
                                                              EntityPlayerMP player,
                                                              Map<BlockPos, PlacementAction> plannedActions,
                                                              Set<BlockPos> blockedPositions,
                                                              List<BlockPos> unresolvedPlanningPositions) {
        Map<String, Integer> required = new HashMap<>();
        Map<BlockPos, BlockRequirement> requirementsByPos = buildRequirementsByPos(sortedBlocks);
        Map<BlockPos, Integer> sortOrderByPos = buildSortOrderByPos(sortedBlocks);
        PlanningSimulation planningSimulation = beginPlanningSimulation(world, origin, sortedBlocks, player);
        Set<BlockPos> satisfied = new HashSet<>(blockedPositions);
        for (Map.Entry<BlockPos, BlockRequirement> entry : sortedBlocks) {
            BlockPos relPos = entry.getKey();
            BlockPos worldPos = origin.add(relPos);
            BlockRequirement requirement = entry.getValue();

            if (blockedPositions.contains(relPos)) continue;

            if (satisfied.contains(relPos) || requirement.matches(world, worldPos, false)) {
                satisfied.add(relPos);
                continue;
            }

            PlacementAction action = planPlacementAction(
                world,
                origin,
                sortedBlocks,
                requirementsByPos,
                sortOrderByPos,
                satisfied,
                relPos,
                requirement,
                player,
                planningSimulation
            );

            if (action == null) {
                logUnplannedRequirement(relPos, requirement, null, "no static footprint match was available");
                unresolvedPlanningPositions.add(relPos);
                required.merge(BlockSourceUtils.requirementToKey(requirement), 1, Integer::sum);
                satisfied.add(relPos);
                continue;
            }

            plannedActions.put(relPos, action);
            required.merge(action.extractedKey, 1, Integer::sum);
            logLargeFootprintPlanning(relPos, action);
            satisfied.addAll(action.coveredPositions);
        }

        return required;
    }

    private static PlacementAction planPlacementAction(WorldServer world,
                                                       BlockPos origin,
                                                       List<Map.Entry<BlockPos, BlockRequirement>> sortedBlocks,
                                                       Map<BlockPos, BlockRequirement> requirementsByPos,
                                                       Map<BlockPos, Integer> sortOrderByPos,
                                                       Set<BlockPos> claimedPositions,
                                                       BlockPos anchorRelPos,
                                                       BlockRequirement anchorRequirement,
                                                       EntityPlayerMP player,
                                                       PlanningSimulation planningSimulation) {
        ItemStack requiredStack = anchorRequirement.getRequiredStack();
        if (requiredStack.isEmpty()) return null;

        String extractedKey = BlockSourceUtils.stackToKey(requiredStack);
        SizedBlockPlacementInfo sizedPlacementInfo = getSizedPlacementInfo(world, requiredStack, player);
        PlacementMatch bestMatch = null;

        if (sizedPlacementInfo == null) return null;

        for (PlacementVariant variant : sizedPlacementInfo.variants) {
            for (Map.Entry<BlockPos, PlacedBlockSample> variantEntry : variant.placedBlocks.entrySet()) {
                if (!matchesRequirementStatic(anchorRequirement, variantEntry.getValue())) continue;

                BlockPos baseRelPos = anchorRelPos.subtract(variantEntry.getKey());
                PlacementAttempt shiftedAttempt = offsetPlacementAttempt(variant.attempt, baseRelPos);
                PlacementProbeResult probe = probePlacementWithPlayer(
                    planningSimulation.world,
                    planningSimulation.origin,
                    shiftedAttempt,
                    planningSimulation.player,
                    planningSimulation.player,
                    false
                );
                if (probe == null) continue;

                PlacementMatch candidate = createPlacementMatch(
                    shiftedAttempt,
                    probe.placedBlocks,
                    anchorRelPos.subtract(shiftedAttempt.targetRelPos),
                    requirementsByPos,
                    sortOrderByPos,
                    claimedPositions,
                    anchorRelPos
                );
                if (!isBetterPlacementMatch(candidate, bestMatch, anchorRelPos)) continue;

                bestMatch = candidate;
            }
        }

        if (bestMatch != null) {
            PlacementProbeResult committedProbe = probePlacementWithPlayer(
                planningSimulation.world,
                planningSimulation.origin,
                bestMatch.attempt,
                planningSimulation.player,
                planningSimulation.player,
                true
            );
            if (committedProbe == null) return null;

            return new PlacementAction(
                extractedKey,
                bestMatch.attempt,
                bestMatch.coveredPositions,
                bestMatch.variant.placedBlocks.keySet());
        }

            return null;
    }

    private static PlanningSimulation beginPlanningSimulation(WorldServer sourceWorld,
                                                              BlockPos origin,
                                                              List<Map.Entry<BlockPos, BlockRequirement>> sortedBlocks,
                                                              EntityPlayerMP player) {
        AutobuildProbeWorld planningWorld = new AutobuildProbeWorld();
        seedPlanningWorld(planningWorld, sourceWorld, origin, sortedBlocks);

        return new PlanningSimulation(planningWorld, new AutobuildProbePlayer(planningWorld, player), origin);
    }

    private static void seedPlanningWorld(AutobuildProbeWorld planningWorld,
                                          WorldServer sourceWorld,
                                          BlockPos origin,
                                          List<Map.Entry<BlockPos, BlockRequirement>> sortedBlocks) {
        if (sortedBlocks.isEmpty()) return;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (Map.Entry<BlockPos, BlockRequirement> entry : sortedBlocks) {
            BlockPos worldPos = origin.add(entry.getKey());

            if (worldPos.getX() < minX) minX = worldPos.getX();
            if (worldPos.getY() < minY) minY = worldPos.getY();
            if (worldPos.getZ() < minZ) minZ = worldPos.getZ();
            if (worldPos.getX() > maxX) maxX = worldPos.getX();
            if (worldPos.getY() > maxY) maxY = worldPos.getY();
            if (worldPos.getZ() > maxZ) maxZ = worldPos.getZ();
        }

        minX -= SIZE_PROBE_RADIUS;
        minY += PLACEMENT_SCAN_MIN_Y;
        minZ -= SIZE_PROBE_RADIUS;
        maxX += SIZE_PROBE_RADIUS;
        maxY += PLACEMENT_SCAN_MAX_Y;
        maxZ += SIZE_PROBE_RADIUS;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos worldPos = new BlockPos(x, y, z);
                    PlacedBlockSample placedBlock = capturePlacedBlockSample(sourceWorld, worldPos);
                    if (placedBlock == null) continue;

                    restorePlacementBlock(planningWorld, worldPos, placedBlock);
                }
            }
        }
    }

        private static PlacementAction planPlacementActionFallback(WorldServer world,
                                                                   BlockPos origin,
                                                                   List<Map.Entry<BlockPos, BlockRequirement>> sortedBlocks,
                                                                   BlockPos anchorRelPos,
                                                                   BlockRequirement anchorRequirement,
                                                                   EntityPlayerMP player) {
            ItemStack requiredStack = anchorRequirement.getRequiredStack();
            if (requiredStack.isEmpty()) return null;

            Map<BlockPos, BlockRequirement> requirementsByPos = buildRequirementsByPos(sortedBlocks);
            Map<BlockPos, Integer> sortOrderByPos = buildSortOrderByPos(sortedBlocks);
            String extractedKey = BlockSourceUtils.stackToKey(requiredStack);
            PlacementMatch bestMatch = findBestPlacementMatch(
                world,
                origin,
                anchorRelPos,
                anchorRequirement,
                requiredStack,
                player,
                requirementsByPos,
                sortOrderByPos
            );

            if (bestMatch == null) return null;

            return new PlacementAction(
                extractedKey,
                bestMatch.attempt,
                bestMatch.coveredPositions,
                bestMatch.variant.placedBlocks.keySet());
        }

        private static PlacementMatch findBestPlacementMatch(WorldServer world,
                                                             BlockPos origin,
                                                             BlockPos anchorRelPos,
                                                             BlockRequirement anchorRequirement,
                                                             ItemStack requiredStack,
                                                             EntityPlayerMP player,
                                                             Map<BlockPos, BlockRequirement> requirementsByPos,
                                                             Map<BlockPos, Integer> sortOrderByPos) {
            EntityPlayer simulationPlayer = new AutobuildProbePlayer(world, player);
            PlacementMatch bestMatch = null;

            for (PlacementAttempt attempt : createPlacementAttempts(anchorRelPos, requiredStack, player)) {
                PlacementProbeResult probe = probePlacement(world, origin, attempt, player, simulationPlayer);
                if (probe == null) continue;

                PlacementVariant variant = new PlacementVariant(attempt, probe.placedBlocks);

                for (Map.Entry<BlockPos, PlacedBlockSample> variantEntry : variant.placedBlocks.entrySet()) {
                    if (!matchesRequirementStatic(anchorRequirement, variantEntry.getValue())) continue;

                    PlacementMatch candidate = createPlacementMatch(
                        attempt,
                        probe.placedBlocks,
                        variantEntry.getKey(),
                        requirementsByPos,
                        sortOrderByPos,
                        new HashSet<>(),
                        anchorRelPos
                    );
                    if (!isBetterPlacementMatch(candidate, bestMatch, anchorRelPos)) continue;

                    bestMatch = candidate;
                }
            }

            return bestMatch;
        }

        private static PlacementProbeResult probePlacement(WorldServer world,
                                                           BlockPos origin,
                                                           PlacementAttempt attempt,
                                                           EntityPlayerMP player,
                                                           EntityPlayer simulationPlayer) {
            PlacementProbeResult probeResult = probePlacementWithPlayer(
                world,
                origin,
                attempt,
                simulationPlayer,
                player,
                false
            );

            if (probeResult != null) return probeResult;

            return probePlacementWithPlayer(world, origin, attempt, player, player, false);
        }


    private static boolean isBetterPlacementMatch(PlacementMatch candidate,
                                                  PlacementMatch currentBest,
                                                  BlockPos anchorRelPos) {
        if (candidate == null) return false;
        if (currentBest == null) return true;

        int coverageComparison = Integer.compare(
            candidate.coveredPositions.size(), currentBest.coveredPositions.size());
        if (coverageComparison != 0) return coverageComparison > 0;

        if (candidate.centerDistanceScore != currentBest.centerDistanceScore) {
            return candidate.centerDistanceScore < currentBest.centerDistanceScore;
        }

        int footprintComparison = Integer.compare(
            candidate.variant.placedBlocks.size(), currentBest.variant.placedBlocks.size());
        if (footprintComparison != 0) return footprintComparison > 0;

        int candidateAnchorDistance = getManhattanDistance(candidate.attempt.clickedRelPos, anchorRelPos);
        int currentAnchorDistance = getManhattanDistance(currentBest.attempt.clickedRelPos, anchorRelPos);
        if (candidateAnchorDistance != currentAnchorDistance) return candidateAnchorDistance < currentAnchorDistance;

        return candidate.attempt.clickedFace.ordinal() < currentBest.attempt.clickedFace.ordinal();
    }

    private static int getManhattanDistance(BlockPos left, BlockPos right) {
        return Math.abs(left.getX() - right.getX())
            + Math.abs(left.getY() - right.getY())
            + Math.abs(left.getZ() - right.getZ());
    }

    private static Map<BlockPos, BlockRequirement> buildRequirementsByPos(
        List<Map.Entry<BlockPos, BlockRequirement>> sortedBlocks) {
        Map<BlockPos, BlockRequirement> requirementsByPos = new HashMap<>();

        for (Map.Entry<BlockPos, BlockRequirement> entry : sortedBlocks) {
            requirementsByPos.put(entry.getKey(), entry.getValue());
        }

        return requirementsByPos;
    }

    private static Map<BlockPos, Integer> buildSortOrderByPos(
        List<Map.Entry<BlockPos, BlockRequirement>> sortedBlocks) {
        Map<BlockPos, Integer> sortOrderByPos = new HashMap<>();
        int index = 0;

        for (Map.Entry<BlockPos, BlockRequirement> entry : sortedBlocks) {
            sortOrderByPos.put(entry.getKey(), index);
            index++;
        }

        return sortOrderByPos;
    }

    private static SizedBlockPlacementInfo getSizedPlacementInfo(WorldServer world,
                                                                 ItemStack requiredStack,
                                                                 EntityPlayerMP player) {
        String cacheKey = BlockSourceUtils.stackToKey(requiredStack);
        SizedBlockPlacementInfo cached = sizedPlacementCache.get(cacheKey);
        if (cached != null) return cached;

        SizedBlockPlacementInfo sizedPlacementInfo = probeSizedPlacementInfo(requiredStack, player);
        if (sizedPlacementInfo == null) return null;

        sizedPlacementCache.put(cacheKey, sizedPlacementInfo);
        return sizedPlacementInfo;
    }

    private static SizedBlockPlacementInfo probeSizedPlacementInfo(ItemStack requiredStack,
                                                                   EntityPlayerMP player) {
        ProbeArena probeArena = beginSizeProbeArena();
        if (probeArena == null) return null;

        AutobuildProbePlayer probePlayer = new AutobuildProbePlayer(probeWorld, player);
        Map<String, PlacementVariant> variantsBySignature = new LinkedHashMap<>();

        try {
            for (PlacementAttempt attempt : createPlacementAttempts(BlockPos.ORIGIN, requiredStack, player)) {
                ProbeSupportState supportState = prepareProbePlacementSupport(
                    probeWorld,
                    probeArena.origin,
                    attempt);

                try {
                    PlacementProbeResult probe = probePlacementWithPlayer(
                        probeWorld,
                        probeArena.origin,
                        attempt,
                        probePlayer,
                        probePlayer,
                        false
                    );
                    if (probe == null) continue;

                    PlacementVariant candidate = new PlacementVariant(attempt, probe.placedBlocks);
                    String signature = buildPlacementVariantSignature(candidate);
                    PlacementVariant current = variantsBySignature.get(signature);
                    if (!isBetterPlacementVariant(candidate, current)) continue;

                    variantsBySignature.put(signature, candidate);
                } finally {
                    restoreProbePlacementSupport(probeWorld, supportState);
                }
            }
        } finally {
            endSizeProbeArena(probeArena);
        }

        if (variantsBySignature.isEmpty()) {
            logMissingProbeVariants(requiredStack, player);
            return null;
        }

        return new SizedBlockPlacementInfo(new ArrayList<>(variantsBySignature.values()));
    }

    private static ProbeArena beginSizeProbeArena() {
        probeWorld.clearAll();

        for (int x = -SIZE_PROBE_RADIUS; x <= SIZE_PROBE_RADIUS; x++) {
            for (int z = -SIZE_PROBE_RADIUS; z <= SIZE_PROBE_RADIUS; z++) {
                probeWorld.setBlockState(SIZE_PROBE_ORIGIN.add(x, -1, z), Blocks.STONE.getDefaultState(), 2);
            }
        }

        return new ProbeArena(SIZE_PROBE_ORIGIN, new ArrayList<>());
    }

    private static ProbeSupportState prepareProbePlacementSupport(World world,
                                                                 BlockPos origin,
                                                                 PlacementAttempt attempt) {
        BlockPos targetWorldPos = origin.add(attempt.targetRelPos);
        BlockPos clickedPos = origin.add(attempt.clickedRelPos);

        if (clickedPos.equals(targetWorldPos)) return null;

        IBlockState clickedState = world.getBlockState(clickedPos);
        if (!needsProbePlacementSupport(world, clickedPos, attempt.clickedFace, clickedState)) return null;

        PlacedBlockSample previousBlock = capturePlacedBlockSample(world, clickedPos);

        // Support-sensitive items like wall skulls need a solid clicked block during size probing.
        world.setBlockState(clickedPos, Blocks.STONE.getDefaultState(), 2);

        return new ProbeSupportState(clickedPos, previousBlock);
    }

    private static boolean needsProbePlacementSupport(World world,
                                                      BlockPos clickedPos,
                                                      EnumFacing clickedFace,
                                                      IBlockState clickedState) {
        if (clickedState.getBlock().isReplaceable(world, clickedPos)) return true;

        return !clickedState.getMaterial().isSolid() && !world.isSideSolid(clickedPos, clickedFace, true);
    }

    private static void restoreProbePlacementSupport(World world, ProbeSupportState supportState) {
        if (supportState == null) return;

        restorePlacementBlock(world, supportState.clickedPos, supportState.previousBlock);
    }

    private static void endSizeProbeArena(ProbeArena probeArena) {
        probeWorld.clearAll();
    }

    private static String buildPlacementVariantSignature(PlacementVariant variant) {
        List<Map.Entry<BlockPos, PlacedBlockSample>> entries = new ArrayList<>(variant.placedBlocks.entrySet());
        entries.sort(Comparator
            .comparingInt((Map.Entry<BlockPos, PlacedBlockSample> e) -> e.getKey().getX())
            .thenComparingInt(e -> e.getKey().getY())
            .thenComparingInt(e -> e.getKey().getZ()));

        StringBuilder signature = new StringBuilder();

        for (Map.Entry<BlockPos, PlacedBlockSample> entry : entries) {
            BlockPos offset = entry.getKey();
            PlacedBlockSample sample = entry.getValue();

            signature.append(offset.getX())
                .append(',')
                .append(offset.getY())
                .append(',')
                .append(offset.getZ())
                .append('=')
                .append(BlockSourceUtils.stateToKey(sample.state));

            if (sample.tileTag != null) {
                signature.append('|').append(sample.tileTag.toString());
            }

            signature.append(';');
        }

        return signature.toString();
    }

    private static boolean isBetterPlacementVariant(PlacementVariant candidate, PlacementVariant current) {
        if (candidate == null) return false;
        if (current == null) return true;

        int candidateTargetDistance = getManhattanDistance(candidate.attempt.targetRelPos, BlockPos.ORIGIN);
        int currentTargetDistance = getManhattanDistance(current.attempt.targetRelPos, BlockPos.ORIGIN);
        if (candidateTargetDistance != currentTargetDistance) return candidateTargetDistance < currentTargetDistance;

        int candidateClickedDistance = getManhattanDistance(candidate.attempt.clickedRelPos, candidate.attempt.targetRelPos);
        int currentClickedDistance = getManhattanDistance(current.attempt.clickedRelPos, current.attempt.targetRelPos);
        if (candidateClickedDistance != currentClickedDistance) return candidateClickedDistance < currentClickedDistance;

        boolean candidateUsesFloorSupport = candidate.attempt.clickedFace == EnumFacing.UP
            && candidate.attempt.clickedRelPos.equals(candidate.attempt.targetRelPos.down());
        boolean currentUsesFloorSupport = current.attempt.clickedFace == EnumFacing.UP
            && current.attempt.clickedRelPos.equals(current.attempt.targetRelPos.down());
        if (candidateUsesFloorSupport != currentUsesFloorSupport) return candidateUsesFloorSupport;

        return candidate.attempt.clickedFace.ordinal() < current.attempt.clickedFace.ordinal();
    }

    private static PlacementMatch createPlacementMatch(PlacementAttempt attempt,
                                                       Map<BlockPos, PlacedBlockSample> placedBlocks,
                                                       BlockPos anchorOffset,
                                                       Map<BlockPos, BlockRequirement> requirementsByPos,
                                                       Map<BlockPos, Integer> sortOrderByPos,
                                                       Set<BlockPos> claimedPositions,
                                                       BlockPos anchorRelPos) {
        if (placedBlocks.isEmpty()) return null;

        PlacementVariant variant = new PlacementVariant(attempt, placedBlocks);
        Set<BlockPos> coveredPositions = new HashSet<>();

        for (Map.Entry<BlockPos, PlacedBlockSample> entry : placedBlocks.entrySet()) {
            BlockPos structureRelPos = attempt.targetRelPos.add(entry.getKey());
            BlockRequirement requirement = requirementsByPos.get(structureRelPos);
            if (requirement == null) continue;
            if (claimedPositions.contains(structureRelPos)) return null;
            if (!matchesRequirementStatic(requirement, entry.getValue())) return null;

            coveredPositions.add(structureRelPos);
        }

        if (coveredPositions.isEmpty()) return null;
        if (!coveredPositions.contains(anchorRelPos)) return null;
        if (!isPrimaryCoveredPosition(anchorRelPos, coveredPositions, sortOrderByPos)) return null;

        int centerDistanceScore = getCenterDistanceScore(variant, anchorOffset);

        return new PlacementMatch(attempt, coveredPositions, variant, centerDistanceScore);
    }

    private static boolean isPrimaryCoveredPosition(BlockPos anchorRelPos,
                                                    Set<BlockPos> coveredPositions,
                                                    Map<BlockPos, Integer> sortOrderByPos) {
        int anchorOrder = sortOrderByPos.getOrDefault(anchorRelPos, Integer.MAX_VALUE);

        for (BlockPos coveredPos : coveredPositions) {
            if (sortOrderByPos.getOrDefault(coveredPos, Integer.MAX_VALUE) < anchorOrder) return false;
        }

        return true;
    }

    private static PlacementAttempt offsetPlacementAttempt(PlacementAttempt template, BlockPos baseRelPos) {
        return new PlacementAttempt(
            template.stack,
            baseRelPos.add(template.targetRelPos),
            baseRelPos.add(template.clickedRelPos),
            template.clickedFace,
            template.hitX,
            template.hitY,
            template.hitZ,
            template.horizontalFacing
        );
    }

    private static int getCenterDistanceScore(PlacementVariant variant, BlockPos anchorOffset) {
        int centerX = variant.minOffset.getX() + variant.maxOffset.getX();
        int centerY = variant.minOffset.getY() + variant.maxOffset.getY();
        int centerZ = variant.minOffset.getZ() + variant.maxOffset.getZ();

        return Math.abs(anchorOffset.getX() * 2 - centerX)
            + Math.abs(anchorOffset.getY() * 2 - centerY)
            + Math.abs(anchorOffset.getZ() * 2 - centerZ);
    }

    private static boolean matchesRequirementStatic(BlockRequirement requirement, PlacedBlockSample placedBlock) {
        if (placedBlock == null) return false;

        IBlockState placedState = placedBlock.state;
        Block placedBlockType = placedState.getBlock();
        int placedMeta = placedBlockType.getMetaFromState(placedState);

        for (BlockStateMatcher descriptor : requirement.getMatchingStates()) {
            for (IBlockState applicable : descriptor.getApplicable()) {
                Block applicableBlock = applicable.getBlock();
                int applicableMeta = applicableBlock.getMetaFromState(applicable);

                if (applicableBlock != placedBlockType || applicableMeta != placedMeta) continue;

                NBTTagCompound matchingTag = requirement.getMatchingTag();
                if (matchingTag == null || matchingTag.getSize() <= 0) return true;
                if (placedBlock.tileTag == null) return false;

                return NBTMatchingHelper.matchNBTCompound(matchingTag, placedBlock.tileTag);
            }
        }

        return false;
    }

    private static void logPlanningStart(EntityPlayerMP player,
                                         ResourceLocation structureId,
                                         BlockPos origin,
                                         List<Map.Entry<BlockPos, BlockRequirement>> sortedBlocks) {
        if (!AutobuildConfig.verboseAutobuildLogging) return;

        MachineryAssembler.LOGGER.info("Planning start for {} by {} at {} with {} structure positions",
            structureId,
            player.getName(),
            formatBlockPos(origin),
            sortedBlocks.size());
    }

    private static void logPlanningEnd(ResourceLocation structureId,
                                       Map<BlockPos, PlacementAction> plannedActions,
                                       Map<String, Integer> required) {
        if (!AutobuildConfig.verboseAutobuildLogging) return;

        MachineryAssembler.LOGGER.info(
            "Planning end for {}: {} required items across {} keys, {} planned actions, {} large-footprint actions",
            structureId,
            countTotalItems(required),
            required.size(),
            plannedActions.size(),
            countLargePlannedActions(plannedActions));
    }

            private static void logUnplannedRequirement(BlockPos relPos,
                                BlockRequirement requirement,
                                PlacementAction action,
                                String reason) {
            if (!AutobuildConfig.verboseAutobuildLogging) return;

            MachineryAssembler.LOGGER.warn(
                "{} Planning gap for {} at {}: reason={}, candidateAttempt={}, candidateCoverage={}",
                VERBOSE_AUTOBUILD_LOG_PREFIX,
                formatExtractedKeyLabel(BlockSourceUtils.requirementToKey(requirement)),
                formatBlockPos(relPos),
                reason,
                action == null ? "none" : formatPlacementAttempt(action.attempt),
                action == null ? "[]" : formatRelativePositions(action.coveredPositions));
            }

    private static void logLargeFootprintPlanning(BlockPos anchorRelPos, PlacementAction action) {
        if (!AutobuildConfig.verboseAutobuildLogging) return;
        if (action.footprintOffsets.size() <= 1) return;

        MachineryAssembler.LOGGER.info(
            "Large footprint {} at {}: registeredSpots={}, detectedFootprint={}, footprintMatch={}/{}",
            formatExtractedKeyLabel(action.extractedKey),
            formatBlockPos(anchorRelPos),
            action.coveredPositions.size(),
            formatRelativePositions(action.footprintOffsets),
            action.coveredPositions.size(),
            action.footprintOffsets.size());
    }

    private static void logExtractionStart(ResourceLocation structureId, Map<String, Integer> toExtract) {
        if (!AutobuildConfig.verboseAutobuildLogging) return;

        MachineryAssembler.LOGGER.info("Extraction start for {}: {} requested items across {} keys",
            structureId,
            countTotalItems(toExtract),
            toExtract.size());
    }

    private static void logExtractionDetails(BlockExtractionResult extractionResult) {
        if (!AutobuildConfig.verboseAutobuildLogging) return;

        if (extractionResult.getExtracted().isEmpty()) {
            MachineryAssembler.LOGGER.info("Extraction used no items");
            return;
        }

        MachineryAssembler.LOGGER.info("Extraction details for {} extracted items across {} keys:",
            countTotalItems(extractionResult.getExtracted()),
            extractionResult.getExtracted().size());
        for (Map.Entry<String, Integer> entry : extractionResult.getExtracted().entrySet()) {
            MachineryAssembler.LOGGER.info("- {} x{} via {}",
                formatExtractedKeyLabel(entry.getKey()),
                entry.getValue(),
                formatSourceBreakdown(extractionResult.getExtractedBySource().get(entry.getKey())));
        }
    }

    private static void logExtractionEnd(ResourceLocation structureId, BlockExtractionResult extractionResult) {
        if (!AutobuildConfig.verboseAutobuildLogging) return;

        MachineryAssembler.LOGGER.info(
            "Extraction end for {}: {} extracted items across {} keys, {} remainder across {} keys",
            structureId,
            countTotalItems(extractionResult.getExtracted()),
            extractionResult.getExtracted().size(),
            countTotalItems(extractionResult.getRemainder()),
            extractionResult.getRemainder().size());
    }

    private static void logPlacementStart(ResourceLocation structureId,
                                          Map<BlockPos, PlacementAction> plannedActions,
                                          Map<String, Integer> extractedCounts) {
        if (!AutobuildConfig.verboseAutobuildLogging) return;

        MachineryAssembler.LOGGER.info(
            "Placement start for {}: {} extracted items across {} keys, {} planned actions",
            structureId,
            countTotalItems(extractedCounts),
            extractedCounts.size(),
            plannedActions.size());
    }

    private static void logPlanningStall(ResourceLocation structureId,
                                         BlockPos origin,
                                         Set<BlockPos> blockedPositions,
                                         ResolvedObstructionResult obstructionResult,
                                         Map<BlockPos, PlacementAction> plannedActions) {
        if (!AutobuildConfig.verboseAutobuildLogging) return;

        MachineryAssembler.LOGGER.warn(
            "{} Planning stalled for {} at {}: obstruction filtering made no progress, blockedPositions={}, obstructedWorldPositions={}, plannedAnchors={}",
            VERBOSE_AUTOBUILD_LOG_PREFIX,
            structureId,
            formatBlockPos(origin),
            formatRelativePositions(blockedPositions),
            formatPositions(obstructionResult.obstructedPositions),
            formatRelativePositions(plannedActions.keySet()));
    }

    private static void logPlanningFailureDetails(ResourceLocation structureId,
                                                  BlockPos origin,
                                                  List<BlockPos> unresolvedPlanningPositions,
                                                  Set<BlockPos> blockedPositions,
                                                  Map<BlockPos, PlacementAction> plannedActions) {
        if (!AutobuildConfig.verboseAutobuildLogging) return;

        MachineryAssembler.LOGGER.warn(
            "{} Planning failure details for {} at {}: unresolvedPositions={}, blockedPositions={}, plannedAnchors={}",
            VERBOSE_AUTOBUILD_LOG_PREFIX,
            structureId,
            formatBlockPos(origin),
            formatPositions(unresolvedPlanningPositions),
            formatRelativePositions(blockedPositions),
            formatRelativePositions(plannedActions.keySet()));
    }

    private static void logUnexpectedExtractionRemainder(ResourceLocation structureId,
                                                         Map<String, Integer> requestedCounts,
                                                         BlockExtractionResult extractionResult) {
        if (!AutobuildConfig.verboseAutobuildLogging) return;
        if (extractionResult.getRemainder().isEmpty()) return;

        MachineryAssembler.LOGGER.warn(
            "{} Real extraction left an unexpected remainder for {}: requested={}, extracted={}, remainder={}",
            VERBOSE_AUTOBUILD_LOG_PREFIX,
            structureId,
            formatKeyCounts(requestedCounts),
            formatKeyCounts(extractionResult.getExtracted()),
            formatKeyCounts(extractionResult.getRemainder()));
    }

    private static void logMissingProbeVariants(ItemStack requiredStack, EntityPlayerMP player) {
        if (!AutobuildConfig.verboseAutobuildLogging) return;

        MachineryAssembler.LOGGER.warn(
            "{} Footprint probing produced no successful variants for {} while planning for {}",
            VERBOSE_AUTOBUILD_LOG_PREFIX,
            formatExtractedKeyLabel(BlockSourceUtils.stackToKey(requiredStack)),
            player.getName());
    }

    private static int countLargePlannedActions(Map<BlockPos, PlacementAction> plannedActions) {
        int largeCount = 0;

        for (PlacementAction action : plannedActions.values()) {
            if (action.footprintOffsets.size() <= 1) continue;

            largeCount++;
        }

        return largeCount;
    }

    private static int countTotalItems(Map<String, Integer> counts) {
        int total = 0;

        for (int count : counts.values()) total += count;

        return total;
    }

    private static String formatExtractedKeyLabel(String key) {
        return BlockSourceUtils.getDisplayName(key) + " [" + key + "]";
    }

    private static String formatSourceBreakdown(Map<BlockSourceProviderId, Integer> sourceBreakdown) {
        if (sourceBreakdown == null || sourceBreakdown.isEmpty()) return "unknown source";

        StringBuilder summary = new StringBuilder();
        boolean first = true;

        for (Map.Entry<BlockSourceProviderId, Integer> entry : sourceBreakdown.entrySet()) {
            if (!first) summary.append(", ");

            summary.append(entry.getKey().getSerializedName())
                .append(' ')
                .append('x')
                .append(entry.getValue());
            first = false;
        }

        return summary.toString();
    }

    private static String formatKeyCounts(Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) return "[]";

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Map.Entry.comparingByKey());

        StringBuilder summary = new StringBuilder("[");

        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) summary.append(", ");

            Map.Entry<String, Integer> entry = entries.get(index);
            summary.append(formatExtractedKeyLabel(entry.getKey()))
                .append(' ')
                .append('x')
                .append(entry.getValue());
        }

        summary.append(']');
        return summary.toString();
    }

    private static String formatPositions(Iterable<BlockPos> positions) {
        List<BlockPos> sortedPositions = new ArrayList<>();

        for (BlockPos pos : positions) sortedPositions.add(pos);

        sortedPositions.sort(Comparator
            .comparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ));

        StringBuilder summary = new StringBuilder("[");

        for (int index = 0; index < sortedPositions.size(); index++) {
            if (index > 0) summary.append(", ");

            summary.append(formatBlockPos(sortedPositions.get(index)));
        }

        summary.append(']');
        return summary.toString();
    }

    private static String formatRelativePositions(Set<BlockPos> positions) {
        return formatPositions(positions);
    }

    private static String formatBlockPos(BlockPos pos) {
        return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private static String formatPlacementAttempt(PlacementAttempt attempt) {
        if (attempt == null) return "none";

        return "target=" + formatBlockPos(attempt.targetRelPos)
            + ", clicked=" + formatBlockPos(attempt.clickedRelPos)
            + ", face=" + attempt.clickedFace
            + ", hit=(" + attempt.hitX + "," + attempt.hitY + "," + attempt.hitZ + ")"
            + ", horizontal=" + (attempt.horizontalFacing == null ? "none" : attempt.horizontalFacing.name());
    }

    private static String formatObservedBlock(World world, BlockPos worldPos) {
        IBlockState state = world.getBlockState(worldPos);
        StringBuilder summary = new StringBuilder(BlockSourceUtils.stateToKey(state));
        NBTTagCompound tileTag = getSanitizedTileTag(world, worldPos);

        if (tileTag != null && tileTag.getSize() > 0) {
            summary.append(' ').append(tileTag);
        }

        return summary.toString();
    }

    private static List<PlacementAttempt> createPlacementAttempts(BlockPos anchorRelPos,
                                                                  ItemStack requiredStack,
                                                                  EntityPlayerMP player) {
        List<PlacementAttempt> attempts = new ArrayList<>();
        List<BlockPos> targetCandidates = getStrictTargetCandidates(anchorRelPos);
        List<EnumFacing> faceOrder = getPlacementFaceOrder();
        List<EnumFacing> horizontalOrder = getHorizontalFacingOrder(player, anchorRelPos);

        for (BlockPos targetRelPos : targetCandidates) {
            for (EnumFacing facing : faceOrder) {
                addPlacementAttempt(attempts, requiredStack, targetRelPos, targetRelPos, facing, horizontalOrder);
                addPlacementAttempt(attempts, requiredStack, targetRelPos, targetRelPos.offset(facing.getOpposite()),
                    facing, horizontalOrder);
            }
        }

        return attempts;
    }

    private static List<BlockPos> getStrictTargetCandidates(BlockPos anchorRelPos) {
        List<BlockPos> targets = new ArrayList<>();
        targets.add(anchorRelPos);

        return targets;
    }

    private static List<EnumFacing> getPlacementFaceOrder() {
        List<EnumFacing> faces = new ArrayList<>();
        faces.add(EnumFacing.UP);
        faces.add(EnumFacing.NORTH);
        faces.add(EnumFacing.SOUTH);
        faces.add(EnumFacing.WEST);
        faces.add(EnumFacing.EAST);
        faces.add(EnumFacing.DOWN);
        return faces;
    }

    private static List<EnumFacing> getHorizontalFacingOrder(EntityPlayerMP player, BlockPos anchorRelPos) {
        List<EnumFacing> facings = new ArrayList<>();
        EnumFacing playerFacing = player.getHorizontalFacing();
        facings.add(playerFacing);

        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            if (facing == playerFacing) continue;

            facings.add(facing);
        }

        return facings;
    }

    private static void addPlacementAttempt(List<PlacementAttempt> attempts,
                                            ItemStack requiredStack,
                                            BlockPos targetRelPos,
                                            BlockPos clickedRelPos,
                                            EnumFacing facing,
                                            List<EnumFacing> horizontalOrder) {
        float hitX = facing.getAxis() == EnumFacing.Axis.X
            ? (facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 1.0F : 0.0F)
            : 0.5F;
        float hitY = facing.getAxis() == EnumFacing.Axis.Y
            ? (facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 1.0F : 0.0F)
            : 0.5F;
        float hitZ = facing.getAxis() == EnumFacing.Axis.Z
            ? (facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 1.0F : 0.0F)
            : 0.5F;

        for (EnumFacing horizontal : horizontalOrder) {
            attempts.add(new PlacementAttempt(requiredStack, targetRelPos, clickedRelPos,
                facing, hitX, hitY, hitZ, horizontal));
        }
    }

    private static PlacementProbeResult probePlacementWithPlayer(World world,
                                                                 BlockPos origin,
                                                                 PlacementAttempt attempt,
                                                                 EntityPlayer placementPlayer,
                                                                 EntityPlayer sourcePlayer,
                                                                 boolean keepChanges) {
        BlockPos clickedPos = origin.add(attempt.clickedRelPos);
        BlockPos targetWorldPos = origin.add(attempt.targetRelPos);

        if (!world.isBlockLoaded(clickedPos) || !world.isBlockLoaded(targetWorldPos)) return null;

        List<BlockSnapshot> previousSnapshots = new ArrayList<>(world.capturedBlockSnapshots);
        world.capturedBlockSnapshots.clear();
        List<BlockSnapshot> probeSnapshots = new ArrayList<>();
        Map<BlockPos, PlacedBlockSample> beforeBlocks = capturePlacementRegion(world, targetWorldPos);
        ItemStack originalMainHand = placementPlayer.getHeldItem(EnumHand.MAIN_HAND);
        float originalYaw = placementPlayer.rotationYaw;
        float originalPrevYaw = placementPlayer.prevRotationYaw;
        EnumActionResult result = EnumActionResult.FAIL;
        PlacementProbeResult probeResult = null;

        if (attempt.horizontalFacing != null) {
            placementPlayer.rotationYaw = attempt.horizontalFacing.getHorizontalAngle();
            placementPlayer.prevRotationYaw = placementPlayer.rotationYaw;
        } else {
            placementPlayer.rotationYaw = sourcePlayer.rotationYaw;
            placementPlayer.prevRotationYaw = sourcePlayer.rotationYaw;
        }

        placementPlayer.setHeldItem(EnumHand.MAIN_HAND, attempt.stack.copy());

        try {
            world.captureBlockSnapshots = true;
            result = placementPlayer.getHeldItem(EnumHand.MAIN_HAND).getItem().onItemUse(
                placementPlayer,
                world,
                clickedPos,
                EnumHand.MAIN_HAND,
                attempt.clickedFace,
                attempt.hitX,
                attempt.hitY,
                attempt.hitZ);
            world.captureBlockSnapshots = false;

            probeSnapshots = new ArrayList<>(world.capturedBlockSnapshots);
            Set<BlockPos> changedOffsets = captureChangedOffsets(world, targetWorldPos, beforeBlocks);
            Map<BlockPos, PlacedBlockSample> placedBlocks = capturePlacedBlocks(world, targetWorldPos, changedOffsets);
            PlacementRestoreData restoreData = new PlacementRestoreData(targetWorldPos, beforeBlocks, changedOffsets, probeSnapshots);

            if (result == EnumActionResult.SUCCESS) probeResult = new PlacementProbeResult(placedBlocks, restoreData);
        } finally {
            world.captureBlockSnapshots = false;
            if (probeSnapshots.isEmpty() && !world.capturedBlockSnapshots.isEmpty()) {
                probeSnapshots = new ArrayList<>(world.capturedBlockSnapshots);
            }

            if (probeResult == null || !keepChanges || probeResult.placedBlocks.isEmpty()) {
                Set<BlockPos> changedOffsets = captureChangedOffsets(world, targetWorldPos, beforeBlocks);
                restorePlacementChanges(world, new PlacementRestoreData(targetWorldPos, beforeBlocks, changedOffsets, probeSnapshots));
            }

            world.capturedBlockSnapshots.clear();
            world.capturedBlockSnapshots.addAll(previousSnapshots);
            placementPlayer.setHeldItem(EnumHand.MAIN_HAND, originalMainHand);
            placementPlayer.rotationYaw = originalYaw;
            placementPlayer.prevRotationYaw = originalPrevYaw;
        }

        if (result != EnumActionResult.SUCCESS) return null;
        if (probeResult == null || probeResult.placedBlocks.isEmpty()) return null;

        return probeResult;
    }

    private static Map<BlockPos, PlacedBlockSample> capturePlacementRegion(World world,
                                                                           BlockPos targetWorldPos) {
        Map<BlockPos, PlacedBlockSample> placedBlocks = new HashMap<>();

        for (int x = -SIZE_PROBE_RADIUS; x <= SIZE_PROBE_RADIUS; x++) {
            for (int y = PLACEMENT_SCAN_MIN_Y; y <= PLACEMENT_SCAN_MAX_Y; y++) {
                for (int z = -SIZE_PROBE_RADIUS; z <= SIZE_PROBE_RADIUS; z++) {
                    BlockPos offset = new BlockPos(x, y, z);
                    BlockPos worldPos = targetWorldPos.add(offset);
                    PlacedBlockSample placedBlock = capturePlacedBlockSample(world, worldPos);
                    if (placedBlock == null) continue;

                    placedBlocks.put(offset, placedBlock);
                }
            }
        }

        return placedBlocks;
    }

    private static Set<BlockPos> captureChangedOffsets(World world,
                                                       BlockPos targetWorldPos,
                                                       Map<BlockPos, PlacedBlockSample> beforeBlocks) {
        Set<BlockPos> changedOffsets = new HashSet<>();

        for (int x = -SIZE_PROBE_RADIUS; x <= SIZE_PROBE_RADIUS; x++) {
            for (int y = PLACEMENT_SCAN_MIN_Y; y <= PLACEMENT_SCAN_MAX_Y; y++) {
                for (int z = -SIZE_PROBE_RADIUS; z <= SIZE_PROBE_RADIUS; z++) {
                    BlockPos offset = new BlockPos(x, y, z);
                    PlacedBlockSample beforeBlock = beforeBlocks.get(offset);
                    PlacedBlockSample afterBlock = capturePlacedBlockSample(world, targetWorldPos.add(offset));

                    if (placedBlocksEqual(beforeBlock, afterBlock)) continue;

                    changedOffsets.add(offset);
                }
            }
        }

        return changedOffsets;
    }

    private static Map<BlockPos, PlacedBlockSample> capturePlacedBlocks(World world,
                                                                        BlockPos targetWorldPos,
                                                                        Set<BlockPos> changedOffsets) {
        Map<BlockPos, PlacedBlockSample> placedBlocks = new HashMap<>();

        for (BlockPos offset : changedOffsets) {
            BlockPos worldPos = targetWorldPos.add(offset);
            PlacedBlockSample placedBlock = capturePlacedBlockSample(world, worldPos);
            if (placedBlock == null) continue;

            placedBlocks.put(offset, placedBlock);
        }

        return placedBlocks;
    }

    private static PlacedBlockSample capturePlacedBlockSample(World world, BlockPos worldPos) {
        IBlockState placedState = world.getBlockState(worldPos);
        if (placedState.getBlock() == Blocks.AIR) return null;

        return new PlacedBlockSample(placedState, getSanitizedTileTag(world, worldPos));
    }

    private static boolean placedBlocksEqual(PlacedBlockSample left, PlacedBlockSample right) {
        if (left == right) return true;
        if (left == null || right == null) return false;

        Block leftBlock = left.state.getBlock();
        Block rightBlock = right.state.getBlock();
        if (leftBlock != rightBlock) return false;
        if (leftBlock.getMetaFromState(left.state) != rightBlock.getMetaFromState(right.state)) return false;
        if (left.tileTag == null || right.tileTag == null) return left.tileTag == right.tileTag;

        return left.tileTag.equals(right.tileTag);
    }

    private static NBTTagCompound getSanitizedTileTag(World world, BlockPos worldPos) {
        TileEntity tileEntity = world.getTileEntity(worldPos);
        if (tileEntity == null) return null;

        NBTTagCompound tileTag = new NBTTagCompound();
        tileEntity.writeToNBT(tileTag);
        tileTag.removeTag("x");
        tileTag.removeTag("y");
        tileTag.removeTag("z");

        return tileTag;
    }

    private static void restoreSnapshots(World world, List<BlockSnapshot> snapshots) {
        for (int index = snapshots.size() - 1; index >= 0; index--) {
            try {
                world.restoringBlockSnapshots = true;
                snapshots.get(index).restore(true, false);
            } finally {
                world.restoringBlockSnapshots = false;
            }
        }
    }

    private static void restorePlacementChanges(World world, PlacementRestoreData restoreData) {
        if (restoreData == null) return;

        restoreSnapshots(world, restoreData.snapshots);

        Set<BlockPos> restoredOffsets = new HashSet<>();
        for (BlockSnapshot snapshot : restoreData.snapshots) {
            restoredOffsets.add(snapshot.getPos().subtract(restoreData.targetWorldPos));
        }

        for (BlockPos offset : restoreData.changedOffsets) {
            if (restoredOffsets.contains(offset)) continue;

            restorePlacementBlock(world, restoreData.targetWorldPos.add(offset), restoreData.beforeBlocks.get(offset));
        }
    }

    private static void restorePlacementBlock(World world,
                                              BlockPos worldPos,
                                              PlacedBlockSample beforeBlock) {
        if (beforeBlock == null) {
            world.setBlockState(worldPos, Blocks.AIR.getDefaultState(), 2);
            world.removeTileEntity(worldPos);

            return;
        }

        world.setBlockState(worldPos, beforeBlock.state, 2);

        if (beforeBlock.tileTag == null) {
            world.removeTileEntity(worldPos);

            return;
        }

        TileEntity tileEntity = world.getTileEntity(worldPos);
        if (tileEntity == null && beforeBlock.state.getBlock().hasTileEntity(beforeBlock.state)) {
            tileEntity = beforeBlock.state.getBlock().createTileEntity(world, beforeBlock.state);
            if (tileEntity != null) {
                tileEntity.setWorld(world);
                tileEntity.setPos(worldPos);
                world.setTileEntity(worldPos, tileEntity);
            }
        }

        if (tileEntity == null) return;

        NBTTagCompound restoreTag = beforeBlock.tileTag.copy();
        restoreTag.setInteger("x", worldPos.getX());
        restoreTag.setInteger("y", worldPos.getY());
        restoreTag.setInteger("z", worldPos.getZ());
        tileEntity.readFromNBT(restoreTag);
        tileEntity.markDirty();
    }

    private static boolean isObstructed(World world, BlockPos pos, IBlockState state) {
        return state.getBlock() != Blocks.AIR
            && !state.getBlock().isReplaceable(world, pos);
    }

    // ==================== Throttled Placement ====================

    /**
     * Tick-driven placement manager. Registered on the event bus while active tasks exist.
     * Each server tick, it processes all active ThrottledPlacementTasks.
     * This avoids the StackOverflowError that occurred when using addScheduledTask
     * (which executes immediately when called from the main thread).
     */
    private static final List<ThrottledPlacementTask> activeTasks = new ArrayList<>();
    private static boolean tickHandlerRegistered = false;

    private static void registerTickHandler() {
        if (tickHandlerRegistered) return;

        MinecraftForge.EVENT_BUS.register(TickHandler.class);
        tickHandlerRegistered = true;
    }

    public static class TickHandler {
        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            if (activeTasks.isEmpty()) return;

            Iterator<ThrottledPlacementTask> it = activeTasks.iterator();

            while (it.hasNext()) {
                ThrottledPlacementTask task = it.next();

                if (task.tick()) it.remove();
            }
        }
    }

    /**
     * Handles placing blocks across multiple ticks according to {@link AutobuildConfig#blocksPerTick}.
     * Fractional rates are supported: e.g. 0.5 means one block every 2 ticks.
     * Blocks are extracted upfront during the request phase and placed incrementally.
     * The chunk loading ticket is held until all blocks are placed, then released.
     */
    private static class ThrottledPlacementTask {

        private final WorldServer world;
        private final BlockPos origin;
        private final List<Map.Entry<BlockPos, BlockRequirement>> sortedBlocks;
        private final Map<String, Integer> remaining;
        private final Map<String, Integer> missing;
        private final Map<BlockPos, PlacementAction> plannedActions;
        private final Set<BlockPos> unplannedPositions;
        private final Map<BlockPos, BlockRequirement> requirementsByPos;
        private final EntityPlayerMP player;
        private final ForgeChunkManager.Ticket ticket;
        private final ResourceLocation structureId;
        private final Set<BlockPos> accountedSatisfiedPositions = new HashSet<>();
        private final Set<BlockPos> consumedPlannedPositions = new HashSet<>();
        private final Map<BlockPos, Integer> deferredPlacementAttempts = new HashMap<>();

        private int nextIndex = 0;
        private int placed = 0;
        private int skipped = 0;
        private int failed = 0;
        private final List<PlacementIssue> issues = new ArrayList<>();

        // Fractional accumulator: when rate < 1, we accumulate until >= 1 to place a block
        private double blockBudget = 0.0;

        ThrottledPlacementTask(WorldServer world, BlockPos origin,
                               List<Map.Entry<BlockPos, BlockRequirement>> sortedBlocks,
                               Map<String, Integer> extractedCounts,
                               Map<String, Integer> missing,
                               Map<BlockPos, PlacementAction> plannedActions,
                               List<BlockPos> unresolvedPlanningPositions,
                               EntityPlayerMP player,
                               ForgeChunkManager.Ticket ticket,
                               ResourceLocation structureId) {
            this.world = world;
            this.origin = origin;
            this.sortedBlocks = sortedBlocks;
            this.remaining = new HashMap<>(extractedCounts);
            this.missing = missing;
            this.plannedActions = new HashMap<>(plannedActions);
            this.unplannedPositions = new HashSet<>(unresolvedPlanningPositions);
            this.requirementsByPos = new HashMap<>();
            this.player = player;
            this.ticket = ticket;
            this.structureId = structureId;

            for (Map.Entry<BlockPos, BlockRequirement> entry : sortedBlocks) {
                requirementsByPos.put(entry.getKey(), entry.getValue());
            }
        }

        /**
         * Register this task for tick-driven processing.
         */
        void start() {
            registerTickHandler();
            activeTasks.add(this);
        }

        /**
         * Process one tick's worth of block placements.
         *
         * @return true if the task is finished and should be removed
         */
        private boolean tick() {
            // Player disconnected or world unloaded - abort gracefully
            if (player.hasDisconnected()) {
                finish();

                // TODO: Handle the remaining blocks. They are currently lost from the player's inventory,
                // but not placed in the world. We should ideally return them to the player
                // whenever they log back in, or send them to EMC/AE2 if those providers are present.
                // Something should only be sent to EMC if it has no NBT data, to avoid losing important stuff.

                return true;
            }

            double rate = AutobuildConfig.blocksPerTick;
            blockBudget += rate;

            // Place as many blocks as our budget allows this tick
            int blocksThisTick = (int) blockBudget;
            blockBudget -= blocksThisTick;

            int placementsThisTick = 0;
            while (placementsThisTick < blocksThisTick && nextIndex < sortedBlocks.size()) {
                if (placeNext()) placementsThisTick++;
            }

            // Check if we're done
            if (nextIndex >= sortedBlocks.size()) {
                finish();

                return true;
            }

            return false;
        }

        /**
         * Place the next block in the sorted list.
         */
        private boolean placeNext() {
            if (nextIndex >= sortedBlocks.size()) return false;

            Map.Entry<BlockPos, BlockRequirement> entry = sortedBlocks.get(nextIndex);
            nextIndex++;

            BlockPos relPos = entry.getKey();
            BlockPos worldPos = origin.add(relPos);
            BlockRequirement requirement = entry.getValue();
            String requiredKey = BlockSourceUtils.requirementToKey(requirement);

            // Check if already correct
            if (requirement.matches(world, worldPos, false)) {
                deferredPlacementAttempts.remove(relPos);

                if (accountedSatisfiedPositions.add(relPos)) skipped++;

                return false;
            }

            if (consumedPlannedPositions.contains(relPos)) {
                logConsumedPlannedPositionConflict(relPos, worldPos, requiredKey);
                issues.add(new PlacementIssue(IssueType.PLACEMENT_FAILED, worldPos,
                    requiredKey, ""));
                failed++;

                return false;
            }

            IBlockState currentState = world.getBlockState(worldPos);
            if (isObstructed(world, worldPos, currentState)) {
                issues.add(new PlacementIssue(IssueType.WRONG_BLOCK, worldPos,
                    requiredKey,
                    BlockSourceUtils.stateToKey(currentState)));
                failed++;

                return false;
            }

            PlacementAction action = plannedActions.remove(relPos);
            if (action == null) {
                action = planPlacementActionFallback(world, origin, sortedBlocks, relPos, requirement, player);
                if (action != null) unplannedPositions.remove(relPos);
            }

            // Check if we have the block
            if (action == null) {
                if (!unplannedPositions.contains(relPos)) {
                    logMissingPlacementAction(relPos, worldPos, requiredKey);
                }

                String deferReason = unplannedPositions.contains(relPos)
                    ? "no placement action available yet; likely waiting on a support block"
                    : "no static placement action available for this block";
                if (deferPlacement(entry, worldPos, requiredKey, deferReason)) return false;

                failed++;

                return false;
            }

            deferredPlacementAttempts.remove(relPos);

            String extractedKey = consumeMatchingExtractedKey(action.extractedKey);
            if (extractedKey == null) {
                logMissingExtractedKey(relPos, worldPos, requiredKey, action);
                failed++;

                return false;
            }

            consumedPlannedPositions.addAll(action.coveredPositions);

            // Check for external interference before placing
            currentState = world.getBlockState(worldPos);

            if (isObstructed(world, worldPos, currentState)) {

                // Something appeared where we expected air
                if (requirement.matches(world, worldPos, false)) {
                    // Lucky - external interference placed correct block
                    issues.add(new PlacementIssue(IssueType.CORRECT_EXTERNAL, worldPos,
                        requiredKey,
                        BlockSourceUtils.stateToKey(currentState)));
                    skipped++;
                } else {
                    // External interference placed wrong block
                    issues.add(new PlacementIssue(IssueType.WRONG_BLOCK, worldPos,
                        requiredKey,
                        BlockSourceUtils.stateToKey(currentState)));
                    failed++;
                }

                return false;
            }

            // Place the block with player-handling to ensure correct tile entity data and placement events
            // This ensures that correct ownership and state are applied,
            // which is crucial for tile entities and modded blocks that rely on placement events.
            ItemStack extractedStack = BlockSourceUtils.keyToStack(extractedKey);
            if (extractedStack.isEmpty()) {
                logEmptyExtractedStack(relPos, worldPos, requiredKey, extractedKey, action);
            }

            PlacementExecutionResult placementResult = placeExtractedBlock(action.attempt, extractedStack);
            if (placementResult != null && placementResult.actionResult == EnumActionResult.SUCCESS) {
                Set<BlockPos> covered = new HashSet<>();
                for (BlockPos coveredPos : action.coveredPositions) {
                    BlockRequirement coveredRequirement = requirementsByPos.get(coveredPos);
                    if (coveredRequirement == null) continue;
                    if (!coveredRequirement.matches(world, origin.add(coveredPos), false)) continue;

                    covered.add(coveredPos);
                }

                if (!covered.contains(relPos)) {
                    logPlacementMismatch(relPos, worldPos, requirement, action, extractedKey, placementResult);
                    issues.add(new PlacementIssue(IssueType.PLACEMENT_FAILED, worldPos,
                        requiredKey, ""));
                    failed++;

                    return false;
                } else {
                    for (BlockPos coveredPos : covered) {
                        if (accountedSatisfiedPositions.add(coveredPos)) placed++;
                    }

                    return true;
                }
            } else {
                logPlacementExecutionFailure(relPos, worldPos, requirement, action, extractedKey, placementResult);
                issues.add(new PlacementIssue(IssueType.PLACEMENT_FAILED, worldPos,
                    requiredKey, ""));
                failed++;

                return false;
            }
        }

        private boolean deferPlacement(Map.Entry<BlockPos, BlockRequirement> entry,
                                       BlockPos worldPos,
                                       String requiredKey,
                                       String reason) {
            BlockPos relPos = entry.getKey();
            int attempts = deferredPlacementAttempts.getOrDefault(relPos, 0);

            if (attempts >= MAX_DEFERRED_PLACEMENT_ATTEMPTS) {
                if (AutobuildConfig.verboseAutobuildLogging) {
                    MachineryAssembler.LOGGER.warn(
                        "{} Exhausted deferred placement retries for {} at {} in {}: reason={}, actualState={}",
                        VERBOSE_AUTOBUILD_LOG_PREFIX,
                        formatExtractedKeyLabel(requiredKey),
                        formatBlockPos(relPos),
                        structureId,
                        reason,
                        formatObservedBlock(world, worldPos));
                }

                return false;
            }

            deferredPlacementAttempts.put(relPos, attempts + 1);
            sortedBlocks.add(new AbstractMap.SimpleEntry<>(relPos, entry.getValue()));

            if (AutobuildConfig.verboseAutobuildLogging) {
                MachineryAssembler.LOGGER.info(
                    "{} Deferred {} at {} in {}: attempt={}/{}, reason={}, actualState={}",
                    VERBOSE_AUTOBUILD_LOG_PREFIX,
                    formatExtractedKeyLabel(requiredKey),
                    formatBlockPos(relPos),
                    structureId,
                    attempts + 1,
                    MAX_DEFERRED_PLACEMENT_ATTEMPTS,
                    reason,
                    formatObservedBlock(world, worldPos));
            }

            return true;
        }

        private void logConsumedPlannedPositionConflict(BlockPos relPos,
                                                        BlockPos worldPos,
                                                        String requiredKey) {
            if (!AutobuildConfig.verboseAutobuildLogging) return;

            MachineryAssembler.LOGGER.warn(
                "{} Planned coverage conflict for {} at {} in {}: position was already marked consumed before its own turn, actualState={}, consumedPositions={}, plannedActionsRemaining={}, deferredAttempts={}",
                VERBOSE_AUTOBUILD_LOG_PREFIX,
                formatExtractedKeyLabel(requiredKey),
                formatBlockPos(relPos),
                structureId,
                formatObservedBlock(world, worldPos),
                consumedPlannedPositions.size(),
                plannedActions.size(),
                deferredPlacementAttempts.getOrDefault(relPos, 0));
        }

        private void logMissingPlacementAction(BlockPos relPos,
                                               BlockPos worldPos,
                                               String requiredKey) {
            if (!AutobuildConfig.verboseAutobuildLogging) return;

            MachineryAssembler.LOGGER.warn(
                "{} Missing static placement action for {} at {} in {}: actualState={}, plannedActionsRemaining={}, deferredAttempts={}, consumedPositions={}",
                VERBOSE_AUTOBUILD_LOG_PREFIX,
                formatExtractedKeyLabel(requiredKey),
                formatBlockPos(relPos),
                structureId,
                formatObservedBlock(world, worldPos),
                plannedActions.size(),
                deferredPlacementAttempts.getOrDefault(relPos, 0),
                consumedPlannedPositions.size());
        }

        private void logMissingExtractedKey(BlockPos relPos,
                                            BlockPos worldPos,
                                            String requiredKey,
                                            PlacementAction action) {
            if (!AutobuildConfig.verboseAutobuildLogging) return;

            MachineryAssembler.LOGGER.warn(
                "{} Extracted-item accounting failed for {} at {} in {}: actualState={}, plannedExtractedKey={}, remainingItems={}, attempt={}, plannedCoverage={}",
                VERBOSE_AUTOBUILD_LOG_PREFIX,
                formatExtractedKeyLabel(requiredKey),
                formatBlockPos(relPos),
                structureId,
                formatObservedBlock(world, worldPos),
                formatExtractedKeyLabel(action.extractedKey),
                formatKeyCounts(remaining),
                formatPlacementAttempt(action.attempt),
                formatRelativePositions(action.coveredPositions));
        }

        private void logEmptyExtractedStack(BlockPos relPos,
                                            BlockPos worldPos,
                                            String requiredKey,
                                            String extractedKey,
                                            PlacementAction action) {
            if (!AutobuildConfig.verboseAutobuildLogging) return;

            MachineryAssembler.LOGGER.warn(
                "{} Extracted stack decoded as empty for {} at {} in {}: actualState={}, extractedKey={}, attempt={}, plannedCoverage={}",
                VERBOSE_AUTOBUILD_LOG_PREFIX,
                formatExtractedKeyLabel(requiredKey),
                formatBlockPos(relPos),
                structureId,
                formatObservedBlock(world, worldPos),
                formatExtractedKeyLabel(extractedKey),
                formatPlacementAttempt(action.attempt),
                formatRelativePositions(action.coveredPositions));
        }

        private void logPlacementExecutionFailure(BlockPos relPos,
                                                  BlockPos worldPos,
                                                  BlockRequirement requirement,
                                                  PlacementAction action,
                                                  String extractedKey,
                                                  PlacementExecutionResult placementResult) {
            if (!AutobuildConfig.verboseAutobuildLogging) return;

            String requiredKey = BlockSourceUtils.requirementToKey(requirement);
            String expectedTag = requirement.getMatchingTag() == null || requirement.getMatchingTag().getSize() <= 0
                ? "none"
                : requirement.getMatchingTag().toString();

            MachineryAssembler.LOGGER.warn(
                "{} Forge placement failed for {} at rel={} world={} in {}: forgeResult={}, actualState={}, expectedTag={}, extractedKey={}, attempt={}, plannedCoverage={}",
                VERBOSE_AUTOBUILD_LOG_PREFIX,
                formatExtractedKeyLabel(requiredKey),
                formatBlockPos(relPos),
                formatBlockPos(worldPos),
                structureId,
                placementResult == null ? "not_run" : placementResult.actionResult,
                formatObservedBlock(world, worldPos),
                expectedTag,
                formatExtractedKeyLabel(extractedKey),
                formatPlacementAttempt(action.attempt),
                formatRelativePositions(action.coveredPositions));

            if (placementResult != null) {
                MachineryAssembler.LOGGER.warn(
                    "{} Forge placement changes for {} at {}: changedOffsets={}, placedBlocks={}",
                    VERBOSE_AUTOBUILD_LOG_PREFIX,
                    formatExtractedKeyLabel(requiredKey),
                    formatBlockPos(worldPos),
                    formatRelativePositions(placementResult.changedOffsets),
                    formatRelativePositions(placementResult.placedBlocks.keySet()));
            }

            PlacementProbeResult directProbe = diagnoseDirectItemUse(action.attempt, extractedKey);
            if (directProbe == null) {
                MachineryAssembler.LOGGER.warn(
                    "{} Direct item-use diagnostic for {} at {} also failed",
                    VERBOSE_AUTOBUILD_LOG_PREFIX,
                    formatExtractedKeyLabel(requiredKey),
                    formatBlockPos(worldPos));

                return;
            }

            MachineryAssembler.LOGGER.warn(
                "{} Direct item-use diagnostic for {} at {} would place offsets={} and satisfy anchorState={}",
                VERBOSE_AUTOBUILD_LOG_PREFIX,
                formatExtractedKeyLabel(requiredKey),
                formatBlockPos(worldPos),
                formatRelativePositions(directProbe.placedBlocks.keySet()),
                formatObservedBlock(world, worldPos));
        }

        private void logPlacementMismatch(BlockPos relPos,
                                          BlockPos worldPos,
                                          BlockRequirement requirement,
                                          PlacementAction action,
                                          String extractedKey,
                                          PlacementExecutionResult placementResult) {
            if (!AutobuildConfig.verboseAutobuildLogging) return;

            String requiredKey = BlockSourceUtils.requirementToKey(requirement);
            String expectedTag = requirement.getMatchingTag() == null || requirement.getMatchingTag().getSize() <= 0
                ? "none"
                : requirement.getMatchingTag().toString();

            MachineryAssembler.LOGGER.warn(
                "{} Placement mismatch for {} at rel={} world={} in {}: forgeResult={}, actualState={}, expectedTag={}, extractedKey={}, attempt={}, plannedCoverage={}, changedOffsets={}, placedBlocks={}",
                VERBOSE_AUTOBUILD_LOG_PREFIX,
                formatExtractedKeyLabel(requiredKey),
                formatBlockPos(relPos),
                formatBlockPos(worldPos),
                structureId,
                placementResult.actionResult,
                formatObservedBlock(world, worldPos),
                expectedTag,
                formatExtractedKeyLabel(extractedKey),
                formatPlacementAttempt(action.attempt),
                formatRelativePositions(action.coveredPositions),
                formatRelativePositions(placementResult.changedOffsets),
                formatRelativePositions(placementResult.placedBlocks.keySet()));
        }

        private PlacementProbeResult diagnoseDirectItemUse(PlacementAttempt attempt, String extractedKey) {
            ItemStack diagnosticStack = BlockSourceUtils.keyToStack(extractedKey);
            PlacementAttempt diagnosticAttempt = new PlacementAttempt(
                diagnosticStack,
                attempt.targetRelPos,
                attempt.clickedRelPos,
                attempt.clickedFace,
                attempt.hitX,
                attempt.hitY,
                attempt.hitZ,
                attempt.horizontalFacing
            );

            return probePlacementWithPlayer(world, origin, diagnosticAttempt, player, player, false);
        }

        private String consumeMatchingExtractedKey(String requiredKey) {
            String bestKey = null;
            int bestSpecificity = Integer.MAX_VALUE;

            ItemStack requiredStack = BlockSourceUtils.keyToStack(requiredKey);

            for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
                if (entry.getValue() <= 0) continue;

                ItemStack availableStack = BlockSourceUtils.keyToStack(entry.getKey());
                if (!BlockSourceUtils.matchesRequiredStack(availableStack, requiredStack)) continue;

                int specificity = BlockSourceUtils.getKeySpecificity(entry.getKey());
                if (specificity > bestSpecificity) continue;
                if (specificity == bestSpecificity && bestKey != null && entry.getKey().compareTo(bestKey) >= 0) continue;

                bestKey = entry.getKey();
                bestSpecificity = specificity;
            }

            if (bestKey == null) return null;

            int available = remaining.get(bestKey) - 1;
            if (available <= 0) {
                remaining.remove(bestKey);
            } else {
                remaining.put(bestKey, available);
            }

            return bestKey;
        }

        /**
         * Place a block using the real item-use pipeline so modded items can place however they need to.
         */
        private PlacementExecutionResult placeExtractedBlock(PlacementAttempt attempt, ItemStack extractedStack) {
            BlockPos clickedPos = origin.add(attempt.clickedRelPos);
            BlockPos targetWorldPos = origin.add(attempt.targetRelPos);
            Map<BlockPos, PlacedBlockSample> beforeBlocks = capturePlacementRegion(world, targetWorldPos);
            ItemStack originalMainHand = player.getHeldItem(EnumHand.MAIN_HAND);
            float originalYaw = player.rotationYaw;
            float originalPrevYaw = player.prevRotationYaw;

            try {
                if (attempt.horizontalFacing != null) {
                    player.rotationYaw = attempt.horizontalFacing.getHorizontalAngle();
                    player.prevRotationYaw = player.rotationYaw;
                }

                player.setHeldItem(EnumHand.MAIN_HAND, extractedStack);

                EnumActionResult result = ForgeHooks.onPlaceItemIntoWorld(
                    player.getHeldItem(EnumHand.MAIN_HAND),
                    player,
                    world,
                    clickedPos,
                    attempt.clickedFace,
                    attempt.hitX,
                    attempt.hitY,
                    attempt.hitZ,
                    EnumHand.MAIN_HAND);

                Set<BlockPos> changedOffsets = captureChangedOffsets(world, targetWorldPos, beforeBlocks);
                Map<BlockPos, PlacedBlockSample> placedBlocks = capturePlacedBlocks(world, targetWorldPos, changedOffsets);

                return new PlacementExecutionResult(result, changedOffsets, placedBlocks);
            } finally {
                player.setHeldItem(EnumHand.MAIN_HAND, originalMainHand);
                player.rotationYaw = originalYaw;
                player.prevRotationYaw = originalPrevYaw;
            }
        }

        /**
         * Finalize placement: send result packets and release chunk ticket.
         */
        private void finish() {
            // Report placement issues
            if (!issues.isEmpty()) {
                NetworkHandler.INSTANCE.sendTo(new PacketAutobuildPlacementIssue(issues), player);
            }

            // Send final result
            ResultType resultType;

            if (failed == 0 && missing.isEmpty()) {
                resultType = ResultType.SUCCESS;
            } else if (placed > 0) {
                resultType = ResultType.PARTIAL_SUCCESS;
            } else {
                resultType = ResultType.FAILED;
            }

            PacketAutobuildResult packet = new PacketAutobuildResult(resultType, placed, skipped, failed);
            NetworkHandler.INSTANCE.sendTo(packet, player);

            MachineryAssembler.LOGGER.info("Autobuild for {}: {} placed, {} skipped, {} failed",
                structureId, placed, skipped, failed);

            // Release chunk loading ticket
            if (ticket != null) ForgeChunkManager.releaseTicket(ticket);
        }
    }

    private static class PlacementAction {

        private final String extractedKey;
        private final PlacementAttempt attempt;
        private final Set<BlockPos> coveredPositions;
        private final Set<BlockPos> footprintOffsets;

        private PlacementAction(String extractedKey,
                                PlacementAttempt attempt,
                                Set<BlockPos> coveredPositions,
                                Set<BlockPos> footprintOffsets) {
            this.extractedKey = extractedKey;
            this.attempt = attempt;
            this.coveredPositions = new HashSet<>(coveredPositions);
            this.footprintOffsets = new HashSet<>(footprintOffsets);
        }
    }

    private static class PlacementAttempt {

        private final ItemStack stack;
        private final BlockPos targetRelPos;
        private final BlockPos clickedRelPos;
        private final EnumFacing clickedFace;
        private final float hitX;
        private final float hitY;
        private final float hitZ;
        private final EnumFacing horizontalFacing;

        private PlacementAttempt(ItemStack stack, BlockPos targetRelPos, BlockPos clickedRelPos,
                                 EnumFacing clickedFace, float hitX, float hitY, float hitZ,
                                 EnumFacing horizontalFacing) {
            this.stack = stack;
            this.targetRelPos = targetRelPos;
            this.clickedRelPos = clickedRelPos;
            this.clickedFace = clickedFace;
            this.hitX = hitX;
            this.hitY = hitY;
            this.hitZ = hitZ;
            this.horizontalFacing = horizontalFacing;
        }
    }

    private static class PlacementProbeResult {

        private final Map<BlockPos, PlacedBlockSample> placedBlocks;
        private final PlacementRestoreData restoreData;

        private PlacementProbeResult(Map<BlockPos, PlacedBlockSample> placedBlocks,
                                     PlacementRestoreData restoreData) {
            this.placedBlocks = placedBlocks;
            this.restoreData = restoreData;
        }
    }

    private static class PlacementExecutionResult {

        private final EnumActionResult actionResult;
        private final Set<BlockPos> changedOffsets;
        private final Map<BlockPos, PlacedBlockSample> placedBlocks;

        private PlacementExecutionResult(EnumActionResult actionResult,
                                         Set<BlockPos> changedOffsets,
                                         Map<BlockPos, PlacedBlockSample> placedBlocks) {
            this.actionResult = actionResult;
            this.changedOffsets = new HashSet<>(changedOffsets);
            this.placedBlocks = new HashMap<>(placedBlocks);
        }
    }

    private static class PlacementRestoreData {

        private final BlockPos targetWorldPos;
        private final Map<BlockPos, PlacedBlockSample> beforeBlocks;
        private final Set<BlockPos> changedOffsets;
        private final List<BlockSnapshot> snapshots;

        private PlacementRestoreData(BlockPos targetWorldPos,
                                     Map<BlockPos, PlacedBlockSample> beforeBlocks,
                                     Set<BlockPos> changedOffsets,
                                     List<BlockSnapshot> snapshots) {
            this.targetWorldPos = targetWorldPos;
            this.beforeBlocks = new HashMap<>(beforeBlocks);
            this.changedOffsets = new HashSet<>(changedOffsets);
            this.snapshots = new ArrayList<>(snapshots);
        }
    }

    private static class ResolvedObstructionResult {

        private final List<BlockPos> obstructedPositions;
        private final Set<BlockPos> blockedStructurePositions;

        private ResolvedObstructionResult(List<BlockPos> obstructedPositions,
                                          Set<BlockPos> blockedStructurePositions) {
            this.obstructedPositions = new ArrayList<>(obstructedPositions);
            this.blockedStructurePositions = new HashSet<>(blockedStructurePositions);
        }
    }

    private static class ProbeArena {

        private final BlockPos origin;
        private final List<BlockSnapshot> snapshots;

        private ProbeArena(BlockPos origin, List<BlockSnapshot> snapshots) {
            this.origin = origin;
            this.snapshots = snapshots;
        }
    }

    private static class ProbeSupportState {

        private final BlockPos clickedPos;
        private final PlacedBlockSample previousBlock;

        private ProbeSupportState(BlockPos clickedPos, PlacedBlockSample previousBlock) {
            this.clickedPos = clickedPos;
            this.previousBlock = previousBlock;
        }
    }

    private static class PlanningSimulation {

        private final AutobuildProbeWorld world;
        private final AutobuildProbePlayer player;
        private final BlockPos origin;

        private PlanningSimulation(AutobuildProbeWorld world,
                                   AutobuildProbePlayer player,
                                   BlockPos origin) {
            this.world = world;
            this.player = player;
            this.origin = origin;
        }
    }

    private static class SizedBlockPlacementInfo {

        private final List<PlacementVariant> variants;

        private SizedBlockPlacementInfo(List<PlacementVariant> variants) {
            this.variants = variants;
        }
    }

    private static class PlacementVariant {

        private final PlacementAttempt attempt;
        private final Map<BlockPos, PlacedBlockSample> placedBlocks;
        private final BlockPos minOffset;
        private final BlockPos maxOffset;

        private PlacementVariant(PlacementAttempt attempt, Map<BlockPos, PlacedBlockSample> placedBlocks) {
            this.attempt = attempt;
            this.placedBlocks = new HashMap<>(placedBlocks);

            int minX = 0;
            int minY = 0;
            int minZ = 0;
            int maxX = 0;
            int maxY = 0;
            int maxZ = 0;
            boolean first = true;

            for (BlockPos offset : placedBlocks.keySet()) {
                if (first) {
                    minX = offset.getX();
                    minY = offset.getY();
                    minZ = offset.getZ();
                    maxX = offset.getX();
                    maxY = offset.getY();
                    maxZ = offset.getZ();
                    first = false;
                    continue;
                }

                if (offset.getX() < minX) minX = offset.getX();
                if (offset.getY() < minY) minY = offset.getY();
                if (offset.getZ() < minZ) minZ = offset.getZ();
                if (offset.getX() > maxX) maxX = offset.getX();
                if (offset.getY() > maxY) maxY = offset.getY();
                if (offset.getZ() > maxZ) maxZ = offset.getZ();
            }

            this.minOffset = new BlockPos(minX, minY, minZ);
            this.maxOffset = new BlockPos(maxX, maxY, maxZ);
        }
    }

    private static class PlacementMatch {

        private final PlacementAttempt attempt;
        private final Set<BlockPos> coveredPositions;
        private final PlacementVariant variant;
        private final int centerDistanceScore;

        private PlacementMatch(PlacementAttempt attempt,
                               Set<BlockPos> coveredPositions,
                               PlacementVariant variant,
                               int centerDistanceScore) {
            this.attempt = attempt;
            this.coveredPositions = coveredPositions;
            this.variant = variant;
            this.centerDistanceScore = centerDistanceScore;
        }
    }

    private static class PlacedBlockSample {

        private final IBlockState state;
        private final NBTTagCompound tileTag;

        private PlacedBlockSample(IBlockState state, NBTTagCompound tileTag) {
            this.state = state;
            this.tileTag = tileTag;
        }
    }

}

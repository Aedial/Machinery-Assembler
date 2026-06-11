// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
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
import com.machineryassembler.common.structure.Structure;
import com.machineryassembler.common.structure.StructurePattern;
import com.machineryassembler.common.structure.StructureRegistry;


/**
 * Server-side handler for autobuild requests.
 * Manages chunk loading, obstruction detection, block extraction, and throttled placement.
 * Placement is spread across multiple ticks based on {@link AutobuildConfig#blocksPerTick}.
 */
public class ServerAutobuildHandler {

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
        List<Map.Entry<BlockPos, BlockRequirement>> sortedBlocks = prepareSortedBlocks(pattern);
        Map<BlockPos, PlacementAction> plannedActions = new HashMap<>();

        // TODO: Benchmark and improve performance
        // TODO: Sanity-check the NBT matching

        // Phase 1: Check for obstructions
        List<BlockPos> obstructed = checkObstructions(world, pattern, origin);
        if (!obstructed.isEmpty()) {
            boolean aborted = !AutobuildConfig.allowPartialBuilds;
            NetworkHandler.INSTANCE.sendTo(new PacketAutobuildObstruction(obstructed), player);

            if (aborted) {
                NetworkHandler.INSTANCE.sendTo(
                    new PacketAutobuildResult(ResultType.FAILED, 0, 0, obstructed.size()), player);
                if (ticket != null) ForgeChunkManager.releaseTicket(ticket);

                return;
            }
        }

        // Phase 2: Simulate extraction in NBT-specific order so stricter requirements reserve
        // matching blocks before more generic requirements can consume them.
        Map<String, Integer> required = orderRequirementsForInclusiveMatching(
            collectRequiredBlocks(world, origin, sortedBlocks, player, plannedActions));
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
        BlockExtractionResult extractionResult = sourceManager.batchExtractDetailed(toExtract, sourceContext, false);
        Map<String, Integer> extractedCounts = extractionResult.getExtracted();

        // Phase 4: Start throttled placement with item-driven actions.
        ThrottledPlacementTask task = new ThrottledPlacementTask(
            world, origin, sortedBlocks, extractedCounts, missing, plannedActions, player, ticket, structureId);
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
    private static List<BlockPos> checkObstructions(WorldServer world, StructurePattern pattern, BlockPos origin) {
        List<BlockPos> obstructed = new ArrayList<>();

        for (Map.Entry<BlockPos, BlockRequirement> entry : pattern.getPattern().entrySet()) {
            BlockPos relPos = entry.getKey();
            BlockPos worldPos = origin.add(relPos);
            BlockRequirement requirement = entry.getValue();

            IBlockState currentState = world.getBlockState(worldPos);

            // Skip if already correct
            if (requirement.matches(world, worldPos, false)) continue;

            // Check if there's an obstruction (non-air, non-replaceable block)
            if (isObstructed(world, worldPos, currentState)) {
                obstructed.add(worldPos);
            }
        }

        return obstructed;
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
                                                              Map<BlockPos, PlacementAction> plannedActions) {
        Map<String, Integer> required = new HashMap<>();
        Set<BlockPos> satisfied = new HashSet<>();

        for (Map.Entry<BlockPos, BlockRequirement> entry : sortedBlocks) {
            BlockPos relPos = entry.getKey();
            BlockPos worldPos = origin.add(relPos);
            BlockRequirement requirement = entry.getValue();
            IBlockState currentState = world.getBlockState(worldPos);

            if (satisfied.contains(relPos) || requirement.matches(world, worldPos, false)) {
                satisfied.add(relPos);
                continue;
            }

            if (isObstructed(world, worldPos, currentState)) continue;

            PlacementAction action = planPlacementAction(world, origin, sortedBlocks, relPos, requirement, player);
            if (action == null) {
                String key = BlockSourceUtils.requirementToKey(requirement);
                required.merge(key, 1, Integer::sum);
                satisfied.add(relPos);
                continue;
            }

            plannedActions.put(relPos, action);
            required.merge(action.extractedKey, 1, Integer::sum);
            satisfied.addAll(action.coveredPositions);
        }

        return required;
    }

    private static PlacementAction planPlacementAction(WorldServer world,
                                                       BlockPos origin,
                                                       List<Map.Entry<BlockPos, BlockRequirement>> sortedBlocks,
                                                       BlockPos anchorRelPos,
                                                       BlockRequirement anchorRequirement,
                                                       EntityPlayerMP player) {
        ItemStack requiredStack = anchorRequirement.getRequiredStack();
        if (requiredStack.isEmpty()) return null;

        PlacementAction bestAction = null;

        for (PlacementAttempt attempt : createPlacementAttempts(anchorRelPos, requiredStack, player)) {
            PlacementProbeResult probe = probePlacement(world, origin, sortedBlocks, attempt, player);
            if (probe == null) continue;
            if (!probe.coveredPositions.contains(anchorRelPos)) continue;

            PlacementAction candidate = new PlacementAction(
                BlockSourceUtils.stackToKey(attempt.stack), attempt, probe.coveredPositions);

            if (isBetterPlacementAction(candidate, bestAction, anchorRelPos)) {
                bestAction = candidate;
            }
        }

        return bestAction;
    }

    private static boolean isBetterPlacementAction(PlacementAction candidate,
                                                   PlacementAction currentBest,
                                                   BlockPos anchorRelPos) {
        if (candidate == null) return false;
        if (currentBest == null) return true;

        int coverageComparison = Integer.compare(
            candidate.coveredPositions.size(), currentBest.coveredPositions.size());
        if (coverageComparison != 0) return coverageComparison > 0;

        boolean candidateTargetsAnchor = candidate.attempt.targetRelPos.equals(anchorRelPos);
        boolean currentTargetsAnchor = currentBest.attempt.targetRelPos.equals(anchorRelPos);
        if (candidateTargetsAnchor != currentTargetsAnchor) return candidateTargetsAnchor;

        int candidateAnchorDistance = getManhattanDistance(candidate.attempt.clickedRelPos, anchorRelPos);
        int currentAnchorDistance = getManhattanDistance(currentBest.attempt.clickedRelPos, anchorRelPos);
        if (candidateAnchorDistance != currentAnchorDistance) return candidateAnchorDistance < currentAnchorDistance;

        return candidate.extractedKey.compareTo(currentBest.extractedKey) < 0;
    }

    private static int getManhattanDistance(BlockPos left, BlockPos right) {
        return Math.abs(left.getX() - right.getX())
            + Math.abs(left.getY() - right.getY())
            + Math.abs(left.getZ() - right.getZ());
    }

    private static List<PlacementAttempt> createPlacementAttempts(BlockPos anchorRelPos,
                                                                  ItemStack requiredStack,
                                                                  EntityPlayerMP player) {
        List<PlacementAttempt> attempts = new ArrayList<>();
        List<BlockPos> targetCandidates = getTargetCandidates(anchorRelPos);
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

    private static List<BlockPos> getTargetCandidates(BlockPos anchorRelPos) {
        List<BlockPos> targets = new ArrayList<>();
        targets.add(anchorRelPos);

        targets.add(anchorRelPos.down());
        targets.add(anchorRelPos.up());

        for (EnumFacing horizontal : EnumFacing.HORIZONTALS) {
            targets.add(anchorRelPos.offset(horizontal));
        }

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
            attempts.add(new PlacementAttempt(requiredStack.copy(), targetRelPos, clickedRelPos,
                facing, hitX, hitY, hitZ, horizontal));
        }
    }

    private static PlacementProbeResult probePlacement(WorldServer world,
                                                       BlockPos origin,
                                                       List<Map.Entry<BlockPos, BlockRequirement>> sortedBlocks,
                                                       PlacementAttempt attempt,
                                                       EntityPlayerMP player) {
        SimulationPlayer simulationPlayer = new SimulationPlayer(world, player);
        BlockPos clickedPos = origin.add(attempt.clickedRelPos);
        BlockPos targetPos = origin.add(attempt.targetRelPos);

        if (!world.isBlockLoaded(clickedPos) || !world.isBlockLoaded(targetPos)) return null;

        Set<BlockPos> satisfiedBefore = getSatisfiedPositions(world, origin, sortedBlocks);
        List<BlockSnapshot> previousSnapshots = new ArrayList<>(world.capturedBlockSnapshots);
        world.capturedBlockSnapshots.clear();
        List<BlockSnapshot> probeSnapshots = new ArrayList<>();
        Set<BlockPos> covered = new HashSet<>();

        if (attempt.horizontalFacing != null) {
            simulationPlayer.rotationYaw = attempt.horizontalFacing.getHorizontalAngle();
            simulationPlayer.prevRotationYaw = simulationPlayer.rotationYaw;
        } else {
            simulationPlayer.rotationYaw = player.rotationYaw;
            simulationPlayer.prevRotationYaw = player.rotationYaw;
        }

        simulationPlayer.setHeldItem(EnumHand.MAIN_HAND, attempt.stack.copy());

        EnumActionResult result;

        try {
            world.captureBlockSnapshots = true;
            result = simulationPlayer.getHeldItem(EnumHand.MAIN_HAND).getItem().onItemUse(
                simulationPlayer,
                world,
                clickedPos,
                EnumHand.MAIN_HAND,
                attempt.clickedFace,
                attempt.hitX,
                attempt.hitY,
                attempt.hitZ);
            world.captureBlockSnapshots = false;

            probeSnapshots = new ArrayList<>(world.capturedBlockSnapshots);
            if (result == EnumActionResult.SUCCESS) {
                covered = findSatisfiedPositions(world, origin, sortedBlocks, satisfiedBefore);
            }
        } finally {
            world.captureBlockSnapshots = false;
            if (probeSnapshots.isEmpty() && !world.capturedBlockSnapshots.isEmpty()) {
                probeSnapshots = new ArrayList<>(world.capturedBlockSnapshots);
            }

            restoreSnapshots(world, probeSnapshots);
            world.capturedBlockSnapshots.clear();
            world.capturedBlockSnapshots.addAll(previousSnapshots);
        }

        if (result != EnumActionResult.SUCCESS) return null;
        if (covered.isEmpty()) return null;

        return new PlacementProbeResult(covered);
    }

    private static Set<BlockPos> getSatisfiedPositions(WorldServer world,
                                                       BlockPos origin,
                                                       List<Map.Entry<BlockPos, BlockRequirement>> sortedBlocks) {
        Set<BlockPos> satisfied = new HashSet<>();

        for (Map.Entry<BlockPos, BlockRequirement> entry : sortedBlocks) {
            if (!entry.getValue().matches(world, origin.add(entry.getKey()), false)) continue;

            satisfied.add(entry.getKey());
        }

        return satisfied;
    }

    private static Set<BlockPos> findSatisfiedPositions(WorldServer world,
                                                        BlockPos origin,
                                                        List<Map.Entry<BlockPos, BlockRequirement>> sortedBlocks,
                                                        Set<BlockPos> satisfiedBefore) {
        Set<BlockPos> covered = new HashSet<>();
        for (Map.Entry<BlockPos, BlockRequirement> entry : sortedBlocks) {
            if (satisfiedBefore.contains(entry.getKey())) continue;
            if (!entry.getValue().matches(world, origin.add(entry.getKey()), false)) continue;

            covered.add(entry.getKey());
        }

        return covered;
    }

    private static void restoreSnapshots(WorldServer world, List<BlockSnapshot> snapshots) {
        for (int index = snapshots.size() - 1; index >= 0; index--) {
            try {
                world.restoringBlockSnapshots = true;
                snapshots.get(index).restore(true, false);
            } finally {
                world.restoringBlockSnapshots = false;
            }
        }
    }

    private static boolean isObstructed(WorldServer world, BlockPos pos, IBlockState state) {
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
        private final Map<BlockPos, BlockRequirement> requirementsByPos;
        private final EntityPlayerMP player;
        private final ForgeChunkManager.Ticket ticket;
        private final ResourceLocation structureId;
        private final Set<BlockPos> accountedSatisfiedPositions = new HashSet<>();

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
                               EntityPlayerMP player,
                               ForgeChunkManager.Ticket ticket,
                               ResourceLocation structureId) {
            this.world = world;
            this.origin = origin;
            this.sortedBlocks = sortedBlocks;
            this.remaining = new HashMap<>(extractedCounts);
            this.missing = missing;
            this.plannedActions = new HashMap<>(plannedActions);
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

            // Check if already correct
            if (requirement.matches(world, worldPos, false)) {
                if (accountedSatisfiedPositions.add(relPos)) skipped++;

                return false;
            }

            IBlockState currentState = world.getBlockState(worldPos);
            if (isObstructed(world, worldPos, currentState)) {
                issues.add(new PlacementIssue(IssueType.WRONG_BLOCK, worldPos,
                    BlockSourceUtils.requirementToKey(requirement),
                    BlockSourceUtils.stateToKey(currentState)));
                failed++;

                return false;
            }

            PlacementAction action = plannedActions.remove(relPos);
            if (action == null) {
                action = planPlacementAction(world, origin, sortedBlocks, relPos, requirement, player);
            }

            String requiredKey = BlockSourceUtils.requirementToKey(requirement);

            // Check if we have the block
            if (action == null) {
                failed++;

                return false;
            }

            String extractedKey = consumeMatchingExtractedKey(action.extractedKey);
            if (extractedKey == null) {
                failed++;

                return false;
            }

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
            boolean success = placeExtractedBlock(action.attempt, extractedStack);
            if (success) {
                Set<BlockPos> covered = new HashSet<>();
                for (BlockPos coveredPos : action.coveredPositions) {
                    BlockRequirement coveredRequirement = requirementsByPos.get(coveredPos);
                    if (coveredRequirement == null) continue;
                    if (!coveredRequirement.matches(world, origin.add(coveredPos), false)) continue;

                    covered.add(coveredPos);
                }

                if (!covered.contains(relPos)) {
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
                issues.add(new PlacementIssue(IssueType.PLACEMENT_FAILED, worldPos,
                    requiredKey, ""));
                failed++;

                return false;
            }
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
        private boolean placeExtractedBlock(PlacementAttempt attempt, ItemStack extractedStack) {
            BlockPos clickedPos = origin.add(attempt.clickedRelPos);
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

                return result == EnumActionResult.SUCCESS;
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

        private PlacementAction(String extractedKey,
                                PlacementAttempt attempt, Set<BlockPos> coveredPositions) {
            this.extractedKey = extractedKey;
            this.attempt = attempt;
            this.coveredPositions = coveredPositions;
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

        private final Set<BlockPos> coveredPositions;

        private PlacementProbeResult(Set<BlockPos> coveredPositions) {
            this.coveredPositions = coveredPositions;
        }
    }

    private static class SimulationPlayer extends FakePlayer {

        private final EntityPlayerMP sourcePlayer;

        private SimulationPlayer(WorldServer world, EntityPlayerMP sourcePlayer) {
            super(world, sourcePlayer.getGameProfile());
            this.sourcePlayer = sourcePlayer;

            capabilities.allowEdit = sourcePlayer.capabilities.allowEdit;
            capabilities.isCreativeMode = sourcePlayer.capabilities.isCreativeMode;
            capabilities.allowFlying = sourcePlayer.capabilities.allowFlying;
            capabilities.disableDamage = sourcePlayer.capabilities.disableDamage;
            capabilities.isFlying = sourcePlayer.capabilities.isFlying;

            inventory.currentItem = sourcePlayer.inventory.currentItem;
            for (int slot = 0; slot < sourcePlayer.inventory.getSizeInventory(); slot++) {
                ItemStack sourceStack = sourcePlayer.inventory.getStackInSlot(slot);
                inventory.setInventorySlotContents(slot, sourceStack.isEmpty() ? ItemStack.EMPTY : sourceStack.copy());
            }

            setPositionAndRotation(sourcePlayer.posX, sourcePlayer.posY, sourcePlayer.posZ,
                sourcePlayer.rotationYaw, sourcePlayer.rotationPitch);
            rotationYawHead = sourcePlayer.rotationYawHead;
            renderYawOffset = sourcePlayer.renderYawOffset;
        }

        @Override
        public EnumHandSide getPrimaryHand() {
            return sourcePlayer.getPrimaryHand();
        }

        @Override
        public Vec3d getPositionVector() {
            return new Vec3d(posX, posY, posZ);
        }
    }
}

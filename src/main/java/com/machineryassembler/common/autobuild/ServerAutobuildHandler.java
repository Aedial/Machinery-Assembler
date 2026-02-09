// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.autobuild;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
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
    public static void handleAutobuildRequest(EntityPlayerMP player, ResourceLocation structureId, BlockPos origin) {
        WorldServer world = player.getServerWorld();
        Structure structure = StructureRegistry.getRegistry().getStructure(structureId);

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

        // Phase 1: Check for obstructions (TODO: if allow partial builds, we should highlight, but not abort)
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

        // Phase 2: Check block availability
        BlockSource source = InventoryBlockSource.INSTANCE;  // FIXME: use a provider manager!
        Map<String, Integer> required = collectRequiredBlocks(world, pattern, origin);
        Map<String, Integer> available = source.checkAvailability(required, player);

        // Calculate missing = required - available
        Map<String, Integer> missing = new HashMap<>();

        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            String key = entry.getKey();
            int need = entry.getValue();
            int have = available.getOrDefault(key, 0);

            if (have < need) missing.put(key, need - have);
        }

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
        Map<String, Integer> remainder = source.batchExtract(toExtract, player, false);
        Map<String, Integer> extractedCounts = new HashMap<>();

        for (Map.Entry<String, Integer> entry : toExtract.entrySet()) {
            String key = entry.getKey();
            int requested = entry.getValue();
            int notExtracted = remainder.getOrDefault(key, 0);
            int actuallyExtracted = requested - notExtracted;

            if (actuallyExtracted > 0) extractedCounts.put(key, actuallyExtracted);
        }

        // Phase 4: Build sorted block list and start throttled placement
        List<Map.Entry<BlockPos, BlockRequirement>> sortedBlocks = prepareSortedBlocks(pattern);

        ThrottledPlacementTask task = new ThrottledPlacementTask(
            world, origin, sortedBlocks, extractedCounts, missing, player, ticket, structureId);
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
            if (currentState.getBlock() != Blocks.AIR &&
                !currentState.getBlock().isReplaceable(world, worldPos)) {
                obstructed.add(worldPos);
            }
        }

        return obstructed;
    }

    /**
     * Collect all required blocks (those not already placed correctly).
     */
    private static Map<String, Integer> collectRequiredBlocks(WorldServer world, StructurePattern pattern, BlockPos origin) {
        Map<String, Integer> required = new HashMap<>();

        for (Map.Entry<BlockPos, BlockRequirement> entry : pattern.getPattern().entrySet()) {
            BlockPos relPos = entry.getKey();
            BlockPos worldPos = origin.add(relPos);
            BlockRequirement requirement = entry.getValue();

            // Skip if already correct
            if (requirement.matches(world, worldPos, false)) continue;

            IBlockState targetState = requirement.getSampleState();
            String key = BlockSourceUtils.stateToKey(targetState);
            required.merge(key, 1, Integer::sum);
        }

        return required;
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
        private final EntityPlayerMP player;
        private final ForgeChunkManager.Ticket ticket;
        private final ResourceLocation structureId;

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
                               EntityPlayerMP player,
                               ForgeChunkManager.Ticket ticket,
                               ResourceLocation structureId) {
            this.world = world;
            this.origin = origin;
            this.sortedBlocks = sortedBlocks;
            this.remaining = new HashMap<>(extractedCounts);
            this.missing = missing;
            this.player = player;
            this.ticket = ticket;
            this.structureId = structureId;
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

                // TODO: handle the remaining blocks,
                // they are currently lost from the player's inventory but not placed in the world.
                // We should ideally return them to the player

                return true;
            }

            double rate = AutobuildConfig.blocksPerTick;
            blockBudget += rate;

            // Place as many blocks as our budget allows this tick
            int blocksThisTick = (int) blockBudget;
            blockBudget -= blocksThisTick;

            for (int i = 0; i < blocksThisTick && nextIndex < sortedBlocks.size(); i++) placeNext();

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
        private void placeNext() {
            if (nextIndex >= sortedBlocks.size()) return;

            Map.Entry<BlockPos, BlockRequirement> entry = sortedBlocks.get(nextIndex);
            nextIndex++;

            BlockPos relPos = entry.getKey();
            BlockPos worldPos = origin.add(relPos);
            BlockRequirement requirement = entry.getValue();

            // Check if already correct
            if (requirement.matches(world, worldPos, false)) {
                skipped++;

                return;
            }

            IBlockState targetState = requirement.getSampleState();
            String key = BlockSourceUtils.stateToKey(targetState);

            // Check if we have the block
            int available = remaining.getOrDefault(key, 0);

            if (available <= 0) {
                failed++;

                return;
            }

            // Decrement the count
            remaining.put(key, available - 1);

            // TODO: should we just not throttle skipped/failed blocks and move on to the next one immediately, instead of counting them against the block budget?
            // Check for external interference before placing
            IBlockState currentState = world.getBlockState(worldPos);

            if (currentState.getBlock() != Blocks.AIR &&
                !currentState.getBlock().isReplaceable(world, worldPos)) {

                // Something appeared where we expected air
                if (requirement.matches(world, worldPos, false)) {
                    // Lucky - external interference placed correct block
                    issues.add(new PlacementIssue(IssueType.CORRECT_EXTERNAL, worldPos,
                        BlockSourceUtils.stateToKey(targetState),
                        BlockSourceUtils.stateToKey(currentState)));
                    skipped++;
                } else {
                    // External interference placed wrong block
                    issues.add(new PlacementIssue(IssueType.WRONG_BLOCK, worldPos,
                        BlockSourceUtils.stateToKey(targetState),
                        BlockSourceUtils.stateToKey(currentState)));
                    failed++;
                }

                return;
            }

            // Place the block
            boolean success = world.setBlockState(worldPos, targetState, 3);

            if (success) {
                placed++;
            } else {
                issues.add(new PlacementIssue(IssueType.PLACEMENT_FAILED, worldPos,
                    BlockSourceUtils.stateToKey(targetState), ""));
                failed++;
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
}

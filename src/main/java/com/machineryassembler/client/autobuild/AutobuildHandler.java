// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.client.autobuild;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.client.ClientProxy;
import com.machineryassembler.client.render.BatonHighlightRenderer;
import com.machineryassembler.client.render.StructureRenderContext;
import com.machineryassembler.common.autobuild.BlockSourceUtils;
import com.machineryassembler.common.item.ItemAssemblerBaton;
import com.machineryassembler.common.network.NetworkHandler;
import com.machineryassembler.common.network.PacketAutobuildMissingBlocks;
import com.machineryassembler.common.network.PacketAutobuildObstruction;
import com.machineryassembler.common.network.PacketAutobuildPlacementIssue;
import com.machineryassembler.common.network.PacketAutobuildPlacementIssue.PlacementIssue;
import com.machineryassembler.common.network.PacketAutobuildRequest;
import com.machineryassembler.common.network.PacketAutobuildResult;
import com.machineryassembler.common.structure.BlockRequirement;
import com.machineryassembler.common.structure.Structure;
import com.machineryassembler.common.structure.StructurePattern;
import com.machineryassembler.common.structure.StructureRegistry;


/**
 * Client-side handler for autobuild functionality.
 * Manages ghost preview, sends requests to server, handles responses.
 */
@SideOnly(Side.CLIENT)
public class AutobuildHandler {

    // Current autobuild state
    private static ResourceLocation currentStructure = null;
    private static BlockPos lastAnchor = null;
    private static StructurePattern currentPattern = null;

    /**
     * Select a structure for autobuild.
     * Shows ghost preview in-world, optionally offset to align focus block with anchor position.
     *
     * @param batonStack The baton item stack
     * @param structureId The structure to select
     * @param focusBlock The block type used as anchor (for filtering, may be null)
     * @param anchorPos The world position of the anchor block (for offset, may be null)
     */
    public static void selectStructure(ItemStack batonStack, ResourceLocation structureId,
                                       @Nullable IBlockState focusBlock, @Nullable BlockPos anchorPos) {
        Structure structure = StructureRegistry.getRegistry().getStructure(structureId);
        if (structure == null) {
            MachineryAssembler.LOGGER.warn("Attempted to select non-existent structure: {}", structureId);
            return;
        }

        ItemAssemblerBaton.setSelectedStructure(batonStack, structureId);

        if (focusBlock != null) ItemAssemblerBaton.setFocusBlock(batonStack, focusBlock);

        currentStructure = structureId;
        currentPattern = new StructurePattern(structure.getPattern());

        // Start the ghost preview using InWorldPreviewRenderer
        StructureRenderContext context = StructureRenderContext.createContext(structure);
        context.snapSamples();

        // If we have a focus block and anchor position, calculate the offset
        BlockPos focusOffset = null;
        if (focusBlock != null && anchorPos != null) {
            focusOffset = findFirstOccurrence(currentPattern, focusBlock);
        }

        if (ClientProxy.previewRenderer.startPreview(context, focusOffset, anchorPos, true)) {
            EntityPlayer player = Minecraft.getMinecraft().player;

            if (player != null) {
                player.sendMessage(new TextComponentTranslation("message.machineryassembler.baton.selected"));
            }
        }
    }

    /**
     * Clear the current selection.
     * Called when pressing Escape.
     */
    public static void clearSelection(ItemStack batonStack) {
        ItemAssemblerBaton.clearSelectedStructure(batonStack);
        currentStructure = null;
        currentPattern = null;
        ClientProxy.previewRenderer.cancelPreview();
        BatonHighlightRenderer.clearHighlights();

        EntityPlayer player = Minecraft.getMinecraft().player;

        if (player != null) {
            player.sendMessage(new TextComponentTranslation("message.machineryassembler.preview_cancelled"));
        }
    }

    /**
     * Get the last anchor position.
     * Used when right-clicking in air to restore previous position.
     */
    @Nullable
    public static BlockPos getLastAnchor() {
        return lastAnchor;
    }

    /**
     * Set the last anchor position.
     */
    public static void setLastAnchor(BlockPos anchor) {
        lastAnchor = anchor;
    }

    /**
     * Check if there's an active autobuild selection.
     */
    public static boolean hasSelection() {
        return currentStructure != null && ClientProxy.previewRenderer.hasActivePreview();
    }

    /**
     * Attempt to autobuild the structure by sending a request to the server.
     *
     * @param player The player
     * @param clickedPos The clicked position (can be null if triggered from air)
     * @param batonStack The baton item stack
     * @return true if request was sent, false if failed
     */
    public static boolean attemptAutobuild(EntityPlayer player, @Nullable BlockPos clickedPos, ItemStack batonStack) {
        ResourceLocation structureId = ItemAssemblerBaton.getSelectedStructure(batonStack);
        if (structureId == null) return false;

        Structure structure = StructureRegistry.getRegistry().getStructure(structureId);

        if (structure == null) {
            player.sendMessage(new TextComponentTranslation("message.machineryassembler.baton.invalid_structure"));

            return false;
        }

        // Get the fixed position from the preview renderer
        BlockPos origin = ClientProxy.previewRenderer.getFixedPosition();
        if (origin == null) {
            player.sendMessage(new TextComponentTranslation("message.machineryassembler.baton.fix_first"));

            return false;
        }

        // Clear any existing highlights from previous attempts
        BatonHighlightRenderer.clearHighlights();

        // Send request to server
        player.sendMessage(new TextComponentTranslation("message.machineryassembler.baton.building"));
        NetworkHandler.INSTANCE.sendToServer(new PacketAutobuildRequest(structureId, origin));

        // Fully tear down the autobuild state. The anchor position served its purpose
        // and must not linger, otherwise further right-clicks would re-trigger autobuild.
        // The user drives the next action (shift right-click for GUI, etc.).
        // The focus block type is kept so the GUI can still filter by it on next open.
        lastAnchor = null;
        currentStructure = null;
        currentPattern = null;
        ClientProxy.previewRenderer.cancelPreview();
        ItemAssemblerBaton.clearSelectedStructure(batonStack);
        ItemAssemblerBaton.clearLastAnchorPos(batonStack);

        return true;
    }

    // ==================== Server Response Handlers ====================

    /**
     * Handle obstruction response from server.
     * Highlights obstructed blocks in red and clears selection.
     */
    public static void handleObstructionResponse(PacketAutobuildObstruction packet) {
        List<BlockPos> obstructed = packet.getObstructedPositions();

        BatonHighlightRenderer.addHighlights(obstructed, BatonHighlightRenderer.HighlightType.OBSTRUCTION);

        EntityPlayer player = Minecraft.getMinecraft().player;

        if (player != null) {
            player.sendMessage(new TextComponentTranslation("message.machineryassembler.baton.obstructed", obstructed.size()));
        }

        // Clear selection since build cannot proceed
        clearCurrentSelection();
    }

    /**
     * Handle missing blocks response from server.
     */
    public static void handleMissingBlocksResponse(PacketAutobuildMissingBlocks packet) {
        Map<String, Integer> missing = packet.getMissingBlocks();
        EntityPlayer player = Minecraft.getMinecraft().player;

        if (player == null) return;

        if (packet.isAborted()) {
            player.sendMessage(new TextComponentTranslation("message.machineryassembler.baton.missing_aborted"));

            // Clear selection since build cannot proceed
            clearCurrentSelection();
        }

        // Show missing blocks to player
        int totalMissing = missing.values().stream().mapToInt(Integer::intValue).sum();
        player.sendMessage(new TextComponentTranslation("message.machineryassembler.baton.missing_blocks", totalMissing));

        // Show details
        for (Map.Entry<String, Integer> entry : missing.entrySet()) {
            String displayName = BlockSourceUtils.getDisplayName(entry.getKey());
            String key = "message.machineryassembler.baton.missing_entry";
            player.sendMessage(new TextComponentTranslation(key, displayName, entry.getValue()));
        }
    }

    /**
     * Handle build result from server.
     */
    public static void handleBuildResult(PacketAutobuildResult packet) {
        EntityPlayer player = Minecraft.getMinecraft().player;

        if (player == null) return;

        switch (packet.getResult()) {
            case SUCCESS:
                player.sendMessage(new TextComponentTranslation("message.machineryassembler.baton.build_complete", packet.getPlacedCount()));
                break;

            case PARTIAL_SUCCESS:
                player.sendMessage(new TextComponentTranslation("message.machineryassembler.baton.partial_build",
                    packet.getPlacedCount(), packet.getFailedCount()));
                break;

            case FAILED:
                player.sendMessage(new TextComponentTranslation("message.machineryassembler.baton.build_failed"));
                break;
        }
    }

    /**
     * Handle placement issues from server.
     */
    public static void handlePlacementIssues(PacketAutobuildPlacementIssue packet) {
        List<PlacementIssue> issues = packet.getIssues();

        for (PlacementIssue issue : issues) {
            BatonHighlightRenderer.HighlightType highlightType;

            switch (issue.getType()) {
                case WRONG_BLOCK:
                    highlightType = BatonHighlightRenderer.HighlightType.WRONG_BLOCK;
                    break;

                case CORRECT_EXTERNAL:
                    highlightType = BatonHighlightRenderer.HighlightType.CORRECT_EXTERNAL;
                    break;

                case PLACEMENT_FAILED:
                default:
                    highlightType = BatonHighlightRenderer.HighlightType.OBSTRUCTION;
                    break;
            }

            BatonHighlightRenderer.addHighlight(issue.getPos(), highlightType);
        }
    }

    /**
     * Clear selection without needing the baton stack.
     * Used by response handlers.
     */
    private static void clearCurrentSelection() {
        currentStructure = null;
        currentPattern = null;
        ClientProxy.previewRenderer.cancelPreview();
    }

    /**
     * Get the first occurrence of a block in the structure pattern.
     * Used for positioning the ghost preview at anchor block.
     */
    @Nullable
    public static BlockPos findFirstOccurrence(StructurePattern pattern, IBlockState targetState) {
        for (Map.Entry<BlockPos, BlockRequirement> entry : pattern.getPattern().entrySet()) {
            BlockRequirement req = entry.getValue();

            for (IBlockState sample : req.getSamples()) {
                if (statesMatch(sample, targetState)) return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Check if two block states match (same block and metadata).
     */
    private static boolean statesMatch(IBlockState a, IBlockState b) {
        if (a.getBlock() != b.getBlock()) return false;

        return a.getBlock().getMetaFromState(a) == b.getBlock().getMetaFromState(b);
    }
}

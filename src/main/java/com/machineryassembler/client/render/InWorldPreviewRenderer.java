// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/client/util/BlockArrayPreviewRenderHelper.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.client.render;

import java.util.Map;

import javax.annotation.Nullable;

import org.lwjgl.opengl.GL11;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.common.structure.BlockRequirement;
import com.machineryassembler.common.structure.StructurePattern;


/**
 * Renders a structure preview in the world as translucent ghost blocks.
 * The preview floats in front of the player's eyes and can be moved with keybinds.
 * Right-click fixes the preview at the current position for guided building.
 */
@SideOnly(Side.CLIENT)
public class InWorldPreviewRenderer {

    private static final double DEFAULT_DISTANCE = 4.0;

    private static int hash = -1;
    private static int batchDList = -1;

    private StructureRenderHelper renderHelper = null;
    private StructurePattern matchArray = null;
    private BlockPos patternOffset = null;

    // Floating preview: offset stored as forward/right/up relative to player
    private int relativeForward = 0;
    private int relativeRight = 0;
    private int relativeUp = 0;

    // Fixed position after right-click
    private BlockPos fixedPosition = null;
    private int renderedLayer = -1;

    // When true, all layers are rendered even when the position is fixed.
    // Baton-selected autobuild sets this to true (server places all blocks at once).
    // JEI manual preview sets this to false (layer-by-layer guided building).
    private boolean showAllLayers = false;

    // Cached structure center offset for proper rotation
    private BlockPos structureCenter = BlockPos.ORIGIN;

    /**
     * Start a preview for the given structure context.
     * Returns true if preview was successfully started.
     */
    public boolean startPreview(StructureRenderContext context) {
        return startPreview(context, null, null, false);
    }

    /**
     * Start a preview for the given structure context with optional anchor offset.
     *
     * @param context The render context
     * @param focusOffset The position of the focus block within the structure (can be null)
     * @param anchorPos The world position where the focus block should be placed (can be null)
     * @return true if preview was successfully started
     */
    public boolean startPreview(StructureRenderContext context,
                                @Nullable BlockPos focusOffset, @Nullable BlockPos anchorPos) {
        return startPreview(context, focusOffset, anchorPos, false);
    }

    /**
     * Start a preview for the given structure context with optional anchor offset and layer mode.
     *
     * @param context The render context
     * @param focusOffset The position of the focus block within the structure (can be null)
     * @param anchorPos The world position where the focus block should be placed (can be null)
     * @param allLayers If true, all layers are shown when fixed (autobuild mode).
     *                  If false, layers are shown one at a time (guided building mode).
     * @return true if preview was successfully started
     */
    public boolean startPreview(StructureRenderContext context,
                                @Nullable BlockPos focusOffset, @Nullable BlockPos anchorPos,
                                boolean allLayers) {
        if (context.getShiftSnap() == -1) return false;

        this.renderHelper = context.getRender();
        this.matchArray = context.getPattern();
        this.renderHelper.setSampleSnap(context.getShiftSnap());
        this.patternOffset = context.getMoveOffset();
        this.relativeForward = 0;
        this.relativeRight = 0;
        this.relativeUp = 0;
        this.fixedPosition = null;
        this.showAllLayers = allLayers;

        // Calculate structure center for proper positioning and rotation
        BlockPos min = matchArray.getMin();
        BlockPos max = matchArray.getMax();
        this.structureCenter = new BlockPos(
            (min.getX() + max.getX()) / 2,
            min.getY(),
            (min.getZ() + max.getZ()) / 2
        );

        // If we have an anchor position and focus offset, fix the preview there immediately
        if (focusOffset != null && anchorPos != null) {
            // Calculate the origin position such that the focus block is at anchorPos
            // origin + focusOffset = anchorPos
            // origin = anchorPos - focusOffset
            this.fixedPosition = anchorPos.subtract(focusOffset);

            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player != null) {
                player.sendMessage(new TextComponentTranslation("gui.machineryassembler.preview.fixed"));
            }

            // Only compute layer-by-layer guidance when not showing all layers
            if (!showAllLayers) {
                updateLayers();
            }
        } else {
            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player != null) {
                player.sendMessage(new TextComponentTranslation("gui.machineryassembler.preview.place"));
            }
        }

        return true;
    }

    /**
     * Fix the preview at the current floating position.
     * After this, the preview won't follow the player's view.
     * Returns true if successfully fixed.
     */
    public boolean fixPreview() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null || renderHelper == null || fixedPosition != null) return false;

        // Calculate the current floating position and fix it there
        fixedPosition = calculateFloatingPosition(player);
        if (fixedPosition == null) return false;

        updateLayers();
        player.sendMessage(new TextComponentTranslation("gui.machineryassembler.preview.fixed"));

        return true;
    }

    /**
     * Cancel the current preview.
     */
    public void cancelPreview() {
        clearSelection();
    }

    /**
     * Rotate the preview pattern 90 degrees counter-clockwise around Y axis.
     * For fixed previews, rotation is around the structure's center point.
     * For floating previews, the structure stays in front of the player.
     */
    public void rotateCCW() {
        if (matchArray == null) return;

        // Store old center before rotation (for fixed position adjustment)
        BlockPos oldMin = matchArray.getMin();
        BlockPos oldMax = matchArray.getMax();
        BlockPos oldCenter = new BlockPos(
            (oldMin.getX() + oldMax.getX()) / 2,
            oldMin.getY(),
            (oldMin.getZ() + oldMax.getZ()) / 2
        );

        matchArray = matchArray.rotateYCCW();

        // Recalculate center after rotation
        BlockPos min = matchArray.getMin();
        BlockPos max = matchArray.getMax();
        structureCenter = new BlockPos(
            (min.getX() + max.getX()) / 2,
            min.getY(),
            (min.getZ() + max.getZ()) / 2
        );

        // For fixed position, adjust so structure rotates around its center
        if (fixedPosition != null) {
            // Calculate world position of old center
            BlockPos oldWorldCenter = fixedPosition.add(oldCenter);
            // New position = old world center - new local center
            fixedPosition = oldWorldCenter.subtract(structureCenter);
        }

        // Force display list rebuild
        if (batchDList != -1) {
            GLAllocation.deleteDisplayLists(batchDList);
            batchDList = -1;
            hash = -1;
        }

        if (fixedPosition != null) updateLayers();
    }

    /**
     * Rotate the preview pattern 90 degrees clockwise around Y axis.
     * For fixed previews, rotation is around the structure's center point.
     * For floating previews, the structure stays in front of the player.
     */
    public void rotateCW() {
        if (matchArray == null) return;

        // Store old center before rotation (for fixed position adjustment)
        BlockPos oldMin = matchArray.getMin();
        BlockPos oldMax = matchArray.getMax();
        BlockPos oldCenter = new BlockPos(
            (oldMin.getX() + oldMax.getX()) / 2,
            oldMin.getY(),
            (oldMin.getZ() + oldMax.getZ()) / 2
        );

        matchArray = matchArray.rotateYCW();

        // Recalculate center after rotation
        BlockPos min = matchArray.getMin();
        BlockPos max = matchArray.getMax();
        structureCenter = new BlockPos(
            (min.getX() + max.getX()) / 2,
            min.getY(),
            (min.getZ() + max.getZ()) / 2
        );

        // For fixed position, adjust so structure rotates around its center
        if (fixedPosition != null) {
            // Calculate world position of old center
            BlockPos oldWorldCenter = fixedPosition.add(oldCenter);
            // New position = old world center - new local center
            fixedPosition = oldWorldCenter.subtract(structureCenter);
        }

        // Force display list rebuild
        if (batchDList != -1) {
            GLAllocation.deleteDisplayLists(batchDList);
            batchDList = -1;
            hash = -1;
        }

        if (fixedPosition != null) updateLayers();
    }

    /**
     * Move the floating offset in world coordinates. Works whether or not the preview is fixed.
     */
    public void moveOffset(int dx, int dy, int dz) {
        if (fixedPosition != null) {
            fixedPosition = fixedPosition.add(dx, dy, dz);
            updateLayers();
        } else {
            // For floating preview, convert world delta to relative offset
            // This is approximation - not perfect but reasonable
            relativeUp += dy;
        }
    }

    /**
     * Move the preview relative to player facing direction.
     * This is player-relative so the offset stays in front/behind as player turns.
     */
    public void moveRelative(EnumFacing horizontalFacing, int forward, int right, int up) {
        if (fixedPosition != null) {
            // For fixed position, convert to world coords and move
            int dx = 0, dz = 0;

            switch (horizontalFacing) {
                case NORTH:
                    dx = right;
                    dz = -forward;
                    break;
                case SOUTH:
                    dx = -right;
                    dz = forward;
                    break;
                case EAST:
                    dx = forward;
                    dz = right;
                    break;
                case WEST:
                    dx = -forward;
                    dz = -right;
                    break;
                default:
                    break;
            }

            fixedPosition = fixedPosition.add(dx, up, dz);
            updateLayers();
        } else {
            // For floating preview, store in relative coordinates
            relativeForward += forward;
            relativeRight += right;
            relativeUp += up;
        }
    }

    /**
     * Check if there is an active preview.
     */
    public boolean hasActivePreview() {
        return renderHelper != null;
    }

    /**
     * Check if the preview is fixed at a position (vs floating).
     */
    public boolean isFixed() {
        return fixedPosition != null;
    }

    /**
     * Get the fixed position, or null if floating.
     */
    @Nullable
    public BlockPos getFixedPosition() {
        return fixedPosition;
    }

    /**
     * Clear just the fixed position without canceling the preview.
     * The preview will continue floating from the player's view.
     * Used after autobuild is started to allow placing another copy elsewhere.
     */
    public void clearFixedPosition() {
        fixedPosition = null;
        relativeForward = 0;
        relativeRight = 0;
        relativeUp = 0;
    }

    /**
     * Check if the preview is in autobuild mode (all layers shown).
     * When true, the preview was started by baton selection for server-side autobuild.
     * When false, the preview was started by JEI for layer-by-layer guided building.
     */
    public boolean isAutobuildMode() {
        return showAllLayers;
    }
    /**
     * Calculate the floating position based on player's eye position and look direction.
     * The structure is positioned so the FACE the player is looking at is at a constant distance.
     * This ensures the preview follows the player's gaze smoothly.
     */
    @Nullable
    private BlockPos calculateFloatingPosition(EntityPlayer player) {
        if (player == null || matchArray == null) return null;

        // Get player's eye position and look vector
        Vec3d eyePos = player.getPositionEyes(1.0f);
        Vec3d lookVec = player.getLookVec();

        // Calculate structure dimensions
        BlockPos min = matchArray.getMin();
        BlockPos max = matchArray.getMax();
        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;

        // Determine which face of the structure the player is looking at
        // based on the dominant component of the look vector (horizontal only)
        double absX = Math.abs(lookVec.x);
        double absZ = Math.abs(lookVec.z);

        // Calculate distance from eye to the face the player would be looking at
        // This keeps a constant gap between player and the nearest face
        double distanceToFace = 3.0;
        double halfSizeX = sizeX / 2.0;
        double halfSizeZ = sizeZ / 2.0;

        double totalDistance;
        if (absX > absZ) {
            // Looking primarily along X axis - structure face is perpendicular to X
            totalDistance = distanceToFace + halfSizeX;
        } else {
            // Looking primarily along Z axis - structure face is perpendicular to Z
            totalDistance = distanceToFace + halfSizeZ;
        }

        // Project forward from eye position along look vector (horizontal plane)
        double horizontalLength = Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z);
        if (horizontalLength < 0.01) {
            // Looking straight up or down, use player's horizontal facing
            EnumFacing facing = player.getHorizontalFacing();
            Vec3d facingVec = new Vec3d(facing.getXOffset(), 0, facing.getZOffset());
            lookVec = facingVec;
            horizontalLength = 1.0;
        }

        // Normalize horizontal component and scale to total distance
        double normalizedX = lookVec.x / horizontalLength;
        double normalizedZ = lookVec.z / horizontalLength;

        double targetX = eyePos.x + normalizedX * totalDistance;
        double targetZ = eyePos.z + normalizedZ * totalDistance;
        double targetY = player.posY;  // Keep at player's feet level

        // This is where the CENTER of the structure should be
        // Calculate the min corner position from center
        BlockPos structurePos = new BlockPos(
            MathHelper.floor(targetX) - structureCenter.getX(),
            MathHelper.floor(targetY) - structureCenter.getY(),
            MathHelper.floor(targetZ) - structureCenter.getZ()
        );

        // Apply relative offset based on player's current facing
        // Forward = along look direction, Right = perpendicular to left
        int dx = 0, dz = 0;
        if (absX > absZ) {
            // Looking primarily along X
            if (normalizedX > 0) {
                // Looking +X (East)
                dx = relativeForward;
                dz = relativeRight;
            } else {
                // Looking -X (West)
                dx = -relativeForward;
                dz = -relativeRight;
            }
        } else {
            // Looking primarily along Z
            if (normalizedZ > 0) {
                // Looking +Z (South)
                dx = -relativeRight;
                dz = relativeForward;
            } else {
                // Looking -Z (North)
                dx = relativeRight;
                dz = -relativeForward;
            }
        }

        return structurePos.add(dx, relativeUp, dz);
    }

    /**
     * Called every tick to update the preview state.
     */
    public void tick() {
        if (fixedPosition == null) return;

        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player != null && player.getDistanceSqToCenter(fixedPosition) >= 1024) {
            clearSelection();
            return;
        }

        World world = Minecraft.getMinecraft().world;
        if (world == null || renderHelper == null) return;

        // In showAllLayers mode (autobuild), just check if the entire structure is complete
        if (showAllLayers) {
            if (matchArray.matches(world, fixedPosition, true)) {
                EntityPlayer p = Minecraft.getMinecraft().player;
                if (p != null) {
                    p.sendMessage(new TextComponentTranslation("gui.machineryassembler.preview.complete"));
                }

                clearSelection();
            }

            return;
        }

        // Layer-by-layer mode: check if lower layers are now complete
        if (hasLowerLayer() && !doesPlacedLayerMatch(renderedLayer - 1)) {
            updateLayers();

            return;
        }

        if (!doesPlacedLayerMatch(renderedLayer)) return;

        // Current layer complete, check if whole structure matches
        if (!matchArray.matches(world, fixedPosition, true)) {
            updateLayers();

            return;
        }

        // Structure complete!
        EntityPlayer p = Minecraft.getMinecraft().player;
        if (p != null) {
            p.sendMessage(new TextComponentTranslation("gui.machineryassembler.preview.complete"));
        }

        clearSelection();
    }

    /**
     * Render the preview blocks in the world.
     */
    public void renderTranslucentBlocks() {
        if (renderHelper == null) return;

        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();

        float partialTicks = Minecraft.getMinecraft().getRenderPartialTicks();
        Entity rView = Minecraft.getMinecraft().getRenderViewEntity();
        if (rView == null) rView = Minecraft.getMinecraft().player;

        Entity entity = rView;
        double tx = entity.lastTickPosX + ((entity.posX - entity.lastTickPosX) * partialTicks);
        double ty = entity.lastTickPosY + ((entity.posY - entity.lastTickPosY) * partialTicks);
        double tz = entity.lastTickPosZ + ((entity.posZ - entity.lastTickPosZ) * partialTicks);
        GlStateManager.translate(-tx, -ty, -tz);

        GlStateManager.color(1F, 1F, 1F, 1F);

        if (batchDList == -1) {
            batchBlocks();
            hash = hashBlocks();
        } else {
            int currentHash = hashBlocks();
            if (hash != currentHash) {
                GLAllocation.deleteDisplayLists(batchDList);
                batchBlocks();
                hash = currentHash;
            }
        }

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_DST_COLOR);
        GlStateManager.callList(batchDList);
        Blending.DEFAULT.applyStateManager();
        GlStateManager.enableDepth();

        GlStateManager.popMatrix();

        // Color desync on block rendering - prevent that, resync
        GlStateManager.color(1F, 1F, 1F, 1F);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private int hashBlocks() {
        int hashValue = 80238287;

        if (renderHelper == null || Minecraft.getMinecraft().player == null) return hashValue;

        BlockPos move = getRenderOffset();
        if (move == null) return hashValue;

        StructurePattern render = new StructurePattern(matchArray, move);
        long snapTick = renderHelper.getSampleSnap();

        for (Map.Entry<BlockPos, BlockRequirement> entry : render.getPattern().entrySet()) {
            BlockPos pos = entry.getKey();
            BlockRequirement info = entry.getValue();

            World world = Minecraft.getMinecraft().world;
            if (world != null && info.matches(world, pos, false)) continue;

            int layer = pos.subtract(move).getY();
            if (fixedPosition != null && !showAllLayers && renderedLayer != layer) continue;

            IBlockState state = info.getSampleState(snapTick);
            hashValue = (hashValue << 4) ^ (hashValue >> 28) ^ (pos.getX() * 5449 % 130651);
            hashValue = (hashValue << 4) ^ (hashValue >> 28) ^ (pos.getY() * 5449 % 130651);
            hashValue = (hashValue << 4) ^ (hashValue >> 28) ^ (pos.getZ() * 5449 % 130651);
            hashValue = (hashValue << 4) ^ (hashValue >> 28) ^ (state.hashCode() * 5449 % 130651);
        }

        return hashValue % 75327403;
    }

    private void batchBlocks() {
        BlockPos move = getRenderOffset();

        if (move == null || renderHelper == null) {
            if (batchDList != -1) {
                GlStateManager.glDeleteLists(batchDList, 1);
                batchDList = -1;
            }
            return;
        }

        batchDList = GLAllocation.generateDisplayLists(1);
        GlStateManager.glNewList(batchDList, GL11.GL_COMPILE);

        Tessellator tes = Tessellator.getInstance();
        BufferBuilder vb = tes.getBuffer();
        StructurePattern matchPattern = matchArray;

        DummyBlockAccess access = new DummyBlockAccess();
        long snapTick = renderHelper.getSampleSnap();

        // Populate access with blocks
        for (Map.Entry<BlockPos, BlockRequirement> entry : matchPattern.getPattern().entrySet()) {
            BlockPos relPos = entry.getKey();
            BlockPos worldPos = relPos.add(move);
            IBlockState state = entry.getValue().getSampleState(snapTick);
            access.setBlockState(worldPos, state);
        }

        BlockRendererDispatcher brd = Minecraft.getMinecraft().getBlockRendererDispatcher();
        VertexFormat blockFormat = DefaultVertexFormats.BLOCK;

        for (Map.Entry<BlockPos, BlockRequirement> entry : matchPattern.getPattern().entrySet()) {
            BlockPos relPos = entry.getKey();
            int layer = relPos.getY();
            if (fixedPosition != null && !showAllLayers && renderedLayer != layer) continue;

            BlockPos worldPos = relPos.add(move);
            BlockRequirement info = entry.getValue();

            World world = Minecraft.getMinecraft().world;
            if (world != null && info.matches(world, worldPos, false)) continue;

            IBlockState state = info.getSampleState(snapTick);
            if (state.getBlock() == Blocks.AIR) continue;

            IBlockState actualState = state.getBlock().getActualState(state, access, worldPos);

            GlStateManager.pushMatrix();
            GlStateManager.translate(worldPos.getX(), worldPos.getY(), worldPos.getZ());
            GlStateManager.translate(0.125, 0.125, 0.125);
            GlStateManager.scale(0.75, 0.75, 0.75);
            vb.begin(GL11.GL_QUADS, blockFormat);
            brd.renderBlock(actualState, BlockPos.ORIGIN, access, vb);
            tes.draw();
            GlStateManager.popMatrix();
        }

        GlStateManager.glEndList();
    }

    @Nullable
    private BlockPos getRenderOffset() {
        if (fixedPosition != null) return fixedPosition;

        EntityPlayer player = Minecraft.getMinecraft().player;

        return calculateFloatingPosition(player);
    }

    private boolean doesPlacedLayerMatch(int slice) {
        if (fixedPosition == null) return false;

        World world = Minecraft.getMinecraft().world;
        if (world == null) return true;

        Map<BlockPos, BlockRequirement> patternSlice = matchArray.getPatternSlice(slice);

        for (Map.Entry<BlockPos, BlockRequirement> entry : patternSlice.entrySet()) {
            BlockPos offset = entry.getKey();
            BlockPos actualPosition = offset.add(fixedPosition);
            BlockRequirement info = entry.getValue();

            if (!info.matches(world, actualPosition, false)) return false;
        }

        return true;
    }

    private boolean hasLowerLayer() {
        if (fixedPosition == null) return false;

        return matchArray.getMin().getY() <= renderedLayer - 1;
    }

    private void updateLayers() {
        renderedLayer = -1;

        if (fixedPosition == null) return;

        int lowestSlice = matchArray.getMin().getY();
        int maxSlice = matchArray.getMax().getY();

        for (int y = lowestSlice; y <= maxSlice; y++) {
            if (!doesPlacedLayerMatch(y)) {
                renderedLayer = y;

                return;
            }
        }
    }

    private void clearSelection() {
        renderHelper = null;
        matchArray = null;
        patternOffset = null;
        fixedPosition = null;
        showAllLayers = false;
        relativeForward = 0;
        relativeRight = 0;
        relativeUp = 0;
        structureCenter = BlockPos.ORIGIN;
    }

    public void unloadWorld() {
        clearSelection();
    }
}

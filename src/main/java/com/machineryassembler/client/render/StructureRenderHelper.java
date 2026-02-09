// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/client/util/BlockArrayRenderHelper.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.client.render;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.lwjgl.opengl.GL11;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.common.structure.BlockRequirement;
import com.machineryassembler.common.structure.StructurePattern;


/**
 * Helper class for rendering StructurePatterns in GUI.
 * Handles rotation, scaling, slicing, and block/tile entity rendering.
 */
@SideOnly(Side.CLIENT)
public class StructureRenderHelper {

    private final StructurePattern pattern;
    private final DummyBlockAccess renderAccess;
    private final Map<BlockPos, RenderData> renderDataMap = new HashMap<>();

    private double rotX = -30;
    private double rotY = 45;
    private double rotZ = 0;
    private double sliceTrX = 0;
    private double sliceTrY = 0;
    private double sliceTrZ = 0;

    private long sampleSnap = -1;

    public StructureRenderHelper(StructurePattern pattern) {
        this.pattern = pattern;
        this.renderAccess = new DummyBlockAccess();
        buildRenderData();
        resetRotation();
    }

    private void buildRenderData() {
        renderDataMap.clear();
        renderAccess.clear();

        for (Map.Entry<BlockPos, BlockRequirement> entry : pattern.getPattern().entrySet()) {
            BlockPos pos = entry.getKey();
            BlockRequirement info = entry.getValue();
            RenderData data = new RenderData(info);
            renderDataMap.put(pos, data);
        }
    }

    public void resetRotation() {
        this.rotX = -30;
        this.rotY = 45;
        this.rotZ = 0;
        this.sliceTrX = 0;
        this.sliceTrY = 0;
        this.sliceTrZ = 0;
    }

    public void resetRotation2D() {
        this.rotX = -90;
        this.rotY = 0;
        this.rotZ = 0;
        this.sliceTrX = 0;
        this.sliceTrY = 0;
        this.sliceTrZ = 0;
    }

    public void translate(double x, double y, double z) {
        this.sliceTrX += x;
        this.sliceTrY += y;
        this.sliceTrZ += z;
    }

    public void rotate(double x, double y, double z) {
        this.rotX += x;
        this.rotY += y;
        this.rotZ += z;
    }

    public Vec3d getCurrentTranslation() {
        return new Vec3d(sliceTrX, sliceTrY, sliceTrZ);
    }

    public long getSampleSnap() {
        return sampleSnap;
    }

    public void setSampleSnap(long snap) {
        this.sampleSnap = snap;
    }

    public StructurePattern getPattern() {
        return pattern;
    }

    /**
     * Render the structure in 3D GUI context.
     */
    public void render3DGUI(double x, double y, float scaleMultiplier, float pTicks) {
        render3DGUI(x, y, scaleMultiplier, pTicks, Optional.empty());
    }

    /**
     * Render the structure in 3D GUI context with optional layer slicing.
     */
    public void render3DGUI(double x, double y, float scaleMultiplier, float pTicks, Optional<Integer> slice) {
        if (Minecraft.getMinecraft().currentScreen == null) return;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        BlockPos max = pattern.getMax();
        BlockPos min = pattern.getMin();

        Minecraft mc = Minecraft.getMinecraft();
        double sc = new ScaledResolution(mc).getScaleFactor();
        GL11.glTranslated(x + 16D / sc, y + 16D / sc, 512);

        double mul = scaleMultiplier * 10 * 0.75;
        double size = 2.5;
        double minSize = 0.5;

        int dx = max.getX() - min.getX() + 1;
        int dy = max.getY() - min.getY() + 1;
        int dz = max.getZ() - min.getZ() + 1;

        int maxLength;
        if (slice.isPresent()) {
            // In 2D mode, scale based on X and Z dimensions only
            maxLength = Math.max(dx, dz);
        } else {
            // In 3D mode, scale based on all dimensions
            maxLength = Math.max(dx, Math.max(dy, dz));
        }

        // Scale down based on structure size so larger structures fit in the preview
        // The larger the structure, the smaller the size multiplier
        if (maxLength > 3) {
            // Linear falloff: from size (at 3 blocks) down to minSize (at ~20 blocks)
            double t = Math.min(1.0, (maxLength - 3) / 17.0);
            size = size - t * (size - minSize);
        }

        double dr = -5.75 * size;
        GL11.glTranslated(dr, dr, dr);
        GL11.glRotated(rotX, 1, 0, 0);
        GL11.glRotated(rotY, 0, 1, 0);
        GL11.glRotated(rotZ, 0, 0, 1);
        GL11.glTranslated(-dr, -dr, -dr);

        GL11.glTranslated(sliceTrX, sliceTrY, sliceTrZ);

        GL11.glScaled(-size * mul, -size * mul, -size * mul);

        // Update render access with current sample states
        updateRenderAccess(slice.orElse(null));

        BlockRendererDispatcher brd = Minecraft.getMinecraft().getBlockRendererDispatcher();
        VertexFormat blockFormat = DefaultVertexFormats.BLOCK;

        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        Tessellator tes = Tessellator.getInstance();
        BufferBuilder vb = tes.getBuffer();

        // Render blocks
        vb.begin(GL11.GL_QUADS, blockFormat);
        for (Map.Entry<BlockPos, RenderData> entry : renderDataMap.entrySet()) {
            BlockPos pos = entry.getKey();
            if (slice.isPresent() && slice.get() != pos.getY()) continue;

            RenderData data = entry.getValue();
            IBlockState state = data.getSampleState(sampleSnap);
            if (state == null || state.getBlock() == Blocks.AIR) continue;

            // Skip states with missing models to avoid purple/black checkerboard textures
            // But allow fluid blocks which render differently
            boolean isFluid = state.getBlock() instanceof BlockLiquid || state.getBlock() instanceof IFluidBlock;
            if (!isFluid && !BlockStateRenderValidator.canRender(state)) continue;

            try {
                IBlockState actualState = state.getBlock().getActualState(state, renderAccess, pos);

                if (isFluid) {
                    // Fluids are rendered through the normal block renderer as well
                    // The BlockRendererDispatcher handles BlockLiquid specially via BlockModelRenderer
                    brd.renderBlock(actualState, pos, renderAccess, vb);
                } else {
                    brd.renderBlock(actualState, pos, renderAccess, vb);
                }
            } catch (Exception ignored) {
                // Some blocks fail to render in fake world context - silently skip
            }
        }
        tes.draw();

        // Render tile entities
        for (Map.Entry<BlockPos, RenderData> entry : renderDataMap.entrySet()) {
            BlockPos pos = entry.getKey();
            if (slice.isPresent() && slice.get() != pos.getY()) continue;

            RenderData data = entry.getValue();
            TileEntity te = data.getTileEntity();
            if (te == null) continue;

            @SuppressWarnings("unchecked")
            TileEntitySpecialRenderer<TileEntity> renderer =
                TileEntityRendererDispatcher.instance.getRenderer(te);
            if (renderer == null) continue;

            te.setWorld(Minecraft.getMinecraft().world);
            te.setPos(pos);

            try {
                renderer.render(te, pos.getX(), pos.getY(), pos.getZ(), pTicks, 0, 1F);
            } catch (Exception e) {
                // Some TileEntities throw when rendered without a proper world context
                // This is expected for blocks like AE2's Quantum Bridge
            }
        }

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /**
     * Update the render access with current sample states.
     */
    private void updateRenderAccess(@Nullable Integer slice) {
        renderAccess.clear();

        for (Map.Entry<BlockPos, RenderData> entry : renderDataMap.entrySet()) {
            BlockPos pos = entry.getKey();
            if (slice != null && slice != pos.getY()) continue;

            RenderData data = entry.getValue();
            IBlockState state = data.getSampleState(sampleSnap);
            renderAccess.setBlockState(pos, state);

            TileEntity te = data.getTileEntity();
            if (te != null) renderAccess.setTileEntity(pos, te);
        }
    }

    /**
     * Holds render data for a single block position.
     */
    private static class RenderData {
        private final BlockRequirement info;
        private TileEntity cachedTileEntity;
        private IBlockState lastState;

        RenderData(BlockRequirement info) {
            this.info = info;
        }

        IBlockState getSampleState(long snapTick) {
            return info.getSampleState(snapTick);
        }

        @Nullable
        TileEntity getTileEntity() {
            if (!info.hasTileEntity()) return null;

            // Update tile entity if state changed
            IBlockState currentState = getSampleState(-1);
            if (lastState != currentState || cachedTileEntity == null) {
                lastState = currentState;

                if (currentState.getBlock().hasTileEntity(currentState)) {
                    try {
                        cachedTileEntity = currentState.getBlock().createTileEntity(
                            Minecraft.getMinecraft().world, currentState);

                        // Apply preview tag if present
                        NBTTagCompound previewTag = info.getPreviewTag();
                        if (cachedTileEntity != null && previewTag != null && !previewTag.isEmpty()) {
                            NBTTagCompound nbt = new NBTTagCompound();
                            cachedTileEntity.writeToNBT(nbt);
                            for (String key : previewTag.getKeySet()) nbt.setTag(key, previewTag.getTag(key));
                            cachedTileEntity.readFromNBT(nbt);
                        }
                    } catch (Exception e) {
                        cachedTileEntity = null;
                    }
                } else {
                    cachedTileEntity = null;
                }
            }

            return cachedTileEntity;
        }
    }
}

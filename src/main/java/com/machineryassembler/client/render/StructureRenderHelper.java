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
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.common.structure.BlockRequirement;
import com.machineryassembler.common.structure.StructurePattern;


/**
 * Helper class for rendering StructurePatterns in GUI.
 * Handles rotation, scaling, slicing, and block/tile entity rendering.
 * <p>
 * Global TESRs are still skipped because they render relative to player state.
 */
@SideOnly(Side.CLIENT)
public class StructureRenderHelper {

    private final StructurePattern pattern;
    private final PreviewWorld previewWorld;
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
        this.previewWorld = PreviewWorld.create();
        buildRenderData();
        resetRotation();
    }

    private void buildRenderData() {
        renderDataMap.clear();
        previewWorld.clearPreview();

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

        // Render blocks per Forge layer so layer-sensitive baked models like AE2 cable buses
        // can emit the correct quads in preview rendering.
        for (BlockRenderLayer renderLayer : BlockRenderLayer.values()) {
            vb.begin(GL11.GL_QUADS, blockFormat);
            ForgeHooksClient.setRenderLayer(renderLayer);

            for (Map.Entry<BlockPos, RenderData> entry : renderDataMap.entrySet()) {
                BlockPos pos = entry.getKey();
                if (slice.isPresent() && slice.get() != pos.getY()) continue;

                RenderData data = entry.getValue();
                IBlockState state = data.getSampleState(sampleSnap);
                if (state == null || state.getBlock() == Blocks.AIR) continue;
                if (!state.getBlock().canRenderInLayer(state, renderLayer)) continue;

                // Skip states with missing models to avoid purple/black checkerboard textures
                // But allow fluid blocks which render differently.
                EnumBlockRenderType renderType = state.getRenderType();
                boolean isFluid = renderType == EnumBlockRenderType.LIQUID ||
                    state.getBlock() instanceof BlockLiquid || state.getBlock() instanceof IFluidBlock;
                if (!isFluid && !BlockStateRenderValidator.canRender(state)) continue;

                try {
                    IBlockState actualState = PreviewRenderStateResolver.resolveActual(state, previewWorld, pos);
                    IBlockState renderState = PreviewRenderStateResolver.resolve(state, previewWorld, pos);

                    if (renderType == EnumBlockRenderType.LIQUID) {
                        brd.renderBlock(state, pos, previewWorld, vb);
                        continue;
                    }

                    IBakedModel model = brd.getModelForState(actualState);

                    brd.getBlockModelRenderer().renderModel(previewWorld, model, renderState, pos, vb, true);
                } catch (Exception ignored) {
                    // Some blocks fail to render in fake world context - silently skip.
                }
            }

            tes.draw();
        }

        ForgeHooksClient.setRenderLayer(null);

        // Render tile entities with proper lighting and render pass setup
        // Render both pass 0 (opaque) and pass 1 (translucent) for proper transparency support
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableAlpha();

        TileEntityRendererDispatcher dispatcher = TileEntityRendererDispatcher.instance;
        World previousWorld = dispatcher.world;
        dispatcher.setWorld(previewWorld);

        try {
            for (int pass = 0; pass <= 1; pass++) {
                ForgeHooksClient.setRenderPass(pass);

                for (Map.Entry<BlockPos, RenderData> entry : renderDataMap.entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (slice.isPresent() && slice.get() != pos.getY()) continue;

                    TileEntity te = previewWorld.getTileEntity(pos);
                    if (te == null) continue;

                    if (!te.shouldRenderInPass(pass)) continue;

                    TileEntitySpecialRenderer<TileEntity> renderer = dispatcher.getRenderer(te);
                    if (renderer == null) continue;

                    // Skip global renderers as they often cause issues in GUI context
                    // They render relative to player position rather than tile position
                    if (renderer.isGlobalRenderer(te)) continue;

                    te.setWorld(previewWorld);
                    te.setPos(pos);

                    try {
                        int light = previewWorld.getCombinedLight(pos, 0);
                        int lightX = light % 65536;
                        int lightY = light / 65536;
                        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
                        GlStateManager.color(1F, 1F, 1F, 1F);

                        // Preview TESRs are regular renders, not block-breaking overlays.
                        dispatcher.render(te, pos.getX(), pos.getY(), pos.getZ(), pTicks);
                    } catch (Exception e) {
                        // Some TileEntities throw when rendered without a proper world context
                        // This is expected for blocks like AE2's Quantum Bridge
                    }
                }
            }
        } finally {
            dispatcher.setWorld(previousWorld);
        }

        ForgeHooksClient.setRenderPass(-1);
        RenderHelper.disableStandardItemLighting();

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /**
     * Update the render access with current sample states.
     */
    private void updateRenderAccess(@Nullable Integer slice) {
        previewWorld.clearPreview();

        for (Map.Entry<BlockPos, RenderData> entry : renderDataMap.entrySet()) {
            BlockPos pos = entry.getKey();
            if (slice != null && slice != pos.getY()) continue;

            RenderData data = entry.getValue();
            IBlockState state = data.getSampleState(sampleSnap);

            TileEntity te = data.getTileEntity(previewWorld, pos);
            previewWorld.setPreviewState(pos, state, te);
        }

        // FIXME: Fix AE2 tiles not connecting with each other in the preview
        // previewWorld.finalizePreviewTileEntities();
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
        TileEntity getTileEntity(PreviewWorld previewWorld, BlockPos pos) {
            if (!info.hasTileEntity()) return null;

            // Update tile entity if state changed
            IBlockState currentState = getSampleState(-1);
            if (currentState == null || currentState.getBlock() == Blocks.AIR) {
                cachedTileEntity = null;
                lastState = null;
                return null;
            }

            if (lastState != currentState || cachedTileEntity == null || cachedTileEntity.isInvalid()) {
                lastState = currentState;

                if (currentState.getBlock().hasTileEntity(currentState)) {
                    try {
                        cachedTileEntity = currentState.getBlock().createTileEntity(
                            previewWorld, currentState);

                        info.applyPreviewTag(cachedTileEntity);
                    } catch (Exception e) {
                        cachedTileEntity = null;
                    }
                } else {
                    cachedTileEntity = null;
                }
            }

            if (cachedTileEntity != null) {
                cachedTileEntity.setWorld(previewWorld);
                cachedTileEntity.setPos(pos);
            }

            return cachedTileEntity;
        }
    }
}

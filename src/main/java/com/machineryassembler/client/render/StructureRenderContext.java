// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/client/util/DynamicMachineRenderContext.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.common.structure.Structure;
import com.machineryassembler.common.structure.StructurePattern;


/**
 * Render context for a structure. Manages the StructureRenderHelper
 * and rendering state (3D vs 2D, slicing, scale, etc.).
 */
@SideOnly(Side.CLIENT)
public class StructureRenderContext {

    private final Structure structure;
    private final StructureRenderHelper render;
    private final StructurePattern pattern;
    private final BlockPos moveOffset;

    private boolean render3D = true;
    private int renderSlice = 0;
    private float scale = 1F;

    private long shiftSnap = -1;

    private StructureRenderContext(Structure structure) {
        this.structure = structure;
        this.pattern = new StructurePattern(structure.getPattern());

        BlockPos min = pattern.getMin();
        BlockPos max = pattern.getMax();

        // Calculate center using double division for proper centering of odd-sized structures
        // The offset moves the structure so its center is at origin
        int centerX = (int) Math.floor((min.getX() + max.getX()) / 2.0);
        int centerY = (int) Math.floor((min.getY() + max.getY()) / 2.0);
        int centerZ = (int) Math.floor((min.getZ() + max.getZ()) / 2.0);

        this.moveOffset = new BlockPos(-centerX, -centerY, -centerZ);

        StructurePattern centeredPattern = new StructurePattern(pattern, moveOffset);
        this.render = new StructureRenderHelper(centeredPattern);
    }

    public static StructureRenderContext createContext(Structure structure) {
        return new StructureRenderContext(structure);
    }

    public StructureRenderHelper getRender() {
        return render;
    }

    public StructurePattern getPattern() {
        return pattern;
    }

    public Structure getStructure() {
        return structure;
    }

    public BlockPos getMoveOffset() {
        return moveOffset;
    }

    public long getShiftSnap() {
        return shiftSnap;
    }

    public void setShiftSnap(long shiftSnap) {
        this.shiftSnap = shiftSnap;
    }

    public void snapSamples() {
        this.shiftSnap = getClientTick();
    }

    public void releaseSamples() {
        this.shiftSnap = -1;
    }

    public boolean doesRender3D() {
        return render3D;
    }

    public void setTo3D() {
        render3D = true;
        render.resetRotation();
    }

    public void setTo2D() {
        render3D = false;
        render.resetRotation2D();
    }

    public int getRenderSlice() {
        return renderSlice;
    }

    public void setRenderSlice(int slice) {
        this.renderSlice = slice;
    }

    public boolean hasSliceUp() {
        return pattern.getMax().getY() + moveOffset.getY() > renderSlice;
    }

    public boolean hasSliceDown() {
        return pattern.getMin().getY() + moveOffset.getY() < renderSlice;
    }

    public void sliceUp() {
        this.renderSlice++;
    }

    public void sliceDown() {
        this.renderSlice--;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = Math.max(0.1f, scale);
    }

    public void zoomIn() {
        this.scale = Math.min(3f, scale + 0.1f);
    }

    public void zoomOut() {
        this.scale = Math.max(0.1f, scale - 0.1f);
    }

    public void resetScale() {
        this.scale = 1f;
    }

    private static long getClientTick() {
        return Minecraft.getMinecraft().world != null
            ? Minecraft.getMinecraft().world.getTotalWorldTime()
            : 0;
    }
}

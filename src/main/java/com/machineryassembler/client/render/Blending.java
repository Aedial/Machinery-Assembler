// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors
// Based on hellfirepvp/modularmachinery/client/util/Blending.java from MMCE
// https://github.com/KasumiNova/ModularMachinery-Community-Edition

package com.machineryassembler.client.render;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * OpenGL blending modes for rendering.
 */
@SideOnly(Side.CLIENT)
public enum Blending {

    DEFAULT(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA),
    ALPHA(GL11.GL_ONE, GL11.GL_SRC_ALPHA),
    PREALPHA(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA),
    MULTIPLY(GL11.GL_DST_COLOR, GL11.GL_ONE_MINUS_SRC_ALPHA),
    ADDITIVE(GL11.GL_ONE, GL11.GL_ONE),
    ADDITIVEDARK(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_COLOR),
    OVERLAYDARK(GL11.GL_SRC_COLOR, GL11.GL_ONE),
    ADDITIVE_ALPHA(GL11.GL_SRC_ALPHA, GL11.GL_ONE),
    CONSTANT_ALPHA(GL11.GL_ONE, GL11.GL_ONE_MINUS_CONSTANT_ALPHA),
    INVERTEDADD(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR);

    public final int sfactor;
    public final int dfactor;

    Blending(int s, int d) {
        sfactor = s;
        dfactor = d;
    }

    public void apply() {
        GL11.glBlendFunc(sfactor, dfactor);
    }

    public void applyStateManager() {
        GlStateManager.blendFunc(sfactor, dfactor);
    }
}

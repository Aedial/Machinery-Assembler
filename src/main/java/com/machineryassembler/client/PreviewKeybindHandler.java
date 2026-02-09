// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.client;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.client.autobuild.AutobuildHandler;
import com.machineryassembler.client.render.InWorldPreviewRenderer;
import com.machineryassembler.common.item.ItemAssemblerBaton;


/**
 * Handles keybindings for controlling the in-world structure preview.
 * - Escape: Cancel the current preview
 * - Arrow keys: Move the preview relative to player facing direction
 * - Page Up/Down: Move preview up/down
 * - Right-click: Fix the preview at the current position
 * - Shift + scroll wheel: Rotate the preview
 */
@SideOnly(Side.CLIENT)
public class PreviewKeybindHandler {

    private static final String KEYBIND_CATEGORY = "key.categories." + MachineryAssembler.MODID;

    public static final KeyBinding KEY_CANCEL_PREVIEW = new KeyBinding(
        "key.machineryassembler.cancel_preview",
        KeyConflictContext.IN_GAME,
        Keyboard.KEY_ESCAPE,
        KEYBIND_CATEGORY
    );

    public static final KeyBinding KEY_MOVE_FORWARD = new KeyBinding(
        "key.machineryassembler.move_forward",
        KeyConflictContext.IN_GAME,
        Keyboard.KEY_UP,
        KEYBIND_CATEGORY
    );

    public static final KeyBinding KEY_MOVE_BACKWARD = new KeyBinding(
        "key.machineryassembler.move_backward",
        KeyConflictContext.IN_GAME,
        Keyboard.KEY_DOWN,
        KEYBIND_CATEGORY
    );

    public static final KeyBinding KEY_MOVE_LEFT = new KeyBinding(
        "key.machineryassembler.move_left",
        KeyConflictContext.IN_GAME,
        Keyboard.KEY_LEFT,
        KEYBIND_CATEGORY
    );

    public static final KeyBinding KEY_MOVE_RIGHT = new KeyBinding(
        "key.machineryassembler.move_right",
        KeyConflictContext.IN_GAME,
        Keyboard.KEY_RIGHT,
        KEYBIND_CATEGORY
    );

    public static final KeyBinding KEY_MOVE_UP = new KeyBinding(
        "key.machineryassembler.move_up",
        KeyConflictContext.IN_GAME,
        Keyboard.KEY_PRIOR, // Page Up
        KEYBIND_CATEGORY
    );

    public static final KeyBinding KEY_MOVE_DOWN = new KeyBinding(
        "key.machineryassembler.move_down",
        KeyConflictContext.IN_GAME,
        Keyboard.KEY_NEXT, // Page Down
        KEYBIND_CATEGORY
    );

    // Cooldown to prevent too-fast movement
    private int moveCooldown = 0;
    private static final int MOVE_COOLDOWN_TICKS = 3;

    public static void registerKeybinds() {
        ClientRegistry.registerKeyBinding(KEY_CANCEL_PREVIEW);
        ClientRegistry.registerKeyBinding(KEY_MOVE_FORWARD);
        ClientRegistry.registerKeyBinding(KEY_MOVE_BACKWARD);
        ClientRegistry.registerKeyBinding(KEY_MOVE_LEFT);
        ClientRegistry.registerKeyBinding(KEY_MOVE_RIGHT);
        ClientRegistry.registerKeyBinding(KEY_MOVE_UP);
        ClientRegistry.registerKeyBinding(KEY_MOVE_DOWN);
    }

    /**
     * Checks if there is an active in-world preview that can be controlled.
     */
    private static boolean hasActivePreview() {
        return ClientProxy.previewRenderer.hasActivePreview();
    }

    /**
     * Cancels the current in-world preview.
     * Also clears the baton selection if player is holding the baton.
     */
    private static void cancelPreview() {
        ClientProxy.previewRenderer.cancelPreview();

        // Also clear baton selection if holding the baton
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player != null) {
            ItemStack heldItem = player.getHeldItemMainhand();
            if (heldItem.getItem() instanceof ItemAssemblerBaton) {
                AutobuildHandler.clearSelection(heldItem);
                return;
            }

            heldItem = player.getHeldItemOffhand();
            if (heldItem.getItem() instanceof ItemAssemblerBaton) {
                AutobuildHandler.clearSelection(heldItem);
                return;
            }

            player.sendMessage(new TextComponentTranslation("message.machineryassembler.preview_cancelled"));
        }
    }

    /**
     * Gets the horizontal facing direction from the player's yaw.
     * Returns NORTH, SOUTH, EAST, or WEST.
     */
    private static EnumFacing getPlayerHorizontalFacing() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return EnumFacing.NORTH;

        // Player yaw: 0 = south, 90 = west, 180/-180 = north, -90 = east
        float yaw = MathHelper.wrapDegrees(player.rotationYaw);

        if (yaw >= -45 && yaw < 45) {
            return EnumFacing.SOUTH;
        } else if (yaw >= 45 && yaw < 135) {
            return EnumFacing.WEST;
        } else if (yaw >= -135 && yaw < -45) {
            return EnumFacing.EAST;
        } else {
            return EnumFacing.NORTH;
        }
    }

    /**
     * Moves the current in-world preview in the specified relative direction.
     */
    private static void movePreview(EnumFacing playerFacing, RelativeDirection relative) {
        InWorldPreviewRenderer renderer = ClientProxy.previewRenderer;

        int forward = 0, right = 0, up = 0;

        switch (relative) {
            case FORWARD:
                forward = 1;
                break;
            case BACKWARD:
                forward = -1;
                break;
            case LEFT:
                right = -1;
                break;
            case RIGHT:
                right = 1;
                break;
            case UP:
                up = 1;
                break;
            case DOWN:
                up = -1;
                break;
        }

        renderer.moveRelative(playerFacing, forward, right, up);
    }

    /**
     * Handle shift+scroll wheel to rotate the preview.
     * Note: Right-click to fix preview is now handled by ItemAssemblerBaton directly.
     */
    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        if (!hasActivePreview()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return;

        // Shift + scroll wheel to rotate
        int scroll = Mouse.getDWheel();
        if (scroll != 0 && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))) {
            InWorldPreviewRenderer renderer = ClientProxy.previewRenderer;

            if (scroll > 0) {
                renderer.rotateCW();
            } else {
                renderer.rotateCCW();
            }
        }
    }

    /**
     * Intercept the pause menu opening when Escape is pressed while a preview is active.
     * In MC 1.12, Minecraft processes Escape internally and opens GuiIngameMenu BEFORE
     * KeyInputEvent fires, so we must intercept at the GuiOpenEvent stage instead.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onGuiOpen(GuiOpenEvent event) {
        if (!(event.getGui() instanceof GuiIngameMenu)) return;
        if (!hasActivePreview()) return;

        // Cancel the pause menu and cancel the preview instead
        event.setCanceled(true);
        cancelPreview();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Update cooldown
        if (moveCooldown > 0) moveCooldown--;

        // Don't process when a GUI is open
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return;

        if (!hasActivePreview()) return;

        // Process movement keys with cooldown
        if (moveCooldown > 0) return;

        EnumFacing playerFacing = getPlayerHorizontalFacing();

        if (KEY_MOVE_FORWARD.isKeyDown()) {
            movePreview(playerFacing, RelativeDirection.FORWARD);
            moveCooldown = MOVE_COOLDOWN_TICKS;
            return;
        }

        if (KEY_MOVE_BACKWARD.isKeyDown()) {
            movePreview(playerFacing, RelativeDirection.BACKWARD);
            moveCooldown = MOVE_COOLDOWN_TICKS;
            return;
        }

        if (KEY_MOVE_LEFT.isKeyDown()) {
            movePreview(playerFacing, RelativeDirection.LEFT);
            moveCooldown = MOVE_COOLDOWN_TICKS;
            return;
        }

        if (KEY_MOVE_RIGHT.isKeyDown()) {
            movePreview(playerFacing, RelativeDirection.RIGHT);
            moveCooldown = MOVE_COOLDOWN_TICKS;
            return;
        }

        if (KEY_MOVE_UP.isKeyDown()) {
            movePreview(playerFacing, RelativeDirection.UP);
            moveCooldown = MOVE_COOLDOWN_TICKS;
            return;
        }

        if (KEY_MOVE_DOWN.isKeyDown()) {
            movePreview(playerFacing, RelativeDirection.DOWN);
            moveCooldown = MOVE_COOLDOWN_TICKS;
        }
    }

    private enum RelativeDirection {
        FORWARD,
        BACKWARD,
        LEFT,
        RIGHT,
        UP,
        DOWN
    }
}

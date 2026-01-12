package com.machineryassembler.client;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import hellfirepvp.modularmachinery.client.ClientProxy;
import hellfirepvp.modularmachinery.client.util.BlockArrayPreviewRenderHelper;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.mixin.AccessorBlockArrayPreviewRenderHelper;


/**
 * Handles keybindings for controlling the in-world structure preview.
 * - Escape: Cancel the current preview
 * - Arrow keys: Move the preview relative to player facing direction
 * - Shift + Scroll: Rotate the preview (TODO)
 */
public class PreviewKeybindHandler {

    private static final String KEYBIND_CATEGORY = "key.categories." + MachineryAssembler.MODID;

    public static final KeyBinding KEY_CANCEL_PREVIEW = new KeyBinding(
        "key." + MachineryAssembler.MODID + ".cancel_preview",
        KeyConflictContext.IN_GAME,
        Keyboard.KEY_ESCAPE,
        KEYBIND_CATEGORY
    );

    public static final KeyBinding KEY_MOVE_FORWARD = new KeyBinding(
        "key." + MachineryAssembler.MODID + ".move_forward",
        KeyConflictContext.IN_GAME,
        Keyboard.KEY_UP,
        KEYBIND_CATEGORY
    );

    public static final KeyBinding KEY_MOVE_BACKWARD = new KeyBinding(
        "key." + MachineryAssembler.MODID + ".move_backward",
        KeyConflictContext.IN_GAME,
        Keyboard.KEY_DOWN,
        KEYBIND_CATEGORY
    );

    public static final KeyBinding KEY_MOVE_LEFT = new KeyBinding(
        "key." + MachineryAssembler.MODID + ".move_left",
        KeyConflictContext.IN_GAME,
        Keyboard.KEY_LEFT,
        KEYBIND_CATEGORY
    );

    public static final KeyBinding KEY_MOVE_RIGHT = new KeyBinding(
        "key." + MachineryAssembler.MODID + ".move_right",
        KeyConflictContext.IN_GAME,
        Keyboard.KEY_RIGHT,
        KEYBIND_CATEGORY
    );

    public static final KeyBinding KEY_MOVE_UP = new KeyBinding(
        "key." + MachineryAssembler.MODID + ".move_up",
        KeyConflictContext.IN_GAME,
        Keyboard.KEY_PRIOR, // Page Up
        KEYBIND_CATEGORY
    );

    public static final KeyBinding KEY_MOVE_DOWN = new KeyBinding(
        "key." + MachineryAssembler.MODID + ".move_down",
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
        BlockArrayPreviewRenderHelper helper = ClientProxy.renderHelper;
        if (helper == null) return false;

        // Check if there's an active context (preview in progress)
        if (helper.getContext() != null) return true;

        // Also check if we have an attached position for placed previews
        AccessorBlockArrayPreviewRenderHelper accessor = (AccessorBlockArrayPreviewRenderHelper) helper;

        return accessor.getAttachedPosition() != null;
    }

    /**
     * Cancels the current in-world preview.
     */
    private static void cancelPreview() {
        BlockArrayPreviewRenderHelper helper = ClientProxy.renderHelper;
        if (helper == null) return;

        helper.unloadWorld();
        // Offset is reset automatically by the mixin

        if (Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player.sendMessage(
                new TextComponentTranslation("message." + MachineryAssembler.MODID + ".preview_cancelled")
            );
        }
    }

    /**
     * Gets the horizontal facing direction from the player's yaw.
     * Returns NORTH, SOUTH, EAST, or WEST.
     */
    private static EnumFacing getPlayerHorizontalFacing() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) {
            return EnumFacing.NORTH;
        }

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
     * Gets the direction to move based on player facing and relative direction.
     */
    private static EnumFacing getRelativeDirection(EnumFacing playerFacing, RelativeDirection relative) {
        return switch (relative) {
            case FORWARD -> playerFacing;
            case BACKWARD -> playerFacing.getOpposite();
            case LEFT -> playerFacing.rotateYCCW();
            case RIGHT -> playerFacing.rotateY();
            case UP -> EnumFacing.UP;
            case DOWN -> EnumFacing.DOWN;
        };
    }

    /**
     * Moves the current in-world preview in the specified direction.
     * Works for both floating (unplaced) and placed previews.
     */
    private static void movePreview(EnumFacing direction) {
        BlockArrayPreviewRenderHelper helper = ClientProxy.renderHelper;
        if (helper == null) return;

        AccessorBlockArrayPreviewRenderHelper accessor = (AccessorBlockArrayPreviewRenderHelper) helper;
        BlockPos attachedPos = accessor.getAttachedPosition();

        if (attachedPos != null) {
            // Preview is placed/anchored - move the attached position directly
            BlockPos newPos = attachedPos.offset(direction);
            accessor.setAttachedPosition(newPos);
        } else {
            // Preview is floating - use our offset system
            BlockPos currentOffset = PreviewOffsetHolder.getPreviewOffset();
            BlockPos newOffset = currentOffset.offset(direction);
            PreviewOffsetHolder.setPreviewOffset(newOffset);
        }
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

        // Check cancel key (Escape)
        if (KEY_CANCEL_PREVIEW.isKeyDown()) {
            cancelPreview();
            return;
        }

        // Process movement keys with cooldown
        if (moveCooldown > 0) return;

        EnumFacing playerFacing = getPlayerHorizontalFacing();

        if (KEY_MOVE_FORWARD.isKeyDown()) {
            movePreview(getRelativeDirection(playerFacing, RelativeDirection.FORWARD));
            moveCooldown = MOVE_COOLDOWN_TICKS;
            return;
        }

        if (KEY_MOVE_BACKWARD.isKeyDown()) {
            movePreview(getRelativeDirection(playerFacing, RelativeDirection.BACKWARD));
            moveCooldown = MOVE_COOLDOWN_TICKS;
            return;
        }

        if (KEY_MOVE_LEFT.isKeyDown()) {
            movePreview(getRelativeDirection(playerFacing, RelativeDirection.LEFT));
            moveCooldown = MOVE_COOLDOWN_TICKS;
            return;
        }

        if (KEY_MOVE_RIGHT.isKeyDown()) {
            movePreview(getRelativeDirection(playerFacing, RelativeDirection.RIGHT));
            moveCooldown = MOVE_COOLDOWN_TICKS;
            return;
        }

        if (KEY_MOVE_UP.isKeyDown()) {
            movePreview(EnumFacing.UP);
            moveCooldown = MOVE_COOLDOWN_TICKS;
            return;
        }

        if (KEY_MOVE_DOWN.isKeyDown()) {
            movePreview(EnumFacing.DOWN);
            moveCooldown = MOVE_COOLDOWN_TICKS;
        }
    }

    /**
     * Handles mouse scroll events for rotating the preview when shift is held.
     */
    @SubscribeEvent
    public void onMouseInput(net.minecraftforge.client.event.MouseEvent event) {
        // Only process scroll events
        int dWheel = event.getDwheel();
        if (dWheel == 0) return;

        // Only process when shift is held
        if (!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) return;

        // Don't process when a GUI is open
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return;

        if (!hasActivePreview()) return;

        // TODO: Implement rotation when we have access to the pattern rotation
        // For now, rotation is not implemented - would need to modify the matchArray
        // which requires more complex mixin work
        MachineryAssembler.LOGGER.debug("Shift+scroll detected: {} (rotation not yet implemented)", dWheel > 0 ? "up" : "down");
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

package com.machineryassembler.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.client.integration.jei.JEIScrollHandler;
import com.machineryassembler.client.integration.jei.MAJEIPlugin;
import com.machineryassembler.client.render.BatonHighlightRenderer;
import com.machineryassembler.client.render.InWorldPreviewRenderer;
import com.machineryassembler.common.CommonProxy;


@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    public static final InWorldPreviewRenderer previewRenderer = new InWorldPreviewRenderer();
    public static final BatonHighlightRenderer highlightRenderer = new BatonHighlightRenderer();

    private final PreviewKeybindHandler keybindHandler = new PreviewKeybindHandler();
    private final PreviewInteractionHandler interactionHandler = new PreviewInteractionHandler();
    private final JEIScrollHandler jeiScrollHandler = new JEIScrollHandler();

    @Override
    public void preInit() {
        super.preInit();
        PreviewKeybindHandler.registerKeybinds();
        MinecraftForge.EVENT_BUS.register(keybindHandler);
        MinecraftForge.EVENT_BUS.register(interactionHandler);
        MinecraftForge.EVENT_BUS.register(new PreviewRenderHandler());
        MinecraftForge.EVENT_BUS.register(highlightRenderer);
    }

    @Override
    public void init() {
        super.init();
        MinecraftForge.EVENT_BUS.register(jeiScrollHandler);
    }

    @Override
    public void postInit() {
        super.postInit();
    }

    @Override
    public void onStructuresReloaded() {
        // Notify JEI wrappers on client side
        MAJEIPlugin.onStructuresReloaded();
    }

    @Override
    public void scheduleClientStructureReload() {
        // Schedule the reload notification on the client thread
        Minecraft.getMinecraft().addScheduledTask(this::onStructuresReloaded);
    }
}

package com.machineryassembler.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;

import com.machineryassembler.client.integration.jei.MAJEIPlugin;
import com.machineryassembler.common.CommonProxy;


public class ClientProxy extends CommonProxy {

    private final PreviewKeybindHandler keybindHandler = new PreviewKeybindHandler();

    @Override
    public void preInit() {
        super.preInit();
        PreviewKeybindHandler.registerKeybinds();
        MinecraftForge.EVENT_BUS.register(keybindHandler);
    }

    @Override
    public void init() {
        super.init();
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
        // This is safe because ClientProxy only exists on the client
        Minecraft.getMinecraft().addScheduledTask(this::onStructuresReloaded);
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Machinery Assembler Contributors

package com.machineryassembler.common.registry;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.common.item.ItemAssemblerWrench;
import com.machineryassembler.common.item.ItemMultiblockRecordingTool;


/**
 * Registry for Machinery Assembler items.
 */
@Mod.EventBusSubscriber(modid = MachineryAssembler.MODID)
public class ItemRegistry {

    public static final ItemAssemblerWrench ASSEMBLER_BATON = new ItemAssemblerWrench();
    public static final ItemMultiblockRecordingTool MULTIBLOCK_RECORDING_TOOL = new ItemMultiblockRecordingTool();

    @SubscribeEvent
    public static void onItemRegister(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(ASSEMBLER_BATON);
        event.getRegistry().register(MULTIBLOCK_RECORDING_TOOL);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void onModelRegister(ModelRegistryEvent event) {
        registerItemModel(ASSEMBLER_BATON);
        registerItemModel(MULTIBLOCK_RECORDING_TOOL);
    }

    @SideOnly(Side.CLIENT)
    private static void registerItemModel(Item item) {
        ModelLoader.setCustomModelResourceLocation(item, 0,
            new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}

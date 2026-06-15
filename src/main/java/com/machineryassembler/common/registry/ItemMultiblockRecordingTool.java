package com.machineryassembler.common.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.machineryassembler.MachineryAssembler;
import com.machineryassembler.client.recording.MultiblockRecordingClientController;


/**
 * Client tool used to capture an area and export it as a multiblock JSON definition.
 */
public class ItemMultiblockRecordingTool extends Item {

    public static final String ITEM_NAME = "multiblock_recording_tool";

    public ItemMultiblockRecordingTool() {
        setRegistryName(MachineryAssembler.MODID, ITEM_NAME);
        setTranslationKey(MachineryAssembler.MODID + "." + ITEM_NAME);
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.TOOLS);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (hand != EnumHand.MAIN_HAND) return new ActionResult<>(EnumActionResult.PASS, stack);

        if (world.isRemote) MultiblockRecordingClientController.handleToolUse(player);

        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, net.minecraft.util.math.BlockPos pos,
            EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND) return EnumActionResult.PASS;

        if (world.isRemote) MultiblockRecordingClientController.handleToolUse(player);

        return EnumActionResult.SUCCESS;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add("");
        tooltip.add(I18n.format("item.machineryassembler.multiblock_recording_tool.tooltip.1"));
        tooltip.add(I18n.format("item.machineryassembler.multiblock_recording_tool.tooltip.2"));
        tooltip.add(I18n.format("item.machineryassembler.multiblock_recording_tool.tooltip.3"));
        tooltip.add(I18n.format("item.machineryassembler.multiblock_recording_tool.tooltip.4"));
        tooltip.add(I18n.format("item.machineryassembler.multiblock_recording_tool.tooltip.5"));
    }
}
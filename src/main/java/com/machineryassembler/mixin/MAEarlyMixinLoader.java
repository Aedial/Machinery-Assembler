package com.machineryassembler.mixin;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import zone.rong.mixinbooter.IEarlyMixinLoader;


/**
 * Early mixin loader for Machinery Assembler.
 * Implements IEarlyMixinLoader from MixinBooter for proper mixin registration.
 */
@SuppressWarnings("unused")
@IFMLLoadingPlugin.Name("MachineryAssemblerCore")
@IFMLLoadingPlugin.MCVersion(ForgeVersion.mcVersion)
@IFMLLoadingPlugin.SortingIndex(Integer.MIN_VALUE)
public class MAEarlyMixinLoader implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.machineryassembler.json");
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(final Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}

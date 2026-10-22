package com.syncmod;

import com.mojang.logging.LogUtils;
import com.syncmod.config.ServerSyncConfig;
import com.syncmod.config.SyncClientConfig;
import com.syncmod.network.SyncNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(SyncMod.MOD_ID)
public final class SyncMod {
    public static final String MOD_ID = "syncmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SyncMod(IEventBus modBus, ModContainer container) {
        modBus.addListener(SyncNetwork::registerPayloads);
        modBus.addListener(SyncNetwork::registerTasks);
        container.registerConfig(ModConfig.Type.CLIENT, SyncClientConfig.SPEC);
        container.registerConfig(ModConfig.Type.COMMON, ServerSyncConfig.SPEC);
    }
}

package com.syncmod.network;

import com.syncmod.SyncMod;
import com.syncmod.network.payload.ManifestPayload;
import com.syncmod.server.ServerModIndex;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;

import java.util.Optional;
import java.util.function.Consumer;

public record ManifestConfigTask(ServerConfigurationPacketListener listener) implements ICustomConfigurationTask {
    public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(SyncMod.MOD_ID + ":manifest");

    @Override
    public void run(Consumer<CustomPacketPayload> sender) {
        Optional<String> json = ServerModIndex.INSTANCE.manifestJson();
        if (json.isEmpty()) {
            listener.finishCurrentTask(TYPE);
            return;
        }
        sender.accept(new ManifestPayload(json.get()));
    }

    @Override
    public ConfigurationTask.Type type() {
        return TYPE;
    }
}

package com.syncmod.network;

import com.syncmod.network.payload.ManifestAckPayload;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerPayloadHandler {
    private ServerPayloadHandler() {
    }

    public static void handleAck(ManifestAckPayload payload, IPayloadContext context) {
        if (payload.synced()) {
            context.finishCurrentTask(ManifestConfigTask.TYPE);
        } else {
            context.disconnect(Component.translatable("syncmod.disconnect.outdated"));
        }
    }
}

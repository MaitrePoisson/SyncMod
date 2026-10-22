package com.syncmod.network;

import com.syncmod.network.payload.ManifestAckPayload;
import com.syncmod.network.payload.ManifestPayload;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class SyncNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private SyncNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.configurationToClient(
                ManifestPayload.TYPE,
                ManifestPayload.STREAM_CODEC,
                ClientPayloadHandler::handleManifest
        );
        registrar.configurationToServer(
                ManifestAckPayload.TYPE,
                ManifestAckPayload.STREAM_CODEC,
                ServerPayloadHandler::handleAck
        );
    }

    public static void registerTasks(RegisterConfigurationTasksEvent event) {
        event.register(new ManifestConfigTask(event.getListener()));
    }
}

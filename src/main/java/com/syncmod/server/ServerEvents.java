package com.syncmod.server;

import com.syncmod.SyncMod;
import com.syncmod.config.ServerSyncConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.IOException;

@EventBusSubscriber(modid = SyncMod.MOD_ID)
public final class ServerEvents {
    private static HttpFileServer httpServer;

    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        if (!event.getServer().isDedicatedServer() || !ServerSyncConfig.ENABLED.get()) {
            return;
        }
        int configured = ServerSyncConfig.HTTP_PORT.get();
        if (configured == 0) {
            int gamePort = event.getServer().getPort();
            if (!SharedPortHook.install(event.getServer())) {
                SyncMod.LOGGER.error("Impossible de partager le port du jeu pour la synchronisation");
                return;
            }
            ServerModIndex.INSTANCE.activate(gamePort);
            SyncMod.LOGGER.info("Synchronisation des mods: fichiers servis sur le port du jeu ({})", gamePort);
            return;
        }
        HttpFileServer server = new HttpFileServer();
        try {
            server.start(configured);
        } catch (IOException e) {
            SyncMod.LOGGER.error("Impossible d'ouvrir le port HTTP {} pour la synchronisation", configured, e);
            return;
        }
        httpServer = server;
        ServerModIndex.INSTANCE.activate(configured);
        SyncMod.LOGGER.info("Synchronisation des mods: fichiers servis sur le port {}", configured);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ServerModIndex.INSTANCE.deactivate();
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
    }
}

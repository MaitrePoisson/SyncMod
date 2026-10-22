package com.syncmod.client;

import com.syncmod.SyncMod;
import com.syncmod.client.screen.SyncScreen;
import com.syncmod.client.sync.SyncService;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ModMismatchDisconnectedScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;

import java.util.concurrent.atomic.AtomicBoolean;

@EventBusSubscriber(modid = SyncMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final AtomicBoolean ALLOW_NEXT_MISMATCH = new AtomicBoolean();
    private static volatile ServerAddress lastServer;
    private static volatile ServerAddress candidate;

    private ClientEvents() {
    }

    public static void allowNextMismatchScreen() {
        ALLOW_NEXT_MISMATCH.set(true);
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        ServerAddress leaving = findServerAddress(event.getCurrentScreen());
        if (leaving != null) {
            candidate = leaving;
        }
        if (next instanceof TitleScreen) {
            candidate = null;
        }
        if (next instanceof ConnectScreen) {
            lastServer = candidate;
            candidate = null;
            return;
        }
        if (next instanceof ModMismatchDisconnectedScreen) {
            if (ALLOW_NEXT_MISMATCH.getAndSet(false)) {
                return;
            }
            ServerAddress server = lastServer;
            SyncMod.LOGGER.info("SyncMod: ecran de refus NeoForge detecte, serveur connu: {}",
                    server == null ? "aucun" : server.getHost() + ":" + server.getPort());
            SyncService service = server != null
                    ? SyncService.checkRemote(server.getHost(), server.getPort())
                    : SyncService.unavailable(Component.translatable("syncmod.error.no_server"));
            event.setNewScreen(new SyncScreen(service, next));
            return;
        }
        if (next instanceof DisconnectedScreen && ClientSyncState.hasPending()) {
            ClientSyncState.Pending pending = ClientSyncState.consume();
            if (pending == null) {
                return;
            }
            SyncService service = pending.error() != null
                    ? SyncService.failed(pending.error())
                    : SyncService.create(pending.plan(), pending.origin());
            event.setNewScreen(new SyncScreen(service));
        }
    }

    private static ServerAddress findServerAddress(Screen current) {
        String ip = null;
        if (current instanceof JoinMultiplayerScreen join) {
            for (GuiEventListener child : join.children()) {
                if (child instanceof ServerSelectionList list
                        && list.getSelected() instanceof ServerSelectionList.OnlineServerEntry entry) {
                    ip = entry.getServerData().ip;
                }
            }
        } else if (current instanceof DirectJoinServerScreen direct) {
            for (GuiEventListener child : direct.children()) {
                if (child instanceof EditBox box) {
                    ip = box.getValue();
                    break;
                }
            }
        }
        return ip == null || ip.isBlank() ? null : ServerAddress.parseString(ip.trim());
    }
}

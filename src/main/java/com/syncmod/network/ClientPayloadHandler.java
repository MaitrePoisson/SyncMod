package com.syncmod.network;

import com.syncmod.SyncMod;
import com.syncmod.client.ClientSyncState;
import com.syncmod.client.sync.SyncPlan;
import com.syncmod.client.sync.SyncPlanner;
import com.syncmod.manifest.ManifestEntry;
import com.syncmod.manifest.ManifestValidator;
import com.syncmod.manifest.SyncManifest;
import com.syncmod.network.payload.ManifestAckPayload;
import com.syncmod.network.payload.ManifestPayload;
import com.syncmod.security.DomainWhitelist;
import com.syncmod.security.TrustedOrigin;
import com.syncmod.util.SyncException;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void handleManifest(ManifestPayload payload, IPayloadContext context) {
        try {
            SyncManifest manifest = SyncManifest.fromJson(payload.json());
            TrustedOrigin origin = resolveOrigin(manifest, context);
            DomainWhitelist whitelist = origin == null
                    ? DomainWhitelist.fromConfig()
                    : DomainWhitelist.fromConfig().withOrigin(origin);
            List<ManifestEntry> entries = ManifestValidator.validate(manifest, whitelist);
            SyncPlan plan = SyncPlanner.plan(entries, FMLPaths.MODSDIR.get());
            if (plan.isUpToDate()) {
                context.reply(new ManifestAckPayload(true));
                return;
            }
            ClientSyncState.setPlan(plan, origin);
        } catch (SyncException e) {
            ClientSyncState.setError(e.display());
        } catch (IOException e) {
            SyncMod.LOGGER.error("Analyse du dossier mods impossible", e);
            ClientSyncState.setError(Component.translatable("syncmod.error.io", String.valueOf(e.getMessage())));
        }
        context.reply(new ManifestAckPayload(false));
        context.disconnect(Component.translatable("syncmod.disconnect.sync_required"));
    }

    private static TrustedOrigin resolveOrigin(SyncManifest manifest, IPayloadContext context) {
        String host = manifest.publicHost();
        if (host == null || host.isBlank()) {
            if (!(context.connection().getRemoteAddress() instanceof InetSocketAddress address)) {
                return null;
            }
            host = address.getAddress() != null ? address.getAddress().getHostAddress() : address.getHostString();
        }
        return TrustedOrigin.of(host, manifest.httpPort());
    }
}

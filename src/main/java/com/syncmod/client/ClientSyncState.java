package com.syncmod.client;

import com.syncmod.client.sync.SyncPlan;
import com.syncmod.security.TrustedOrigin;
import net.minecraft.network.chat.Component;

import java.util.concurrent.atomic.AtomicReference;

public final class ClientSyncState {
    public record Pending(SyncPlan plan, Component error, TrustedOrigin origin) {
    }

    private static final AtomicReference<Pending> PENDING = new AtomicReference<>();

    private ClientSyncState() {
    }

    public static void setPlan(SyncPlan plan, TrustedOrigin origin) {
        PENDING.set(new Pending(plan, null, origin));
    }

    public static void setError(Component error) {
        PENDING.set(new Pending(null, error, null));
    }

    public static boolean hasPending() {
        return PENDING.get() != null;
    }

    public static Pending consume() {
        return PENDING.getAndSet(null);
    }
}

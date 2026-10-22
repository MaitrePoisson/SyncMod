package com.syncmod.util;

import net.minecraft.network.chat.Component;

public final class SyncException extends RuntimeException {
    private final Component display;

    public SyncException(String key, Object... args) {
        super(key);
        this.display = Component.translatable(key, args);
    }

    public Component display() {
        return display;
    }
}

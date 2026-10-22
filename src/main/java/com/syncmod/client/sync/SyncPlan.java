package com.syncmod.client.sync;

import java.util.List;

public record SyncPlan(List<PlannedMod> mods, int upToDate) {
    public boolean isUpToDate() {
        return mods.isEmpty();
    }

    public long missingCount() {
        return mods.stream().filter(mod -> !mod.isOutdated()).count();
    }

    public long outdatedCount() {
        return mods.stream().filter(PlannedMod::isOutdated).count();
    }
}

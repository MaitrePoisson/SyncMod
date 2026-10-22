package com.syncmod.client.sync;

import com.syncmod.manifest.ManifestEntry;

import java.nio.file.Path;
import java.util.List;

public record PlannedMod(ManifestEntry entry, List<Path> stale) {
    public boolean isOutdated() {
        return !stale.isEmpty();
    }
}

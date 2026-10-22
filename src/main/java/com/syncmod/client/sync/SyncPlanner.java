package com.syncmod.client.sync;

import com.syncmod.manifest.ManifestEntry;
import com.syncmod.security.PathGuard;
import com.syncmod.util.Hashes;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class SyncPlanner {
    private SyncPlanner() {
    }

    public static SyncPlan plan(List<ManifestEntry> entries, Path modsDir) throws IOException {
        Path root = modsDir.toAbsolutePath().normalize();
        Map<String, Path> localByHash = hashLocalJars(root);
        List<PlannedMod> mods = new ArrayList<>();
        int upToDate = 0;
        for (ManifestEntry entry : entries) {
            if (localByHash.containsKey(entry.sha256())) {
                upToDate++;
                continue;
            }
            Set<Path> stale = new LinkedHashSet<>();
            Path byName = root.resolve(entry.filename()).normalize();
            if (Files.isRegularFile(byName)) {
                stale.add(byName);
            }
            Path loaded = loadedFile(entry.modid());
            if (loaded != null && PathGuard.isDirectChild(root, loaded)) {
                stale.add(loaded.toAbsolutePath().normalize());
            }
            mods.add(new PlannedMod(entry, List.copyOf(stale)));
        }
        return new SyncPlan(List.copyOf(mods), upToDate);
    }

    private static Map<String, Path> hashLocalJars(Path root) throws IOException {
        Map<String, Path> hashes = new HashMap<>();
        if (!Files.isDirectory(root)) {
            return hashes;
        }
        List<Path> jars;
        try (Stream<Path> files = Files.list(root)) {
            jars = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .toList();
        }
        for (Path jar : jars) {
            hashes.put(Hashes.sha256(jar), jar);
        }
        return hashes;
    }

    private static Path loadedFile(String modid) {
        ModList modList = ModList.get();
        if (modList == null) {
            return null;
        }
        IModFileInfo info = modList.getModFileById(modid);
        if (info == null) {
            return null;
        }
        Path path = info.getFile().getFilePath();
        if (path == null || !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            return null;
        }
        return path;
    }
}

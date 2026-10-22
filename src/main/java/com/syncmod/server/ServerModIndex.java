package com.syncmod.server;

import com.syncmod.SyncMod;
import com.syncmod.config.ServerSyncConfig;
import com.syncmod.manifest.ManifestEntry;
import com.syncmod.manifest.ManifestValidator;
import com.syncmod.manifest.SyncManifest;
import com.syncmod.util.Hashes;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ServerModIndex {
    public static final ServerModIndex INSTANCE = new ServerModIndex();

    private record CachedHash(long size, long modified, String sha256) {
    }

    private final Map<Path, CachedHash> cache = new HashMap<>();
    private volatile Map<String, Path> served = Map.of();
    private volatile String manifestJson;
    private volatile boolean serving;
    private volatile int port;

    private ServerModIndex() {
    }

    public void activate(int httpPort) {
        port = httpPort;
        refresh();
        serving = true;
    }

    public void deactivate() {
        serving = false;
    }

    public Optional<Path> lookup(String filename) {
        return Optional.ofNullable(served.get(filename));
    }

    public Optional<String> manifestJson() {
        if (!serving) {
            return Optional.empty();
        }
        refresh();
        String json = manifestJson;
        return served.isEmpty() || json == null ? Optional.empty() : Optional.of(json);
    }

    public synchronized void refresh() {
        Path modsDir = FMLPaths.MODSDIR.get().toAbsolutePath().normalize();
        Set<String> excluded = ServerSyncConfig.EXCLUDED_FILES.get().stream()
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Map<Path, String> modIds = modIdsByPath();
        Map<String, Path> files = new LinkedHashMap<>();
        List<ManifestEntry> entries = new ArrayList<>();
        Set<String> seenModIds = new HashSet<>();
        List<Path> jars = listJars(modsDir);
        cache.keySet().retainAll(jars);
        for (Path jar : jars) {
            String name = jar.getFileName().toString();
            if (excluded.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (!ManifestValidator.isValidFilename(name)) {
                SyncMod.LOGGER.warn("Fichier ignore (nom non supporte): {}", name);
                continue;
            }
            String modId = modIds.get(jar);
            if (modId == null) {
                modId = libraryId(name, seenModIds);
            } else if (!seenModIds.add(modId)) {
                continue;
            }
            try {
                entries.add(new ManifestEntry(modId, name, hash(jar), "/mods/" + name));
                files.put(name, jar);
            } catch (IOException e) {
                SyncMod.LOGGER.error("Hash impossible pour {}", name, e);
            }
        }
        served = Map.copyOf(files);
        manifestJson = new SyncManifest(port, ServerSyncConfig.PUBLIC_HOST.get().trim(), List.copyOf(entries)).toJson();
    }

    private static String libraryId(String filename, Set<String> seen) {
        String base = "lib_" + filename.substring(0, filename.length() - 4).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (base.length() > 56) {
            base = base.substring(0, 56);
        }
        String candidate = base;
        int counter = 2;
        while (!seen.add(candidate)) {
            candidate = base + "_" + counter++;
        }
        return candidate;
    }

    private String hash(Path jar) throws IOException {
        long size = Files.size(jar);
        long modified = Files.getLastModifiedTime(jar).toMillis();
        CachedHash cached = cache.get(jar);
        if (cached != null && cached.size() == size && cached.modified() == modified) {
            return cached.sha256();
        }
        String sha256 = Hashes.sha256(jar);
        cache.put(jar, new CachedHash(size, modified, sha256));
        return sha256;
    }

    private static List<Path> listJars(Path modsDir) {
        if (!Files.isDirectory(modsDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(modsDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            SyncMod.LOGGER.error("Lecture du dossier mods impossible", e);
            return List.of();
        }
    }

    private static Map<Path, String> modIdsByPath() {
        Map<Path, String> modIds = new HashMap<>();
        for (IModFileInfo fileInfo : ModList.get().getModFiles()) {
            Path path = fileInfo.getFile().getFilePath();
            List<IModInfo> mods = fileInfo.getMods();
            if (path != null && !mods.isEmpty()) {
                modIds.put(path.toAbsolutePath().normalize(), mods.get(0).getModId());
            }
        }
        return modIds;
    }
}

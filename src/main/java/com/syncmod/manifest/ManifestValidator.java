package com.syncmod.manifest;

import com.syncmod.security.DomainWhitelist;
import com.syncmod.util.SyncException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ManifestValidator {
    private static final int MAX_ENTRIES = 512;
    private static final int MAX_URL_LENGTH = 2048;
    private static final Pattern MODID = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");
    private static final Pattern FILENAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}\\.jar$");
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-fA-F]{64}$");

    private ManifestValidator() {
    }

    public static boolean isValidFilename(String filename) {
        return FILENAME.matcher(filename).matches() && !filename.contains("..");
    }

    public static List<ManifestEntry> validate(SyncManifest manifest, DomainWhitelist whitelist) {
        if (manifest == null || manifest.mods() == null || manifest.mods().size() > MAX_ENTRIES) {
            throw new SyncException("syncmod.error.manifest_parse");
        }
        Set<String> modids = new HashSet<>();
        Set<String> filenames = new HashSet<>();
        List<ManifestEntry> validated = new ArrayList<>();
        for (ManifestEntry entry : manifest.mods()) {
            if (entry == null || entry.modid() == null || entry.filename() == null
                    || entry.sha256() == null || entry.downloadUrl() == null) {
                throw new SyncException("syncmod.error.entry_invalid", "null");
            }
            if (!MODID.matcher(entry.modid()).matches() || !modids.add(entry.modid())) {
                throw new SyncException("syncmod.error.entry_invalid", entry.modid());
            }
            String filename = entry.filename();
            if (!FILENAME.matcher(filename).matches() || filename.contains("..")
                    || !filenames.add(filename.toLowerCase(Locale.ROOT))) {
                throw new SyncException("syncmod.error.entry_invalid", filename);
            }
            if (!SHA256.matcher(entry.sha256()).matches()) {
                throw new SyncException("syncmod.error.entry_invalid", filename);
            }
            String url = entry.downloadUrl().trim();
            if (url.startsWith("/") && !url.startsWith("//")) {
                url = whitelist.origin()
                        .orElseThrow(() -> new SyncException("syncmod.error.domain_blocked", entry.downloadUrl()))
                        .baseUrl() + url;
            }
            if (url.length() > MAX_URL_LENGTH) {
                throw new SyncException("syncmod.error.entry_invalid", filename);
            }
            whitelist.check(url);
            validated.add(new ManifestEntry(entry.modid(), filename, entry.sha256().toLowerCase(Locale.ROOT), url));
        }
        return List.copyOf(validated);
    }
}

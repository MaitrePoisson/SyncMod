package com.syncmod.manifest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.syncmod.util.SyncException;

import java.util.List;

public record SyncManifest(int httpPort, String publicHost, List<ManifestEntry> mods) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static SyncManifest fromJson(String json) {
        try {
            SyncManifest manifest = GSON.fromJson(json, SyncManifest.class);
            if (manifest == null) {
                throw new SyncException("syncmod.error.manifest_parse");
            }
            return manifest;
        } catch (JsonParseException e) {
            throw new SyncException("syncmod.error.manifest_parse");
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }
}

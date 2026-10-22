package com.syncmod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class SyncClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_DOMAINS;
    public static final ModConfigSpec.LongValue MAX_FILE_BYTES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ALLOWED_DOMAINS = builder
                .comment("Hotes HTTPS autorises pour telecharger des mods (correspondance exacte).")
                .defineListAllowEmpty(
                        "allowedDomains",
                        List.of(
                                "cdn.modrinth.com",
                                "edge.forgecdn.net",
                                "mediafilez.forgecdn.net",
                                "github.com",
                                "objects.githubusercontent.com",
                                "release-assets.githubusercontent.com"
                        ),
                        () -> "",
                        value -> value instanceof String host && !host.isBlank()
                );
        MAX_FILE_BYTES = builder
                .comment("Taille maximale d'un fichier telecharge, en octets.")
                .defineInRange("maxFileBytes", 256L * 1024L * 1024L, 1024L, 2L * 1024L * 1024L * 1024L);
        SPEC = builder.build();
    }

    private SyncClientConfig() {
    }
}

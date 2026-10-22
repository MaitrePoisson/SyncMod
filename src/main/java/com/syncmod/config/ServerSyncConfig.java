package com.syncmod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class ServerSyncConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue HTTP_PORT;
    public static final ModConfigSpec.ConfigValue<String> PUBLIC_HOST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_FILES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ENABLED = builder
                .comment("Active la synchronisation des mods depuis le dossier mods/ du serveur dedie.")
                .define("enabled", true);
        HTTP_PORT = builder
                .comment("0 = distribuer les jars sur le port du jeu (aucun port a ouvrir). Sinon, port TCP separe a ouvrir dans le pare-feu.")
                .defineInRange("separatePort", 0, 0, 65535);
        PUBLIC_HOST = builder
                .comment("Nom d'hote ou IP publique des fichiers. Laisser vide pour utiliser l'adresse a laquelle le joueur se connecte.")
                .define("publicHost", "");
        EXCLUDED_FILES = builder
                .comment("Noms de fichiers jar du dossier mods/ a ne pas envoyer aux clients (mods serveur uniquement).")
                .defineListAllowEmpty("excludedFiles", List.of(), () -> "", value -> value instanceof String name && !name.isBlank());
        SPEC = builder.build();
    }

    private ServerSyncConfig() {
    }
}

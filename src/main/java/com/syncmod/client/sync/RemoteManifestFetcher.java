package com.syncmod.client.sync;

import com.syncmod.SyncMod;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import com.syncmod.security.TrustedOrigin;
import com.syncmod.util.SyncException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RemoteManifestFetcher {
    public record Result(String json, TrustedOrigin origin) {
    }

    private static final int DEFAULT_PORT = 25566;
    private static final int MAX_BYTES = 1_000_000;
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private RemoteManifestFetcher() {
    }

    public static CompletableFuture<Result> fetch(String host, int gamePort) {
        return CompletableFuture.supplyAsync(() -> fetchBlocking(host, gamePort), EXECUTOR);
    }

    private static Result fetchBlocking(String requestedHost, int requestedPort) {
        String host = requestedHost;
        int gamePort = requestedPort;
        try {
            Optional<ResolvedServerAddress> resolved = ServerNameResolver.DEFAULT
                    .resolveAddress(ServerAddress.parseString(requestedHost + ":" + requestedPort));
            if (resolved.isPresent()) {
                host = resolved.get().getHostName();
                gamePort = resolved.get().getPort();
            }
        } catch (RuntimeException e) {
            SyncMod.LOGGER.info("SyncMod: resolution DNS impossible pour {} ({})", requestedHost, e.toString());
        }
        SyncMod.LOGGER.info("SyncMod: serveur {}:{} resolu en {}:{}", requestedHost, requestedPort, host, gamePort);
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        List<String> attempts = new java.util.ArrayList<>();
        Set<Integer> ports = new LinkedHashSet<>(List.of(gamePort, requestedPort, DEFAULT_PORT, gamePort + 1));
        for (int port : ports) {
            TrustedOrigin origin = TrustedOrigin.of(host, port);
            if (origin == null) {
                continue;
            }
            try {
                SyncMod.LOGGER.info("SyncMod: recherche du manifeste sur {}/manifest.json", origin.baseUrl());
                HttpRequest request = HttpRequest.newBuilder(URI.create(origin.baseUrl() + "/manifest.json"))
                        .timeout(Duration.ofSeconds(5))
                        .header("User-Agent", "SyncMod/1.0")
                        .GET()
                        .build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream in = response.body()) {
                    if (response.statusCode() != 200) {
                        SyncMod.LOGGER.info("SyncMod: {} a repondu {}", origin.baseUrl(), response.statusCode());
                        attempts.add(host + ":" + port + " -> HTTP " + response.statusCode());
                        continue;
                    }
                    byte[] data = in.readNBytes(MAX_BYTES + 1);
                    if (data.length > MAX_BYTES) {
                        continue;
                    }
                    return new Result(new String(data, StandardCharsets.UTF_8), origin);
                }
            } catch (IOException | IllegalArgumentException e) {
                SyncMod.LOGGER.info("SyncMod: {} injoignable ({})", origin.baseUrl(), e.toString());
                attempts.add(host + ":" + port + " -> " + e.getClass().getSimpleName());
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new SyncException("syncmod.error.remote", String.join(" | ", attempts));
    }
}

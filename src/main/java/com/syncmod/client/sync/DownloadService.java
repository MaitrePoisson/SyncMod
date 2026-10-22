package com.syncmod.client.sync;

import com.syncmod.manifest.ManifestEntry;
import com.syncmod.security.DomainWhitelist;
import com.syncmod.security.PathGuard;
import com.syncmod.util.Hashes;
import com.syncmod.util.SyncException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

public final class DownloadService {
    private static final int MAX_REDIRECTS = 5;

    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "SyncMod-IO");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(15))
            .executor(executor)
            .build();
    private final DomainWhitelist whitelist;
    private final long maxBytes;

    public DownloadService(DomainWhitelist whitelist, long maxBytes) {
        this.whitelist = whitelist;
        this.maxBytes = maxBytes;
    }

    public ExecutorService executor() {
        return executor;
    }

    public CompletableFuture<Path> download(ManifestEntry entry, Path tempDir, DoubleConsumer progress, BooleanSupplier cancelled) {
        URI uri;
        try {
            uri = whitelist.check(entry.downloadUrl());
        } catch (SyncException e) {
            return CompletableFuture.failedFuture(e);
        }
        return fetch(uri, 0).thenApplyAsync(response -> store(response, entry, tempDir, progress, cancelled), executor);
    }

    private CompletableFuture<HttpResponse<InputStream>> fetch(URI uri, int hops) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "SyncMod/1.0")
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenCompose(response -> {
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                closeQuietly(response.body());
                if (hops >= MAX_REDIRECTS) {
                    return CompletableFuture.failedFuture(new SyncException("syncmod.error.too_many_redirects"));
                }
                Optional<String> location = response.headers().firstValue("Location");
                if (location.isEmpty()) {
                    return CompletableFuture.failedFuture(new SyncException("syncmod.error.http_status", status));
                }
                try {
                    URI next = whitelist.check(uri.resolve(location.get()).toString());
                    return fetch(next, hops + 1);
                } catch (SyncException | IllegalArgumentException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }
            if (status != 200) {
                closeQuietly(response.body());
                return CompletableFuture.failedFuture(new SyncException("syncmod.error.http_status", status));
            }
            return CompletableFuture.completedFuture(response);
        });
    }

    private Path store(HttpResponse<InputStream> response, ManifestEntry entry, Path tempDir,
                       DoubleConsumer progress, BooleanSupplier cancelled) {
        long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (declared > maxBytes) {
            closeQuietly(response.body());
            throw new SyncException("syncmod.error.too_large", entry.filename());
        }
        Path part = tempDir.resolve(entry.sha256() + ".part");
        MessageDigest digest = Hashes.newSha256();
        long total = 0L;
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (cancelled.getAsBoolean()) {
                    throw new CancellationException();
                }
                total += read;
                if (total > maxBytes) {
                    throw new SyncException("syncmod.error.too_large", entry.filename());
                }
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
                if (declared > 0) {
                    progress.accept((double) total / declared);
                }
            }
        } catch (IOException e) {
            deleteQuietly(part);
            throw new CompletionException(new SyncException("syncmod.error.io", String.valueOf(e.getMessage())));
        } catch (RuntimeException e) {
            deleteQuietly(part);
            throw e;
        }
        if (!Hashes.hex(digest).equals(entry.sha256())) {
            deleteQuietly(part);
            throw new SyncException("syncmod.error.hash_mismatch", entry.filename());
        }
        Path verified = PathGuard.resolveInside(tempDir, entry.filename());
        try {
            Files.move(part, verified, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(part);
            throw new CompletionException(new SyncException("syncmod.error.io", String.valueOf(e.getMessage())));
        }
        progress.accept(1.0);
        return verified;
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}

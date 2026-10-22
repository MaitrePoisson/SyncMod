package com.syncmod.client.sync;

import com.syncmod.SyncMod;
import com.syncmod.config.SyncClientConfig;
import com.syncmod.manifest.ManifestEntry;
import com.syncmod.manifest.ManifestValidator;
import com.syncmod.manifest.SyncManifest;
import com.syncmod.security.DomainWhitelist;
import com.syncmod.security.PathGuard;
import com.syncmod.security.TrustedOrigin;
import com.syncmod.util.SyncException;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public final class SyncService {
    public enum Phase {
        CHECKING,
        UNAVAILABLE,
        CONFIRM,
        DOWNLOADING,
        INSTALLING,
        DONE,
        FAILED
    }

    private volatile SyncPlan plan;
    private final Path modsDir;
    private final Path tempDir;
    private volatile DownloadService downloads;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    private volatile Phase phase;
    private volatile String currentMod = "";
    private volatile int index;
    private volatile double progress;
    private volatile Component error;
    private volatile CompletableFuture<Void> task;

    private SyncService(SyncPlan plan, Path modsDir, Path tempDir, DownloadService downloads, Phase phase, Component error) {
        this.plan = plan;
        this.modsDir = modsDir;
        this.tempDir = tempDir;
        this.downloads = downloads;
        this.phase = phase;
        this.error = error;
    }

    public static SyncService create(SyncPlan plan, TrustedOrigin origin) {
        Path modsDir = FMLPaths.MODSDIR.get().toAbsolutePath().normalize();
        DomainWhitelist whitelist = origin == null
                ? DomainWhitelist.fromConfig()
                : DomainWhitelist.fromConfig().withOrigin(origin);
        DownloadService downloads = new DownloadService(whitelist, SyncClientConfig.MAX_FILE_BYTES.get());
        return new SyncService(plan, modsDir, modsDir.resolve(".sync_temp"), downloads, Phase.CONFIRM, null);
    }

    public static SyncService checkRemote(String host, int gamePort) {
        Path modsDir = FMLPaths.MODSDIR.get().toAbsolutePath().normalize();
        SyncService service = new SyncService(
                new SyncPlan(List.of(), 0), modsDir, modsDir.resolve(".sync_temp"), null, Phase.CHECKING, null);
        RemoteManifestFetcher.fetch(host, gamePort)
                .thenApply(service::prepare)
                .whenComplete((ready, throwable) -> {
                    if (throwable != null || !Boolean.TRUE.equals(ready)) {
                        SyncMod.LOGGER.info("SyncMod: verification distante abandonnee ({})",
                                throwable == null ? "mods deja a jour" : throwable.toString());
                        service.error = describe(throwable);
                        service.phase = Phase.UNAVAILABLE;
                    }
                });
        return service;
    }

    private boolean prepare(RemoteManifestFetcher.Result result) {
        DomainWhitelist whitelist = DomainWhitelist.fromConfig().withOrigin(result.origin());
        List<ManifestEntry> entries = ManifestValidator.validate(SyncManifest.fromJson(result.json()), whitelist);
        SyncPlan newPlan;
        try {
            newPlan = SyncPlanner.plan(entries, modsDir);
        } catch (IOException e) {
            throw new SyncException("syncmod.error.io", String.valueOf(e.getMessage()));
        }
        if (newPlan.isUpToDate()) {
            return false;
        }
        downloads = new DownloadService(whitelist, SyncClientConfig.MAX_FILE_BYTES.get());
        plan = newPlan;
        phase = Phase.CONFIRM;
        return true;
    }

    public static SyncService unavailable(Component reason) {
        return new SyncService(new SyncPlan(List.of(), 0), null, null, null, Phase.UNAVAILABLE, reason);
    }

    private static Component describe(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null && (cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause == null) {
            return Component.translatable("syncmod.unavailable.up_to_date");
        }
        if (cause instanceof SyncException syncException) {
            return syncException.display();
        }
        return Component.translatable("syncmod.error.io", String.valueOf(cause));
    }

    public static SyncService failed(Component error) {
        return new SyncService(new SyncPlan(List.of(), 0), null, null, null, Phase.FAILED, error);
    }

    public Phase phase() {
        return phase;
    }

    public SyncPlan plan() {
        return plan;
    }

    public String currentMod() {
        return currentMod;
    }

    public int index() {
        return index;
    }

    public int total() {
        return plan.mods().size();
    }

    public double progress() {
        return progress;
    }

    public Component error() {
        return error;
    }

    public Set<String> hosts() {
        Set<String> hosts = new LinkedHashSet<>();
        for (PlannedMod mod : plan.mods()) {
            hosts.add(URI.create(mod.entry().downloadUrl()).getHost());
        }
        return hosts;
    }

    public synchronized void start() {
        if (phase != Phase.CONFIRM) {
            return;
        }
        phase = Phase.DOWNLOADING;
        List<PlannedMod> mods = plan.mods();
        CompletableFuture<Void> chain = CompletableFuture.runAsync(this::prepareTempDir, downloads.executor());
        for (int i = 0; i < mods.size(); i++) {
            int position = i;
            PlannedMod mod = mods.get(i);
            chain = chain.thenCompose(ignored -> {
                index = position;
                currentMod = mod.entry().filename();
                progress = (double) position / mods.size();
                return downloads.download(
                        mod.entry(),
                        tempDir,
                        fraction -> progress = (position + fraction) / mods.size(),
                        cancelled::get
                ).thenAccept(path -> {
                });
            });
        }
        task = chain
                .thenRunAsync(this::install, downloads.executor())
                .whenComplete((ignored, throwable) -> {
                    if (throwable == null) {
                        progress = 1.0;
                        phase = Phase.DONE;
                    } else {
                        fail(throwable);
                    }
                });
    }

    public void cancel() {
        cancelled.set(true);
        CompletableFuture<Void> running = task;
        if (running != null) {
            running.cancel(true);
        }
    }

    private void prepareTempDir() {
        try {
            Files.createDirectories(tempDir);
            try (Stream<Path> files = Files.list(tempDir)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (IOException e) {
            throw new SyncException("syncmod.error.io", String.valueOf(e.getMessage()));
        }
    }

    private void install() {
        phase = Phase.INSTALLING;
        List<Path> pendingDeletes = new ArrayList<>();
        List<PendingMove> pendingMoves = new ArrayList<>();
        for (PlannedMod mod : plan.mods()) {
            Path source = PathGuard.resolveInside(tempDir, mod.entry().filename());
            Path target = PathGuard.resolveInside(modsDir, mod.entry().filename());
            for (Path stale : mod.stale()) {
                if (stale.equals(target) || !PathGuard.isDirectChild(modsDir, stale)) {
                    continue;
                }
                try {
                    Files.deleteIfExists(stale);
                } catch (IOException e) {
                    pendingDeletes.add(stale);
                }
            }
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                pendingMoves.add(new PendingMove(source, target));
            }
        }
        if (!pendingDeletes.isEmpty() || !pendingMoves.isEmpty()) {
            RestartHelper.schedule(pendingDeletes, pendingMoves);
        }
    }

    private void fail(Throwable throwable) {
        Throwable cause = throwable;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof SyncException syncException) {
            error = syncException.display();
        } else if (cause instanceof CancellationException) {
            error = Component.translatable("syncmod.error.cancelled");
        } else {
            SyncMod.LOGGER.error("Synchronisation echouee", cause);
            error = Component.translatable("syncmod.error.io", String.valueOf(cause.getMessage()));
        }
        phase = Phase.FAILED;
    }
}

package com.syncmod.client.sync;

import com.syncmod.SyncMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public final class RestartHelper {
    private static final List<Path> DELETES = new CopyOnWriteArrayList<>();
    private static final List<PendingMove> MOVES = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean HOOK_INSTALLED = new AtomicBoolean();
    private static volatile boolean relaunch;

    private RestartHelper() {
    }

    public static void schedule(List<Path> deletes, List<PendingMove> moves) {
        DELETES.addAll(deletes);
        MOVES.addAll(moves);
        installHook();
    }

    public static boolean canRelaunch() {
        return relaunchCommand().isPresent();
    }

    public static void requestRelaunch() {
        if (canRelaunch()) {
            relaunch = true;
            installHook();
        }
    }

    private static void installHook() {
        if (HOOK_INSTALLED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(RestartHelper::spawn, "SyncMod-Restart"));
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static Optional<List<String>> relaunchCommand() {
        ProcessHandle.Info info = ProcessHandle.current().info();
        Optional<String> command = info.command();
        Optional<String[]> arguments = info.arguments();
        if (command.isEmpty() || arguments.isEmpty() || arguments.get().length == 0) {
            return Optional.empty();
        }
        List<String> full = new ArrayList<>();
        full.add(command.get());
        full.addAll(List.of(arguments.get()));
        return Optional.of(full);
    }

    private static void spawn() {
        if (DELETES.isEmpty() && MOVES.isEmpty() && !relaunch) {
            return;
        }
        try {
            boolean windows = isWindows();
            Path script = Files.createTempFile("syncmod-", windows ? ".bat" : ".sh");
            Files.writeString(script, windows ? windowsScript() : unixScript(), StandardCharsets.UTF_8);
            ProcessBuilder builder = windows
                    ? new ProcessBuilder("cmd.exe", "/c", "start", "SyncMod", "/min", script.toString())
                    : new ProcessBuilder("sh", script.toString());
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.start();
        } catch (IOException e) {
            SyncMod.LOGGER.error("Impossible de lancer l'assistant de redemarrage", e);
        }
    }

    private static String windowsScript() {
        long pid = ProcessHandle.current().pid();
        List<String> lines = new ArrayList<>();
        lines.add("@echo off");
        lines.add("chcp 65001 >NUL");
        lines.add(":wait");
        lines.add("tasklist /FI \"PID eq " + pid + "\" /NH | find \" " + pid + " \" >NUL");
        lines.add("if not errorlevel 1 (");
        lines.add("ping -n 2 127.0.0.1 >NUL");
        lines.add("goto wait");
        lines.add(")");
        lines.add("ping -n 3 127.0.0.1 >NUL");
        for (Path path : DELETES) {
            lines.add("del /F /Q " + winQuote(path.toString()) + " >NUL 2>&1");
        }
        for (PendingMove move : MOVES) {
            lines.add("move /Y " + winQuote(move.from().toString()) + " " + winQuote(move.to().toString()) + " >NUL 2>&1");
        }
        if (relaunch) {
            Optional<List<String>> command = relaunchCommand();
            if (command.isPresent()) {
                lines.add("cd /d " + winQuote(FMLPaths.GAMEDIR.get().toString()));
                lines.add("start \"\" " + command.get().stream().map(RestartHelper::winQuote).collect(Collectors.joining(" ")));
            }
        }
        lines.add("(goto) 2>NUL & del \"%~f0\"");
        return String.join("\r\n", lines) + "\r\n";
    }

    private static String unixScript() {
        long pid = ProcessHandle.current().pid();
        List<String> lines = new ArrayList<>();
        lines.add("#!/bin/sh");
        lines.add("while kill -0 " + pid + " 2>/dev/null; do sleep 1; done");
        lines.add("sleep 1");
        for (Path path : DELETES) {
            lines.add("rm -f " + shQuote(path.toString()));
        }
        for (PendingMove move : MOVES) {
            lines.add("mv -f " + shQuote(move.from().toString()) + " " + shQuote(move.to().toString()));
        }
        if (relaunch) {
            Optional<List<String>> command = relaunchCommand();
            if (command.isPresent()) {
                String joined = command.get().stream().map(RestartHelper::shQuote).collect(Collectors.joining(" "));
                lines.add("cd " + shQuote(FMLPaths.GAMEDIR.get().toString()) + " && nohup " + joined + " >/dev/null 2>&1 &");
            }
        }
        lines.add("rm -f \"$0\"");
        return String.join("\n", lines) + "\n";
    }

    private static String winQuote(String value) {
        return "\"" + value.replace("%", "%%").replace("\"", "\"\"") + "\"";
    }

    private static String shQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}

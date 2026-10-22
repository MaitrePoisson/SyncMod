package com.syncmod.security;

import com.syncmod.util.SyncException;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PathGuard {
    private PathGuard() {
    }

    public static Path resolveInside(Path root, String filename) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(filename).normalize();
        if (!normalizedRoot.equals(resolved.getParent()) || Files.isSymbolicLink(resolved)) {
            throw new SyncException("syncmod.error.entry_invalid", filename);
        }
        return resolved;
    }

    public static boolean isDirectChild(Path root, Path candidate) {
        Path parent = candidate.toAbsolutePath().normalize().getParent();
        return parent != null && parent.equals(root.toAbsolutePath().normalize());
    }
}

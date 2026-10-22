package com.syncmod.client.sync;

import java.nio.file.Path;

public record PendingMove(Path from, Path to) {
}

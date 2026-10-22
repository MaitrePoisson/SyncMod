package com.syncmod.server;

import com.syncmod.SyncMod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public final class HttpFileServer {
    private static final int MAX_LINE = 2048;
    private static final int MAX_HEADER_LINES = 64;
    private static final int MAX_CONNECTIONS = 32;
    private static final String PREFIX = "/mods/";

    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore permits = new Semaphore(MAX_CONNECTIONS);
    private volatile ServerSocket socket;
    private volatile boolean running;

    public void start(int port) throws IOException {
        socket = new ServerSocket(port);
        running = true;
        Thread acceptor = new Thread(this::acceptLoop, "SyncMod-Http");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    public void stop() {
        running = false;
        try {
            ServerSocket current = socket;
            if (current != null) {
                current.close();
            }
        } catch (IOException ignored) {
        }
        pool.shutdownNow();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = socket.accept();
                pool.execute(() -> handle(client));
            } catch (IOException e) {
                if (running) {
                    SyncMod.LOGGER.error("Erreur du serveur HTTP de synchronisation", e);
                }
            }
        }
    }

    private void handle(Socket client) {
        boolean acquired = permits.tryAcquire();
        try (client) {
            client.setSoTimeout(10_000);
            OutputStream out = client.getOutputStream();
            if (!acquired) {
                writeStatus(out, 503, "Service Unavailable");
                return;
            }
            InputStream in = client.getInputStream();
            String requestLine = readLine(in);
            if (requestLine == null) {
                writeStatus(out, 400, "Bad Request");
                return;
            }
            for (int i = 0; i < MAX_HEADER_LINES; i++) {
                String header = readLine(in);
                if (header == null || header.isEmpty()) {
                    break;
                }
            }
            String[] parts = requestLine.split(" ");
            if (parts.length != 3) {
                writeStatus(out, 400, "Bad Request");
                return;
            }
            if (!parts[0].equals("GET")) {
                writeStatus(out, 405, "Method Not Allowed");
                return;
            }
            if (parts[1].equals("/manifest.json")) {
                sendManifest(out);
                return;
            }
            Optional<Path> file = parts[1].startsWith(PREFIX)
                    ? ServerModIndex.INSTANCE.lookup(parts[1].substring(PREFIX.length()))
                    : Optional.empty();
            if (file.isEmpty()) {
                writeStatus(out, 404, "Not Found");
                return;
            }
            send(out, file.get());
        } catch (IOException ignored) {
        } finally {
            if (acquired) {
                permits.release();
            }
        }
    }

    private static void sendManifest(OutputStream out) throws IOException {
        Optional<String> json = ServerModIndex.INSTANCE.manifestJson();
        if (json.isEmpty()) {
            writeStatus(out, 404, "Not Found");
            return;
        }
        byte[] body = json.get().getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private static void send(OutputStream out, Path file) throws IOException {
        long length = Files.size(file);
        String head = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/java-archive\r\n"
                + "Content-Length: " + length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        try (InputStream in = Files.newInputStream(file)) {
            in.transferTo(out);
        }
        out.flush();
    }

    private static void writeStatus(OutputStream out, int code, String message) throws IOException {
        String response = "HTTP/1.1 " + code + " " + message + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        out.write(response.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int value;
        while ((value = in.read()) != -1) {
            if (value == '\n') {
                String line = buffer.toString(StandardCharsets.ISO_8859_1);
                return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            }
            if (buffer.size() >= MAX_LINE) {
                return null;
            }
            buffer.write(value);
        }
        return null;
    }
}

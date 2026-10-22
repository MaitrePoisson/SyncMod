package com.syncmod.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.ByteToMessageDecoder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class SharedPortHook {
    private static final byte[] GET = {'G', 'E', 'T', ' '};
    private static final int MAX_HEADER_BYTES = 8192;
    private static final String MODS_PREFIX = "/mods/";

    private SharedPortHook() {
    }

    public static boolean install(MinecraftServer server) {
        ServerConnectionListener listener = server.getConnection();
        if (listener == null) {
            return false;
        }
        List<ChannelFuture> futures;
        synchronized (listener.channels) {
            futures = new ArrayList<>(listener.channels);
        }
        int hooked = 0;
        for (ChannelFuture future : futures) {
            Channel channel = future.channel();
            if (channel.isActive()) {
                channel.pipeline().addFirst("syncmod_accept", new AcceptTap());
                hooked++;
            }
        }
        return hooked > 0;
    }

    private static final class AcceptTap extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Channel child) {
                child.pipeline().addFirst("syncmod_sniffer", new HttpSniffer());
            }
            ctx.fireChannelRead(msg);
        }
    }

    private static final class HttpSniffer extends ByteToMessageDecoder {
        private boolean http;
        private boolean responded;

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (responded) {
                in.skipBytes(in.readableBytes());
                return;
            }
            if (!http) {
                if (in.readableBytes() < GET.length) {
                    return;
                }
                for (int i = 0; i < GET.length; i++) {
                    if (in.getByte(in.readerIndex() + i) != GET[i]) {
                        ctx.pipeline().remove(this);
                        return;
                    }
                }
                http = true;
                ctx.executor().schedule(() -> {
                    if (!responded) {
                        ctx.close();
                    }
                }, 10, TimeUnit.SECONDS);
            }
            int end = headerEnd(in);
            if (end < 0) {
                if (in.readableBytes() > MAX_HEADER_BYTES) {
                    ctx.close();
                }
                return;
            }
            String requestLine = in.toString(in.readerIndex(), end - in.readerIndex(), StandardCharsets.ISO_8859_1);
            int lineEnd = requestLine.indexOf("\r\n");
            if (lineEnd >= 0) {
                requestLine = requestLine.substring(0, lineEnd);
            }
            in.skipBytes(in.readableBytes());
            responded = true;
            respond(ctx, requestLine);
        }

        private static int headerEnd(ByteBuf in) {
            int start = in.readerIndex();
            int limit = in.writerIndex() - 3;
            for (int i = start; i < limit; i++) {
                if (in.getByte(i) == '\r' && in.getByte(i + 1) == '\n'
                        && in.getByte(i + 2) == '\r' && in.getByte(i + 3) == '\n') {
                    return i + 4;
                }
            }
            return -1;
        }

        private static void respond(ChannelHandlerContext ctx, String requestLine) {
            String[] parts = requestLine.split(" ");
            if (parts.length != 3) {
                status(ctx, 400, "Bad Request");
                return;
            }
            String path = parts[1];
            int query = path.indexOf('?');
            if (query >= 0) {
                path = path.substring(0, query);
            }
            if (path.equals("/manifest.json")) {
                Optional<String> json = ServerModIndex.INSTANCE.manifestJson();
                if (json.isEmpty()) {
                    status(ctx, 404, "Not Found");
                    return;
                }
                byte[] body = json.get().getBytes(StandardCharsets.UTF_8);
                ctx.write(Unpooled.wrappedBuffer(head(200, "OK", "application/json; charset=utf-8", body.length)));
                ctx.write(Unpooled.wrappedBuffer(body));
                finish(ctx);
                return;
            }
            Optional<Path> file = path.startsWith(MODS_PREFIX)
                    ? ServerModIndex.INSTANCE.lookup(path.substring(MODS_PREFIX.length()))
                    : Optional.empty();
            if (file.isEmpty()) {
                status(ctx, 404, "Not Found");
                return;
            }
            try {
                long length = Files.size(file.get());
                ctx.write(Unpooled.wrappedBuffer(head(200, "OK", "application/java-archive", length)));
                ctx.write(new DefaultFileRegion(file.get().toFile(), 0, length));
                finish(ctx);
            } catch (IOException e) {
                status(ctx, 500, "Internal Server Error");
            }
        }

        private static byte[] head(int code, String message, String contentType, long length) {
            String text = "HTTP/1.1 " + code + " " + message + "\r\n"
                    + "Content-Type: " + contentType + "\r\n"
                    + "Content-Length: " + length + "\r\n"
                    + "Connection: close\r\n\r\n";
            return text.getBytes(StandardCharsets.ISO_8859_1);
        }

        private static void status(ChannelHandlerContext ctx, int code, String message) {
            ctx.write(Unpooled.wrappedBuffer(head(code, message, "text/plain", 0)));
            finish(ctx);
        }

        private static void finish(ChannelHandlerContext ctx) {
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}

package com.syncmod.network.payload;

import com.syncmod.SyncMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ManifestPayload(String json) implements CustomPacketPayload {
    public static final Type<ManifestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SyncMod.MOD_ID, "manifest"));

    public static final StreamCodec<ByteBuf, ManifestPayload> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(500_000).map(ManifestPayload::new, ManifestPayload::json);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

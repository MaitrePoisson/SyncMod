package com.syncmod.network.payload;

import com.syncmod.SyncMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ManifestAckPayload(boolean synced) implements CustomPacketPayload {
    public static final Type<ManifestAckPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SyncMod.MOD_ID, "manifest_ack"));

    public static final StreamCodec<ByteBuf, ManifestAckPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(ManifestAckPayload::new, ManifestAckPayload::synced);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

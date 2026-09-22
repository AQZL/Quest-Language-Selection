package dev.ftbqlang.network;

import dev.ftbqlang.FTBQLang;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record QueryPayload(boolean bookOpened) implements CustomPacketPayload {
    public static final Type<QueryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            FTBQLang.MOD_ID, "query"));
    public static final StreamCodec<FriendlyByteBuf, QueryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, QueryPayload::bookOpened, QueryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package dev.ftbqlang.network;

import dev.ftbqlang.FTBQLang;
import dev.ftbqlang.server.LanguagePolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SelectLanguagePayload(String locale) implements CustomPacketPayload {
    public static final Type<SelectLanguagePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            FTBQLang.MOD_ID, "select_language"));
    public static final StreamCodec<FriendlyByteBuf, SelectLanguagePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SelectLanguagePayload decode(FriendlyByteBuf buffer) {
            return new SelectLanguagePayload(buffer.readUtf(LanguagePolicy.MAX_LOCALE_LENGTH));
        }

        @Override
        public void encode(FriendlyByteBuf buffer, SelectLanguagePayload payload) {
            buffer.writeUtf(payload.locale(), LanguagePolicy.MAX_LOCALE_LENGTH);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

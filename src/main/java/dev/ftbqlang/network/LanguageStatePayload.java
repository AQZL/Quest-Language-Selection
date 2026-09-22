package dev.ftbqlang.network;

import dev.ftbqlang.FTBQLang;
import dev.ftbqlang.server.LanguagePolicy;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record LanguageStatePayload(List<String> languages, String selected, boolean prompt)
        implements CustomPacketPayload {
    public static final Type<LanguageStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            FTBQLang.MOD_ID, "language_state"));
    public static final StreamCodec<FriendlyByteBuf, LanguageStatePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public LanguageStatePayload decode(FriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 0 || count > LanguagePolicy.MAX_LANGUAGES) {
                throw new DecoderException("Invalid quest language count: " + count);
            }
            List<String> languages = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                languages.add(buffer.readUtf(LanguagePolicy.MAX_LOCALE_LENGTH));
            }
            return new LanguageStatePayload(languages, buffer.readUtf(LanguagePolicy.MAX_LOCALE_LENGTH),
                    buffer.readBoolean());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, LanguageStatePayload payload) {
            buffer.writeVarInt(payload.languages().size());
            for (String language : payload.languages()) {
                buffer.writeUtf(language, LanguagePolicy.MAX_LOCALE_LENGTH);
            }
            buffer.writeUtf(payload.selected(), LanguagePolicy.MAX_LOCALE_LENGTH);
            buffer.writeBoolean(payload.prompt());
        }
    };

    public LanguageStatePayload {
        languages = List.copyOf(languages);
        if (languages.size() > LanguagePolicy.MAX_LANGUAGES) {
            throw new IllegalArgumentException("Too many quest languages");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

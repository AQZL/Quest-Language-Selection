package dev.ftbqlang.network;

import dev.ftbqlang.server.LanguageService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.Objects;
import java.util.function.Consumer;

/** No client classes are referenced here, so registration also works on dedicated servers. */
public final class LanguageNetwork {
    private static Consumer<LanguageStatePayload> clientHandler = payload -> { };

    private LanguageNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(QueryPayload.TYPE, QueryPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                LanguageService.query(player, payload.bookOpened());
            }
        });
        registrar.playToServer(SelectLanguagePayload.TYPE, SelectLanguagePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        LanguageService.select(player, payload.locale());
                    }
                });
        registrar.playToClient(LanguageStatePayload.TYPE, LanguageStatePayload.STREAM_CODEC,
                (payload, context) -> clientHandler.accept(payload));
    }

    public static void setClientHandler(Consumer<LanguageStatePayload> handler) {
        clientHandler = Objects.requireNonNull(handler);
    }
}

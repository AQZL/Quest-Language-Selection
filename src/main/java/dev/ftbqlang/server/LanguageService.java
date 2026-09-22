package dev.ftbqlang.server;

import dev.architectury.networking.NetworkManager;
import dev.ftb.mods.ftbquests.net.SyncTranslationTableMessage;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.translation.TranslationTable;
import dev.ftbqlang.network.LanguageStatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.LinkedHashSet;

public final class LanguageService {
    private LanguageService() {
    }

    public static void query(ServerPlayer player, boolean bookOpened) {
        respond(player, bookOpened, null);
    }

    public static void select(ServerPlayer player, String requested) {
        respond(player, false, requested);
    }

    private static void respond(ServerPlayer player, boolean bookOpened, String requested) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null || !file.isValid()) {
            PacketDistributor.sendToPlayer(player, new LanguageStatePayload(List.of(), "", false));
            return;
        }
        LanguageCatalog catalog = LanguageCatalog.scan(file.getFolder().resolve("lang"));
        List<String> languages = catalog.languages();
        LanguagePreferences preferences = LanguagePreferences.get(player.serverLevel().getServer());
        LanguagePolicy.Preference previous = preferences.get(player.getUUID());
        String requestedLocale = LanguagePolicy.normalize(requested);
        if (requested != null && LanguagePolicy.isValidLocale(requested)
                && catalog.tables().containsKey(requestedLocale)) {
            previous = new LanguagePolicy.Preference(requestedLocale, true);
        }
        LanguagePolicy.Decision decision = LanguagePolicy.decide(languages, previous,
                player.clientInformation().language(), bookOpened);
        preferences.put(player.getUUID(), new LanguagePolicy.Preference(decision.selected(), decision.prompted()));

        // Keep FTB's configurable fallback available without counting its virtual table as a file.
        var toSend = new LinkedHashSet<>(List.of("en_us", file.getFallbackLocale(), decision.selected()));
        toSend.remove("");
        for (String locale : toSend) {
            TranslationTable table = catalog.tables().get(locale);
            if (table != null) {
                NetworkManager.sendToPlayer(player, new SyncTranslationTableMessage(locale, table));
            } else {
                file.getTranslationManager().sendTableToPlayer(player, locale);
            }
        }
        PacketDistributor.sendToPlayer(player,
                new LanguageStatePayload(languages, decision.selected(), decision.prompt()));
    }
}

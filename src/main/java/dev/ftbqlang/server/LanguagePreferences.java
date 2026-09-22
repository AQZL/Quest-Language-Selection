package dev.ftbqlang.server;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Stored in overworld data storage, independently for each player and each save. */
public final class LanguagePreferences extends SavedData {
    private static final String FILE_ID = "ftbqlang_preferences";
    private static final Factory<LanguagePreferences> FACTORY =
            new Factory<>(LanguagePreferences::new, LanguagePreferences::load, null);

    private final Map<UUID, LanguagePolicy.Preference> players = new HashMap<>();

    public static LanguagePreferences get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    private static LanguagePreferences load(CompoundTag tag, HolderLookup.Provider registries) {
        LanguagePreferences data = new LanguagePreferences();
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            if (!players.contains(key, Tag.TAG_COMPOUND)) {
                continue;
            }
            try {
                UUID playerId = UUID.fromString(key);
                CompoundTag entry = players.getCompound(key);
                String selected = entry.getString("selected");
                data.players.put(playerId, new LanguagePolicy.Preference(
                        LanguagePolicy.isValidLocale(selected) ? LanguagePolicy.normalize(selected) : "",
                        entry.getBoolean("prompted")));
            } catch (IllegalArgumentException ignored) {
                // An invalid UUID entry must not prevent the remaining players from loading.
            }
        }
        return data;
    }

    public LanguagePolicy.Preference get(UUID playerId) {
        return players.getOrDefault(playerId, LanguagePolicy.Preference.UNSET);
    }

    public void put(UUID playerId, LanguagePolicy.Preference preference) {
        if (!get(playerId).equals(preference)) {
            players.put(playerId, preference);
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag entries = new CompoundTag();
        players.forEach((playerId, preference) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("selected", preference.selected());
            entry.putBoolean("prompted", preference.prompted());
            entries.put(playerId.toString(), entry);
        });
        tag.put("players", entries);
        return tag;
    }
}

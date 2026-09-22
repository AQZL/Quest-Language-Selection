package dev.ftbqlang.server;

import dev.ftb.mods.ftblibrary.snbt.SNBT;
import dev.ftb.mods.ftbquests.quest.translation.TranslationTable;
import dev.ftbqlang.FTBQLang;
import net.minecraft.nbt.CompoundTag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Only real, readable FTB SNBT language files count as selectable languages. */
public record LanguageCatalog(Map<String, TranslationTable> tables) {
    public LanguageCatalog {
        tables = Collections.unmodifiableMap(new TreeMap<>(tables));
    }

    public static LanguageCatalog scan(Path directory) {
        Map<String, TranslationTable> tables = new TreeMap<>();
        if (!Files.isDirectory(directory)) {
            return new LanguageCatalog(tables);
        }
        try (Stream<Path> files = Files.list(directory)) {
            var candidates = files.filter(Files::isRegularFile)
                    .filter(Files::isReadable)
                    .filter(path -> !LanguagePolicy.localeFromFilename(path.getFileName().toString()).isEmpty())
                    .sorted().toList();
            for (Path path : candidates) {
                String locale = LanguagePolicy.localeFromFilename(path.getFileName().toString());
                if (tables.containsKey(locale)) {
                    continue;
                }
                if (tables.size() == LanguagePolicy.MAX_LANGUAGES) {
                    FTBQLang.LOGGER.warn("Ignoring languages beyond the limit of {} in {}",
                            LanguagePolicy.MAX_LANGUAGES, directory);
                    break;
                }
                try {
                    CompoundTag tag = SNBT.read(path);
                    if (tag != null) {
                        tables.put(locale, TranslationTable.fromNBT(tag));
                    }
                } catch (RuntimeException exception) {
                    FTBQLang.LOGGER.warn("Cannot read quest language file {}: {}", path, exception.getMessage());
                }
            }
        } catch (IOException exception) {
            FTBQLang.LOGGER.warn("Cannot scan quest languages in {}: {}", directory, exception.getMessage());
        }
        return new LanguageCatalog(tables);
    }

    public List<String> languages() {
        return List.copyOf(tables.keySet());
    }
}

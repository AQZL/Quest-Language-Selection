package dev.ftbqlang.server;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises synchronization precedence without initializing the Minecraft runtime. */
class LiveTranslationTablesTest {
    private static final String TITLE = "quest.1234567890ABCDEF.title";
    private static final String DESCRIPTION = "quest.1234567890ABCDEF.quest_desc";

    @Test
    void returningToBookCannotOverwriteUnsavedTitleAndDescription() {
        var disk = table("Old title", List.of("Old description"));
        var live = table("Edited title", List.of("Edited first line", "Edited second line"));
        var tables = new HashMap<>(Map.of("zh_cn", live));

        // Multiple book queries or language switches before FTB saves the file.
        for (int i = 0; i < 3; i++) {
            LanguagePolicy.initializeMissingTables(tables, Map.of("zh_cn", disk));
            assertSame(live, tables.get("zh_cn"));
            assertEquals("Edited title", tables.get("zh_cn").get(TITLE));
            assertEquals(List.of("Edited first line", "Edited second line"),
                    tables.get("zh_cn").get(DESCRIPTION));
        }
    }

    @Test
    void clearingTextDoesNotRestoreTheOldDiskValue() {
        var disk = table("Old title", List.of("Old description"));
        var live = table("", List.of());
        var tables = new HashMap<>(Map.of("zh_cn", live));
        LanguagePolicy.initializeMissingTables(tables, Map.of("zh_cn", disk));

        assertEquals("", tables.get("zh_cn").get(TITLE));
        assertEquals(List.of(), tables.get("zh_cn").get(DESCRIPTION));
    }

    @Test
    void deletingEntriesDoesNotRestoreThemFromDisk() {
        var disk = table("Deleted quest title", List.of("Deleted quest description"));
        var live = new HashMap<String, Object>();
        var tables = new HashMap<>(Map.of("zh_cn", live));
        LanguagePolicy.initializeMissingTables(tables, Map.of("zh_cn", disk));

        assertSame(live, tables.get("zh_cn"));
        assertTrue(tables.get("zh_cn").isEmpty());
    }

    @Test
    void newLanguageIsImportedOnceAndSubsequentEditsStayLive() {
        var imported = table("Initial title", List.of("Initial description"));
        var tables = new HashMap<String, HashMap<String, Object>>();
        LanguagePolicy.initializeMissingTables(tables, Map.of("ja_jp", imported));
        tables.get("ja_jp").put(TITLE, "Edited title");

        var rescanned = table("Initial title", List.of("Initial description"));
        LanguagePolicy.initializeMissingTables(tables, Map.of("ja_jp", rescanned));

        assertEquals("Edited title", tables.get("ja_jp").get(TITLE));
        assertSame(imported, tables.get("ja_jp"));
    }

    @Test
    void fileDiscoveryKeepsTheLiveFallbackAndOtherLocales() {
        var fallback = table("English title", List.of("English description"));
        var edited = table("中文标题", List.of("新的描述"));
        var tables = new HashMap<>(Map.of("en_us", fallback, "zh_cn", edited));
        var disk = Map.of("zh_cn", table("旧标题", List.of("旧描述")));
        LanguagePolicy.initializeMissingTables(tables, disk);

        assertSame(fallback, tables.get("en_us"));
        assertSame(edited, tables.get("zh_cn"));
        assertEquals(1, disk.size(), "Live fallback tables must not become selectable disk files");
    }

    private static HashMap<String, Object> table(String title, List<String> description) {
        var table = new HashMap<String, Object>();
        table.put(TITLE, title);
        table.put(DESCRIPTION, description);
        return table;
    }
}

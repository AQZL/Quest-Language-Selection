package dev.ftbqlang.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguagePolicyTest {
    private static final List<String> MULTIPLE = List.of("en_us", "ja_jp", "zh_cn");

    @Test
    void firstBookOpenPromptsOnceAndRetainsChoiceAcrossFutureOpens() {
        var first = LanguagePolicy.decide(MULTIPLE, LanguagePolicy.Preference.UNSET, "zh_cn", true);
        assertTrue(first.prompt());
        assertEquals("zh_cn", first.selected());

        var persisted = new LanguagePolicy.Preference(first.selected(), first.prompted());
        var reopened = LanguagePolicy.decide(MULTIPLE, persisted, "ja_jp", true);
        assertFalse(reopened.prompt());
        assertEquals("zh_cn", reopened.selected());
    }

    @Test
    void loginQueryDoesNotConsumeFirstOpenPrompt() {
        var login = LanguagePolicy.decide(MULTIPLE, LanguagePolicy.Preference.UNSET, "zh_cn", false);
        assertFalse(login.prompt());
        assertFalse(login.prompted());
        var firstOpen = LanguagePolicy.decide(MULTIPLE,
                new LanguagePolicy.Preference(login.selected(), login.prompted()), "zh_cn", true);
        assertTrue(firstOpen.prompt());
    }

    @Test
    void singleLanguageIsAutomaticAndDoesNotConsumeFuturePrompt() {
        var sole = LanguagePolicy.decide(List.of("ja_jp"), LanguagePolicy.Preference.UNSET, "en_us", true);
        assertEquals("ja_jp", sole.selected());
        assertFalse(sole.prompt());
        assertFalse(sole.prompted());
        var expanded = LanguagePolicy.decide(MULTIPLE,
                new LanguagePolicy.Preference(sole.selected(), sole.prompted()), "en_us", true);
        assertTrue(expanded.prompt());
    }

    @Test
    void noActualFilesNeverSynthesizesEnglishOrConsumesPrompt() {
        var decision = LanguagePolicy.decide(List.of(), LanguagePolicy.Preference.UNSET, "en_us", true);
        assertEquals("", decision.selected());
        assertFalse(decision.prompt());
        assertFalse(decision.prompted());
    }

    @Test
    void deletedSavedLanguageFallsBackWithoutPromptingAgain() {
        var previous = new LanguagePolicy.Preference("de_de", true);
        var decision = LanguagePolicy.decide(MULTIPLE, previous, "ja_jp", true);
        assertEquals("ja_jp", decision.selected());
        assertFalse(decision.prompt());
        assertTrue(decision.prompted());
    }

    @Test
    void fallbackUsesEnglishThenFirstAvailable() {
        assertEquals("en_us", LanguagePolicy.decide(MULTIPLE,
                LanguagePolicy.Preference.UNSET, "fr_fr", false).selected());
        assertEquals("ja_jp", LanguagePolicy.decide(List.of("ja_jp", "zh_cn"),
                LanguagePolicy.Preference.UNSET, "fr_fr", false).selected());
    }

    @Test
    void localeNormalizationIsIndependentOfOperatingSystemLocale() {
        assertEquals("zh_cn", LanguagePolicy.localeFromFilename("ZH_CN.snbt"));
        assertEquals("zh_cn", LanguagePolicy.decide(MULTIPLE,
                new LanguagePolicy.Preference("ZH_CN", true), "en_us", false).selected());
    }

    @Test
    void onlySupportedFtbFileNamesAreAccepted() {
        assertEquals("en_us", LanguagePolicy.localeFromFilename("en_us.snbt"));
        for (String name : List.of("en_us.json", "en_us.snbt.bak", ".snbt", "../en_us.snbt",
                "zh-cn.snbt", "en_us.SNBT", "a".repeat(65) + ".snbt")) {
            assertEquals("", LanguagePolicy.localeFromFilename(name), name);
        }
    }

    @Test
    void separatePlayerPreferencesDoNotSharePromptState() {
        var firstPlayer = new LanguagePolicy.Preference("zh_cn", true);
        assertFalse(LanguagePolicy.decide(MULTIPLE, firstPlayer, "en_us", true).prompt());
        assertTrue(LanguagePolicy.decide(MULTIPLE, LanguagePolicy.Preference.UNSET, "en_us", true).prompt());
    }
}

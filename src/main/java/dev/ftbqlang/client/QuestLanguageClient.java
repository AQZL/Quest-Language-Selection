package dev.ftbqlang.client;

import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftbqlang.network.LanguageNetwork;
import dev.ftbqlang.network.LanguageStatePayload;
import dev.ftbqlang.network.QueryPayload;
import dev.ftbqlang.network.SelectLanguagePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** All state here belongs to the current connection; persistent state lives in the world. */
public final class QuestLanguageClient {
    private static List<String> languages = List.of();
    private static String selected = "";
    private static boolean initialQuerySent;
    private static boolean bookWasOpen;
    private static ClientQuestFile syncedFile;
    private static final SelectorSession selector = new SelectorSession();
    private static LanguageSelectionPanel activePanel;
    private static boolean manualRequest;
    private static boolean receivedState;
    private static ClientQuestFile pendingTranslationRefresh;

    private QuestLanguageClient() {
    }

    public static void reset() {
        languages = List.of();
        selected = "";
        initialQuerySent = false;
        bookWasOpen = false;
        syncedFile = null;
        selector.reset();
        activePanel = null;
        manualRequest = false;
        receivedState = false;
        pendingTranslationRefresh = null;
        LanguageNetwork.setClientHandler(QuestLanguageClient::receive);
    }

    public static String localeOverride() {
        return selected;
    }

    public static List<String> languages() {
        return languages;
    }

    public static boolean hasMultipleLanguages() {
        return languages.size() > 1;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !ClientQuestFile.exists()) {
            return;
        }
        if (syncedFile != ClientQuestFile.INSTANCE) {
            syncedFile = ClientQuestFile.INSTANCE;
            initialQuerySent = false;
        }
        // Wait for FTB's initial quest sync before asking for any translation packets.
        if (!initialQuerySent) {
            initialQuerySent = true;
            PacketDistributor.sendToServer(new QueryPayload(false));
        }
        QuestScreen screen = currentQuestScreen();
        if (screen == null) {
            bookWasOpen = false;
            selector.leftBook();
            activePanel = null;
            // An editor can temporarily cover the book and later reuse the same
            // QuestScreen. Keep any translation refresh until that screen is visible.
            return;
        }
        // ClientTickEvent.Post runs outside FTB's modalPanels.forEach(Panel::tick).
        // Remove only the finished modal; rebuilding QuestScreen here produces an empty
        // chapter frame before FTB restores its persisted state on the next tick.
        if (activePanel != null && activePanel.getGui() == screen && activePanel.isExitComplete()) {
            LanguageSelectionPanel closingPanel = activePanel;
            screen.closeModalPanel(closingPanel);
            applyPendingTranslationRefresh(screen);
            return;
        }
        // Track entering/leaving the book rather than screen identity: FTB can still
        // rebuild QuestScreen for non-translation edits and other state changes.
        if (!bookWasOpen) {
            bookWasOpen = true;
            PacketDistributor.sendToServer(new QueryPayload(true));
        }
        if (receivedState && hasMultipleLanguages() && selector.shouldAttach(screen)) {
            selector.attached(screen);
            activePanel = new LanguageSelectionPanel(screen, languages, selected);
            screen.pushModalPanel(activePanel);
        }
        applyPendingTranslationRefresh(screen);
    }

    public static void selectorClosed(QuestScreen host) {
        selector.dismissed(host);
        if (activePanel != null && activePanel.getGui() == host) {
            activePanel = null;
        }
    }

    public static boolean blocksBookClose(QuestScreen host) {
        return selector.isAttachedTo(host);
    }

    public static int modalBackgroundColor(Object host, int color) {
        if (activePanel == null || activePanel.getGui() != host) {
            return color;
        }
        int alpha = (int) Math.round((color >>> 24) * activePanel.backdropOpacity());
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** Translation packets can arrive even after Done. They must never reopen the book. */
    public static void refreshTranslations(ClientQuestFile file) {
        file.clearCachedData();
        pendingTranslationRefresh = file;
    }

    private static void applyPendingTranslationRefresh(QuestScreen screen) {
        if (pendingTranslationRefresh == null || blocksBookClose(screen)) {
            return;
        }
        ClientQuestFile file = pendingTranslationRefresh;
        pendingTranslationRefresh = null;
        if (file != ClientQuestFile.INSTANCE || file.getQuestScreen().orElse(null) != screen) {
            return;
        }
        // Refresh the existing panels synchronously, without setScreen/openGui or a
        // deferred BaseScreen.refreshWidgets (which would also rebuild every modal).
        screen.refreshChapterPanel();
        refreshPanelPreservingScroll(screen.questPanel);
        screen.otherButtonsBottomPanel.refreshWidgets();
        screen.otherButtonsTopPanel.refreshWidgets();
        if (screen.isViewingQuest()) {
            refreshPanelPreservingScroll(screen.viewQuestPanel);
        }
    }

    private static void refreshPanelPreservingScroll(Panel panel) {
        double x = panel.getScrollX();
        double y = panel.getScrollY();
        // ViewQuestPanel recreates its content panel, which owns the description scroll.
        Panel content = firstChildPanel(panel);
        double contentX = content == null ? 0 : content.getScrollX();
        double contentY = content == null ? 0 : content.getScrollY();
        panel.refreshWidgets();
        panel.setScrollX(x);
        panel.setScrollY(y);
        Panel refreshedContent = firstChildPanel(panel);
        if (content != null && refreshedContent != null) {
            refreshedContent.setScrollX(contentX);
            refreshedContent.setScrollY(Math.clamp(contentY, 0,
                    Math.max(0, refreshedContent.getContentHeight() - refreshedContent.getHeight())));
        }
    }

    private static Panel firstChildPanel(Panel panel) {
        for (var widget : panel.getWidgets()) {
            if (widget instanceof Panel child) {
                return child;
            }
        }
        return null;
    }

    public static void openSelector() {
        if (Minecraft.getInstance().player != null && !manualRequest) {
            manualRequest = true;
            PacketDistributor.sendToServer(new QueryPayload(true));
        }
    }

    public static void selectLanguage(String locale) {
        if (languages.contains(locale)) {
            // Apply only the server's acknowledgement, after the matching translation table arrives.
            PacketDistributor.sendToServer(new SelectLanguagePayload(locale));
        }
    }

    private static void receive(LanguageStatePayload payload) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        languages = List.copyOf(payload.languages());
        selected = payload.selected();
        receivedState = true;
        if (!hasMultipleLanguages()) {
            selector.reset();
        } else if (payload.prompt() || manualRequest) {
            selector.requestOpen();
        }
        manualRequest = false;
        if (ClientQuestFile.exists()) {
            refreshTranslations(ClientQuestFile.INSTANCE);
        }
    }

    private static QuestScreen currentQuestScreen() {
        return Minecraft.getInstance().screen instanceof ScreenWrapper wrapper
                && wrapper.getGui() instanceof QuestScreen quests ? quests : null;
    }
}

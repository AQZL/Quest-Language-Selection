package dev.ftbqlang.client;

import com.mojang.math.Axis;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.ModalPanel;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.PanelScrollBar;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.TextField;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.Key;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/** A centered picker with a continuously animated field of languages behind it. */
public final class LanguageSelectionPanel extends ModalPanel {
    private static final int PADDING = 10;
    private static final int ROW_HEIGHT = 24;
    private static final int ENTER_OFFSET = 26;
    private static final String[] WORDS = {
            "English", "中文", "日本語", "한국어", "Русский", "العربية",
            "हिन्दी", "Ελληνικά", "עברית", "ไทย", "Español", "Français",
            "Deutsch", "Italiano", "Português", "Polski", "Türkçe", "Tiếng Việt",
            "Bahasa Indonesia", "Українська", "Nederlands", "Svenska", "Suomi", "বাংলা"
    };
    private static final Component[] FLOATING_TEXT = java.util.Arrays.stream(WORDS)
            .map(Component::literal).toArray(Component[]::new);
    // FTB Quests 2101.1.27's bundled background, independent of QuestTheme selectors.
    private static final Icon DEFAULT_BACKGROUND = Icon.getIcon(
            "ftblibrary:textures/gui/background_squares.png; color=#DCFFFFFF; tile_size=64");

    private final List<String> languages;
    private String selected;
    private final Consumer<String> onSelect;
    private final TextField title;
    private final TextField subtitle;
    private final Panel languageList;
    private final PanelScrollBar scrollbar;
    private final SimpleTextButton close;
    private boolean initialScroll = true;
    private final LanguageAnimation animation = new LanguageAnimation(System.nanoTime());
    private double frameCenterY;
    private double frameOffsetY;
    private double frameScaleY = 1.0;
    private int lastLayoutWidth = -1;
    private int lastLayoutHeight = -1;

    public LanguageSelectionPanel(QuestScreen parent, List<String> languages, String selected) {
        this(parent, languages, selected, QuestLanguageClient::selectLanguage);
    }

    public LanguageSelectionPanel(QuestScreen parent, List<String> languages,
                                  String selected, Consumer<String> onSelect) {
        super(parent);
        this.languages = List.copyOf(languages);
        this.selected = selected;
        this.onSelect = onSelect;
        setExtraZlevel(1250);
        // FTB's legacy scissor does not follow pose transforms. Clip only the scrolling
        // list, using its transformed bounds, so the CRT collapse clips correctly.
        setOnlyRenderWidgetsInside(false);
        title = new TextField(this).addFlags(Theme.CENTERED | Theme.SHADOW)
                .setScale(1.5F).setText(Component.translatable("ftbqlang.screen.title"));
        subtitle = new TextField(this).addFlags(Theme.CENTERED)
                .setText(Component.translatable("ftbqlang.screen.subtitle"));
        languageList = new Panel(this) {
            @Override
            public void addWidgets() {
                for (int index = 0; index < LanguageSelectionPanel.this.languages.size(); index++) {
                    add(new LanguageButton(this, LanguageSelectionPanel.this.languages.get(index)));
                }
            }

            @Override
            public void alignWidgets() {
                for (int index = 0; index < widgets.size(); index++) {
                    widgets.get(index).setPosAndSize(0, index * ROW_HEIGHT, width, ROW_HEIGHT - 2);
                }
            }

            @Override
            public int getContentHeight() {
                return Math.max(0, widgets.size() * ROW_HEIGHT - 2);
            }

            @Override
            public void draw(GuiGraphics graphics, Theme theme, int x, int y, int width, int height) {
                int top = (int) Math.floor(transformedY(y));
                int bottom = (int) Math.ceil(transformedY(y + height));
                graphics.flush();
                GuiHelper.pushScissor(getWindow(), x, top, width, Math.max(0, bottom - top));
                try {
                    super.draw(graphics, theme, x, y, width, height);
                    graphics.flush();
                } finally {
                    GuiHelper.popScissor(getWindow());
                }
            }
        };
        languageList.setOnlyRenderWidgetsInside(false);
        languageList.setScrollStep(ROW_HEIGHT);
        scrollbar = new PanelScrollBar(this, languageList);
        close = new SimpleTextButton(this, Component.translatable("gui.done"), Icon.empty()) {
            @Override
            public boolean renderTitleInCenter() {
                return true;
            }

            @Override
            public void onClicked(MouseButton button) {
                if (button == MouseButton.LEFT) {
                    finish();
                }
            }
        };
    }

    @Override
    public void addWidgets() {
        add(title);
        add(subtitle);
        add(languageList);
        add(scrollbar);
        add(close);
    }

    @Override
    public void alignWidgets() {
        int availableWidth = Math.max(80, getGui().getWidth() - 2 * PADDING);
        int availableHeight = Math.max(80, getGui().getHeight() - 2 * PADDING);
        setWidth(Math.min(320, availableWidth));
        int textWidth = width - 2 * PADDING;

        title.setMaxWidth(textWidth).reflow();
        title.setPosAndSize(PADDING, PADDING + 2, textWidth, title.getHeight());
        subtitle.setMaxWidth(textWidth).reflow();
        subtitle.setPosAndSize(PADDING, title.getPosY() + title.getHeight() + 8,
                textWidth, subtitle.getHeight());

        int listY = subtitle.getPosY() + subtitle.getHeight() + 10;
        int desiredListHeight = Math.max(ROW_HEIGHT - 2, Math.min(8 * ROW_HEIGHT - 2,
                languages.size() * ROW_HEIGHT - 2));
        setHeight(Math.min(availableHeight, listY + desiredListHeight + 32));
        languageList.setPosAndSize(PADDING, listY, textWidth - 10,
                Math.max(1, height - listY - 32));
        languageList.alignWidgets();
        scrollbar.setPosAndSize(width - PADDING - 6, listY, 6, languageList.getHeight());
        close.setPosAndSize((width - Math.min(100, textWidth)) / 2,
                height - PADDING - 20, Math.min(100, textWidth), 20);

        int centeredX = (getGui().getWidth() - width) / 2;
        int centeredY = (getGui().getHeight() - height) / 2;
        setPos(centeredX, centeredY);
        if (initialScroll) {
            int index = languages.indexOf(selected);
            scrollbar.setValue(Math.max(0, index * ROW_HEIGHT - languageList.getHeight() / 2));
            initialScroll = false;
        } else {
            scrollbar.setValue(scrollbar.getValue());
        }
    }

    @Override
    public void tick() {
        super.tick();
        ensureCenteredLayout();
    }

    private void ensureCenteredLayout() {
        int guiWidth = getGui().getWidth();
        int guiHeight = getGui().getHeight();
        if (guiWidth == lastLayoutWidth && guiHeight == lastLayoutHeight) {
            return;
        }

        double scrollValue = scrollbar == null ? 0.0 : scrollbar.getValue();
        lastLayoutWidth = guiWidth;
        lastLayoutHeight = guiHeight;
        alignWidgets();
        if (scrollbar != null) {
            scrollbar.setValue(scrollValue);
        }
    }

    private void finish() {
        if (!animation.isClosing()) {
            playClickSound();
            animation.beginClose(System.nanoTime());
        }
    }

    public boolean isExitComplete() {
        return animation.isFinished(System.nanoTime());
    }

    public double backdropOpacity() {
        return animation.backdropOpacity(System.nanoTime());
    }

    private double transformedY(double y) {
        return frameCenterY + (y - frameCenterY) * frameScaleY + frameOffsetY;
    }

    @Override
    public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int width, int height) {
        DEFAULT_BACKGROUND.draw(graphics, x, y, width, height);
    }

    @Override
    public void draw(GuiGraphics graphics, Theme theme, int x, int y, int width, int height) {
        ensureCenteredLayout();
        long now = System.nanoTime();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0.0, 0.0, -1.0);
            drawFloatingText(graphics, now);
            // Flush the font batch before the picker so its labels remain unobstructed.
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
        frameCenterY = y + height / 2.0;
        frameOffsetY = (1.0 - animation.entrance(now)) * ENTER_OFFSET;
        frameScaleY = animation.panelScaleY(now);
        if (frameScaleY > 0.0) {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(0.0, frameCenterY + frameOffsetY, 0.0);
                graphics.pose().scale(1.0F, (float) frameScaleY, 1.0F);
                graphics.pose().translate(0.0, -frameCenterY, 0.0);
                super.draw(graphics, theme, x, y, width, height);
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
        }
        int lineWidth = (int) Math.ceil(width * animation.lineWidth(now));
        if (lineWidth > 0) {
            int lineX = x + (width - lineWidth) / 2;
            int lineY = (int) Math.round(frameCenterY + frameOffsetY);
            graphics.fill(lineX, lineY, lineX + lineWidth, lineY + 1, 0xFFE4F7FF);
        }
    }

    private void drawFloatingText(GuiGraphics graphics, long now) {
        var font = Minecraft.getInstance().font;
        int screenWidth = getWindow().getGuiScaledWidth();
        int screenHeight = getWindow().getGuiScaledHeight();
        for (int index = 0; index < FLOATING_TEXT.length; index++) {
            Component word = FLOATING_TEXT[index];
            int textWidth = font.width(word);
            float scale = (float) Math.min(0.9 + (index % 3) * 0.12,
                    (screenWidth / (double) LanguageAnimation.WORD_COLUMNS - 10) / Math.max(1, textWidth));
            scale = Math.max(0.4F, scale);
            double drawnWidth = textWidth * scale;
            double drawnHeight = font.lineHeight * scale;
            var pose = animation.word(index, screenWidth, screenHeight, drawnWidth, drawnHeight, now);
            int alpha = (int) Math.round(pose.alpha() * 255);
            // Minecraft treats nearly-zero text alpha as opaque, so skip those frames entirely.
            if (alpha < 4 || pose.y() > screenHeight + drawnWidth || pose.y() + drawnHeight < 0) {
                continue;
            }
            int tint = switch (index % 3) {
                case 0 -> 0xCEEFFF;
                case 1 -> 0xE6D9FF;
                default -> 0xFFF0D3;
            };
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(pose.x() + drawnWidth / 2.0, pose.y() + drawnHeight / 2.0, 0.0);
                graphics.pose().mulPose(Axis.ZP.rotationDegrees((float) pose.rotation()));
                graphics.pose().scale(scale, scale, 1.0F);
                graphics.drawString(font, word, -textWidth / 2, -font.lineHeight / 2, alpha << 24 | tint, true);
            } finally {
                graphics.pose().popPose();
            }
        }
    }

    @Override
    public boolean checkMouseOver(int mouseX, int mouseY) {
        // BaseScreen dismisses a modal when this returns false. Keep the picker modal everywhere.
        return true;
    }

    @Override
    public boolean mousePressed(MouseButton button) {
        if (!animation.isClosing() && animation.entrance(System.nanoTime()) >= 1.0) {
            super.mousePressed(button);
        }
        return true;
    }

    @Override
    public void onClosed() {
        QuestLanguageClient.selectorClosed((QuestScreen) getGui());
        super.onClosed();
    }

    @Override
    public boolean keyPressed(Key key) {
        if (key.enter()) {
            finish();
        }
        // Consume inventory/book/back keys as well; only Done (or Enter) starts the exit.
        return true;
    }

    public static Component languageName(String code) {
        var language = Minecraft.getInstance().getLanguageManager().getLanguage(code);
        return language == null ? Component.literal(code) : language.toComponent();
    }

    private final class LanguageButton extends SimpleTextButton {
        private final String code;

        private LanguageButton(Panel parent, String code) {
            super(parent, languageName(code), code.equals(LanguageSelectionPanel.this.selected)
                    ? Icons.ACCEPT : Icon.empty());
            this.code = code;
        }

        @Override
        public void onClicked(MouseButton button) {
            if (button == MouseButton.LEFT && !animation.isClosing()) {
                playClickSound();
                selected = code;
                onSelect.accept(code);
                for (var widget : languageList.getWidgets()) {
                    if (widget instanceof LanguageButton language) {
                        language.setIcon(language.code.equals(selected) ? Icons.ACCEPT : Icon.empty());
                    }
                }
            }
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(getTitle());
            list.add(Component.literal(code));
            if (code.equals(selected)) {
                list.add(Component.translatable("ftbqlang.screen.selected"));
            }
        }
    }
}

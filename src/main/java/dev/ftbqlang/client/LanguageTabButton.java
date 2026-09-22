package dev.ftbqlang.client;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.client.gui.quests.TabButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** A normal quest-book tab using Minecraft's own language globe sprite. */
public final class LanguageTabButton extends TabButton {
    private static final ResourceLocation LANGUAGE_SPRITE =
            ResourceLocation.withDefaultNamespace("icon/language");
    private static final Icon LANGUAGE_ICON = new Icon() {
        @Override
        public void draw(GuiGraphics graphics, int x, int y, int width, int height) {
            graphics.blitSprite(LANGUAGE_SPRITE, x, y, width, height);
        }
    };

    private final Runnable onClick;

    public LanguageTabButton(Panel parent) {
        this(parent, QuestLanguageClient::openSelector);
    }

    public LanguageTabButton(Panel parent, Runnable onClick) {
        super(parent, Component.translatable("ftbqlang.button.language"), LANGUAGE_ICON);
        this.onClick = onClick;
    }

    @Override
    public void onClicked(MouseButton button) {
        if (button == MouseButton.LEFT) {
            playClickSound();
            onClick.run();
        }
    }

    @Override
    public void addMouseOverText(TooltipList list) {
        list.add(getTitle());
    }
}

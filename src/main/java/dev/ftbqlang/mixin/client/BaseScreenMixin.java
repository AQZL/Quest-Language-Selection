package dev.ftbqlang.mixin.client;

import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftbqlang.client.QuestLanguageClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = BaseScreen.class, remap = false)
public abstract class BaseScreenMixin {
    // FTB Library 2101.1.39 moved the modal backdrop from draw() to
    // drawForeground(); the fade hook must follow it or the mixin fails to apply.
    @ModifyArg(method = "drawForeground", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftblibrary/icon/Color4I;rgba(I)Ldev/ftb/mods/ftblibrary/icon/Color4I;"), index = 0)
    private int ftbqlang$fadeLanguageBackdrop(int color) {
        return QuestLanguageClient.modalBackgroundColor(this, color);
    }
}
package dev.ftbqlang.mixin.client;

import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftbqlang.client.QuestLanguageClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = BaseScreen.class, remap = false)
public abstract class BaseScreenMixin {
    @ModifyArg(method = "draw", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftblibrary/icon/Color4I;rgba(I)Ldev/ftb/mods/ftblibrary/icon/Color4I;"), index = 0)
    private int ftbqlang$fadeLanguageBackdrop(int color) {
        return QuestLanguageClient.modalBackgroundColor(this, color);
    }
}

package dev.ftbqlang.mixin.client;

import dev.ftb.mods.ftblibrary.ui.input.Key;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftbqlang.client.QuestLanguageClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = QuestScreen.class, remap = false)
public abstract class QuestScreenMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void ftbqlang$keepSelectorOpen(Key key, CallbackInfoReturnable<Boolean> callback) {
        if (key.esc() && QuestLanguageClient.blocksBookClose((QuestScreen) (Object) this)) {
            callback.setReturnValue(true);
        }
    }
}

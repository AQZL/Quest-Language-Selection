package dev.ftbqlang.mixin.client;

import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftbqlang.client.QuestLanguageClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import net.minecraft.client.Minecraft;

@Mixin(value = ClientQuestFile.class, remap = false)
public abstract class ClientQuestFileMixin {
    @Inject(method = "refreshGui", at = @At("HEAD"), cancellable = true)
    private void ftbqlang$preserveLanguageModal(CallbackInfo callback) {
        if (Minecraft.getInstance().screen instanceof ScreenWrapper wrapper
                && wrapper.getGui() instanceof QuestScreen screen
                && QuestLanguageClient.blocksBookClose(screen)) {
            QuestLanguageClient.refreshTranslations((ClientQuestFile) (Object) this);
            callback.cancel();
        }
    }

    @Inject(method = "getLocale", at = @At("HEAD"), cancellable = true)
    private void ftbqlang$questLocale(CallbackInfoReturnable<String> callback) {
        String locale = QuestLanguageClient.localeOverride();
        if (!locale.isEmpty()) {
            callback.setReturnValue(locale);
        }
    }
}

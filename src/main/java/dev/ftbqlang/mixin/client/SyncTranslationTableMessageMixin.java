package dev.ftbqlang.mixin.client;

import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.net.SyncTranslationTableMessage;
import dev.ftbqlang.client.QuestLanguageClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SyncTranslationTableMessage.class, remap = false)
public abstract class SyncTranslationTableMessageMixin {
    // The queued handler is where FTB performs its refresh, not handle() itself.
    // Only translation refreshes are redirected; normal quest edits retain FTB's behavior.
    @Redirect(method = "lambda$handle$0", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbquests/client/ClientQuestFile;refreshGui()V"))
    private static void ftbqlang$refreshTranslationsInPlace(ClientQuestFile file) {
        QuestLanguageClient.refreshTranslations(file);
    }
}

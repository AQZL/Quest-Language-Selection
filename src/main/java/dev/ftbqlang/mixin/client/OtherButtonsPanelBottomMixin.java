package dev.ftbqlang.mixin.client;

import dev.ftb.mods.ftbquests.client.gui.quests.OtherButtonsPanelBottom;
import dev.ftbqlang.client.LanguageTabButton;
import dev.ftbqlang.client.QuestLanguageClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OtherButtonsPanelBottom.class, remap = false)
public abstract class OtherButtonsPanelBottomMixin {
    @Inject(method = "addWidgets", at = @At("TAIL"))
    private void ftbqlang$addLanguageButton(CallbackInfo callback) {
        if (QuestLanguageClient.hasMultipleLanguages()) {
            OtherButtonsPanelBottom panel = (OtherButtonsPanelBottom) (Object) this;
            panel.add(new LanguageTabButton(panel));
        }
    }
}

package dev.ftbqlang.mixin;

import dev.ftb.mods.ftbquests.quest.translation.TranslationManager;
import dev.ftb.mods.ftbquests.quest.translation.TranslationTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Access to FTB's live tables, including GUI edits which have not been saved yet. */
@Mixin(value = TranslationManager.class, remap = false)
public interface TranslationManagerAccessor {
    @Accessor("map")
    Map<String, TranslationTable> ftbqlang$getTables();
}

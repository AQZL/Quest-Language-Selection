package dev.ftbqlang.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the actual dependency bytecode without starting or initializing Minecraft. */
class TranslationRefreshIntegrationTest {
    @Test
    void translationRedirectMatchesTheQueuedFtbHandler() throws IOException {
        var calls = callsIn("dev/ftb/mods/ftbquests/net/SyncTranslationTableMessage");
        var rebuilds = calls.stream().filter(call -> call.owner().equals(
                        "dev/ftb/mods/ftbquests/client/ClientQuestFile")
                        && call.method().equals("refreshGui") && call.descriptor().equals("()V"))
                .toList();
        assertEquals(1, rebuilds.size());
        assertEquals("lambda$handle$0", rebuilds.getFirst().caller(),
                "The redirect must run inside the queued client task, including late packets");
    }

    @Test
    void editedTextHookRunsAfterTheQueuedTranslationUpdate() throws IOException {
        var calls = callsIn("dev/ftb/mods/ftbquests/net/SyncTranslationMessageToClient");
        var clearCache = calls.stream().filter(call -> call.owner().equals(
                        "dev/ftb/mods/ftbquests/quest/QuestObjectBase")
                        && call.method().equals("clearCachedData") && call.descriptor().equals("()V"))
                .toList();
        assertEquals(1, clearCache.size());
        assertEquals("lambda$handle$2", clearCache.getFirst().caller());
        var handler = calls.stream().filter(call -> call.caller().equals("lambda$handle$2")).toList();
        assertTrue(handler.stream().anyMatch(call -> call.method().equals("ifLeft")));
        assertTrue(handler.stream().anyMatch(call -> call.method().equals("ifRight")));
        assertEquals(clearCache.getFirst(), handler.getLast(),
                "Both title strings and description lists must be applied before our refresh hook");
    }

    @Test
    void liveTranslationAccessorMatchesTheActualFtbMap() throws IOException {
        var fields = new ArrayList<String>();
        try (var stream = getClass().getResourceAsStream(
                "/dev/ftb/mods/ftbquests/quest/translation/TranslationManager.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor,
                                               String signature, Object value) {
                    if (name.equals("map")) {
                        fields.add(descriptor);
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertEquals(List.of("Ljava/util/Map;"), fields);
    }

    @Test
    void serverRepliesUseFtbLiveTablesInsteadOfDiskSnapshots() throws IOException {
        var calls = callsIn("dev/ftbqlang/server/LanguageService");
        assertTrue(calls.stream().anyMatch(call -> call.owner().equals(
                        "dev/ftb/mods/ftbquests/quest/translation/TranslationManager")
                        && call.method().equals("sendTableToPlayer")));
        assertFalse(calls.stream().anyMatch(call -> call.owner().equals(
                        "dev/ftb/mods/ftbquests/net/SyncTranslationTableMessage")
                        && call.method().equals("<init>")),
                "Never send a disk snapshot which could replace unsaved title/description edits");
    }

    @Test
    void backdropHookMatchesTheFtbLibraryDrawCall() throws IOException {
        var calls = callsIn("dev/ftb/mods/ftblibrary/ui/BaseScreen");
        assertEquals(1, calls.stream().filter(call -> call.caller().equals("draw")
                && call.owner().equals("dev/ftb/mods/ftblibrary/icon/Color4I")
                && call.method().equals("rgba")
                && call.descriptor().equals("(I)Ldev/ftb/mods/ftblibrary/icon/Color4I;")).count());
    }

    @Test
    void languageAcknowledgementsAndExitCannotReopenTheQuestScreen() throws IOException {
        for (String owner : List.of("dev/ftbqlang/client/QuestLanguageClient",
                "dev/ftbqlang/mixin/client/SyncTranslationTableMessageMixin",
                "dev/ftbqlang/mixin/client/SyncTranslationMessageToClientMixin")) {
            var calls = callsIn(owner);
            assertFalse(calls.stream().anyMatch(call -> List.of("refreshGui", "setScreen", "openGui")
                            .contains(call.method())),
                    "Language synchronization must not replace the screen, even after the picker closes");
            assertFalse(calls.stream().anyMatch(call -> call.method().equals("<init>")
                    && call.owner().equals("dev/ftb/mods/ftbquests/client/gui/quests/QuestScreen")));
        }
    }

    private static List<Call> callsIn(String className) throws IOException {
        var calls = new ArrayList<Call>();
        try (var stream = TranslationRefreshIntegrationTest.class.getResourceAsStream("/" + className + ".class")) {
            assertNotNull(stream, "Missing compiled class: " + className);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method,
                                                    String descriptor, boolean isInterface) {
                            calls.add(new Call(name, owner, method, descriptor));
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return calls;
    }

    private record Call(String caller, String owner, String method, String descriptor) {
    }
}

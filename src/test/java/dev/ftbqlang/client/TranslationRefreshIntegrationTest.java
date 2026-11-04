package dev.ftbqlang.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;

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
        int clearIndex = handler.indexOf(clearCache.getFirst());
        int titleIndex = indexOfMethod(handler, "ifLeft");
        int descriptionIndex = indexOfMethod(handler, "ifRight");
        assertTrue(titleIndex >= 0 && titleIndex < clearIndex,
                "Title strings must be applied before our refresh hook");
        assertTrue(descriptionIndex >= 0 && descriptionIndex < clearIndex,
                "Description lists must be applied before our refresh hook");
        // FTB 2101.1.38 refreshes the open quest screen in place after clearing the
        // caches; our hook runs before that and must never rebuild the screen itself.
        assertTrue(indexOfMethod(handler, "getQuestScreen") > clearIndex);
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
     void backdropHookMatchesTheFtbLibraryDrawForegroundCall() throws IOException {
        // FTB Library 2101.1.39 moved the modal backdrop from BaseScreen.draw() into
        // BaseScreen.drawForeground(); the old hook no longer applies to anything.
        assertEquals(List.of("drawForeground"), modifyArgMethodTargets("dev/ftbqlang/mixin/client/BaseScreenMixin"));
        var calls = callsIn("dev/ftb/mods/ftblibrary/ui/BaseScreen");
        assertEquals(1, calls.stream().filter(call -> call.caller().equals("drawForeground")
                && call.owner().equals("dev/ftb/mods/ftblibrary/icon/Color4I")
                && call.method().equals("rgba")
                && call.descriptor().equals("(I)Ldev/ftb/mods/ftblibrary/icon/Color4I;")).count());
        assertFalse(calls.stream().anyMatch(call -> call.caller().equals("draw")
                && call.owner().equals("dev/ftb/mods/ftblibrary/icon/Color4I")),
                "The backdrop is no longer drawn by BaseScreen.draw()");
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

    private static int indexOfMethod(List<Call> calls, String method) {
        for (int index = 0; index < calls.size(); index++) {
            if (calls.get(index).method().equals(method)) {
                return index;
            }
        }
        return -1;
    }
    private static List<String> modifyArgMethodTargets(String className) throws IOException {
        var targets = new ArrayList<String>();
        try (var stream = TranslationRefreshIntegrationTest.class.getResourceAsStream("/" + className + ".class")) {
            assertNotNull(stream, "Missing compiled class: " + className);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                            if (!annotation.equals("Lorg/spongepowered/asm/mixin/injection/ModifyArg;")) {
                                return null;
                            }
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public AnnotationVisitor visitArray(String name) {
                                    if (!name.equals("method")) {
                                        return null;
                                    }
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override
                                        public void visit(String key, Object value) {
                                            if (value instanceof String target) {
                                                targets.add(target);
                                            }
                                        }
                                    };
                                }
                                @Override
                                public void visit(String key, Object value) {
                                    if (key.equals("method") && value instanceof String single) {
                                        targets.add(single);
                                    }
                                }
                            };
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return targets;
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

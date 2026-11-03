package icyllis.modernui.mc.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Checks the actual 1.21.8 sound paths used by focus-volume control, without opening audio. */
public final class SoundVolumeApiCheck {
    public static void main(String[] args) throws Exception {
        var node = new ClassNode();
        try (var in = SoundVolumeApiCheck.class.getResourceAsStream(
                "/net/minecraft/client/sounds/SoundEngine.class")) {
            if (in == null) throw new AssertionError("Missing Minecraft SoundEngine");
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        int calculateReturns = 0, listenerCalls = 0, libraryListenerCalls = 0, channelRefreshes = 0;
        for (var method : node.methods) {
            if (method.name.equals("calculateVolume") && method.desc.equals("(FLnet/minecraft/sounds/SoundSource;)F")) {
                for (var instruction : method.instructions) {
                    if (instruction.getOpcode() == Opcodes.FRETURN) calculateReturns++;
                }
            }
            if (method.name.equals("loadLibrary") && method.desc.equals("()V")) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call &&
                            call.owner.equals("com/mojang/blaze3d/audio/Listener") &&
                            call.name.equals("setGain") && call.desc.equals("(F)V")) libraryListenerCalls++;
                }
            }
            if (method.name.equals("updateCategoryVolume") && method.desc.equals("(Lnet/minecraft/sounds/SoundSource;F)V")) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call) {
                        if (call.owner.equals("com/mojang/blaze3d/audio/Listener") &&
                                call.name.equals("setGain") && call.desc.equals("(F)V")) listenerCalls++;
                        if (call.owner.equals("java/util/Map") && call.name.equals("forEach")) channelRefreshes++;
                    }
                }
            }
        }
        if (calculateReturns != 1 || listenerCalls != 1 || libraryListenerCalls != 1 || channelRefreshes != 1) {
            throw new AssertionError("Sound API changed: volume returns=" + calculateReturns +
                    ", listener updates=" + listenerCalls + ", library listener updates=" + libraryListenerCalls + ", channel refreshes=" + channelRefreshes);
        }
        // The device can reopen without a focus/multiplier change, so both setters
        // must be intercepted. Check the compiled production annotation as well.
        var mixin = new ClassNode();
        try (var in = SoundVolumeApiCheck.class.getResourceAsStream(
                "/icyllis/modernui/mc/mixin/MixinSoundEngine.class")) {
            if (in == null) throw new AssertionError("Missing production sound mixin");
            new ClassReader(in).accept(mixin, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        var handler = mixin.methods.stream().filter(m -> m.name.equals("modernui$scaleGlobalVolume"))
                .findFirst().orElseThrow();
        var annotations = new java.util.ArrayList<org.objectweb.asm.tree.AnnotationNode>();
        if (handler.visibleAnnotations != null) annotations.addAll(handler.visibleAnnotations);
        if (handler.invisibleAnnotations != null) annotations.addAll(handler.invisibleAnnotations);
        var annotation = annotations.stream().filter(a -> a.desc.equals(
                "Lorg/spongepowered/asm/mixin/injection/ModifyArg;")).findFirst().orElseThrow();
        var values = new java.util.HashMap<String, Object>();
        for (int i = 0; i < annotation.values.size(); i += 2) {
            values.put((String) annotation.values.get(i), annotation.values.get(i + 1));
        }
        var expected = java.util.Set.of("updateCategoryVolume(Lnet/minecraft/sounds/SoundSource;F)V", "loadLibrary()V");
        if (!(values.get("method") instanceof java.util.List<?> selectors)
                || !java.util.Set.copyOf(selectors).equals(expected)
                || !Integer.valueOf(2).equals(values.get("require"))
                || !Integer.valueOf(2).equals(values.get("allow"))
                || !Integer.valueOf(0).equals(values.get("index"))) {
            throw new AssertionError("Global multiplier must guard both real listener setters: " + values);
        }
        System.out.println("Minecraft sound API: channel-volume return, MASTER listener (including device reload) and non-MASTER channel refresh verified");
    }
}

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
        int calculateReturns = 0, listenerCalls = 0, channelRefreshes = 0;
        for (var method : node.methods) {
            if (method.name.equals("calculateVolume") && method.desc.equals("(FLnet/minecraft/sounds/SoundSource;)F")) {
                for (var instruction : method.instructions) {
                    if (instruction.getOpcode() == Opcodes.FRETURN) calculateReturns++;
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
        if (calculateReturns != 1 || listenerCalls != 1 || channelRefreshes != 1) {
            throw new AssertionError("Sound API changed: volume returns=" + calculateReturns +
                    ", listener updates=" + listenerCalls + ", channel refreshes=" + channelRefreshes);
        }
        System.out.println("Minecraft sound API: channel-volume return, MASTER listener and non-MASTER channel refresh verified");
    }
}

/*
 * Modern UI.
 * Copyright (C) 2019-2024 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.modernui.mc;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class MixinConfigPlugin implements IMixinConfigPlugin {

    private static final Set<String> AUDIO_TRACK_METHODS = Set.of(
            "isPlaying()Z", "play()V", "pause()V", "setPosition(FFF)V", "setGain(F)V",
            "getTime()F", "getLength()F", "getSampleRate()I", "seek(I)Z", "seekToSeconds(F)Z",
            "swapBuffers(Z)I", "tick()V",
            "setAnalyzer(Licyllis/modernui/audio/FFT;Ljava/util/function/Consumer;)V",
            "forward(I)I", "close()V");

    private boolean mDisableSmoothScrolling;
    private boolean mDisableEnhancedTextField;

    @Override
    public void onLoad(String mixinPackage) {
        if (MuiPlatform.get().isClient()) {
            mDisableSmoothScrolling = Boolean.parseBoolean(
                    ModernUIMod.getBootstrapProperty(ModernUIMod.BOOTSTRAP_DISABLE_SMOOTH_SCROLLING)
            );
            mDisableEnhancedTextField = Boolean.parseBoolean(
                    ModernUIMod.getBootstrapProperty(ModernUIMod.BOOTSTRAP_DISABLE_ENHANCED_TEXT_FIELD)
            );
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        /*if (ModernUIForge.isOptiFineLoaded() &&
                mixinClassName.equals("icyllis.modernui.mc.forge.mixin.AccessVideoSettings")) {
            return false;
        }*/
        if (mDisableSmoothScrolling) {
            if (mixinClassName.equals("icyllis.modernui.mc.mixin.MixinScrollPanel") ||
                    mixinClassName.equals("icyllis.modernui.mc.mixin.MixinSelectionList")) {
                return false;
            }
        }
        if (mDisableEnhancedTextField) {
            if (mixinClassName.equals("icyllis.modernui.mc.mixin.MixinEditBox") ||
                    mixinClassName.equals("icyllis.modernui.mc.mixin.MixinStringSplitter") ||
                    mixinClassName.equals("icyllis.modernui.mc.mixin.MixinTextFieldHelper")) {
                return false;
            }
        }
        if (true/*(mLevel & ModernUIForge.BOOTSTRAP_ENABLE_DEBUG_INJECTORS) == 0*/) {
            return !mixinClassName.endsWith("DBG");
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (!targetClassName.equals("icyllis.modernui.audio.Track") ||
                !mixinClassName.equals("icyllis.modernui.mc.mixin.MixinAudioTrack")) {
            return;
        }
        // Method monitors cover injected guards and the complete original body, even on throw.
        for (String signature : AUDIO_TRACK_METHODS) {
            MethodNode match = null;
            for (MethodNode method : targetClass.methods) {
                if (signature.equals(method.name + method.desc)) {
                    if (match != null || (method.access &
                            (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
                        throw new IllegalStateException("Invalid Core Track method: " + signature);
                    }
                    match = method;
                }
            }
            if (match == null) {
                throw new IllegalStateException("Missing Core Track method: " + signature);
            }
            match.access |= Opcodes.ACC_SYNCHRONIZED;
        }
    }
}

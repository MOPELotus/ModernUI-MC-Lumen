/*
 * Modern UI.
 * Copyright (C) 2019-2023 BloCamLimb. All rights reserved.
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

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import icyllis.modernui.animation.ColorEvaluator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.joml.Matrix3x2f;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Handling the blur effect of screen background. Client only.
 */
public enum BlurHandler {
    INSTANCE;

    private static final Marker MARKER = MarkerManager.getMarker("Blur");
    private static final Identifier GAUSSIAN_BLUR =
            ModernUIMod.location("gaussian_blur");

    /**
     * Config values
     */
    public static volatile boolean sBlurEffect;
    //public static volatile boolean sBlurWithBackground;
    public static volatile boolean sBlurForVanillaScreens;
    public static volatile boolean sOverrideVanillaBlur;
    public static volatile int sBlurRadius;
    public static volatile int sBackgroundDuration; // milliseconds
    public static volatile int[] sBackgroundColor = new int[4];

    public static volatile int sFramerateInactive;
    //public static volatile int sFramerateMinimized;
    public static volatile float sMasterVolumeInactive = 1;
    public static volatile float sMasterVolumeMinimized = 1;
    public static volatile boolean sGlobalVolumeControl = false;

    private final Minecraft minecraft = Minecraft.getInstance();

    private volatile ArrayList<Class<? extends Screen>> mBlacklist = new ArrayList<>();

    private final int[] mBackgroundColor = new int[4];

    /**
     * If it is playing animation
     */
    private boolean mFadingIn;

    /**
     * If blur is running
     */
    private boolean mBlurring;

    private float mBlurRadius;

    private boolean mLastBlurEffect;
    private boolean mLastBlurForVanillaScreens;
    private int mLastBlurRadius;
    private List<Class<? extends Screen>> mLastBlurBlacklist = List.of();
    private boolean mBlurConfigInitialized;

    /**
     * If a screen excluded, the other screens that opened after this screen won't be blurred, unless current screen
     * closed
     */
    private boolean mHasScreen;

    private volatile float mVolumeMultiplier = 1;
    private boolean mAppliedGlobalVolumeControl;
    private boolean mAudioVolumeDirty = true;

    /**
     * Use blur shader in game renderer post-processing.
     */
    public void blur(@Nullable Screen nextScreen) {
        if (!mBlurConfigInitialized) {
            mLastBlurEffect = sBlurEffect;
            mLastBlurForVanillaScreens = sBlurForVanillaScreens;
            mLastBlurRadius = sBlurRadius;
            mLastBlurBlacklist = mBlacklist;
            mBlurConfigInitialized = true;
        }
        if (minecraft.level == null) {
            return;
        }
        boolean hasScreen = nextScreen != null;

        boolean blocked = hasScreen && sBlurEffect &&
                isScreenBlocked(nextScreen, sBlurForVanillaScreens, mBlacklist);

        if (blocked && mBlurring) {
            mBlurring = false;
        }

        if (hasScreen && !mHasScreen) {
            if (!blocked && sBlurEffect && !mBlurring && sBlurRadius >= 1) {
                mBlurring = true;
                if (sBackgroundDuration > 0) {
                    updateRadius(1);
                } else {
                    updateRadius(sBlurRadius);
                }
            }
            if (sBackgroundDuration > 0) {
                mFadingIn = true;
                Arrays.fill(mBackgroundColor, 0);
            } else {
                mFadingIn = false;
                System.arraycopy(sBackgroundColor, 0, mBackgroundColor, 0, 4);
            }
        } else if (!hasScreen) {
            if (mBlurring) {
                mBlurring = false;
            }
            mFadingIn = false;
        }
        mHasScreen = hasScreen;
    }

    private static boolean isScreenBlocked(Screen screen, boolean blurForVanillaScreens,
                                           List<Class<? extends Screen>> blacklist) {
        if (screen instanceof MuiScreen muiScreen) {
            ScreenCallback callback = muiScreen.getCallback();
            return callback != null && !callback.shouldBlurBackground();
        }
        if (!blurForVanillaScreens) {
            return true;
        }
        for (Class<?> blockedClass : blacklist) {
            if (blockedClass.isAssignableFrom(screen.getClass())) {
                return true;
            }
        }
        return false;
    }

    /** Reconcile live settings on the render owner, without replaying the screen-open fade. */
    private void syncBlurConfig() {
        boolean enabled = sBlurEffect;
        boolean blurForVanillaScreens = sBlurForVanillaScreens;
        int radius = sBlurRadius;
        var blacklist = mBlacklist;
        if (mLastBlurEffect == enabled && mLastBlurForVanillaScreens == blurForVanillaScreens &&
                mLastBlurRadius == radius && mLastBlurBlacklist.equals(blacklist)) {
            return;
        }
        mLastBlurEffect = enabled;
        mLastBlurForVanillaScreens = blurForVanillaScreens;
        mLastBlurRadius = radius;
        mLastBlurBlacklist = blacklist;
        mBlurConfigInitialized = true;

        Screen screen = minecraft.gui.screen();
        if (!enabled || minecraft.level == null || screen == null || radius < 1 ||
                isScreenBlocked(screen, blurForVanillaScreens, blacklist)) {
            mBlurring = false;
            return;
        }
        mBlurring = true;
        if (mBlurring && !mFadingIn) {
            updateRadius(radius);
        }
    }

    @SuppressWarnings("unchecked")
    public void loadBlacklist(@Nullable List<? extends String> names) {
        ArrayList<Class<? extends Screen>> blacklist = new ArrayList<>();
        if (names != null) {
            for (String s : names) {
                if (StringUtils.isEmpty(s)) {
                    continue;
                }
                try {
                    Class<?> clazz = Class.forName(s, false, ModernUIMod.class.getClassLoader());
                    blacklist.add((Class<? extends Screen>) clazz);
                } catch (ClassNotFoundException e) {
                    ModernUIMod.LOGGER.warn(MARKER,
                            "Failed to add blur blacklist {}: make sure class name exists", s, e);
                } catch (ClassCastException e) {
                    ModernUIMod.LOGGER.warn(MARKER,
                            "Failed to add blur blacklist {}: make sure class is a valid subclass of Screen", s, e);
                }
            }
            blacklist.trimToSize();
        }
        mBlacklist = blacklist;
    }

    /**
     * Render tick, should called before rendering things
     */
    public void onRenderTick(long elapsedTimeMillis) {
        syncBlurConfig();
        if (mFadingIn) {
            float p = Math.min((float) elapsedTimeMillis / sBackgroundDuration, 1.0f);
            if (mBlurring) {
                updateRadius(Math.max(p * sBlurRadius, 1.0f));
            }
            for (int i = 0; i < 4; i++) {
                mBackgroundColor[i] = ColorEvaluator.evaluate(p, 0, sBackgroundColor[i]);
            }
            if (p == 1.0f) {
                mFadingIn = false;
            }
        }
    }

    private void updateRadius(float radius) {
        mBlurRadius = radius;
    }

    public void onClientTick() {
        float targetVolumeMultiplier;
        if (minecraft.isWindowActive()) {
            targetVolumeMultiplier = 1;
        } else if (sMasterVolumeMinimized < sMasterVolumeInactive &&
                minecraft.getWindow().isIconified()) {
            targetVolumeMultiplier = sMasterVolumeMinimized;
        } else {
            targetVolumeMultiplier = sMasterVolumeInactive;
        }
        boolean changed = mVolumeMultiplier != targetVolumeMultiplier;
        if (changed) {
            // fade down is slower, 1 second = 20 ticks
            if (mVolumeMultiplier < targetVolumeMultiplier) {
                mVolumeMultiplier = Math.min(
                        mVolumeMultiplier + 0.5f,
                        targetVolumeMultiplier
                );
            } else {
                mVolumeMultiplier = Math.max(
                        mVolumeMultiplier - 0.05f,
                        targetVolumeMultiplier
                );
            }
        }
        if (changed || mAudioVolumeDirty || mAppliedGlobalVolumeControl != sGlobalVolumeControl) {
            applyVolumeControl();
        }
    }

    /** Additional Minecraft-channel gain; the user's MASTER setting is already applied by Minecraft. */
    public float getSoundVolumeMultiplier() {
        return sGlobalVolumeControl ? 1.0f : mVolumeMultiplier;
    }

    /** Reapply after a successful library load, even if the fade target did not change. */
    public void onSoundLibraryLoaded() {
        mAudioVolumeDirty = true;
        applyVolumeControl();
    }

    private void applyVolumeControl() {
        // Library.init makes the context current on this calling thread before creating AL capabilities.
        if (ALC10.alcGetCurrentContext() == 0) {
            mAudioVolumeDirty = true;
            return;
        }
        AL10.alListenerf(AL10.AL_GAIN, sGlobalVolumeControl ? mVolumeMultiplier : 1.0f);
        // Refresh all active channels without modifying Minecraft's per-source gain map.
        minecraft.getSoundManager().refreshCategoryVolume(SoundSource.MASTER);
        mAppliedGlobalVolumeControl = sGlobalVolumeControl;
        mAudioVolumeDirty = false;
    }

    // INTERNAL HOOK
    public void drawScreenBackground(@Nonnull GuiGraphicsExtractor gr, int x1, int y1, int x2, int y2) {
        if (minecraft.level == null) {
            gr.fill(x1, y1, x2, y2, 0xFF191919);
        } else {
            if (mBlurring) {
                gr.blurBeforeThisStratum();
            }
            ScreenRectangle scissorArea = MuiModApi.get().peekScissorStack(gr);
            MuiModApi.get().submitGuiElementRenderState(gr,
                    new GradientRectangleRenderState(
                            RenderPipelines.GUI,
                            TextureSetup.noTexture(),
                            new Matrix3x2f(gr.pose()),
                            x1, y1, x2, y2,
                            mBackgroundColor[0], mBackgroundColor[1], mBackgroundColor[2], mBackgroundColor[3],
                            scissorArea
                    ));
        }
    }

    // INTERNAL HOOK
    public int getBlurRadius(int option) {
        float radius;
        if (mBlurring) {
            if (sOverrideVanillaBlur) {
                radius = mBlurRadius;
            } else {
                radius = Math.max(mBlurRadius / 1.8f, 1.0f);
            }
        } else {
            if (sOverrideVanillaBlur) {
                // Vanilla 3-pass box blur to ModernUI 1-pass gaussian blur
                // radius is approximately 1.8 times, performance is better
                radius = option * 1.8f;
            } else {
                radius = option;
            }
        }
        return (int) radius;
    }

    // INTERNAL HOOK
    public void processBlurEffect(GraphicsResourceAllocator resourceAllocator) {
        PostChain blurEffect = minecraft.getShaderManager().getPostChain(
                GAUSSIAN_BLUR, LevelTargetBundle.MAIN_TARGETS);
        if (blurEffect != null) {
            blurEffect.process(minecraft.gameRenderer.mainRenderTarget(), resourceAllocator);
        }
    }
}

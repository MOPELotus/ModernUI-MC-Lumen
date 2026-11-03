package icyllis.modernui.mc;

/** Keeps game-source attenuation separate from the shared OpenAL listener. */
public final class BackgroundAudioControl {
    private static volatile float gameMultiplier = 1.0f;
    private static volatile float listenerGain = 1.0f;

    private BackgroundAudioControl() {}

    public static boolean update(float multiplier, boolean global) {
        float nextGame = global ? 1.0f : multiplier;
        float nextListener = global ? multiplier : 1.0f;
        boolean changed = gameMultiplier != nextGame || listenerGain != nextListener;
        gameMultiplier = nextGame;
        listenerGain = nextListener;
        return changed;
    }

    public static float gameVolume(float volume) {
        return volume * gameMultiplier;
    }

    public static float listenerGain() {
        return listenerGain;
    }
}

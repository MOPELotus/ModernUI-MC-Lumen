package icyllis.modernui.mc.neoforge;

import icyllis.modernui.mc.MixinConfigPlugin;
import net.neoforged.fml.loading.LoadingModList;

/** Keep renderer entry repairs limited to the audited Reforged renderer ABI. */
public final class NeoForgeMixinConfigPlugin extends MixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".MixinReforgedScreenshot") ||
                mixinClassName.endsWith(".MixinReforgedScreenshotRenderer") ||
                mixinClassName.endsWith(".MixinReforgedScreenBlur")) {
            var mods = LoadingModList.get().getMods();
            if (mods.stream().noneMatch(mod -> mod.getModId().equals("minecraft") &&
                    mod.getVersion().toString().equals("1.21.1")) ||
                    mods.stream().noneMatch(mod -> mod.getModId().equals("vulkanmod") &&
                            mod.getVersion().toString().equals("0.3.5-ALPHA+1.21.1"))) {
                return false;
            }
        }
        return super.shouldApplyMixin(targetClassName, mixinClassName);
    }
}

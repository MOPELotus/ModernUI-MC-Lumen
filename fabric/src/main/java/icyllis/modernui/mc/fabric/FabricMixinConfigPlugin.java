package icyllis.modernui.mc.fabric;

import icyllis.modernui.mc.MixinConfigPlugin;
import net.fabricmc.loader.api.FabricLoader;

/** Keep the shutdown repair limited to the backend whose ownership was audited. */
public final class FabricMixinConfigPlugin extends MixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".MixinFabricVulkanShutdown") ||
                mixinClassName.endsWith(".AccessFabricVulkanLevelRenderer")) {
            var loader = FabricLoader.getInstance();
            if (!loader.getModContainer("minecraft").map(mod ->
                    switch (mod.getMetadata().getVersion().getFriendlyString()) {
                        case "1.21", "1.21.1" -> true;
                        default -> false;
                    }).orElse(false) ||
                    !loader.getModContainer("vulkanmod").map(mod ->
                    mod.getMetadata().getVersion().getFriendlyString().equals("0.6.7+1.21.1")).orElse(false)) {
                return false;
            }
        }
        return super.shouldApplyMixin(targetClassName, mixinClassName);
    }
}

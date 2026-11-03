package icyllis.modernui.mc.neoforge;

import icyllis.modernui.mc.ModernUIMod;
import net.neoforged.fml.loading.LoadingModList;

/** Reads the completed mod scan before renderer startup, without initializing a mod container. */
public final class VulkanModBootstrap extends ModernUIMod {
    private VulkanModBootstrap() {}

    public static boolean detectVulkanMod() {
        return sVulkanModLoaded = LoadingModList.get().getModFileById("vulkanmod") != null;
    }
}

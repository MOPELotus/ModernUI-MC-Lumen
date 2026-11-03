package icyllis.modernui.mc;

import org.spongepowered.asm.service.MixinService;

/** NeoForge-only guard for the extra native stencil pipeline lifetime. */
public final class NeoMixinConfigPlugin extends MixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".MixinNeoVulkanRenderPipeline")) {
            try {
                return VulkanPipelineCleanupCompat.needsCleanup(
                        MixinService.getService().getBytecodeProvider().getClassNode(targetClassName));
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot verify NeoForge Vulkan pipeline ownership", exception);
            }
        }
        return super.shouldApplyMixin(targetClassName, mixinClassName);
    }
}

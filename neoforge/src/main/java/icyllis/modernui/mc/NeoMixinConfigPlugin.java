package icyllis.modernui.mc;

import org.spongepowered.asm.service.MixinService;

/** NeoForge-only guard for the extra native stencil pipeline lifetime. */
public final class NeoMixinConfigPlugin extends MixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".MixinNeoVulkanStencilOnlyPipeline")) {
            try {
                var target = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
                VulkanPipelineCleanupCompat.configureNativeOwnership(target);
                return VulkanPipelineCleanupCompat.needsCleanup(target, "withStencilPipeline");
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot verify NeoForge stencil-only pipeline ownership", exception);
            }
        }
        if (mixinClassName.endsWith(".MixinNeoVulkanRenderPipeline")) {
            try {
                var target = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
                VulkanPipelineCleanupCompat.configureNativeOwnership(target);
                return VulkanPipelineCleanupCompat.needsCleanup(target);
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot verify NeoForge Vulkan pipeline ownership", exception);
            }
        }
        return super.shouldApplyMixin(targetClassName, mixinClassName);
    }
}

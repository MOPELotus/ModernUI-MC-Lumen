package icyllis.modernui.mc;

import icyllis.arc3d.core.Rect2i;
import icyllis.arc3d.engine.Engine;

/** Aligns Vulkan's physical render area with Granite's lower-left projection and scissor. */
public final class VulkanRenderArea {
    private VulkanRenderArea() {}

    public static void normalize(Rect2i bounds, int backend, int origin, int height) {
        if (backend == Engine.BackendApi.kVulkan && origin == Engine.SurfaceOrigin.kLowerLeft) {
            int top = bounds.top();
            bounds.set(bounds.left(), height - bounds.bottom(), bounds.right(), height - top);
        }
    }
}

package icyllis.modernui.mc;

import icyllis.arc3d.compiler.SPIRVVersion;
import icyllis.arc3d.engine.Swizzle;
import icyllis.arc3d.vulkan.*;
import icyllis.modernui.core.Core;
import icyllis.modernui.core.VulkanManager;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.lang.reflect.*;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.vulkan.VK10.*;

/** VulkanMod's pre-GpuDevice bridge; the mod continues to own its device and instance. */
public final class VulkanModIntegration {
    private VulkanModIntegration() {}

    private static final List<Runnable> RETIREMENTS = new ArrayList<>();

    // Loaded only on the Vulkan path. The two 1.21.1 distributions have different GL facades.
    private static final class Api {
        static final Class<?> DEVICE = type("net.vulkanmod.vulkan.device.DeviceManager");
        static final Class<?> IMAGE = type("net.vulkanmod.vulkan.texture.VulkanImage");
        static final Class<?> RENDERER = type("net.vulkanmod.vulkan.Renderer");
        static final Class<?> MEMORY = type("net.vulkanmod.vulkan.memory.MemoryManager");
        static final Class<?> GL_TEXTURE = textureFacade();
        static final Class<?> QUEUE = type("net.vulkanmod.vulkan.queue.Queue");
        static final Field MAIN_VIEW = field(IMAGE, "mainImageView");
        static final Field SAMPLER = field(IMAGE, "sampler");
        static final boolean FABRIC = GL_TEXTURE.getSimpleName().equals("VkGlTexture");
    }

    public static Class<?> type(String name) {
        try { return Class.forName(name); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Missing VulkanMod API: " + name, e); }
    }

    private static Class<?> textureFacade() {
        try { return Class.forName("net.vulkanmod.gl.VkGlTexture"); }
        catch (ClassNotFoundException ignored) { return type("net.vulkanmod.gl.GlTexture"); }
    }

    public static Field field(Class<?> owner, String name) {
        try { var f = owner.getDeclaredField(name); f.setAccessible(true); return f; }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Unsupported VulkanMod field: " + owner.getName() + "." + name, e); }
    }

    public static Object get(Field field, Object receiver) {
        try { return field.get(receiver); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    public static Object call(Class<?> owner, Object receiver, String name, Class<?>[] params, Object... args) {
        try { return owner.getMethod(name, params).invoke(receiver, args); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            if (e.getCause() instanceof Error err) throw err;
            throw new IllegalStateException("VulkanMod call failed: " + name, e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported VulkanMod method: " + owner.getName() + "." + name, e);
        }
    }

    static Object graphicsQueue() { return call(Api.DEVICE, null, "getGraphicsQueue", new Class<?>[0]); }
    static VkDevice device() { return (VkDevice) get(field(Api.DEVICE, "vkDevice"), null); }

    /** Neo Reforged may end a frame in a nested tick before the outer render returns. */
    public static boolean isFrameActive() {
        return (boolean) call(Api.RENDERER, null, "isRecording", new Class<?>[0]) &&
                !(boolean) get(field(Api.RENDERER, "skipRendering"), null);
    }

    static void finishTextureUploads() {
        if (Api.FABRIC) {
            Class<?> helper = type("net.vulkanmod.render.texture.ImageUploadHelper");
            call(helper, get(field(helper, "INSTANCE"), null), "submitCommands", new Class<?>[0]);
        } else {
            Object queue = graphicsQueue();
            // Reforged submits standalone uploads immediately; only TextureManager.tick
            // owns a recording batch. getCommandBuffer() would allocate when absent.
            Object pending = get(field(queue.getClass(), "currentCmdBuffer"), null);
            if (pending != null) {
                call(queue.getClass(), queue, "endRecordingAndSubmit", new Class<?>[0]);
                // Neo's end method schedules reset through MemoryManager, but does not
                // register its fence with Synchronization. Keep that single reset owner.
                long fence = (long) call(pending.getClass(), pending, "getFence", new Class<?>[0]);
                VKUtil._CHECK_(vkWaitForFences(device(), fence, true, -1L));
                // The enclosing tick reacquires its buffer after the callback and still
                // ends its batch. Preserve that scope with a fresh recording buffer.
                call(queue.getClass(), queue, "startRecording", new Class<?>[0]);
            }
        }
        Class<?> sync = type("net.vulkanmod.vulkan.Synchronization");
        call(sync, get(field(sync, "INSTANCE"), null), "waitFences", new Class<?>[0]);
    }

    static Class<?> imageClass() { return Api.IMAGE; }
    static Class<?> textureFacadeClass() { return Api.GL_TEXTURE; }
    static Object imageCall(Object image, String name, Class<?>[] params, Object... args) {
        return call(Api.IMAGE, image, name, params, args);
    }
    static Object textureCall(String name, Class<?>[] params, Object... args) {
        return call(Api.GL_TEXTURE, null, name, params, args);
    }
    static Object getImage(int textureId) {
        Object texture = textureCall("getTexture", new Class<?>[]{int.class}, textureId);
        return Objects.requireNonNull(call(Api.GL_TEXTURE, texture, "getVulkanImage", new Class<?>[0]), "Missing Vulkan texture");
    }
    static long sampler(Object image) { return (long) get(Api.SAMPLER, image); }
    static void sampler(Object image, long sampler) {
        try { Api.SAMPLER.setLong(image, sampler); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    public static VulkanBackendContext wrapContext() {
        VulkanBackendContext backend = new VulkanBackendContext();
        backend.mPhysicalDevice = (VkPhysicalDevice) get(field(Api.DEVICE, "physicalDevice"), null);
        backend.mInstance = backend.mPhysicalDevice.getInstance();
        backend.mDevice = (VkDevice) get(field(Api.DEVICE, "vkDevice"), null);
        Object families = call(Api.QUEUE, null, "getQueueFamilies", new Class<?>[0]);
        backend.mGraphicsQueueIndex = (int) get(field(families.getClass(), "graphicsFamily"), families);
        Object queue = call(Api.DEVICE, null, "getGraphicsQueue", new Class<?>[0]);
        backend.mQueue = (VkQueue) call(Api.QUEUE, queue, Api.FABRIC ? "vkQueue" : "queue", new Class<?>[0]);
        var manager = VulkanManager.get();
        // Physical support does not prove a feature was enabled on VulkanMod's logical device.
        manager.setPhysicalDeviceFeatures2(VkPhysicalDeviceFeatures2.calloc().sType$Default());
        backend.mDeviceFeatures2 = manager.getPhysicalDeviceFeatures2();
        var properties = (VkPhysicalDeviceProperties) get(field(Api.DEVICE, "deviceProperties"), null);
        backend.mMaxAPIVersion = Math.min(VK12.VK_API_VERSION_1_2, properties.apiVersion());
        manager.setMemoryAllocator(VulkanMemoryAllocator.make(backend.mInstance,
                backend.mPhysicalDevice, backend.mDevice, backend.mMaxAPIVersion, 0));
        backend.mMemoryAllocator = manager.getMemoryAllocator();
        ModernUIMod.LOGGER.info(ModernUIMod.MARKER, "VulkanMod bridge: {}, device {} (API capped to Vulkan 1.2)",
                Api.GL_TEXTURE.getName(), properties.deviceNameString());
        return backend;
    }

    public static void closeContext(Thread uiThread) {
        VulkanContextLifecycle.close(VulkanManager.get(), uiThread,
                Core.peekImmediateContext(), Core.peekUiRecordingContext());
    }

    public static void configureShaderCaps() {
        Core.requireImmediateContext().getCaps().shaderCaps().mSPIRVVersion = SPIRVVersion.SPIRV_1_5;
    }

    /** Submit and finish Minecraft's previous use before Arc3D writes the layer again. */
    public static void beforeSubmit(Layer previousLayer) {
        // The current frame has not sampled this layer yet. Do not flush its main
        // command buffer: VulkanMod's flushCmds omits the swapchain acquire semaphore.
        // Wait only for previously submitted reads on our shared graphics queue.
        finishTextureUploads();
        Object queue = graphicsQueue();
        VKUtil._CHECK_(vkQueueWaitIdle((VkQueue) call(queue.getClass(),
                queue, Api.FABRIC ? "vkQueue" : "queue", new Class<?>[0])));
        if (previousLayer != null) previousLayer.syncToArc3D();
    }

    public static void addFrameOp(Runnable action) {
        AtomicBoolean done = new AtomicBoolean();
        Runnable guarded = new Runnable() {
            @Override public void run() {
                if (!done.compareAndSet(false, true)) return;
                try { action.run(); }
                finally { synchronized (RETIREMENTS) { RETIREMENTS.remove(this); } }
            }
        };
        synchronized (RETIREMENTS) { RETIREMENTS.add(guarded); }
        Object memory = call(Api.MEMORY, null, "getInstance", new Class<?>[0]);
        call(Api.MEMORY, memory, "addFrameOp", new Class<?>[]{Runnable.class}, guarded);
    }

    /** Call after the UI thread stops and before dropping the Arc3D immediate context. */
    public static void drainFrameOps() {
        beforeSubmit(null);
        VKUtil._CHECK_(vkDeviceWaitIdle(device()));
        List<Runnable> actions;
        synchronized (RETIREMENTS) { actions = List.copyOf(RETIREMENTS); }
        actions.forEach(Runnable::run);
    }

    static void replaceMainImageView(Object image, short swizzle) {
        int format = (int) get(field(Api.IMAGE, "format"), image);
        int levels = (int) get(field(Api.IMAGE, "mipLevels"), image);
        long handle = (long) imageCall(image, "getId", new Class<?>[0]);
        long view;
        try (var stack = MemoryStack.stackPush()) {
            var desc = VkImageViewCreateInfo.calloc(stack).sType$Default().image(handle)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D).format(format);
            desc.components().set(VKUtil.toVkComponentSwizzle(Swizzle.getR(swizzle)),
                    VKUtil.toVkComponentSwizzle(Swizzle.getG(swizzle)),
                    VKUtil.toVkComponentSwizzle(Swizzle.getB(swizzle)),
                    VKUtil.toVkComponentSwizzle(Swizzle.getA(swizzle)));
            desc.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(levels).baseArrayLayer(0).layerCount(1);
            var result = stack.mallocLong(1);
            VkDevice device = (VkDevice) get(field(Api.DEVICE, "vkDevice"), null);
            VKUtil._CHECK_(vkCreateImageView(device, desc, null, result));
            view = result.get(0);
            vkDestroyImageView(device, (long) get(Api.MAIN_VIEW, image), null);
        }
        try { Api.MAIN_VIEW.setLong(image, view); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    /** Borrowed Arc image registered in the old Minecraft texture-id namespace. */
    public static final class Layer implements AutoCloseable {
        private final VulkanImage source;
        private final Object image;
        private int textureId;

        public Layer(VulkanImage source) {
            this.source = Objects.requireNonNull(source);
            var desc = source.getVulkanDesc();
            long view = Objects.requireNonNull(source.findOrCreateTextureView(Swizzle.RGBA)).vkImageView();
            try {
                if (Api.FABRIC) {
                    image = Api.IMAGE.getConstructor(String.class, long.class, int.class, int.class,
                            int.class, int.class, int.class, int.class, long.class).newInstance(
                            source.getLabel(), source.vkImage(), desc.mVkFormat, desc.getMipLevelCount(),
                            desc.getWidth(), desc.getHeight(), VKUtil.vkFormatBytesPerBlock(desc.mVkFormat),
                            desc.mImageUsageFlags, view);
                } else {
                    image = Api.IMAGE.getConstructor(long.class, int.class, int.class, int.class,
                            int.class, int.class, int.class, long.class).newInstance(
                            source.vkImage(), desc.mVkFormat, desc.getMipLevelCount(),
                            desc.getWidth(), desc.getHeight(), VKUtil.vkFormatBytesPerBlock(desc.mVkFormat),
                            desc.mImageUsageFlags, view);
                }
            } catch (ReflectiveOperationException e) { throw new IllegalStateException("Unsupported borrowed VulkanImage constructor", e); }
            textureId = (int) textureCall("genTextureId", new Class<?>[0]);
            textureCall("bindIdToImage", new Class<?>[]{int.class, Api.IMAGE}, textureId, image);
            source.ref();
        }

        public boolean sameImage(VulkanImage other) { return source == other; }
        public int getTextureId() { return textureId; }

        public void prepareForSampling() {
            imageCall(image, "setCurrentLayout", new Class<?>[]{int.class}, source.getVulkanMutableState().getImageLayout());
            // VulkanMod inserts the transfer/color-write to shader-read barrier, including upload synchronization.
            imageCall(image, "readOnlyLayout", new Class<?>[0]);
            syncToArc3D();
            source.refCommandBuffer();
            addFrameOp(source::unrefCommandBuffer);
        }

        /** Draw the same lower-left texture convention on both pre-GpuDevice distributions. */
        public void drawComposite() {
            prepareForSampling();
            // Resume the native main target with LOAD semantics after any image
            // transition ended its pass; beginning the main pass would clear it.
            net.minecraft.client.Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            Class<?> pipelines = type(Api.FABRIC
                    ? "net.vulkanmod.render.shader.PipelineManager" : "net.vulkanmod.render.PipelineManager");
            Object pipeline = call(pipelines, null, Api.FABRIC
                    ? "getFastBlitPipeline" : "getGlFramebufferBlitPipeline", new Class<?>[0]);
            Class<?> pipelineType = type("net.vulkanmod.vulkan.shader.Pipeline");
            Class<?> graphicsType = type("net.vulkanmod.vulkan.shader.GraphicsPipeline");
            Class<?> selector = type("net.vulkanmod.vulkan.texture.VTextureSelector");
            Object previousTexture = call(selector, null, "getBoundTexture", new Class<?>[]{int.class}, 0);
            Class<?> renderSystem = type("net.vulkanmod.vulkan.VRenderSystem");
            Field topology = field(renderSystem, "topology");
            int previousTopology = (int) get(topology, null);
            try {
                topology.setInt(null, VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
                call(selector, null, "bindTexture", new Class<?>[]{int.class, Api.IMAGE}, 0, image);
                Object renderer = call(Api.RENDERER, null, "getInstance", new Class<?>[0]);
                call(Api.RENDERER, renderer, "bindGraphicsPipeline", new Class<?>[]{graphicsType}, pipeline);
                call(Api.RENDERER, renderer, "uploadAndBindUBOs", new Class<?>[]{pipelineType}, pipeline);
                VkCommandBuffer cb = (VkCommandBuffer) call(Api.RENDERER, null, "getCommandBuffer", new Class<?>[0]);
                vkCmdDraw(cb, 3, 1, 0, 0);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            } finally {
                call(selector, null, "bindTexture", new Class<?>[]{int.class, Api.IMAGE}, 0, previousTexture);
                try { topology.setInt(null, previousTopology); }
                catch (IllegalAccessException e) { throw new IllegalStateException(e); }
            }
        }

        public void syncToArc3D() {
            source.getVulkanMutableState().setImageLayout((int) imageCall(image, "getCurrentLayout", new Class<?>[0]));
        }

        @Override public void close() {
            if (textureId == 0) return;
            // Detach first: glDeleteTextures must never free Arc3D's VkImage or image view.
            textureCall("setVulkanImage", new Class<?>[]{int.class, Api.IMAGE}, textureId, null);
            textureCall("glDeleteTextures", new Class<?>[]{int.class}, textureId);
            textureId = 0;
            addFrameOp(source::unref);
        }
    }
}

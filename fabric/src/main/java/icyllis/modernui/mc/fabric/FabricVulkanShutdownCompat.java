package icyllis.modernui.mc.fabric;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import icyllis.modernui.mc.mixin.AccessFabricVulkanLevelRenderer;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Owner shutdown for Fabric VulkanMod 0.6.7+1.21.1; no native-handle destruction. */
public final class FabricVulkanShutdownCompat {
    private static final String BUFFER = "net.vulkanmod.vulkan.memory.buffer.Buffer";
    private static final String STAGING = "net.vulkanmod.vulkan.memory.buffer.StagingBuffer";
    private static final String POOL = "net.vulkanmod.vulkan.memory.buffer.StagingBuffers";
    private static final String LIST = "it.unimi.dsi.fastutil.objects.ObjectArrayList";
    private static int shutdownStage;
    private static Thread shutdownThread;

    private FabricVulkanShutdownCompat() {}

    /** The original device idle has succeeded; native owners are still available. */
    public static void afterDeviceIdle() {
        RenderSystem.assertOnRenderThread();
        require(shutdownStage == 0, "Duplicate VulkanMod shutdown");
        var client = Minecraft.getInstance();
        var target = client.getMainRenderTarget();
        require(target != null && target.getClass() == MainTarget.class, "Unexpected main render target owner");
        var renderer = client.levelRenderer;
        require(renderer instanceof AccessFabricVulkanLevelRenderer, "Missing sky buffer accessors");

        // cloudRenderer is a VulkanMod-injected field, not Minecraft's vanilla cloudBuffer.
        Object cloud = read(field(renderer.getClass(), "cloudRenderer"), renderer);
        Class<?> cloudType = type("net.vulkanmod.render.sky.CloudRenderer");
        require(cloud == null || cloud.getClass() == cloudType, "Unexpected cloud renderer owner");
        Method resetCloud = method(cloudType, "resetBuffer");
        require(resetCloud.getReturnType() == void.class, "Unexpected cloud teardown signature");

        // Use mapped Minecraft APIs and VM's idempotent owner close methods. In particular,
        // VBO.close does not free Drawer-owned shared auto-index buffers.
        target.destroyBuffers();
        var sky = (AccessFabricVulkanLevelRenderer) renderer;
        if (sky.modernui$getStarBuffer() != null) {
            sky.modernui$getStarBuffer().close();
            sky.modernui$setStarBuffer(null);
        }
        if (sky.modernui$getSkyBuffer() != null) {
            sky.modernui$getSkyBuffer().close();
            sky.modernui$setSkyBuffer(null);
        }
        if (sky.modernui$getDarkBuffer() != null) {
            sky.modernui$getDarkBuffer().close();
            sky.modernui$setDarkBuffer(null);
        }
        // The backend initializes this owner lazily on the first cloud render.
        if (cloud != null) invoke(resetCloud, cloud);
        shutdownThread = Thread.currentThread();
        shutdownStage = 1;
    }

    /** Immediately precedes the original freeStagingBuffers -> StagingBuffers.free call. */
    public static void beforeStagingFree() {
        RenderSystem.assertOnRenderThread();
        require(shutdownStage == 1 && shutdownThread == Thread.currentThread(), "Out-of-order VulkanMod staging teardown");
        Class<?> vulkan = type("net.vulkanmod.vulkan.Vulkan");
        Object staging = invoke(method(vulkan, "getStagingBuffers"), null);
        retainCurrentStagingBuffer(staging);
        shutdownStage = 2;
    }

    /** Return the live current owner to the original pool drain, without scheduling it twice. */
    @SuppressWarnings("unchecked")
    static void retainCurrentStagingBuffer(Object staging) {
        require(staging != null && staging.getClass().getName().equals(POOL), "Unexpected staging pool");
        Field currentField = field(staging.getClass(), "currentBuffer");
        Object current = read(currentField, staging);
        if (current == null) return;
        require(current.getClass().getName().equals(STAGING) &&
                current.getClass().getSuperclass().getName().equals(BUFFER), "Unexpected current staging buffer");
        Class<?> buffer = current.getClass().getSuperclass();
        long id = number(field(buffer, "id"), current);
        long allocation = number(field(buffer, "allocation"), current);
        require(id != 0 && allocation != 0, "Closed current staging buffer");
        Class<?> memory = type("net.vulkanmod.vulkan.memory.MemoryManager");
        Object registry = read(field(memory, "buffers"), null);
        require(registry instanceof Map<?, ?> map && map.get(id) == current,
                "Current staging buffer is no longer registered");

        Object available = read(field(staging.getClass(), "availableBuffers"), staging);
        require(available != null && available.getClass().getName().equals(LIST) && available instanceof List<?>,
                "Unexpected available staging buffers");
        Object used = read(field(staging.getClass(), "usedBuffersByFrame"), staging);
        require(used instanceof Object[] && used.getClass().getComponentType().getName().equals(LIST),
                "Unexpected used staging buffers");
        IdentityHashMap<Object, Boolean> owners = new IdentityHashMap<>();
        collectOwners((List<?>) available, owners);
        for (Object frame : (Object[]) used) {
            require(frame != null && frame.getClass().getName().equals(LIST) && frame instanceof List<?>,
                    "Unexpected frame staging buffers");
            collectOwners((List<?>) frame, owners);
        }

        // A previously queued current owner is already handled by the original memory
        // drain. It must not also enter a pool whose free() enqueues that owner again.
        Object manager = read(field(memory, "INSTANCE"), null);
        require(manager != null, "Missing VulkanMod memory manager");
        Object queues = read(field(memory, "freeableBuffers"), manager);
        require(queues instanceof Object[], "Unexpected deferred buffer queues");
        boolean queued = false;
        for (Object queue : (Object[]) queues) {
            require(queue != null && queue.getClass().getName().equals(LIST) && queue instanceof List<?>,
                    "Unexpected deferred buffer queue");
            for (Object info : (List<?>) queue) {
                require(info != null && info.getClass().getName().equals(BUFFER + "$BufferInfo"),
                        "Unexpected deferred buffer owner");
                if (number(field(info.getClass(), "id"), info) == id) {
                    require(number(field(info.getClass(), "allocation"), info) == allocation,
                            "Current staging allocation differs from its deferred owner");
                    require(!queued, "Duplicate queued current staging buffer");
                    queued = true;
                }
            }
        }
        require(!queued || !owners.containsKey(current), "Current staging buffer has two retirement owners");
        if (!queued && !owners.containsKey(current)) ((List<Object>) available).add(current);
        write(currentField, staging, null);
        // No new frame runs between this handoff and the original pool's free().
    }

    private static void collectOwners(List<?> list, IdentityHashMap<Object, Boolean> owners) {
        for (Object owner : list) {
            require(owner != null && owner.getClass().getName().equals(STAGING), "Unexpected pooled staging owner");
            require(owners.put(owner, Boolean.TRUE) == null, "Duplicate pooled staging owner");
        }
    }

    private static Class<?> type(String name) {
        try { return Class.forName(name, false, FabricVulkanShutdownCompat.class.getClassLoader()); }
        catch (ReflectiveOperationException e) { throw contract(name, e); }
    }
    private static Field field(Class<?> type, String name) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException e) { throw contract(type.getName() + "." + name, e); }
    }
    private static Object read(Field field, Object owner) {
        try { return field.get(owner); }
        catch (ReflectiveOperationException e) { throw contract(field.getName(), e); }
    }
    private static long number(Field field, Object owner) {
        require(field.getType() == long.class, "Unexpected native owner field type");
        try { return field.getLong(owner); }
        catch (ReflectiveOperationException e) { throw contract(field.getName(), e); }
    }
    private static void write(Field field, Object owner, Object value) {
        try { field.set(owner, value); }
        catch (ReflectiveOperationException e) { throw contract(field.getName(), e); }
    }
    private static Method method(Class<?> type, String name) {
        try { return type.getMethod(name); }
        catch (ReflectiveOperationException e) { throw contract(type.getName() + "." + name, e); }
    }
    private static Object invoke(Method method, Object owner) {
        try { return method.invoke(owner); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException failure) throw failure;
            if (e.getCause() instanceof Error failure) throw failure;
            throw contract(method.getName(), e.getCause());
        } catch (ReflectiveOperationException e) { throw contract(method.getName(), e); }
    }
    private static IllegalStateException contract(String operation, Throwable cause) {
        return new IllegalStateException("Unsupported Fabric VulkanMod shutdown contract: " + operation, cause);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

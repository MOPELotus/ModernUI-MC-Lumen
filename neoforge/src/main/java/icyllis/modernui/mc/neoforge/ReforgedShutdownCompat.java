package icyllis.modernui.mc.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;

/** Terminal ownership repair for the audited Reforged 0.3.5 Minecraft 1.21.1 release. */
public final class ReforgedShutdownCompat {
    private static final String VERSION = "0.3.5-ALPHA+1.21.1";
    private static final String PIPELINE = "net.vulkanmod.vulkan.shader.Pipeline";
    private static final String GRAPHICS_PIPELINE = "net.vulkanmod.vulkan.shader.GraphicsPipeline";
    private static int shutdownStage;
    private static long shutdownThread;

    private ReforgedShutdownCompat() {}

    /** Called only after the original vkDeviceWaitIdle returned VK_SUCCESS. */
    public static void afterDeviceIdle() {
        assertRenderThread();
        require(shutdownStage == 0, "Duplicate or out-of-order Reforged shutdown");
        requireExactRelease();
        Class<?> renderer = type("net.vulkanmod.vulkan.Renderer");
        require(Boolean.FALSE.equals(invoke(publicMethod(renderer, "isRecording"), null)),
                "Reforged shutdown still has a recording frame");

        // The vanilla main target owns an ordinary GlFramebuffer as well as the
        // renderer's separate main pass. Its deferred RenderPass deletion must
        // enter MemoryManager before the original terminal drain runs.
        Class<?> minecraft = type("net.minecraft.client.Minecraft");
        Object client = invoke(publicMethod(minecraft, "getInstance"), null);
        Object target = invoke(publicMethod(minecraft, "getMainRenderTarget"), client);
        require(target != null && target.getClass().getName().equals("com.mojang.blaze3d.pipeline.MainTarget"),
                "Unexpected Reforged main render target owner");
        Class<?> renderTarget = type("com.mojang.blaze3d.pipeline.RenderTarget");
        invoke(publicMethod(renderTarget, "destroyBuffers"), target);
        try {
            Field framebuffer = renderTarget.getField("frameBufferId");
            require(framebuffer.getType() == int.class && framebuffer.getInt(target) == -1,
                    "Main render target did not release its framebuffer owner");
        } catch (ReflectiveOperationException e) {
            throw contract("Main render target teardown", e);
        }
        shutdownThread = Thread.currentThread().threadId();
        shutdownStage = 1;
    }

    /** Called after the original MemoryManager.cleanUp returned normally, before allocator destruction. */
    public static void afterMemoryCleanup() {
        assertRenderThread();
        require(shutdownStage == 1 && shutdownThread == Thread.currentThread().threadId(),
                "Reforged registry cleanup did not follow idle/main-target teardown on the same thread");
        Class<?> pipeline = type(PIPELINE);
        Class<?> graphics = type(GRAPHICS_PIPELINE);
        try {
            Field field = pipeline.getDeclaredField("PIPELINES");
            require(field.getType() == List.class && Modifier.isPublic(field.getModifiers()) &&
                            Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()),
                    "Unexpected Reforged pipeline registry field");
            Object registry = field.get(null);
            require(registry instanceof List<?>, "Missing Reforged pipeline registry");
            retireRemaining((List<?>) registry, graphics);
        } catch (ReflectiveOperationException e) {
            throw contract("Pipeline registry teardown", e);
        }
        shutdownStage = 2;
    }

    /** Validates the complete snapshot before any native owner is retired. */
    static void retireRemaining(List<?> registry, Class<?> exactGraphicsType) {
        require(registry != null && registry.getClass() == LinkedList.class,
                "Unexpected Reforged pipeline registry implementation");
        require(exactGraphicsType != null && exactGraphicsType.getName().equals(GRAPHICS_PIPELINE) &&
                        exactGraphicsType.getSuperclass() != null &&
                        exactGraphicsType.getSuperclass().getName().equals(PIPELINE),
                "Unexpected Reforged pipeline type hierarchy");
        try {
            Field closed = exactGraphicsType.getDeclaredField("closed");
            require(closed.getType() == boolean.class && Modifier.isPrivate(closed.getModifiers()) &&
                            !Modifier.isStatic(closed.getModifiers()) && closed.trySetAccessible(),
                    "Unexpected Reforged pipeline closed-state contract");
            Method cleanup = exactGraphicsType.getDeclaredMethod("cleanUp");
            require(cleanup.getReturnType() == void.class && Modifier.isPublic(cleanup.getModifiers()) &&
                            !Modifier.isStatic(cleanup.getModifiers()),
                    "Unexpected Reforged pipeline cleanup contract");
            Object[] snapshot = registry.toArray();
            IdentityHashMap<Object, Boolean> expected = new IdentityHashMap<>();
            for (Object owner : snapshot) {
                require(owner != null && owner.getClass() == exactGraphicsType,
                        "Foreign or null Reforged pipeline owner");
                require(expected.put(owner, Boolean.TRUE) == null, "Duplicate Reforged pipeline owner");
                require(!closed.getBoolean(owner), "Closed Reforged pipeline remains registered; cannot retry native cleanup");
            }
            requireSameOwners(registry, expected);
            for (Object owner : snapshot) {
                invoke(cleanup, owner);
                require(closed.getBoolean(owner), "Reforged cleanup did not close its pipeline owner");
                expected.remove(owner);
                requireSameOwners(registry, expected);
            }
            require(registry.isEmpty(), "Reforged pipeline registry remains live after terminal cleanup");
        } catch (ReflectiveOperationException e) {
            throw contract("Pipeline owner contract", e);
        }
    }

    private static void requireSameOwners(List<?> registry, IdentityHashMap<Object, Boolean> expected) {
        require(registry.size() == expected.size(), "Reforged pipeline registry changed during terminal cleanup");
        IdentityHashMap<Object, Boolean> observed = new IdentityHashMap<>();
        for (Object owner : registry) {
            require(expected.containsKey(owner) && observed.put(owner, Boolean.TRUE) == null,
                    "Reforged pipeline registry ownership changed during terminal cleanup");
        }
        require(observed.size() == expected.size(), "Reforged pipeline registry snapshot is incomplete");
    }

    private static void requireExactRelease() {
        Class<?> loading = type("net.neoforged.fml.loading.LoadingModList");
        Object mods = invoke(publicMethod(loading, "get"), null);
        Object file = invoke(publicMethod(loading, "getModFileById", String.class), mods, "vulkanmod");
        require(file != null, "Missing Reforged mod metadata at shutdown");
        Class<?> fileInfo = type("net.neoforged.neoforgespi.language.IModFileInfo");
        require(fileInfo.isInstance(file), "Unexpected Reforged mod-file metadata type");
        Object entries = invoke(publicMethod(fileInfo, "getMods"), file);
        require(entries instanceof List<?>, "Unexpected Reforged mod metadata");
        Class<?> modInfo = type("net.neoforged.neoforgespi.language.IModInfo");
        boolean matched = false;
        for (Object entry : (List<?>) entries) {
            require(modInfo.isInstance(entry), "Unexpected Reforged mod metadata entry");
            Object id = invoke(publicMethod(modInfo, "getModId"), entry);
            if ("vulkanmod".equals(id)) {
                Object version = invoke(publicMethod(modInfo, "getVersion"), entry);
                require(version != null && VERSION.equals(version.toString()),
                        "Unsupported Reforged release for terminal ownership repair: " + version);
                matched = true;
            }
        }
        require(matched, "Missing Reforged version contract");
    }

    private static void assertRenderThread() {
        invoke(publicMethod(type("com.mojang.blaze3d.systems.RenderSystem"), "assertOnRenderThread"), null);
    }

    private static Class<?> type(String name) {
        try {
            return Class.forName(name, false, ReforgedShutdownCompat.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw contract(name, e);
        }
    }

    private static Method publicMethod(Class<?> owner, String name, Class<?>... args) {
        try {
            return owner.getMethod(name, args);
        } catch (ReflectiveOperationException e) {
            throw contract(owner.getName() + "." + name, e);
        }
    }

    private static Object invoke(Method method, Object owner, Object... args) {
        try {
            return method.invoke(owner, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException failure) throw failure;
            if (cause instanceof Error failure) throw failure;
            throw contract(method.toString(), cause);
        } catch (ReflectiveOperationException e) {
            throw contract(method.toString(), e);
        }
    }

    private static IllegalStateException contract(String operation, Throwable cause) {
        return new IllegalStateException("Unsupported Reforged shutdown contract: " + operation, cause);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

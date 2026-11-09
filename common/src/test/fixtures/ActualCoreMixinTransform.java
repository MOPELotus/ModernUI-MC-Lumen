import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.launch.MappingConfiguration;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Uses Fabric's real Sponge Mixin service to transform shipped Core classes without starting Minecraft. */
public final class ActualCoreMixinTransform {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("config-resource output-directory target-class...");
        System.setProperty("mixin.service", "net.fabricmc.loader.impl.launch.knot.MixinServiceKnot");
        var loader = ActualCoreMixinTransform.class.getClassLoader();
        var properties = FabricLauncherBase.class.getDeclaredMethod("setProperties", Map.class);
        properties.setAccessible(true);
        properties.invoke(null, new HashMap<>());
        FabricLauncherBase.setLauncher((FabricLauncher) Proxy.newProxyInstance(loader,
                new Class<?>[]{FabricLauncher.class}, (proxy, method, parameters) -> switch (method.getName()) {
                    case "getEnvironmentType" -> EnvType.CLIENT;
                    case "getClassPath" -> List.of();
                    case "getTargetClassLoader" -> loader;
                    case "getResourceAsStream" -> loader.getResourceAsStream((String) parameters[0]);
                    case "getClassByteArray" -> {
                        try (var stream = loader.getResourceAsStream(((String) parameters[0]).replace('.', '/') + ".class")) {
                            yield stream == null ? null : stream.readAllBytes();
                        }
                    }
                    case "isClassLoaded", "isDevelopment" -> false;
                    case "getEntrypoint" -> ActualCoreMixinTransform.class.getName();
                    case "getMappingConfiguration" -> new MappingConfiguration();
                    default -> null;
                }));
        MixinBootstrap.init();
        Mixins.addConfiguration(args[0]);
        var phase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        phase.setAccessible(true);
        phase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        var accessor = Class.forName("net.fabricmc.loader.impl.launch.knot.MixinServiceKnot")
                .getDeclaredMethod("getTransformer");
        accessor.setAccessible(true);
        var transformer = (IMixinTransformer) accessor.invoke(null);
        var synthetic = new TreeSet<String>();
        for (int i = 2; i < args.length; i++) {
            String name = args[i];
            String resource = name.replace('.', '/') + ".class";
            byte[] original;
            try (var stream = loader.getResourceAsStream(resource)) {
                if (stream == null) throw new ClassNotFoundException(name);
                original = stream.readAllBytes();
            }
            byte[] transformed = transformer.transformClassBytes(name, name, original);
            if (Arrays.equals(original, transformed)) throw new IllegalStateException("No mixin applied to " + name);
            write(args[1], resource, transformed);
            new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String method, String descriptor,
                                                 String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method,
                                                    String descriptor, boolean isInterface) {
                            if (owner.startsWith("org/spongepowered/asm/synthetic/")) synthetic.add(owner);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            System.out.println("Actual Mixin transformed " + name);
        }
        for (String owner : synthetic) {
            String name = owner.replace('/', '.');
            byte[] bytes = transformer.transformClassBytes(name, name, null);
            if (bytes == null) throw new ClassNotFoundException(name);
            write(args[1], owner + ".class", bytes);
        }
    }

    private static void write(String directory, String resource, byte[] bytes) throws Exception {
        Path output = Path.of(directory, resource);
        Files.createDirectories(output.getParent());
        Files.write(output, bytes);
    }
}

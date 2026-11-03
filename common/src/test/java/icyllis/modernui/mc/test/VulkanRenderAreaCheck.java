package icyllis.modernui.mc.test;

import icyllis.arc3d.core.Rect2i;
import icyllis.arc3d.core.Rect2ic;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.granite.DrawCommandList;
import icyllis.arc3d.vulkan.VulkanPrimaryCommandBuffer;
import icyllis.modernui.mc.VulkanRenderArea;
import org.lwjgl.vulkan.VkRect2D;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/** Actual Arc scissor/render-area contracts; uses CPU native structs, never a Vulkan device. */
public final class VulkanRenderAreaCheck {
    private static final String GRANITE = "icyllis/arc3d/granite/";

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static ClassNode read(String owner) throws Exception {
        try (var input = VulkanRenderAreaCheck.class.getResourceAsStream("/" + owner + ".class")) {
            if (input == null) throw new AssertionError("Missing actual class " + owner);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode owner, String name) {
        var matches = owner.methods.stream().filter(m -> m.name.equals(name)).toList();
        require(matches.size() == 1, "Ambiguous/missing actual method " + owner.name + "." + name);
        return matches.getFirst();
    }

    private static List<MethodInsnNode> calls(MethodNode method, String owner, String name) {
        return Arrays.stream(method.instructions.toArray()).filter(i -> i instanceof MethodInsnNode)
                .map(i -> (MethodInsnNode) i).filter(i -> i.owner.equals(owner) && i.name.equals(name)).toList();
    }

    private static SourceValue argument(ClassNode owner, MethodNode method, MethodInsnNode call, int argument)
            throws Exception {
        var frames = new Analyzer<>(new SourceInterpreter()).analyze(owner.name, method);
        var frame = frames[method.instructions.indexOf(call)];
        int count = Type.getArgumentTypes(call.desc).length;
        return frame.getStack(frame.getStackSize() - count + argument);
    }

    private static int loadedLocal(SourceValue value) {
        require(value.insns.size() == 1, "Expected one actual source local");
        var node = value.insns.iterator().next();
        require(node instanceof VarInsnNode && node.getOpcode() == Opcodes.ALOAD,
                "Expected unchanged reference argument");
        return ((VarInsnNode) node).var;
    }

    private static AbstractInsnNode nextCode(AbstractInsnNode node) {
        do { node = node.getNext(); } while (node != null && node.getOpcode() < 0);
        return node;
    }

    private static void actualBytecodeContract() throws Exception {
        var context = read(GRANITE + "SurfaceDrawContext");
        var snap = method(context, "snapDrawPass");
        require(snap.desc.equals("(Licyllis/arc3d/granite/RecordingContext;)Licyllis/arc3d/granite/DrawPass;"),
                "Actual private draw-pass hook descriptor changed");
        var construct = calls(snap, GRANITE + "DrawPass", "<init>");
        require(construct.size() == 1, "Actual draw-pass allocation changed");
        int boundsLocal = loadedLocal(argument(context, snap, construct.getFirst(), 1));
        var rounds = Arrays.stream(snap.instructions.toArray()).filter(i -> i instanceof MethodInsnNode)
                .map(i -> (MethodInsnNode) i).filter(i -> i.name.equals("roundOut")
                        && i.desc.equals("(Licyllis/arc3d/core/Rect2i;)V")).toList();
        require(rounds.size() == 1 && loadedLocal(argument(context, snap, rounds.getFirst(), 0)) == boundsLocal,
                "DrawPass no longer receives the freshly rounded logical pass bounds");
        boolean freshBounds = false;
        for (var node : snap.instructions) {
            if (node instanceof TypeInsnNode allocation && allocation.getOpcode() == Opcodes.NEW
                    && allocation.desc.equals("icyllis/arc3d/core/Rect2i")) {
                for (var scan = nextCode(node); scan != null; scan = nextCode(scan)) {
                    if (scan instanceof VarInsnNode store && store.getOpcode() == Opcodes.ASTORE) {
                        if (store.var == boundsLocal) freshBounds = true;
                        break;
                    }
                    if (scan.getOpcode() == Opcodes.ARETURN) break;
                }
            }
        }
        require(freshBounds, "Bounds must be per-pass ownership, not a shared cached rectangle");

        var pass = read(GRANITE + "DrawPass");
        var getBounds = method(pass, "getBounds");
        require(Arrays.stream(getBounds.instructions.toArray()).anyMatch(i -> i instanceof FieldInsnNode f
                        && f.getOpcode() == Opcodes.GETFIELD && f.name.equals("mBounds")
                        && f.desc.equals("Licyllis/arc3d/core/Rect2i;")),
                "Actual getBounds no longer returns the mutable per-pass Rect2i");

        var task = read(GRANITE + "task/RenderPassTask");
        var execute = method(task, "execute");
        var getters = calls(execute, GRANITE + "DrawPass", "getBounds");
        var begins = calls(execute, "icyllis/arc3d/engine/CommandBuffer", "beginRenderPass");
        require(getters.size() == 1 && begins.size() == 1, "Actual render-pass bounds forwarding changed");
        var store = nextCode(getters.getFirst());
        require(store instanceof VarInsnNode && store.getOpcode() == Opcodes.ASTORE,
                "Actual pass bounds must be forwarded through a local");
        require(loadedLocal(argument(task, execute, begins.getFirst(), 2)) == ((VarInsnNode) store).var,
                "beginRenderPass no longer receives the unchanged DrawPass bounds");
        require(Arrays.stream(execute.instructions.toArray()).noneMatch(i -> i instanceof MethodInsnNode m
                        && (m.name.equals("getOrigin") || m.name.equals("set") || m.name.equals("offset"))),
                "Arc now adjusts render-area orientation itself; revisit the compatibility hook");
    }

    private static int[] scissor(Rect2ic logical, int height, int origin) {
        var commands = new DrawCommandList();
        commands.setScissor(logical, height, origin);
        int[] words = commands.mPrimitives.toIntArray();
        require(words.length == 5 && words[0] == DrawCommandList.CMD_SET_SCISSOR,
                "Actual Arc scissor command encoding changed");
        return Arrays.copyOfRange(words, 1, 5);
    }

    private static int[] renderArea(Method adjust, Rect2ic bounds, int granularity, int width, int height)
            throws Exception {
        try (var nativeArea = VkRect2D.calloc()) {
            adjust.invoke(null, bounds, granularity, granularity, width, height, nativeArea);
            return new int[]{nativeArea.offset().x(), nativeArea.offset().y(),
                    nativeArea.extent().width(), nativeArea.extent().height()};
        }
    }

    private static boolean contains(int[] outer, int[] inner) {
        return outer[0] <= inner[0] && outer[1] <= inner[1]
                && outer[0] + outer[2] >= inner[0] + inner[2]
                && outer[1] + outer[3] >= inner[1] + inner[3];
    }

    private static boolean intersects(int[] a, int[] b) {
        return a[0] < b[0] + b[2] && b[0] < a[0] + a[2]
                && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
    }

    public static void main(String[] args) throws Exception {
        actualBytecodeContract();
        var adjust = VulkanPrimaryCommandBuffer.class.getDeclaredMethod("adjust_render_area",
                Rect2ic.class, int.class, int.class, int.class, int.class, VkRect2D.class);
        adjust.setAccessible(true);
        // Screen-space rows from the actual H=900 failure, then asymmetric/edge/full-frame cases.
        int[][] cases = {{1600,900,512,379,1088,419}, {1600,900,512,508,1088,550},
                {1600,900,500,266,1100,634}, {1600,900,0,0,1600,900},
                {127,137,7,17,93,48}, {127,137,0,0,127,1}, {127,137,0,136,127,137},
                {1,1,0,0,1,1}, {1920,1080,4,29,1917,1079}, {127,137,9,33,9,39},
                {127,137,7,19,91,19}};
        int checks = 0;
        for (int[] c : cases) {
            var logical = new Rect2i(c[2],c[3],c[4],c[5]);
            for (int backend : new int[]{Engine.BackendApi.kVulkan, Engine.BackendApi.kOpenGL, -1}) {
                for (int origin : new int[]{Engine.SurfaceOrigin.kLowerLeft, Engine.SurfaceOrigin.kUpperLeft}) {
                    var fixed = new Rect2i(logical);
                    VulkanRenderArea.normalize(fixed, backend, origin, c[1]);
                    if (backend != Engine.BackendApi.kVulkan || origin != Engine.SurfaceOrigin.kLowerLeft) {
                        require(fixed.equals(logical), "GL, upper-left, and unrelated backends must remain unchanged");
                    } else {
                        require(Arrays.equals(renderArea(adjust, fixed, 1, c[0], c[1]), scissor(logical,c[1],origin)),
                                "Normalized native render area must match the independently generated actual Arc scissor");
                        if (!logical.isEmpty()) for (int granularity : new int[]{2,8,16,64}) {
                            require(contains(renderArea(adjust,fixed,granularity,c[0],c[1]),scissor(logical,c[1],origin)),
                                    "Driver render-area granularity must cover the actual physical scissor");
                        }
                    }
                    checks++;
                }
            }
        }
        var green = new Rect2i(512,379,1088,419);
        var input = new Rect2i(512,508,1088,550);
        int[] greenScissor = scissor(green,900,Engine.SurfaceOrigin.kLowerLeft);
        int[] inputScissor = scissor(input,900,Engine.SurfaceOrigin.kLowerLeft);
        require(Arrays.equals(greenScissor,new int[]{512,481,576,40}), "Actual green-row physical scissor changed");
        require(Arrays.equals(inputScissor,new int[]{512,350,576,42}), "Actual input-row physical scissor changed");
        int[] staleGreen = renderArea(adjust,green,1,1600,900);
        int[] staleInput = renderArea(adjust,input,1,1600,900);
        require(!intersects(staleGreen,greenScissor) && !intersects(staleInput,inputScissor),
                "Original asymmetric dirty regions must reproduce the missing-update negative control");
        require(intersects(staleInput,greenScissor),
                "Original input render area must reproduce refreshing part of the wrong green row");
        require(!Arrays.equals(staleGreen,greenScissor), "Unfixed-bounds negative control unexpectedly passed");
        require(Arrays.equals(renderArea(adjust,null,1,1600,900),new int[]{0,0,1600,900}),
                "Actual null/full-frame render area contract changed");
        System.out.println("Vulkan render-area checks passed: " + checks
                + " backend/origin cases; actual Arc ownership, forwarding, scissor, native area, granularity and regression controls verified");
    }
}

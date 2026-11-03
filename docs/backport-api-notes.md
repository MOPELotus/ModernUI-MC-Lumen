# Minecraft 1.21 and 1.21.1 API adaptation

The implementation retains this game family's immediate-mode rendering API. Later Minecraft GUI render-state classes, texture-view APIs and input-event records must not be substituted into this branch.

## Verified compatibility boundary

Both official client JARs and Mojang mappings were downloaded from the URLs in Mojang's version metadata and verified against their declared SHA-1. All 31 vanilla classes targeted by the original enabled Fabric/NeoForge mixin lists are byte-identical between 1.21 and 1.21.1. Their 2,591 mapped members match. The audit checked 94 explicit descriptors and 86 in-method instruction targets; the four loader-specific ScrollPanel calls were checked in actual NeoForge class files.

This establishes the shared API surface, not graphical acceptance. Each supported Minecraft/loader/backend combination still requires an actual hardware-rendered run.

| Boundary | Actual parameter order in both games |
| --- | --- |
| Public positioned tooltip | `renderTooltip(Font, List, ClientTooltipPositioner, int x, int y)` |
| Internal tooltip injection | `renderTooltipInternal(Font, List, int x, int y, ClientTooltipPositioner)` |
| Keyboard callback | `keyPress(long window, int key, int scancode, int action, int modifiers)` |
| Character callback | `charTyped(long window, int codePoint, int modifiers)` |
| Screen character input | `charTyped(char character, int modifiers)` |
| Mouse callback | `onPress(long window, int button, int action, int modifiers)` |
| Screen scroll | `mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical)` |
| Renderer initialization | `RenderSystem.initRenderer(int debugLevel, boolean debugSync)` |
| Frame timer | `DeltaTracker.Timer.advanceTime(long timeMillis, boolean advanceGameTime)` |
| Display update | `Window.updateDisplay()` |

The different positioned-tooltip argument orders are intentional. Screen callbacks use primitive arguments, not the newer KeyEvent/MouseButtonEvent records. Supplementary characters arrive as an integer code point at the keyboard callback and UTF-16 characters at the screen boundary; the Modern UI input queue preserves paired characters.

Tooltips use the original named ShaderInstance uniforms (`ModelViewMat`, `ProjMat`, and six `u_PushData` vec4 values). This branch does not use the later DynamicTransforms uniform buffer layout.

## Loader dependencies

The same per-loader product JAR can address both game versions, but its dependencies must match the running game:

| Game | Forge Config API Port for Fabric | Audited NeoForge |
| --- | --- | --- |
| 1.21 | 21.0.8 | 21.0.167 |
| 1.21.1 | 21.1.0 | 21.1.50; ScrollPanel also checked at 21.1.250 |

FCAP 21.0.8 declares Minecraft 1.21, while FCAP 21.1.0 declares Minecraft 1.21.1. Their 42 configuration/v4 API class member surfaces match. Do not install FCAP 21.1.0 in the 1.21 runtime. Fabric API 0.104.0+1.21.1 declares a range admitting both games. The six relevant NeoForge-patched Minecraft classes and ScrollPanel API were checked across the loader versions above.

## Framework ABI

Core 3.13 / Arc3D 2026.2 call some newer typed LWJGL methods absent from Minecraft's LWJGL 3.3.3. Narrow mixins redirect these calls to the real `Buffer` overloads. The Windows WndProc passes a null last-error buffer to both SetWindowLongPtr and SetWindowPos at construction and destruction; the shim uses LWJGL 3.3.3's matching overloads without that buffer. Four actual call sites were checked. This is a static Windows ABI check and does not claim Windows runtime acceptance.

## VulkanMod architecture

This game family predates Minecraft's GpuDevice API. The bridge uses actual VulkanMod image and texture-ID registry APIs instead. Fabric VulkanMod 0.6.7 uses `VkGlTexture` and `Queue.vkQueue()`; NeoForge Reforged 0.3.5 uses `GlTexture` and `Queue.queue()`. Their borrowed-image constructors also differ and are selected explicitly. Fabric 0.6.7 declares both 1.21 and 1.21.1 and requires Fabric Loader >=0.18.0. Reforged 0.3.5 declares only Minecraft [1.21.1,1.21.2) and NeoForge [21.1,21.2); it must not be forced into the 1.21 NeoForge matrix.

Canvas images remain owned by Arc3D. A borrowed registry entry must detach the image before deleting the texture ID. The Minecraft text engine uses VulkanMod-owned font images with CPU-backed atlas growth; it must not cast the Vulkan device or image to OpenGL types. Vulkan API/feature caps must describe the logical device actually enabled by VulkanMod, rather than all features advertised by the physical device.

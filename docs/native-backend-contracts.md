# Native Minecraft backend contracts

Minecraft 26.2 uses `com.mojang.blaze3d` GPU classes and GLFW input. The mod retains its public API, mod ID, configuration paths and Maven coordinates when its display name changes.

- The real Minecraft `KeyEvent` constructor is `(key, scancode, modifiers)`. `CharacterEvent` is `(codepoint)` (its modifier constructor parameter was removed) and `MouseButtonEvent` is `(x, y, MouseButtonInfo)`. Core editing receives only Shift, Control, Alt and Super bits; GLFW Caps Lock and Num Lock flags must not disable editing shortcuts.
- Tooltip uniforms are checked against Minecraft's actual `DynamicUniforms.Transform.write` serializer, including the shader fields' std140 offsets. The check uses distinct matrix, vector and color values to detect parameter reordering.
- MiSans UI weights are nine presets, rather than original OS/2 weights. The official Demibold file precedes Semibold; metadata from all 28 official font files is retained as a test fixture without bundling the fonts.
- Native Vulkan is Minecraft's own backend. It does not use the older VulkanMod ABI, submission semaphore patches or Reforged cleanup hooks.

## Native Vulkan ownership and shutdown

Minecraft owns its Vulkan instance, device, submission queue and swapchain. Modern UI owns its separate Arc3D allocator and copied feature chain, which it stores in `VulkanManager` without giving that manager ownership of Minecraft's instance or device.

A wrapped Arc3D image is non-owning: closing the fabricated Minecraft texture would incorrectly destroy the Arc3D image. Its native image view, however, must be closed. The bridge retains one image reference until a deferred callback after image-view destruction, in addition to each submitted frame's command-buffer reference.

`Minecraft.close` calls `GpuSurface.close` before `RenderSystem.shutdownRenderer`. Modern UI stops before the former call so that final command submission still has a valid swapchain. It stops and joins the UI thread, closes views, submits the native encoder, waits for the graphics queue, and drains its delayed destruction queue. Only then may it release its immediate context and close its own allocator and features. Live UI threads, outstanding context references or accidental ownership of Minecraft's instance/device stop this cleanup with a clear error.

The actual native `DestructionQueue` implements `AutoCloseable`. Its `close` drains buckets without permanently closing the queue, so Minecraft's later normal encoder destruction can safely drain it again. The bridge never destroys Minecraft's encoder or device itself.

The CPU contract check verifies the real Minecraft shutdown order and native queue methods. It does not replace hardware OpenGL/Vulkan testing.

## Audio focus gain

Minecraft includes the user MASTER setting in each channel's final gain. Its modern listener has no `setGain` method, and the `gainBySource` map is keyed by the actual sound category: writing only a MASTER map entry cannot attenuate ordinary channels.

The focus fade therefore multiplies Minecraft's calculated channel volume once in non-global mode. Global mode uses the OpenAL listener instead, with a channel multiplier of one. Switching back to non-global restores listener gain to one, refreshes existing channels, and preserves all user and external category gains. A successful audio library reload reapplies the current mode and multiplier even when the window's fade target did not change. Failed library initialization leaves the update pending. Minecraft's actual `Library.init` makes the calling thread's context current before creating AL capabilities; the bridge skips listener calls when no context exists.

The CPU audio contract check inspects the target Minecraft bytecode, mixer hook registration, single user MASTER combination, category-map use, active-channel refresh and OpenAL context initialization order. Hardware/runtime verification must additionally observe actual channel/listener gains through mode changes and device reload.

## Native Vulkan API and image handoff

Minecraft creates its native Vulkan instance for API 1.2 even on GPUs advertising newer versions. Arc3D's shader target is explicitly limited to SPIR-V 1.5. The bridge advertises enabled logical-device features rather than all physical-device support: 26.2 uses the backend's guaranteed required-feature subset, while 26.3 copies its retained enabled-feature set.

The actual native sampled-image descriptors specify `VK_IMAGE_LAYOUT_GENERAL`. Arc3D output is transitioned to GENERAL before Minecraft samples it, and the Arc3D mutable image state is returned as GENERAL after native rendering. SHADER_READ_ONLY_OPTIMAL is a different valid Vulkan layout, but it does not satisfy these native descriptors. This contract is separate from older VulkanMod image tracking.

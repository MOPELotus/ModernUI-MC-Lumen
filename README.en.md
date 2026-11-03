[CN](README.md) | [EN](README.en.md)

# ModernUI Lumen

**ModernUI Lumen** is **MOPELotus**'s independently maintained fork of [Modern UI for Minecraft](https://github.com/BloCamLimb/ModernUI-MC), originally created by **BloCamLimb**, and based on [ModernUI Framework](https://github.com/BloCamLimb/ModernUI). Upstream credits and licenses are retained. This is not an official upstream release.

Current mod version: **3.13.0.10**. Repository: **ModernUI-MC-Lumen**. In-game name: **ModernUI Lumen**, or **ModernUI Lumen (MiSans)** for the MiSans edition.

[Source](https://github.com/MOPELotus/ModernUI-MC-Lumen) · [Issues](https://github.com/MOPELotus/ModernUI-MC-Lumen/issues) · [Releases](https://github.com/MOPELotus/ModernUI-MC-Lumen/releases) · [Changelog](changelogs.en.md)

## Features

The mod hosts ModernUI interfaces and development APIs in Minecraft and enhances vanilla GUI text layout and rendering: TrueType/OpenType fonts, font fallback, anti-aliasing, SDF, Unicode line breaking, bidirectional text and color emoji. Actual glyph availability depends on the font and rendering path.

Options include background blur and animation, rounded/gradient tooltips, smooth scrolling, GUI scale, window modes, inactive/minimized frame-rate and volume fading, inventory pause, text undo/redo, chat emoji shortcodes, and local music playback with a spectrum. The zoom shortcut belongs to loaders providing that upstream extension; Fabric does not provide it.

[Standard and MiSans editions](docs/font-editions.en.md) share the Minecraft adaptations and fixes, with different font policies. Install one edition at a time and replace its JAR when switching. The `modernui` mod ID, resource namespace, Java packages, public API, Maven coordinates and `config/ModernUI/` paths retain compatibility.

## Maintained branches

This table describes maintenance scope, not completed runtime acceptance for every loader, backend and font edition. Consult the selected branch's metadata and compatibility notes before installing.

| Branch | Minecraft maintenance target | Status |
| --- | --- | --- |
| `1.21.1` | 1.21–1.21.1 | Maintained |
| `1.21.8` | 1.21.6–1.21.8 | Maintained |
| `1.21.9` | 1.21.9 | Maintained |
| `1.21.10` | 1.21.10 | Maintained |
| `1.21.11` | 1.21.11 | Maintained |
| `26.1` | 26.1–26.1.2 | Maintained |
| `26.2` | 26.2 | Maintained |
| `master` | 26.3 | Maintained |

The `1.21.8` branch covers 1.21.6, 1.21.7 and 1.21.8 together. Branches retain their own toolchains and dependencies; the maintenance range does not imply a single shared JAR for every listed version.

## Rendering backends and compatibility

Fabric and NeoForge provide OpenGL adaptations. Minecraft 26.2/26.3 use Minecraft's native Vulkan backend without VulkanMod. Select `--graphicsBackend VULKAN` or `--graphicsBackend OPENGL` in the launcher's **game arguments**, not JVM arguments. Earlier Vulkan adaptations require a compatible VulkanMod package for the exact Minecraft version and loader; missing packages are not treated as supported or tested combinations.

### NeoForge early loading window

For Minecraft 26.2 / FML 11.0.16 native Vulkan, set this top-level key in the instance's `config/fml.toml`, outside any TOML section, then save and restart:

```toml
earlyWindowControl = false
```

Create the file with that line if absent, or start once with OpenGL to generate it. The 26.2 early screen creates an OpenGL window that cannot be reused as a Vulkan surface (`GLFW_NO_API` / 65540). Minecraft 26.3 uses SDL, so the same GLFW explanation does not apply.

In the tested Linux / NVIDIA 580.178.04 / Tesla T10 environment, NeoForge **26.3.0.101 / FML 12.0.12** also requires disabling this window for OpenGL and native Vulkan. With it enabled, OpenGL can crash inside the NVIDIA driver at startup, and Vulkan leaves the early screen's `ScreenSize UBO` and `shared_error` GPU buffers unclosed at shutdown. Both were reproduced without ModernUI. The precise OpenGL cause and behavior on other drivers remain unconfirmed. Disabling the window is a compatibility condition for that tested configuration, not proof that all features or other devices have passed.

### Remove Reloading Screen (26.3 / Fabric)

A player reported a startup crash with ModernUI and Remove Reloading Screen when the latter uses `hideOverlays = "ALL"`, reporting missing shaders that exist in the JAR. The reported workaround is to edit the existing `[global]` entry in **Remove Reloading Screen's configuration** and restart:

```toml
[global]
    hideOverlays = "RELOADING"
```

This preserves the initial loading overlay and hides subsequent resource reload overlays. It is a configuration workaround from a player's client investigation. The suspected initialization-order cause, a code fix, and other versions/loaders have not been verified here.

## Building

Use the selected branch's required JDK. `master` (26.3) uses **JDK 25** and builds Fabric and NeoForge by default. It currently pins NeoForge **26.3.0.101**; older 26.3 beta configuration APIs are incompatible. The Gradle wrapper and `gradle.properties` are authoritative.

```sh
./gradlew clean build -Pfont_variant=standard
# Collect the standard artifacts before cleaning.
./gradlew clean build -Pfont_variant=misans
```

Final JARs are in each loader's `build/libs/`:

- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal.jar`
- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal-misans.jar`

CI builds both editions and uploads `modernui-lumen-release-jars`. Normal branch pushes do not publish a Release. Pushing a tag, or manually running the workflow with a release tag, creates or updates a Release. Gradle project/task names and Maven coordinates remain compatible. This fork does not claim that its new version is published to upstream Maven.

## Validation limits

Ports check the actual target APIs for tooltip parameters and uniform layout, keyboard/mouse input, window transitions and GPU resource ownership. Minecraft 26.3 uses SDL and RenderPearl; older GLFW and VulkanMod interfaces cannot be reused without adaptation.

Compilation and CPU checks do not replace actual page operations, screenshots, renderer logs and normal-exit checks on a physical GPU. Matrix results are tracked separately for exact JAR, loader, Minecraft version and backend. This document does not claim that the whole matrix has passed.

## License and credits

Modern UI for Minecraft: Copyright (C) 2019–2026 BloCamLimb et al., licensed under [LGPL-3.0-or-later](LICENSE). Fork maintenance: MOPELotus.

Additional font assets include Adobe [Source Han Sans](https://github.com/adobe-fonts/source-han-sans), JetBrains [JetBrains Mono](https://www.jetbrains.com/lp/mono/) and RSMS [Inter](https://github.com/rsms/inter), retaining their respective OFL licenses. The bundled modified Inter retains the original license and font copyright notice. MiSans has its own official license; read its terms before downloading and using it.

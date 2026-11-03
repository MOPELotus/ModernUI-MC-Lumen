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

This branch targets Minecraft **1.21–1.21.1** with Fabric and NeoForge OpenGL adaptations. Vulkan requires a VulkanMod package matching the exact Minecraft version and loader; missing packages are not treated as supported or tested combinations. This branch uses legacy GLFW and immediate GUI APIs. Minecraft 26.3 SDL, RenderPearl and native Vulkan interfaces cannot be applied directly.

## Building

This branch uses **JDK 21** and builds Fabric and NeoForge by default. The Gradle wrapper and `gradle.properties` define its exact Minecraft, loader and dependency versions. Other branches retain their own toolchains.

```sh
./gradlew clean build -Pfont_variant=standard
# Collect the standard artifacts before cleaning.
./gradlew clean build -Pfont_variant=misans
```

Final JARs are in each loader's `build/libs/`:

- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal.jar`
- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal-misans.jar`

This branch includes the **Build and Release** workflow (JDK 21), building both editions and uploading `modernui-lumen-release-jars`. Automatic branch push/PR triggers target only `master`, so pushing `1.21.1` does not itself start a build. Select this branch when running the workflow manually in Actions. Pushing a tag, or supplying a release tag manually, creates or updates a Release; leaving it empty only builds artifacts. Gradle project/task names and Maven coordinates remain compatible. This fork does not claim that its new version is published to upstream Maven.

## Validation limits

Ports check the actual 1.21 and 1.21.1 APIs for tooltip parameter order, keyboard/mouse callbacks, window transitions and GPU resource ownership. Sharing a branch or JAR does not replace exact-version runtime verification.

Compilation and CPU checks do not replace actual page operations, screenshots, renderer logs and normal-exit checks on a physical GPU. Matrix results are tracked separately for exact JAR, loader, Minecraft version and backend. This document does not claim that the whole matrix has passed.

## License and credits

Modern UI for Minecraft: Copyright (C) 2019–2026 BloCamLimb et al., licensed under [LGPL-3.0-or-later](LICENSE). Fork maintenance: MOPELotus.

Additional font assets include Adobe [Source Han Sans](https://github.com/adobe-fonts/source-han-sans), JetBrains [JetBrains Mono](https://www.jetbrains.com/lp/mono/) and RSMS [Inter](https://github.com/rsms/inter), retaining their respective OFL licenses. The bundled modified Inter retains the original license and font copyright notice. MiSans has its own official license; read its terms before downloading and using it.

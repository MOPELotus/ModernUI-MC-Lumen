[CN](README.md) | [EN](README.en.md)

# ModernUI Lumen

**ModernUI Lumen** is **MOPELotus**’s independently maintained fork of [Modern UI for Minecraft](https://github.com/BloCamLimb/ModernUI-MC), originally created by **BloCamLimb** and based on the [ModernUI framework](https://github.com/BloCamLimb/ModernUI). It retains upstream credits and licenses and is not an official upstream release.

Current mod version: **3.13.0.10**. Choose either the standard edition or **ModernUI Lumen (MiSans)**.

[Source](https://github.com/MOPELotus/ModernUI-MC-Lumen) · [Issues](https://github.com/MOPELotus/ModernUI-MC-Lumen/issues) · [Releases](https://github.com/MOPELotus/ModernUI-MC-Lumen/releases) · [Font editions](docs/font-editions.en.md) · [Changelog](changelogs.en.md)

## Features

- Unicode layout, bidirectional text, font fallback, TrueType/OpenType font previews and reloads, color emoji and chat shortcodes.
- Antialiasing and SDF text rendering, font hinting, device-pixel font sizes and asynchronous layout.
- Smooth scrolling, text-field undo/redo and Unicode word navigation.
- Configurable background colors, gradients, blur and fade animations; rounded tooltips, title separators, centering, RTL and pixel positioning.
- Window modes, inactive/minimized frame-rate limits and volume fading, single-player inventory pause and local music playback.
- C zoom on branches/loaders that provide the upstream zoom extension; Fabric does not provide it.
- Native ModernUI interfaces and text APIs for other mods, plus enhanced text rendering for vanilla GUI.

The developer **Take UI screenshot (Y)** feature supports OpenGL only and is not applicable on Vulkan. This does not mean that the entire UI or vanilla game screenshots are unavailable.

## Installation and fonts

Install the JAR matching your Minecraft version and loader, using only one font edition at a time. Both editions retain mod ID `modernui`; the resource namespace, Java packages, API, Maven coordinates and `config/ModernUI/` paths remain compatible.

The standard edition offers bundled fonts and configurable font selection. The MiSans edition offers a fixed font collection, weight presets and an installer for missing fonts. Downloads start only when the user chooses them in the installer. See [font editions](docs/font-editions.en.md) for behavior, licenses and configuration paths.

## Maintained branches

| Branch | Minecraft maintenance target | Status |
| --- | --- | --- |
| `1.21.1` | 1.21–1.21.1 | Maintained |
| `1.21.8` | 1.21.6–1.21.8 | Maintained |
| `1.21.9` | 1.21.9 | Maintained |
| `1.21.10` | 1.21.10 | Maintained |
| `1.21.11` | 1.21.11 | Maintained |
| `26.1` | 26.1 family | Maintained |
| `26.2` | 26.2 | Maintained |
| `master` | 26.3 | Maintained |

A maintenance target does not imply a single JAR for every listed Minecraft version or completed acceptance for every loader/backend combination. Check the selected branch’s mod metadata before installing. Fabric and NeoForge are maintained; VulkanMod package availability must be checked per version.

## Known compatibility conditions

### NeoForge early loading window

Minecraft 26.2 native Vulkan is incompatible with the FML 11.0.16 early window: its OpenGL window cannot be reused as a Vulkan surface (`GLFW_NO_API` / 65540). Set this top-level key in the selected instance’s `config/fml.toml`, save and restart:

```toml
earlyWindowControl = false
```

Keep the key outside every TOML section. If the file does not exist, create it with that line or launch once with OpenGL and edit the generated file.

26.3 / FML 12 uses an SDL handoff, so the GLFW explanation above does not apply. However, the tested **NeoForge 26.3.0.101 / FML 12.0.12, Linux, Tesla T10, NVIDIA 580.178.04** environment also requires the early window disabled for OpenGL and native Vulkan. With it enabled, OpenGL can crash in the NVIDIA driver at startup; Vulkan leaves the early screen’s `ScreenSize UBO` and `shared_error` GPU buffers unclosed at shutdown. Both were reproduced without ModernUI installed. The precise upstream cause of the OpenGL crash and behavior on other drivers remain unconfirmed.

With the early window disabled, the ModernUI native Vulkan core UI flow, normal exit and zero Vulkan validation errors were verified in that environment. This does not establish that every feature or other driver/configuration passed testing.

Native Vulkan on 26.2/26.3 does not require VulkanMod. Use `--graphicsBackend VULKAN` in the launcher’s **game arguments**, or `--graphicsBackend OPENGL` to select OpenGL. These are not JVM arguments.

### Remove Reloading Screen (26.3 / Fabric)

A player reported a missing-shader startup crash with Remove Reloading Screen configured as `hideOverlays = "ALL"`, even though the shader files exist in the JAR. The reported workaround is to edit the existing `[global]` setting in that mod’s configuration and restart:

```toml
[global]
    hideOverlays = "RELOADING"
```

This keeps the initial loading overlay and hides subsequent resource-reload overlays. It is not a ModernUI setting. An initialization-order explanation remains a hypothesis; other versions/loaders were not verified.

## Building

This branch uses **JDK 25**. Its Gradle configuration defines the toolchain and dependencies; Fabric and NeoForge are the default platforms:

```sh
./gradlew clean build -Pfont_variant=standard
# Collect standard JARs before cleaning for the MiSans build.
./gradlew clean build -Pfont_variant=misans
```

Final JARs are in each loader’s `build/libs/`:

- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal.jar`
- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal-misans.jar`

See [font editions and builds](docs/font-editions.en.md). Compatible Gradle project/task names remain `ModernUI-Fabric`, `ModernUI-NeoForge` and `ModernUI-Forge`; retaining historical Forge code does not add a Forge maintenance target. This version does not imply publication to upstream’s Maven repository.

This branch has no GitHub Actions build/release workflow; the commands above build locally.

This branch: Minecraft 26.1 family (build target 26.1.2).

## License and credits

Modern UI for Minecraft: Copyright (C) 2019–2026 BloCamLimb et al., under [LGPL-3.0-or-later](https://www.gnu.org/licenses/lgpl-3.0.en.html). Fork maintenance: MOPELotus.

Additional fonts: [Source Han Sans](https://github.com/adobe-fonts/source-han-sans) (Adobe), [JetBrains Mono](https://www.jetbrains.com/lp/mono/) (JetBrains) and [Inter](https://github.com/rsms/inter) (RSMS), under OFL-1.1. The modified bundled Inter retains its original license and copyright notice. See [font editions](docs/font-editions.en.md) for MiSans licensing and downloads. Repository license and font-authorization files retain their original text.

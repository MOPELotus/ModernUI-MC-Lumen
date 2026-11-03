[CN](README.md) | [EN](README.en.md)

# ModernUI Lumen

**ModernUI Lumen** is **MOPELotus**’s independently maintained fork of [Modern UI for Minecraft](https://github.com/BloCamLimb/ModernUI-MC), originally created by **BloCamLimb**, and based on the [ModernUI framework](https://github.com/BloCamLimb/ModernUI). Upstream credits and licenses are retained. This is not an official upstream release.

The current mod version is **3.13.0.10**. The standard edition is named **ModernUI Lumen**; the MiSans edition is named **ModernUI Lumen (MiSans)**.

[Source](https://github.com/MOPELotus/ModernUI-MC-Lumen) · [Issues](https://github.com/MOPELotus/ModernUI-MC-Lumen/issues) · [Releases](https://github.com/MOPELotus/ModernUI-MC-Lumen/releases) · [Changelog](changelogs.en.md)

## Features

- ModernUI screens, widgets, themes and APIs for Minecraft mods.
- Unicode text layout, font fallback, anti-aliasing, configurable shadows and size, and bidirectional text handling for vanilla GUI text.
- Rounded or square tooltips, title separators and centering, border/background colors and long-content movement.
- Screen blur, fade-in, window modes, framerate limits while inactive, and volume settings while inactive or minimized.
- Unicode editing, undo/redo, chat emoji shortcodes, local music playback, seeking, volume controls and a spectrum view.
- A GUI-scale slider, smooth list scrolling, a load-completion sound, and an option to pause single-player while inventory is open.
- C-key zoom on NeoForge; Fabric does not provide this feature.

Color emoji in Minecraft’s native GUI are distinct from emoji rendering in Core controls. Emoji rendering in Core TextView, Canvas and input controls remains limited by upstream implementation. Automatic MARQUEE in Core TextView is also unfinished; externally driven Canvas and native GUI scrolling depend on their draw calls.

## Installation and font editions

Install one final JAR matching your Minecraft version, loader and font edition. Both editions use mod ID `modernui`; replace the existing JAR when switching editions.

- **Standard** uses defaults including Inter and Source Han Sans, with preferred-font, fallback-font and font-registration settings.
- **MiSans** uses MiSans, MiSans Latin, MiSans TC and MiSans L3. Missing fonts open the license/install flow; downloading is a user action. Nine weight presets are available.

See [font editions](docs/font-editions.en.md) for configuration, installation and actual weight mappings. The mod ID, resource namespace, Java packages, API and existing `config/ModernUI/` paths remain compatible.

## Maintained branches

All eight branches below are maintained. Maintenance targets are not a claim that every version, loader and backend combination has passed runtime acceptance. Use the corresponding branch metadata for exact installation ranges.

| Branch | Minecraft maintenance range | Status |
| --- | --- | --- |
| `1.21.1` | 1.21–1.21.1 | Maintained |
| `1.21.8` | 1.21.6–1.21.8 | Maintained |
| `1.21.9` | 1.21.9 | Maintained |
| `1.21.10` | 1.21.10 | Maintained |
| `1.21.11` | 1.21.11 | Maintained |
| `26.1` | 26.1–26.1.2 | Maintained |
| `26.2` | 26.2 | Maintained |
| `master` | 26.3 | Maintained |

## Loaders and backends for 1.21.9–1.21.11

Each of these three branches builds Fabric and NeoForge artifacts for its own Minecraft version, in standard and MiSans editions. Do not use an artifact for another Minecraft version.

| Minecraft | Fabric OpenGL | NeoForge OpenGL | Fabric Vulkan dependency | NeoForge Vulkan dependency |
| --- | --- | --- | --- | --- |
| 1.21.9 | Integration target | Integration target | VulkanMod 0.6.6 | No matching release |
| 1.21.10 | Integration target | Integration target | VulkanMod 0.6.6 | No matching release |
| 1.21.11 | Integration target | Integration target | VulkanMod 0.6.8 | VulkanMod Reforged 0.3.0-ALPHA |

The integration uses the external VulkanMod backend and its Vulkan 1.2 device. The 0.6.6 package metadata supports both 1.21.9 and 1.21.10 despite the `1.21.10` filename. Combinations lacking a matching dependency package are outside the runnable matrix. This table describes targets and dependencies, not a completed runtime matrix.

## Building

This branch targets **Minecraft 1.21.9** and uses **JDK 21**, building Fabric and NeoForge by default. Give 1.21.9 and 1.21.10 separate `GRADLE_USER_HOME` directories because their Loom layered-mapping caches must be isolated. Per-version caches are recommended for all branches.

```sh
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
mc_version=$(sed -n 's/^minecraft_version=//p' gradle.properties)
export GRADLE_USER_HOME="$HOME/.gradle-modernui-$mc_version"
./gradlew clean build -Pfont_variant=standard
# Collect the standard artifacts before cleaning for the other edition.
./gradlew clean build -Pfont_variant=misans
```

Final artifacts are in `fabric/build/libs` and `neoforge/build/libs`:

- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal.jar`
- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal-misans.jar`

Install the final remapped artifacts rather than `-shadow.jar` or development JARs. CI uploads `modernui-lumen-release-jars`; ordinary branch pushes do not publish a release. Tag pushes, or manually running the workflow with a `tag` value, create or update a GitHub Release.

Stable Gradle project/task names such as `ModernUI-Fabric` and `ModernUI-NeoForge`, and Maven coordinates remain compatible. The historical Forge project name does not imply maintained Forge artifacts on this branch. This fork does not claim that its new version is published to upstream Maven. Use the corresponding branch source for parameters, uniform layouts and check tasks. Builds and CPU checks do not replace physical-GPU UI and interaction acceptance.

## License and attribution

Modern UI for Minecraft: Copyright (C) 2019–2026 BloCamLimb et al., licensed under [LGPL-3.0-or-later](https://www.gnu.org/licenses/lgpl-3.0.en.html). The repository’s original license texts and resource notices apply to their respective code and assets.

Additional fonts include Adobe’s [Source Han Sans](https://github.com/adobe-fonts/source-han-sans), JetBrains’ [JetBrains Mono](https://www.jetbrains.com/lp/mono/) and RSMS’ [Inter](https://github.com/rsms/inter), under SIL OFL. The bundled modified Inter retains its original license and copyright notice. See [font editions](docs/font-editions.en.md) for MiSans licensing and sources.

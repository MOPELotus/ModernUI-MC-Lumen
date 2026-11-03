[CN](README.md) | [EN](README.en.md)

# ModernUI Lumen

**ModernUI Lumen** is **MOPELotus**’s independently maintained fork of [Modern UI for Minecraft](https://github.com/BloCamLimb/ModernUI-MC), originally created by **BloCamLimb**, based on the [ModernUI framework](https://github.com/BloCamLimb/ModernUI). It retains upstream credits and licenses and is not an official upstream release. The current mod version is **3.13.0.10**.

[Source](https://github.com/MOPELotus/ModernUI-MC-Lumen) · [Issues](https://github.com/MOPELotus/ModernUI-MC-Lumen/issues) · [Releases](https://github.com/MOPELotus/ModernUI-MC-Lumen/releases) · [Changelog](changelogs.en.md)

## Features

The mod provides a ModernUI application environment and development API inside Minecraft, together with text layout and rendering extensions for vanilla GUI. Features include font selection and fallback, TrueType/OpenType loading, antialiased text, bidirectional text and line breaking, editing undo/redo, smooth scrolling, configurable tooltips, background blur, window modes, inactive-window frame and volume controls, local music playback and spectrum display.

Marquee adaptations cover floating-point movement, sampling and clipping. Minecraft’s text engine and Core widgets use different glyph paths: vanilla color emoji rendering does not establish color emoji or IME preedit support in every Core widget. NeoForge provides the C-key zoom extension; Fabric does not. Compatibility with other rendering mods requires checks for the exact version, loader and backend.

## Font editions and installation

- **Standard**: defaults to `Inter Frozen Medium`, with fallback including `Source Han Sans CN Medium`. Preferred, fallback and registered font lists are configurable. Installing MiSans is unnecessary.
- **MiSans**: displayed as `ModernUI Lumen (MiSans)`, with MiSans, MiSans L3, MiSans Latin and MiSans TC. Missing fonts open a setup interface with the license link, an explicit Download action, progress, retry and exit/restart controls. Four Xiaomi packages supply 28 TTF files; the installer also saves the license PDF and a download record.

Both editions use the mod ID `modernui`. **Install only one edition at a time**, replacing the JAR when switching. Resource namespaces, Java packages, APIs and `config/ModernUI/` paths remain compatible. See [font editions](docs/font-editions.en.md) for configuration, weight mappings and downloading behavior.

## Maintained branches

All eight families below are maintained. Maintenance scope does not mean every Minecraft/loader/backend combination has completed runtime acceptance. Functional, visual and physical-GPU checks are still being performed per combination. Use the matching branch’s mod metadata when installing.

| Branch | Minecraft maintenance range | Status |
| --- | --- | --- |
| `1.21.1` | 1.21–1.21.1 | Maintained |
| `1.21.8` | 1.21.6–1.21.8 | Maintained |
| `1.21.9` | 1.21.9 | Maintained |
| `1.21.10` | 1.21.10 | Maintained |
| `1.21.11` | 1.21.11 | Maintained |
| `26.1` | 26.1 family | Maintained |
| `26.2` | 26.2 | Maintained |
| `master` | 26.3 | Maintained |

`master` tracks 26.3. Branches keep their own toolchains and loader dependencies; sharing a JAR across families is not guaranteed.

## This branch: Minecraft 1.21.10

Fabric and NeoForge each have standard and MiSans builds, producing four artifacts. OpenGL is a target for both loaders. Fabric’s Vulkan combination uses external VulkanMod 0.6.6, whose package metadata includes 1.21.9 and 1.21.10. The currently recorded NeoForge 1.21.10 Vulkan combination lacks a matching published package and is recorded as unavailable, not tested successfully.

Vulkan integration uses the external backend’s device. A successful build, process launch or detected GPU alone does not establish acceptance. Graphics validation must confirm the physical device in client logs and operate and inspect the target pages. Tooltip, input-event and uniform interfaces still require version-specific checks.

## Building

Use **JDK 21** and this checkout’s Gradle wrapper and dependencies. Give 1.21.9 and 1.21.10 separate `GRADLE_USER_HOME` directories to avoid sharing Loom layered-mapping caches.

```sh
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="$HOME/.gradle-modernui-1.21.10"
./gradlew clean build -Pfont_variant=standard
# Collect the final standard JARs before the next clean.
./gradlew clean build -Pfont_variant=misans
```

Final remapped artifacts are under `fabric/build/libs` and `neoforge/build/libs`:

- Standard: `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal.jar`
- MiSans: `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal-misans.jar`

Install final artifacts rather than `-shadow.jar` or development JARs. Omitting `font_variant` builds standard. This branch’s workflow builds on pushes to `1.21.10`, pull requests targeting it, manual dispatch and tag pushes. CI builds both editions and uploads `modernui-lumen-release-jars`; a normal branch push only builds artifacts. A tag push or a manual workflow with a nonempty release tag creates or updates a GitHub Release.

Gradle project names `ModernUI-Fabric`, `ModernUI-NeoForge`, `ModernUI-Forge` and Maven coordinates remain compatible. A project name does not imply every loader is enabled here or that this fork is published to upstream’s Maven repository.

## Credits and licenses

- Modern UI for Minecraft: Copyright (C) 2019–2026 BloCamLimb et al., [LGPL-3.0-or-later](https://www.gnu.org/licenses/lgpl-3.0.en.html).
- [Source Han Sans](https://github.com/adobe-fonts/source-han-sans): Adobe, OFL-1.1.
- [JetBrains Mono](https://www.jetbrains.com/lp/mono/): JetBrains, OFL-1.1.
- [Inter](https://github.com/rsms/inter): RSMS, OFL-1.1. The included modified Inter retains the original license and copyright notices.
- MiSans is copyrighted by Xiaomi and governed by its Font License Agreement. The MiSans edition provides the official license link and download workflow; MiSans is not treated as a freely relicensable repository asset.

The repository’s original license texts remain applicable. This documentation does not replace them.

[CN](README.md) | [EN](README.en.md)

# ModernUI Lumen

**ModernUI Lumen** is **MOPELotus**'s independently maintained fork of [Modern UI for Minecraft](https://github.com/BloCamLimb/ModernUI-MC), originally created by **BloCamLimb**, based on the [ModernUI framework](https://github.com/BloCamLimb/ModernUI). Original credits and licenses are retained. Current mod version: **3.13.0.10**.

[Source](https://github.com/MOPELotus/ModernUI-MC-Lumen) · [Issues](https://github.com/MOPELotus/ModernUI-MC-Lumen/issues) · [Downloads](https://github.com/MOPELotus/ModernUI-MC-Lumen/releases) · [Font editions](docs/font-editions.en.md) · [Changelog](changelogs.en.md)

## Installation and versions

This branch targets Minecraft 1.21.11 with Fabric and NeoForge OpenGL integration, Fabric VulkanMod 0.6.8 and NeoForge VulkanMod Reforged 0.3.0-ALPHA integration. Vulkan uses the external VulkanMod backend and its Vulkan 1.2 device. The neighboring 1.21.9 and 1.21.10 branches target Fabric VulkanMod 0.6.6: its metadata covers both games despite the 1.21.10 filename. Those two games have no matching NeoForge Vulkan package.

The standard edition displays **ModernUI Lumen**; the MiSans edition displays **ModernUI Lumen (MiSans)**. Choose the matching game and loader JAR and install one font edition at a time. The `modernui` mod ID, resource namespace, Java packages, API, Maven coordinates and `config/ModernUI/` paths remain compatible.

These are this fork's maintenance targets. Maintenance status does not imply completed physical-client acceptance for every loader, edition and rendering backend combination.

| Branch | Minecraft | Status |
| --- | --- | --- |
| `1.21.1` | 1.21–1.21.1 | Maintained |
| `1.21.8` | 1.21.6–1.21.8 | Maintained |
| `1.21.9` | 1.21.9 | Maintained |
| `1.21.10` | 1.21.10 | Maintained |
| `1.21.11` | 1.21.11 | Maintained |
| `26.1` | 26.1 / 26.1.1 / 26.1.2 | Maintained |
| `26.2` | 26.2 | Maintained |
| `master` | 26.3 | Maintained |

`master` targets 26.3. Each maintenance branch retains its own build toolchain and loader dependencies; consult that branch's package metadata for compatibility ranges.

## Features

This mod provides the ModernUI runtime and modding API inside Minecraft, allowing ModernUI applications to run natively. Its text engine also enhances vanilla GUI and mods using vanilla GUI. Applications can run independently of Minecraft or integrate through this mod; developers can combine ModernUI and vanilla interfaces.

The text system supports:

- Live TrueType/OpenType font preview, reload and font fallback.
- FreeType hinting, antialiasing, 2D/3D SDF and batched text drawing, with glyph sizes computed in device space.
- Noto Color Emoji, Unicode 16.0 emoji, and Discord, Slack, GitHub, IamCal and JoyPixels chat shortcodes.
- Bidirectional-text heuristics, configurable shadows and raw font size, Unicode line breaking and CSS line-break / word-break.
- Asynchronous Unicode layout, glyph rectangle packing and one-byte alpha masks, reducing rendering cost and allocations for GUI, signs and glowing signs.
- Minecraft JSON font definitions (bitmap and TTF), and version-specific compatibility paths for OptiFine, Sodium/Rubidium, Iris/Oculus and other mods.

Interface and gameplay features include:

- Background colors, fade-in and Gaussian blur; borderless fullscreen and borderless maximized window modes.
- Inactive/minimized frame limits and master-volume fading; single-player pause while inventory is open.
- GUI scale slider and hints, main-menu notification sound, smooth scrolling in vanilla lists and Forge scroll panels.
- Text-field undo/redo and Unicode word boundaries; local music playback, seeking and spectrum.
- C-key zoom on branches/loaders providing the upstream zoom extension; Fabric does not provide this feature.
- Rounded or normal antialiased tooltip borders, title break/spacing, centered titles and RTL, pixel-grid positioning, gradient and animated border/background colors.
- Fractional motion, glyph clipping edges and cache stability adaptations for native and Canvas text.

Parameter order, uniform byte layouts, input and rendering adaptations are maintained for each game version. Builds and CPU checks do not replace actual client-page, interaction and physical-GPU rendering acceptance.

## Known compatibility issue

A player report on Minecraft 26.3 / Fabric found that Remove Reloading Screen with `hideOverlays = "ALL"` can crash with missing ModernUI shader errors despite those files being present in the JAR. Change the existing entry under `[global]` in **that mod's configuration** and restart:

```toml
[global]
    hideOverlays = "RELOADING"
```

This keeps the initial loading screen while hiding later resource-reload screens. It is a player-tested configuration workaround; the suspected initialization-order cause remains unconfirmed. The report covers 26.3 / Fabric.

## Building

This branch uses **JDK 21**; `master` for 26.3 uses **JDK 25**. The selected checkout defines its build tools and dependencies. Default loaders are Fabric and NeoForge.

```sh
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
mc_version=$(sed -n 's/^minecraft_version=//p' gradle.properties)
export GRADLE_USER_HOME="$HOME/.gradle-modernui-$mc_version"
./gradlew clean build -Pfont_variant=standard
# Collect standard JARs before cleaning for the other edition.
./gradlew clean build -Pfont_variant=misans
```

Use separate Gradle caches for different game versions, especially the Loom layered mappings for 1.21.9 and 1.21.10. Final files are in each loader's `build/libs/`:

- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal.jar`
- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal-misans.jar`

Install the final universal JARs. CI builds both editions and uploads `modernui-lumen-release-jars`; ordinary branch pushes do not publish a Release. A tag push, or a manual workflow run with a nonempty `tag`, creates or updates the corresponding GitHub Release. Stable Gradle project names `ModernUI-Fabric`, `ModernUI-NeoForge` and `ModernUI-Forge` remain compatible. This fork does not claim publication in the upstream Maven repository.

## License and credits

* Modern UI for Minecraft
  - Copyright (C) 2019-2026 BloCamLimb et al.
  - [![License](https://img.shields.io/badge/License-LGPL--3.0--or--later-blue.svg?style=flat-square)](https://www.gnu.org/licenses/lgpl-3.0.en.html)
* Additional Assets
  - [source-han-sans](https://github.com/adobe-fonts/source-han-sans) by Adobe, licensed under the OFL-1.1
  - [jetbrains-mono](https://www.jetbrains.com/lp/mono/) by JetBrains, licensed under the OFL-1.1
  - [inter](https://github.com/rsms/inter) by RSMS, licensed under the OFL-1.1
    + Modern UI for Minecraft contains a modified version of Inter font family, the original license and
      copyright notice were retained in the font file

## Historical upstream screenshots

These show upstream features and are not acceptance evidence for this fork's current version.

Center Screen  
![2024-03-30_16.17.11.png](https://cdn.modrinth.com/data/3sjzyvGR/images/2571f7372b1f9bbb116c118f29a93255338f4e41.png)
New Tooltip  
![new tooltip.png](https://s2.loli.net/2024/03/30/VhyoFPAD2Js1HWO.png)
Markdown  
![markdown](https://cdn.modrinth.com/data/3sjzyvGR/images/989a77ba61c62ff580a30dcf158e391080b949bd.png)  
Enhanced Texts for Minecraft Vanilla  
![fast text](https://cdn.modrinth.com/data/3sjzyvGR/images/d27f5d77555fd3f45392f5b8eb28efcb80f0b677.png)
![new4](https://s2.loli.net/2022/03/06/TM5dVKnpqNvDiJH.png)  
Navigation  
![new5](https://s2.loli.net/2022/03/06/hwAoHTgZNWBvEdq.png)  
Graphics  
![new3.gif](https://i.loli.net/2021/09/27/yNsL98XtpKP7UVA.gif)  
Audio visualization  
![new2](https://i.loli.net/2021/09/24/TJjyzd6oOf5pPcq.png)  
Out-of-date widgets  
![a](https://i.loli.net/2020/05/15/fYAow29d4JtqaGu.png)
![b](https://i.loli.net/2020/04/10/LDBFc1qo5wtnS8u.png)

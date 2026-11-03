# ModernUI Lumen

**ModernUI Lumen** is MOPELotus’s independently maintained fork of [Modern UI for Minecraft](https://github.com/BloCamLimb/ModernUI-MC), originally created by **BloCamLimb**. It is based on the [ModernUI framework](https://github.com/BloCamLimb/ModernUI) and retains the upstream authors’ credits and licenses. This fork is not an official upstream release.

本 Fork 由 **MOPELotus** 维护，原作者为 **BloCamLimb**。当前模组版本为 **3.13.0.10**。游戏内显示名为 **ModernUI Lumen**；MiSans 版显示为 **ModernUI Lumen (MiSans)**。

[Source](https://github.com/MOPELotus/ModernUI-MC-Lumen) · [Issues](https://github.com/MOPELotus/ModernUI-MC-Lumen/issues) · [Releases](https://github.com/MOPELotus/ModernUI-MC-Lumen/releases) · [普通版 / MiSans 版说明](docs/font-editions.md)

The mod ID `modernui`, resource namespace, Java packages, API and existing `config/ModernUI/` paths remain unchanged for compatibility. Install one edition at a time, replacing the original JAR.

### Description
This fork builds on **Modern UI for Minecraft**, a Minecraft mod based on
[ModernUI Framework](https://github.com/BloCamLimb/ModernUI) (a cross-platform framework to
build rich desktop applications on top of JDK) and its extensions.  

This project provides ModernUI bootstrap program in Minecraft environment, allowing ModernUI-based applications
to run *natively* in Minecraft. It also provides Modding API based on Forge/NeoForge/Fabric
to make powerful Graphical User Interface in Minecraft.

This Mod also includes a powerful text layout engine and text rendering system designed for Minecraft.
This engine allows Minecraft and Mods based on Vanilla GUI system to enjoy a modern text system similar
to ModernUI framework without modifying their code. It provides appropriate methods for processing Unicode
text and gives you more readable text in any scale, in 2D/3D. In details:
* Real-time preview and reload TrueType/OpenType fonts
* A better font fallback implementation
* Anti-aliasing text and FreeType font hinting
* Use improved SDF text rendering in 2D/3D (also use batch rendering)
* Compute exact font size in device space for native glyph rendering
* Use Google Noto Color Emoji and support all the Unicode 16.0 Emoji
* Configurable bidirectional text heuristic algorithm
* Configurable text shadow and raw font size
* Unicode line breaking and CSS line-break & word-break
* Fast, exact and asynchronous Unicode text layout computation
* Faster and more memory efficient rectangle packing algorithm for glyphs
* Use real alpha mask texture (1 byte-per-pixel, whereas Minecraft is 4 bpp)
* Many optimizations for text rendering, multiplying the performance of
  GUI text rendering, Sign/Glowing Sign text rendering, greatly improving framerate,
  significantly reducing object allocations and lowering GC pressure.
* Compatible with OptiFine, Sodium (Rubidium), Iris (Oculus) and many mods
* Compatible with Minecraft's JSON font definition (bitmap fonts, TTF fonts)

Additionally, this Mod provides many utilities which improve game performance and gaming experience. Currently, we have:
* Changing screen background color
* Gaussian blur to screen backdrop image
* Fade-in animation to screen background
* More window modes: Fullscreen (borderless), Maximized (borderless)
* Framerate limit and master volume fading on window inactive (out of focus) and minimized
* Pausing single-player game when Inventory is open
* Changing GUI scale option to Slider and providing hint text
* Playing a "Ding" sound when Minecraft loads and reaches the Main Menu
* Enabling smooth scrolling in Vanilla's Selection List and Forge's Scroll Panel
* Pressing "C" to zoom on branches/loaders that provide the upstream zoom extension (not Fabric)
* Undo/Redo and Unicode word iterator for all text fields
* Playing local music, allowing to seek and view spectrum
* Support Discord/Slack/GitHub/IamCal/JoyPixels emoji shortcodes in Chatting
* A fancy tooltip style
  + Choose rounded border or normal border (with anti-aliasing)
  + Add title break and control title line spacing
  + Center the title line, support RTL layout direction
  + Exactly position the tooltip to pixel grid (smooth movement)
  + Change background color and border color (with gradient and animation)

You can think of this project as a bridge/service layer, along with several improvements and extensions specifically
made for Minecraft. Applications (or program fragments) you develop with ModernUI can run independently of Minecraft,
or inside Minecraft — via this Mod but without any code changes. Other Minecraft Mods based on Minecraft Vanilla
can also benefit from this Mod.

If you want to develop mods — especially ones with feature-rich interfaces — it's recommended to build on the
**ModernUI framework** (instead of just putting ModernUI-MC in your mod list). You may also implement a hybrid UI:
part of it based on ModernUI, and part of it on the Vanilla GUI system (which will be enhanced by ModernUI-MC,
though these enhancements can be disabled).

### Minecraft 26.1 branch

This branch targets Minecraft 26.1, 26.1.1 and 26.1.2 with Fabric or NeoForge, using Java 25.
The shared source uses the actual 26.1 GLFW input and GPU facade APIs. Vulkan rendering uses
an optional compatible Fabric VulkanMod build; these Minecraft releases do not provide the
native Vulkan backend introduced in 26.2. NeoForge has no matching VulkanMod package in this range.

Build the standard and MiSans editions separately:

```sh
./gradlew build
./gradlew build -Pfont_variant=misans
```

Both loaders are enabled by default; use `-Penabled_platforms=fabric` or
`-Penabled_platforms=neoforge` to select one. Edition preferences are kept in separate files.
MiSans uses the official nine static weights and requires the four official font families;
the first-run installer shows the license notice before downloading. The standard edition retains
customizable font preferences and contains no MiSans installer.

### Maintained Minecraft branches

All branches below are maintained by this fork. These are maintenance targets, not a claim that every loader/backend/version combination has passed runtime acceptance. Exact compatibility ranges and current test results are being checked per branch; consult that branch’s metadata and validation notes before installing.

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

`master` tracks Minecraft 26.3. Version branches retain their own build toolchains and loader dependencies. A maintenance target does not imply a shared JAR across all listed Minecraft versions.

### Known compatibility issues / 已知兼容性问题

#### Remove Reloading Screen — Minecraft 26.3 (Fabric)

Players have reported a startup crash with ModernUI and Remove Reloading Screen on Minecraft 26.3 / Fabric
when Remove Reloading Screen uses `hideOverlays = "ALL"`. The error reports missing shader files even though
they are present in the ModernUI JAR. In the reported environment, changing the following setting in
**Remove Reloading Screen's configuration** and restarting the game resolved the crash:

```toml
[global]
    hideOverlays = "RELOADING"
```

Edit the existing `hideOverlays` entry under `[global]`; this is not a ModernUI setting.
`RELOADING` keeps the initial loading overlay visible while hiding subsequent resource reload overlays.
This is a configuration workaround based on a player's real-client investigation, not a code fix;
the suspected resource initialization ordering issue has not been confirmed here.
The report covers Fabric on 26.3; other Minecraft versions and loaders have not been verified for this issue.

**中文：** Minecraft 26.3 / Fabric 同时安装 ModernUI 和 Remove Reloading Screen 时，后者使用
`hideOverlays = "ALL"` 的配置可能导致启动崩溃，报错为找不到着色器文件，即使 JAR 内实际存在这些文件。
据玩家实机排查，将 **Remove Reloading Screen 配置**中 `[global]` 下已有的 `hideOverlays` 改为
`"RELOADING"`，保存并重启游戏即可规避：保留首次加载屏幕，仅隐藏后续资源重载屏幕。
这是已反馈有效的配置规避方案；初始化时序原因仍属推测，尚无代码修复或其他版本、加载器的验证结论。

### License
* Modern UI for Minecraft
  - Copyright (C) 2019-2026 BloCamLimb et al.
  - [![License](https://img.shields.io/badge/License-LGPL--3.0--or--later-blue.svg?style=flat-square)](https://www.gnu.org/licenses/lgpl-3.0.en.html)
* Additional Assets
  - [source-han-sans](https://github.com/adobe-fonts/source-han-sans) by Adobe, licensed under the OFL-1.1
  - [jetbrains-mono](https://www.jetbrains.com/lp/mono/) by JetBrains, licensed under the OFL-1.1
  - [inter](https://github.com/rsms/inter) by RSMS, licensed under the OFL-1.1
    + Modern UI for Minecraft contains a modified version of Inter font family, the original license and
      copyright notice were retained in the font file

### Building ModernUI Lumen

This Minecraft 26.1.x branch uses **JDK 25**. The Gradle wrapper and dependencies in this checkout are the build source of truth. The default platforms are Fabric and NeoForge.

```sh
./gradlew clean build -Pfont_variant=standard
# Collect the standard JARs before cleaning for the other edition.
./gradlew clean build -Pfont_variant=misans
```

Final shaded JARs are under each loader’s `build/libs/`:

- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal.jar`
- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal-misans.jar`

See [font editions](docs/font-editions.md) for configuration and artifact checks. Build both editions before collecting the four JARs; this branch does not define a release workflow. Releases and tags require a separate explicit publishing instruction.

The stable Gradle project/task names (`ModernUI-Fabric`, `ModernUI-NeoForge`, `ModernUI-Forge`) and Maven group/coordinates remain compatible. This fork does not claim that its new version is published to upstream’s Maven repository. Upstream development documentation and libraries remain available in [ModernUI](https://github.com/BloCamLimb/ModernUI) and [ModernUI-MC](https://github.com/BloCamLimb/ModernUI-MC).

### Historical upstream screenshots (not acceptance evidence for this fork)
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

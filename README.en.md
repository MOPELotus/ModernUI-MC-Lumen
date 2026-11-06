[CN](README.md) | [EN](README.en.md)

# ModernUI Lumen

ModernUI Lumen is a fork of [Modern UI for Minecraft](https://github.com/BloCamLimb/ModernUI-MC), maintained by **MOPELotus**. The original mod is by **BloCamLimb**.

Modern UI brings its interface framework to Minecraft and provides text layout and rendering for vanilla screens. You can choose fonts, change screen backgrounds and tooltips, and use its APIs to build interfaces for other mods.

Lumen maintains the Minecraft versions listed below, with fixes for scrolling-lyric text jitter, tooltips, input, and rendering backends. The current development version is **3.13.0.10**.

[Downloads](https://github.com/MOPELotus/ModernUI-MC-Lumen/releases) · [Changelog](changelogs.en.md) · [Issues](https://github.com/MOPELotus/ModernUI-MC-Lumen/issues)

## Features

Most features come from upstream. Press **Ctrl+K** to open the action center, then open **Preferences**:

- Choose **Preferred Font Family** and edit the **Fallback Font Family List** and **Font Registration List**. TrueType and OpenType fonts are supported.
- **Modern Text Engine** provides Unicode text layout, **Anti-aliasing**, and **Color Emoji**. **Emoji Shortcodes** are also available in chat.
- Adjust **Blur Effect**, **Background Fade-in Duration**, and **Background Opacity**.
- Change tooltip **Border Style**, **Rounded Corners**, **Center Title**, and **Adaptive Colors**.
- Enable **Smooth Scrolling**, **Inventory Pause**, and **Ding**, or adjust **Window Mode** and the frame rate and volume when the window is inactive.

Upstream's text-field undo/redo, local music playback, and spectrum display are also included.

## Installation

Put the JAR for your Minecraft version and loader in the client `mods` folder. Both Fabric and NeoForge are supported.

- **Fabric** requires Fabric API.
- Configuration is built in. **Forge Config API Port is not required.** ModernUI Core is bundled too.
- Install this instead of upstream Modern UI, as both use the `modernui` mod ID. Replace the JAR when switching.

CurseForge releases will contain the standard edition only. See [font editions](docs/font-editions.en.md) for the differences between the standard and MiSans editions.

## Supported versions

| Minecraft | Branch | Loaders | Java | Status |
| --- | --- | --- | --- | --- |
| 1.21–1.21.1 | `1.21.1` | Fabric / NeoForge | 21+ | Maintained |
| 1.21.6–1.21.8 | `1.21.8` | Fabric / NeoForge | 21+ | Maintained |
| 1.21.9 | `1.21.9` | Fabric / NeoForge | 21+ | Maintained |
| 1.21.10 | `1.21.10` | Fabric / NeoForge | 21+ | Maintained |
| 1.21.11 | `1.21.11` | Fabric / NeoForge | 21+ | Maintained |
| 26.1–26.1.2 | `26.1` | Fabric / NeoForge | 25+ | Maintained |
| 26.2 | `26.2` | Fabric / NeoForge | 25+ | Maintained |
| 26.3 | `master` | Fabric / NeoForge | 25+ | Maintained |

## Vulkan and compatibility notes

Minecraft 26.2 / 26.3 have a native Vulkan backend, so VulkanMod is not needed. Add `--graphicsBackend VULKAN` to the launcher's **game arguments**, or `--graphicsBackend OPENGL` to switch back. These are not JVM arguments.

Earlier versions need a VulkanMod build for the matching Minecraft version and loader. Use OpenGL if no suitable build is available.

<details>
<summary>NeoForge early loading window</summary>

Disable the early loading window for native Vulkan on 26.2. Add this line before any TOML section in the instance's `config/fml.toml`, then restart:

```toml
earlyWindowControl = false
```

The NeoForge 26.3.0.101 setup we tested on Linux / NVIDIA also needs this setting to avoid startup crashes or reports of unclosed GPU resources on exit. Other systems may behave differently.

</details>

<details>
<summary>Remove Reloading Screen (26.3 / Fabric)</summary>

If you use Remove Reloading Screen and encounter missing-shader errors at startup, change `[global]` → `hideOverlays` in its configuration from `"ALL"` to `"RELOADING"`, then restart. This keeps the initial loading screen and hides later resource reload screens.

</details>

## Building

Use the JDK required by your branch and run:

```sh
./gradlew build -Pfont_variant=standard
```

Output JARs are in `fabric/build/libs/` and `neoforge/build/libs/`. Use `-Pfont_variant=misans` to build the MiSans edition.

[CurseForge page materials and publishing setup](https://github.com/MOPELotus/ModernUI-MC-Lumen/tree/master/distribution/curseforge).

## License and credits

The original mod and [ModernUI Framework](https://github.com/BloCamLimb/ModernUI) are by **BloCamLimb and contributors**, under [LGPL-3.0-or-later](LICENSE). The Lumen fork is maintained by **MOPELotus**.

Bundled [Source Han Sans](https://github.com/adobe-fonts/source-han-sans), [JetBrains Mono](https://www.jetbrains.com/lp/mono/), and [Inter](https://github.com/rsms/inter) retain their OFL licenses and font copyright notices. MiSans uses Xiaomi's official license.

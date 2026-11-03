[CN](font-editions.md) | [EN](font-editions.en.md)

# Standard and MiSans editions

Mod version: **3.13.0.10**. Both editions share the Minecraft adaptations and fixes. Each maintained build target produces standard and MiSans artifacts for Fabric / NeoForge; exact compatibility is defined by the selected branch's metadata.

| Edition | JAR suffix | In-game name |
| --- | --- | --- |
| Standard | `-universal.jar` | ModernUI Lumen |
| MiSans | `-universal-misans.jar` | ModernUI Lumen (MiSans) |

Full filename: `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal[-misans].jar`. Both use mod ID `modernui`. Install one at a time and replace the JAR when switching.

## Fonts and configuration

Standard uses configurable default fonts with preferred-font selection, fallback and registration lists. MiSans uses a fixed MiSans family set, nine weight presets and a missing-font setup flow. After the user reads the license and chooses to download, it retrieves and verifies four official Xiaomi packages containing 28 font files. It supports retry and restart prompts and can reuse existing fonts.

The editions use separate client settings: `config/ModernUI/client-standard.toml` and `config/ModernUI/client-misans.toml`.

`text.toml`, `common.toml` and bootstrap configuration are shared. Downloaded fonts live in `config/ModernUI/fonts`.

## MiSans presets

MiSans offers nine presets from **100 to 900**, with **400 (Regular)** as the default. The main, Latin and TC families switch together; L3 remains Regular. Preset 600 is Demibold and 700 is Semibold. These are selection presets rather than the font files' original OS/2 weight values.

## Building

Use JDK 21 on this branch and the appropriate toolchain on other branches. Omitting the property builds standard; runtime configuration cannot change the artifact's edition.

```sh
./gradlew clean build -Pfont_variant=standard
# Collect the previous artifacts before cleaning.
./gradlew clean build -Pfont_variant=misans
```

Select this branch when manually running **Build and Release** in Actions to build both editions and upload `modernui-lumen-release-jars`. Normal pushes to this branch do not trigger automatic builds. An empty release tag does not create a Release.

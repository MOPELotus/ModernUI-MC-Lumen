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

The editions use `config/ModernUI/client-standard.toml` and `config/ModernUI/client-misans.toml`. Initial creation imports the old `client.toml`, retaining the original; existing edition-specific files are not overwritten. Standard import recognizes the old complete fixed MiSans set and uses ordinary defaults while preserving other custom sets and settings. MiSans applies its fixed font policy.

`text.toml`, `common.toml` and bootstrap configuration are shared. Downloaded fonts live in `config/ModernUI/fonts`.

## MiSans presets

100–900 are nine discrete presets from thin to heavy, **not original OS/2 weight values**. The main, Latin and TC families share this mapping. L3 remains Regular and does not switch weight.

| Preset | Style | OS/2 `usWeightClass` |
| --- | --- | --- |
| 100 | Thin | 150 |
| 200 | ExtraLight | 200 |
| 300 | Light | 250 |
| 400 (default) | Regular | 330 |
| 500 | Medium | 380 |
| 600 | Demibold | 450 |
| 700 | Semibold | 520 |
| 800 | Bold | 630 |
| 900 | Heavy | 700 |

The mapping follows the official fonts' name and OS/2 metadata. Repository fixtures record names, weights, SHA-256 and sources without committing the downloaded fonts.

## Building

Use JDK 25 on `master` and the appropriate toolchain on other branches. Omitting the property builds standard; runtime configuration cannot change the artifact's edition.

```sh
./gradlew clean build -Pfont_variant=standard
# Collect the previous artifacts before cleaning.
./gradlew clean build -Pfont_variant=misans
```

CI builds and checks both editions and uploads `modernui-lumen-release-jars`. Normal branch pushes do not create Releases. Artifact checks do not replace real installation, license, retry, font-reload and post-restart page verification.

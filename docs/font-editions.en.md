[CN](font-editions.md) | [EN](font-editions.en.md)

# Font editions

ModernUI Lumen `3.13.0.10` provides standard and MiSans editions with the same UI, text-scrolling, clipping and input fixes. Each maintained branch builds both editions for Fabric and NeoForge, producing four JARs. Check each branch’s mod metadata for compatibility ranges.

| Item | Standard | MiSans |
| --- | --- | --- |
| In-game name | ModernUI Lumen | ModernUI Lumen (MiSans) |
| JAR suffix | `-universal.jar` | `-universal-misans.jar` |
| Font settings | Bundled defaults, custom preferred fonts and fallback list | Fixed MiSans collection and nine weight presets |
| Separate config | `config/ModernUI/client-standard.toml` | `config/ModernUI/client-misans.toml` |

Both editions use mod ID `modernui`. Install one edition at a time and replace the JAR when switching. `text.toml`, `common.toml` and bootstrap configuration are shared; font files are stored in `config/ModernUI/fonts`.

## MiSans installation and weights

The MiSans edition checks for missing fonts and provides an installer. Downloads start when the user chooses them. It obtains four collections—MiSans, MiSans Latin, MiSans TC and MiSans L3—from Xiaomi, totaling 28 files. Installation includes license notices, file verification, retries and a restart prompt. Complete existing fonts need not be downloaded again. Read and comply with the font license in the installer.

100–900 are nine presets ordered from thin to heavy, not the original OS/2 weight values. MiSans, MiSans Latin and MiSans TC use the mapping below; MiSans L3 always uses Regular.

| Preset | Style | Official OS/2 `usWeightClass` |
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

The mapping follows the official fonts’ name and OS/2 metadata. The repository test fixture stores names, weights, SHA-256 values and official source URLs, without font files.

## Builds and files

Use JDK 25 for this branch:

```sh
./gradlew clean build -Pfont_variant=standard
# Collect standard artifacts first.
./gradlew clean build -Pfont_variant=misans
```

Omitting `font_variant` builds the standard edition. JARs follow `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal[-misans].jar` and are saved in each loader’s `build/libs/`. Builds run checks; ordinary branch pushes generate artifacts without automatically publishing Releases. Runtime settings do not switch a JAR’s font edition.

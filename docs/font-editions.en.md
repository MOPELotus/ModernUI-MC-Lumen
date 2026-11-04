[CN](font-editions.md) | [EN](font-editions.en.md)

# Standard and MiSans editions

Both editions use the same Minecraft adaptations and text scrolling, clipping and input fixes. Choose the matching game and loader file. Install one edition at a time and replace its JAR when switching; both use mod ID `modernui`.

| Edition | In-game name | JAR filename suffix | Font policy |
| --- | --- | --- | --- |
| Standard | ModernUI Lumen | `-universal.jar` | Customizable fonts |
| MiSans | ModernUI Lumen (MiSans) | `-universal-misans.jar` | MiSans families and weight selection |

With the current Core 3.13.0, the Minecraft text rendering path can display color emoji, while Core text controls (including TextView) and Canvas leave bitmap emoji blank. Switching between the standard and MiSans editions does not remove this limitation.

## Configuration

- Standard: `config/ModernUI/client-standard.toml`.
- MiSans: `config/ModernUI/client-misans.toml`.
- Both share `text.toml`, `common.toml` and bootstrap configuration.
- MiSans font directory: `config/ModernUI/fonts`.

## MiSans installation and weights

The MiSans edition checks for missing fonts and provides an installation screen. After reading and accepting the license, the user initiates downloading Xiaomi's official MiSans, MiSans Latin, MiSans TC and MiSans L3 packages, totaling 28 font files. The screen provides verification, failure retry and restart guidance. An existing complete installation can be used directly.

Weights are nine discrete presets from 100 to 900, in increasing order: Thin, ExtraLight, Light, Regular, Medium, Demibold, Semibold, Bold and Heavy. The default is **400 / Regular**; **600 selects Demibold and 700 selects Semibold**. MiSans, MiSans Latin and MiSans TC share these presets; MiSans L3 always uses Regular.

See [README](../README.en.md) for builds and artifact locations.

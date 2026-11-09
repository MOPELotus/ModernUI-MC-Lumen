[CN](font-editions.md) | [EN](font-editions.en.md)

# Font editions

ModernUI Lumen provides standard and MiSans editions with the same UI and text features. They differ in font settings.

| Item | Standard | MiSans |
| --- | --- | --- |
| In-game name | ModernUI Lumen | ModernUI Lumen (MiSans) |
| Font settings | Bundled defaults, configurable preferred fonts and fallback list | Fixed MiSans font collection with selectable weight |
| Separate config | `config/ModernUI/client-standard.toml` | `config/ModernUI/client-misans.toml` |

Both editions use mod ID `modernui`. Install one edition at a time and replace the JAR when switching. `text.toml`, `common.toml` and bootstrap configuration are shared; font files are stored in `config/ModernUI/fonts`.

Starting with Lumen 3.13.0.11, both editions display color emoji in native TextView and Canvas text.

## MiSans installation and weights

The MiSans edition checks for missing fonts and provides an installer. Downloads begin when the user chooses them. It obtains MiSans, MiSans Latin, MiSans TC and MiSans L3 from Xiaomi’s official source, totaling 28 files. Installation includes license notices, file verification, retries and a restart prompt. Complete existing fonts need not be downloaded again. Read and comply with the font license in the installer.

MiSans, MiSans Latin and MiSans TC offer nine presets ordered from thin to heavy, with **400 (Regular)** as the default. MiSans L3 always uses Regular.

| Preset | Style |
| --- | --- |
| 100 | Thin |
| 200 | ExtraLight |
| 300 | Light |
| 400 (default) | Regular |
| 500 | Medium |
| 600 | Demibold |
| 700 | Semibold |
| 800 | Bold |
| 900 | Heavy |

See [README](../README.md) for installation, version compatibility and build instructions.

[CN](font-editions.md) | [EN](font-editions.en.md)

# Font editions

Choose one edition matching your Minecraft version and loader. Both use mod ID `modernui`; install only one edition at a time and replace its JAR when switching.

| Edition | Current font policy | In-game name |
| --- | --- | --- |
| `standard` | Inter Frozen Medium preferred, with fallbacks including Source Han Sans; preferred-font, fallback and font-registration settings | ModernUI Lumen |
| `misans` | MiSans, MiSans Latin, MiSans TC and MiSans L3, with weight controls and a font-installation entry | ModernUI Lumen (MiSans) |

Starting with Lumen 3.13.0.11, both editions display color emoji in native TextView and Canvas text.

## MiSans installation and weights

The MiSans edition displays its installer when required fonts are missing. Read [Xiaomi’s official font-license page](https://hyperos.mi.com/font/zh/download/) before choosing to download. The installer downloads four font packages from Xiaomi’s official source, installs 28 TTF files, and saves the license PDF and download record. Complete existing font files can be used directly.

Failed downloads can be retried. Exit and restart the client after installation as instructed. Font use and redistribution follow the font license, which is separate from the mod license.

MiSans, MiSans Latin and MiSans TC offer the nine presets below, defaulting to **400 (Regular)**. MiSans L3 always uses Regular.

| Preset | Style |
| --- | --- |
| 100 | Thin |
| 200 | ExtraLight |
| 300 | Light |
| 400 | Regular |
| 500 | Medium |
| 600 | Demibold |
| 700 | Semibold |
| 800 | Bold |
| 900 | Heavy |

## Configuration and directories

- Standard: `config/ModernUI/client-standard.toml`.
- MiSans: `config/ModernUI/client-misans.toml`.
- Font directory: `config/ModernUI/fonts`.

Both editions share `text.toml`, `common.toml` and bootstrap configuration. The standard edition’s custom font settings and MiSans’s fixed font set are different policies.

See the [README](../README.md) for the build environment, artifact names and rendering-backend dependencies.

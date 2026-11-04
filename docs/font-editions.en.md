[CN](font-editions.md) | [EN](font-editions.en.md)

# Standard and MiSans editions

Fabric and NeoForge for Minecraft 1.21.10 each provide two font editions. They share UI, marquee, tooltip and input adaptations, with different font policies.

| Edition | Filename ending | In-game name |
| --- | --- | --- |
| Standard | `-universal.jar` | ModernUI Lumen |
| MiSans | `-universal-misans.jar` | ModernUI Lumen (MiSans) |

Both use the `modernui` mod ID. **Install one edition at a time**, replacing the JAR when switching.

With the current Core 3.13.0, the Minecraft text rendering path can display color emoji, while Core text controls (including TextView) and Canvas leave bitmap emoji blank. Switching between the standard and MiSans editions does not remove this limitation.

## Font policies

Standard defaults to `Inter Frozen Medium`, with fallback including `Source Han Sans CN Medium` and system fonts. Installing MiSans is unnecessary. The Preferences Font page provides preferred-font, fallback-list and font-registration settings.

MiSans uses `MiSans` followed by `MiSans L3`, `MiSans Latin` and `MiSans TC`. It offers nine weight steps from 100 to 900, with 400 selecting Regular by default; 600 selects Demibold and 700 selects Semibold. MiSans L3 remains Regular.

## MiSans installation and license

Complete valid fonts are used directly. Missing fonts open setup, temporarily using Source Han Sans for instructions:

1. Select “查看 MiSans 许可协议” (View MiSans License Agreement) to open the [official Xiaomi page](https://hyperos.mi.com/font/zh/download/).
2. Read the license, then explicitly select “下载” (Download) to accept and start downloading. No download starts before this action.
3. The installer obtains 28 TTF files from the official MiSans, MiSans Latin, MiSans TC and MiSans L3 packages, and separately saves the license PDF and download record.
4. Download and exit controls are disabled while downloading. Failure enables “重试下载” (Retry Download) and exit. Success enables “退出并手动重启” (Exit and Restart Manually); restart the client to load the fonts.

MiSans is copyrighted by Xiaomi and governed by its Font License Agreement. The count of 28 font files excludes the PDF and download record.

## Configuration and building

- Standard configuration: `config/ModernUI/client-standard.toml`.
- MiSans configuration: `config/ModernUI/client-misans.toml`.
- Font directory: `config/ModernUI/fonts`.

See [README](../README.en.md) for build commands, artifact locations and backend targets. Runtime acceptance continues per combination; a successful build does not establish acceptance of every feature.

[CN](font-editions.md) | [EN](font-editions.en.md)

# 字体版本

ModernUI Lumen 提供普通版与 MiSans 版，二者的界面和文字功能相同，区别在字体设置。

| 项目 | 普通版 | MiSans 版 |
| --- | --- | --- |
| 游戏内名称 | ModernUI Lumen | ModernUI Lumen (MiSans) |
| 字体设置 | 内置默认字体，可选择首选字体和回退列表 | 固定使用 MiSans 字体组合，可选择字重 |
| 独立配置 | `config/ModernUI/client-standard.toml` | `config/ModernUI/client-misans.toml` |

两版的模组 ID 都是 `modernui`，请每次只安装其中一版，切换时替换 JAR。`text.toml`、`common.toml` 与 bootstrap 配置共用；字体文件位于 `config/ModernUI/fonts`。

从 Lumen 3.13.0.11 起，普通版和 MiSans 版都支持在原生 TextView 和 Canvas 中显示彩色 Emoji。

## MiSans 安装与字重

MiSans 版会检查缺失字体并提供安装界面。用户主动点击下载后，从小米官方获取 MiSans、MiSans Latin、MiSans TC 与 MiSans L3 四套字体，共 28 个文件。安装流程包含许可提示、文件校验、重试及重启提示；已有完整字体时无需重复下载。请在安装界面阅读并遵守字体许可。

MiSans、MiSans Latin 与 MiSans TC 提供九个由细到粗的字重档位，默认 **400（Regular）**。MiSans L3 固定使用 Regular。

| 档位 | 字体风格 |
| --- | --- |
| 100 | Thin |
| 200 | ExtraLight |
| 300 | Light |
| 400（默认） | Regular |
| 500 | Medium |
| 600 | Demibold |
| 700 | Semibold |
| 800 | Bold |
| 900 | Heavy |

安装、版本兼容范围与构建入口见 [README](../README.md)。

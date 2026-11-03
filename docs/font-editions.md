[CN](font-editions.md) | [EN](font-editions.en.md)

# 字体版本

选择与 Minecraft 和加载器匹配的一个字体版本。两版使用相同的模组 ID `modernui`，每次只安装其中一版；切换时替换原 JAR。

| 版本 | 当前字体策略 | 游戏内名称 |
| --- | --- | --- |
| 普通版 `standard` | 默认首选 Inter Frozen Medium，使用思源黑体等回退字体；提供首选、回退和字体注册设置 | ModernUI Lumen |
| MiSans 版 `misans` | 使用 MiSans、MiSans Latin、MiSans TC 和 MiSans L3，提供字重与字体安装入口 | ModernUI Lumen (MiSans) |

## MiSans 安装与字重

缺少所需字体时，MiSans 版显示安装页面。请先查看[小米官方字体许可页面](https://hyperos.mi.com/font/zh/download/)，再决定是否点击下载。安装器从小米官方来源下载四套字体包，安装 28 个 TTF 文件，并保存许可 PDF 和下载记录。已有完整所需字体时可直接使用。

下载失败可重试；安装完成后按提示退出并重启客户端。字体的使用和分发遵循其许可，模组许可证不替代字体许可。

MiSans、MiSans Latin 和 MiSans TC 提供以下九档字重，默认 **400（Regular）**；MiSans L3 固定使用 Regular。

| 档位 | 风格 |
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

## 配置与目录

- 普通版：`config/ModernUI/client-standard.toml`。
- MiSans 版：`config/ModernUI/client-misans.toml`。
- 字体目录：`config/ModernUI/fonts`。

`text.toml`、`common.toml` 和 bootstrap 配置由两版共用。普通版的自定义字体设置与 MiSans 版的固定字体组合是不同策略。

构建环境、成品文件名及渲染后端依赖见 [README](../README.md)。

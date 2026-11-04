[CN](font-editions.md) | [EN](font-editions.en.md)

# 普通版与 MiSans 版

两版使用相同的 Minecraft 适配、文字滚动、裁剪和输入修复。选择匹配游戏版本与加载器的文件，每次只安装其中一版，切换时替换原 JAR；两版的模组 ID 均为 `modernui`。

| 版本 | 游戏内名称 | JAR 文件名结尾 | 字体策略 |
| --- | --- | --- | --- |
| 普通版 | ModernUI Lumen | `-universal.jar` | 可自定义字体 |
| MiSans 版 | ModernUI Lumen (MiSans) | `-universal-misans.jar` | MiSans 字体组合与字重选择 |

当前 Core 3.13.0 中，Minecraft 文本渲染路径可显示彩色 emoji；Core 文本控件（如 TextView）及 Canvas 对位图 emoji 存在留白限制。切换普通版与 MiSans 版不会消除这一限制。

## 配置

- 普通版：`config/ModernUI/client-standard.toml`。
- MiSans 版：`config/ModernUI/client-misans.toml`。
- 两版共用 `text.toml`、`common.toml` 和 bootstrap 配置。
- MiSans 字体目录：`config/ModernUI/fonts`。

## MiSans 安装与字重

MiSans 版提供缺失字体检查与安装界面。阅读并同意许可后，由用户主动下载小米官方的 MiSans、MiSans Latin、MiSans TC、MiSans L3 四套字体，共 28 个字体文件。界面提供文件校验、失败重试与重启提示；已有完整字体时可直接使用。

字重为 100–900 的九个离散档位，由细到粗依次为 Thin、ExtraLight、Light、Regular、Medium、Demibold、Semibold、Bold、Heavy。默认 **400 / Regular**；**600 对应 Demibold，700 对应 Semibold**。MiSans、MiSans Latin、MiSans TC 使用相同档位，MiSans L3 固定为 Regular。

构建方式与产物位置见 [README](../README.md)。

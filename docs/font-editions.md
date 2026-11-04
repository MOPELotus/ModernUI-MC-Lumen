[CN](font-editions.md) | [EN](font-editions.en.md)

# 普通版与 MiSans 版

Minecraft 1.21.10 的 Fabric 与 NeoForge 均提供两种字体版本。两版共享界面、走马灯、tooltip 和输入适配，字体策略不同。

| 版本 | 文件名结尾 | 游戏内显示名 |
| --- | --- | --- |
| 普通版（standard） | `-universal.jar` | ModernUI Lumen |
| MiSans 版 | `-universal-misans.jar` | ModernUI Lumen (MiSans) |

两版模组 ID 都是 `modernui`，**每次只安装一版**；切换时替换原 JAR。

当前 Core 3.13.0 中，Minecraft 文本渲染路径可显示彩色 emoji；Core 文本控件（如 TextView）及 Canvas 对位图 emoji 存在留白限制。切换普通版与 MiSans 版不会消除这一限制。

## 字体策略

普通版默认首选 `Inter Frozen Medium`，回退包含 `Source Han Sans CN Medium`（思源黑体）和系统字体等，无需安装 MiSans。Preferences 的 Font 页可设置首选字体、回退列表和字体注册列表。

MiSans 版首选 `MiSans`，回退依次为 `MiSans L3`、`MiSans Latin`、`MiSans TC`。提供 100–900 九个字重档位，默认 400 为 Regular；600 为 Demibold、700 为 Semibold。MiSans L3 固定使用 Regular。

## MiSans 安装与许可

已有完整有效字体时直接使用。字体缺失时显示安装界面，临时使用思源黑体显示说明：

1. 点击“查看 MiSans 许可协议”，打开[小米官方页面](https://hyperos.mi.com/font/zh/download/)。
2. 阅读许可后明确点击“下载”，表示同意协议并开始下载；未点击时不会自动下载。
3. 安装器从官方 MiSans、MiSans Latin、MiSans TC、MiSans L3 四套包获取 28 个 TTF 字体文件，并另存许可 PDF 和下载记录。
4. 下载期间下载及退出按钮禁用；失败后可“重试下载”或退出。成功后点击“退出并手动重启”，重新启动客户端加载字体。

MiSans 字体版权归 Xiaomi，使用须遵守其字体知识产权许可协议。28 个字体文件的数量不包含许可 PDF 和下载记录。

## 配置与构建

- 普通版配置：`config/ModernUI/client-standard.toml`。
- MiSans 版配置：`config/ModernUI/client-misans.toml`。
- 字体目录：`config/ModernUI/fonts`。

构建命令、产物位置和后端范围见 [README](../README.md)。当前仍在逐组合运行验收，不能将构建成功视为全部功能已通过。

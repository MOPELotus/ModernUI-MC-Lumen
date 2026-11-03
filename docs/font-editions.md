[CN](font-editions.md) | [EN](font-editions.en.md)

# 字体版本

ModernUI Lumen `3.13.0.10` 提供普通版与 MiSans 版，二者包含相同的界面、文字滚动、裁剪和输入修复。每个维护分支为 Fabric、NeoForge 各构建两种字体版本，共四个 JAR；兼容范围以各分支模组元数据为准。

| 项目 | 普通版 | MiSans 版 |
| --- | --- | --- |
| 游戏内名称 | ModernUI Lumen | ModernUI Lumen (MiSans) |
| JAR 后缀 | `-universal.jar` | `-universal-misans.jar` |
| 字体设置 | 内置默认字体、自定义首选字体与回退列表 | MiSans 固定字体组合与九档字重 |
| 独立配置 | `config/ModernUI/client-standard.toml` | `config/ModernUI/client-misans.toml` |

两版的模组 ID 都是 `modernui`，请每次只安装其中一版，切换时替换 JAR。`text.toml`、`common.toml` 与 bootstrap 配置共用；字体文件位于 `config/ModernUI/fonts`。

## MiSans 安装与字重

MiSans 版会检查缺失字体，并提供安装界面。用户主动点击下载后，从小米获取 MiSans、MiSans Latin、MiSans TC 与 MiSans L3 四套字体，共 28 个文件；安装流程包含许可提示、文件校验、重试及重启提示。已有完整字体时无需重复下载。请在安装界面阅读并遵守字体许可。

100–900 是由细到粗的九个选择档位，不等同于字体文件的原始 OS/2 字重。MiSans、MiSans Latin 与 MiSans TC 使用以下对应关系；MiSans L3 固定使用 Regular。

| 档位 | 字体风格 | 官方 OS/2 `usWeightClass` |
| --- | --- | --- |
| 100 | Thin | 150 |
| 200 | ExtraLight | 200 |
| 300 | Light | 250 |
| 400（默认） | Regular | 330 |
| 500 | Medium | 380 |
| 600 | Demibold | 450 |
| 700 | Semibold | 520 |
| 800 | Bold | 630 |
| 900 | Heavy | 700 |

映射依据官方字体的 name 表与 OS/2 元数据。仓库内的测试夹具仅记录名称、字重、SHA-256 与官方来源地址，不包含字体文件。

## 构建与文件

本分支使用 JDK 25：

```sh
./gradlew clean build -Pfont_variant=standard
# 先收集普通版产物。
./gradlew clean build -Pfont_variant=misans
```

省略 `font_variant` 时构建普通版。JAR 命名为 `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal[-misans].jar`，保存在各加载器的 `build/libs/`。构建会执行检查；普通分支推送仅生成构建产物，不自动发布 Release。运行时配置不会切换 JAR 的字体版本。

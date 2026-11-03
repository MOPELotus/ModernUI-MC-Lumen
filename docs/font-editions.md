[CN](font-editions.md) | [EN](font-editions.en.md)

# 普通版与 MiSans 版

模组版本为 **3.13.0.10**。两版提供相同的 Minecraft 适配与修复；每个已维护的构建目标分别生成 Fabric / NeoForge 普通版和 MiSans 版。准确兼容范围以所选分支元数据为准。

| 字体版本 | JAR 后缀 | 游戏内名称 |
| --- | --- | --- |
| 普通版 | `-universal.jar` | ModernUI Lumen |
| MiSans 版 | `-universal-misans.jar` | ModernUI Lumen (MiSans) |

完整文件名为 `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal[-misans].jar`。两版 ID 都是 `modernui`，每次只安装一版，切换时替换 JAR。

## 字体和配置

普通版使用可配置的默认字体方案，支持选择首选字体及设置回退、注册列表。MiSans 版使用固定 MiSans 字体组合，提供 100–900 档位和缺失字体安装流程：用户阅读许可并主动下载后，从小米官方包获取、校验四套字体，共 28 个字体文件；支持失败重试和重启提示，已有字体可复用。

两版分别使用 `config/ModernUI/client-standard.toml` 和 `config/ModernUI/client-misans.toml`。首次创建时导入旧 `client.toml`，原文件保留；已有该版配置时不覆盖。普通版导入时会识别旧 MiSans 完整固定组合并采用普通默认值，保留其他自定义组合和设置。MiSans 版按其固定字体策略处理。

`text.toml`、`common.toml` 和 bootstrap 配置共用，下载字体位于 `config/ModernUI/fonts`。

## MiSans 档位

100–900 是由细到粗的九个离散预设，**不是字体文件原始 OS/2 字重数值**。主字体、Latin 和 TC 使用同一映射；L3 固定 Regular，不随档位切换。

| 档位 | 字体风格 | OS/2 `usWeightClass` |
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

映射依据官方字体的 name 与 OS/2 元数据；仓库测试夹具记录名称、字重、SHA-256 和来源，不随之提交下载的字体。

## 构建

在 `master` 使用 JDK 25；其他分支使用其工具链。省略参数时构建普通版，运行时配置不会改变构建版本。

```sh
./gradlew clean build -Pfont_variant=standard
# 清理前先收集上一版产物
./gradlew clean build -Pfont_variant=misans
```

CI 分别构建并检查两版，上传 `modernui-lumen-release-jars`；普通分支推送不创建 Release。包结构检查不代替真实安装、许可、失败重试、字体重载及重启后的页面验证。

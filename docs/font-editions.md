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

两版使用独立的客户端设置：`config/ModernUI/client-standard.toml` 和 `config/ModernUI/client-misans.toml`。

`text.toml`、`common.toml` 和 bootstrap 配置共用，下载字体位于 `config/ModernUI/fonts`。

## MiSans 档位

MiSans 提供 **100–900** 九个由细到粗的预设，默认 **400（Regular）**。主字体、Latin 和 TC 同步切换；L3 固定 Regular。600 档为 Demibold，700 档为 Semibold。这些是选择档位，不是字体文件原始 OS/2 字重数值。

## 构建

本分支使用 JDK 21；其他分支使用其工具链。省略参数时构建普通版，运行时配置不会改变构建版本。

```sh
./gradlew clean build -Pfont_variant=standard
# 清理前先收集上一版产物
./gradlew clean build -Pfont_variant=misans
```

CI 分别构建两版，上传 `modernui-lumen-release-jars`；普通分支推送不创建 Release。

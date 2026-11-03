[CN](README.md) | [EN](README.en.md)

# ModernUI Lumen

**ModernUI Lumen** 是由 **MOPELotus** 独立维护的 [Modern UI for Minecraft](https://github.com/BloCamLimb/ModernUI-MC) Fork，原作者为 **BloCamLimb**，基于 [ModernUI 框架](https://github.com/BloCamLimb/ModernUI)。本项目保留上游作者署名与许可证，不是上游官方发行版。

当前模组版本：**3.13.0.10**。支持普通版与 **ModernUI Lumen (MiSans)** 两种字体版本，安装时选择其中一种。

[源码](https://github.com/MOPELotus/ModernUI-MC-Lumen) · [问题反馈](https://github.com/MOPELotus/ModernUI-MC-Lumen/issues) · [发行页面](https://github.com/MOPELotus/ModernUI-MC-Lumen/releases) · [字体版本说明](docs/font-editions.md) · [更新记录](changelogs.md)

## 功能

- Unicode 文本排版、双向文字、字体回退、TrueType/OpenType 字体预览与重载，以及彩色 emoji 和聊天短代码。
- 抗锯齿与 SDF 文字渲染、字体 hinting、设备像素字号计算和异步文字布局。
- 平滑滚动、文本框撤销/重做与 Unicode 词边界移动。
- 可配置的背景颜色、渐变、模糊与淡入动画；圆角提示框、标题分隔、居中、RTL 和像素定位。
- 窗口模式切换、失焦/最小化时的帧率限制与音量淡出、单人背包暂停和本地音乐播放。
- 支持上游缩放扩展的分支/加载器可按 C 缩放；Fabric 不提供此扩展。
- 为其他模组提供 ModernUI 原生界面及文字渲染 API；原版 GUI 也可使用增强的文字系统。

开发者的 **Take UI screenshot (Y)** 仅支持 OpenGL；Vulkan 下该导出功能不适用。此限制不等同于整个界面或原版游戏截图不可用。

## 安装与字体

安装匹配 Minecraft 版本和加载器的 JAR，每次只放入普通版或 MiSans 版其中一个。两版的模组 ID 都是 `modernui`；资源命名空间、Java 包、API、Maven 坐标及 `config/ModernUI/` 路径保持兼容。

普通版提供内置字体与可配置的字体选择；MiSans 版提供固定字体组合、字重档位以及缺失字体的安装流程。下载由用户在安装界面主动开始，具体行为、许可证和配置路径见[字体版本说明](docs/font-editions.md)。

## 维护分支

| 分支 | Minecraft 维护范围 | 状态 |
| --- | --- | --- |
| `1.21.1` | 1.21–1.21.1 | 维护中 |
| `1.21.8` | 1.21.6–1.21.8 | 维护中 |
| `1.21.9` | 1.21.9 | 维护中 |
| `1.21.10` | 1.21.10 | 维护中 |
| `1.21.11` | 1.21.11 | 维护中 |
| `26.1` | 26.1 系列 | 维护中 |
| `26.2` | 26.2 | 维护中 |
| `master` | 26.3 | 维护中 |

维护范围不表示一个 JAR 通用于表内所有 Minecraft 版本，也不表示每种加载器/图形后端组合均已完成验收。安装前以所选分支的模组元数据为准。维护的加载器为 Fabric 与 NeoForge；VulkanMod 是否有对应包需按版本确认。

## 已知兼容性条件

### NeoForge 早期加载窗口

Minecraft 26.2 原生 Vulkan 与 FML 11.0.16 的早期加载窗口不兼容：早窗使用 OpenGL，无法作为 Vulkan 窗口表面复用（`GLFW_NO_API` / 65540）。在对应游戏实例的 `config/fml.toml` 中设置以下顶层键，保存后重启：

```toml
earlyWindowControl = false
```

此键应位于所有 TOML 分节之外。文件不存在时可创建并写入该行，或先用 OpenGL 启动一次，再编辑生成的文件。

26.3 / FML 12 使用 SDL 交接，不能套用上述 GLFW 原因。但在已验证的 **NeoForge 26.3.0.101 / FML 12.0.12、Linux、Tesla T10、NVIDIA 580.178.04** 环境中，OpenGL 和原生 Vulkan 也需要关闭早窗：启用早窗时，OpenGL 可在启动时发生 NVIDIA 驱动崩溃；Vulkan 会在退出时遗留早窗的 `ScreenSize UBO` 与 `shared_error` GPU 缓冲区。两种现象都在未安装 ModernUI 的对照中复现。OpenGL 崩溃的精确上游原因及其他驱动环境尚未确认。

关闭早窗后，该环境中的 ModernUI 原生 Vulkan 核心界面流程、正常退出及 Vulkan 验证无错误已得到验证；这不代表所有功能或其他驱动/配置已通过测试。

26.2/26.3 的原生 Vulkan 不需要 VulkanMod。在启动器的**游戏参数**中使用 `--graphicsBackend VULKAN`；切换 OpenGL 使用 `--graphicsBackend OPENGL`。这些不是 JVM 参数。

### Remove Reloading Screen（26.3 / Fabric）

玩家报告同时安装 Remove Reloading Screen 且配置为 `hideOverlays = "ALL"` 时，可能出现着色器缺失的启动崩溃，即使 JAR 内实际包含对应文件。已反馈有效的规避方式是在该模组的配置中修改现有 `[global]` 项后重启：

```toml
[global]
    hideOverlays = "RELOADING"
```

此设置保留首次加载屏幕，仅隐藏后续资源重载屏幕；它不是 ModernUI 配置。初始化顺序原因仍属推测，其他版本和加载器未验证。

## 构建

本分支使用 **JDK 25**。工具链和依赖以仓库内 Gradle 配置为准，默认构建 Fabric 与 NeoForge：

```sh
./gradlew clean build -Pfont_variant=standard
# 先收集普通版 JAR，再清理并构建 MiSans 版。
./gradlew clean build -Pfont_variant=misans
```

最终 JAR 位于各加载器的 `build/libs/`：

- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal.jar`
- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal-misans.jar`

详见[字体版本与构建说明](docs/font-editions.md)。Gradle 项目/任务兼容名称仍为 `ModernUI-Fabric`、`ModernUI-NeoForge`、`ModernUI-Forge`；保留历史 Forge 代码不表示新增 Forge 维护目标。构建或普通分支推送不会自动发布 Release，本仓库版本也不表示已发布至上游 Maven 仓库。

本分支：Minecraft 26.2。

## 许可证与署名

Modern UI for Minecraft：Copyright (C) 2019–2026 BloCamLimb et al.，遵循 [LGPL-3.0-or-later](https://www.gnu.org/licenses/lgpl-3.0.en.html)。Fork 维护：MOPELotus。

附加字体资源：[Source Han Sans](https://github.com/adobe-fonts/source-han-sans)（Adobe）、[JetBrains Mono](https://www.jetbrains.com/lp/mono/)（JetBrains）、[Inter](https://github.com/rsms/inter)（RSMS），均遵循 OFL-1.1。随附的修改版 Inter 字体保留原许可证与版权声明。MiSans 字体的许可和下载说明见[字体版本文档](docs/font-editions.md)。仓库内许可证及字体授权文件保留原文。

[CN](README.md) | [EN](README.en.md)

# ModernUI Lumen

**ModernUI Lumen** 是 **MOPELotus** 独立维护的 [Modern UI for Minecraft](https://github.com/BloCamLimb/ModernUI-MC) 分支，原作者为 **BloCamLimb**，基于 [ModernUI 框架](https://github.com/BloCamLimb/ModernUI)。本项目保留上游作者署名及许可证，不是上游官方发行版。

当前模组版本为 **3.13.0.10**。普通版显示为 **ModernUI Lumen**，MiSans 版显示为 **ModernUI Lumen (MiSans)**。

[源码](https://github.com/MOPELotus/ModernUI-MC-Lumen) · [问题反馈](https://github.com/MOPELotus/ModernUI-MC-Lumen/issues) · [发行页面](https://github.com/MOPELotus/ModernUI-MC-Lumen/releases) · [更新记录](changelogs.md)

## 功能

- 为 Minecraft 提供 ModernUI 界面框架、控件、主题及模组开发接口。
- 为原版 GUI 提供 Unicode 文字布局、字体回退、抗锯齿及可配置的文字阴影、字号和双向文字处理。
- 提供圆角或直角 tooltip、标题分隔与居中、边框和背景颜色、长内容移动等设置。
- 提供界面背景模糊、淡入、窗口模式、失焦时的帧率限制，以及失焦或最小化时的音量设置。
- 提供 Unicode 编辑、撤销与重做、聊天 emoji 短代码，以及本地音乐播放、进度与音量控制和频谱界面。
- 提供 GUI 缩放滑块、列表平滑滚动、加载提示音，以及打开背包时暂停单人游戏的选项。
- NeoForge 提供 C 键缩放；Fabric 不提供这一功能。

Minecraft 原生 GUI 的彩色 emoji 与 Core 控件的 emoji 渲染能力不同：Core TextView、Canvas 和输入控件的 emoji 渲染仍受上游实现限制。Core TextView 的自动 MARQUEE 也不是已完成的功能；外部驱动的 Canvas 与原生 GUI 文字滚动应按实际绘制调用使用。

## 安装与字体版本

选择匹配 Minecraft、加载器和字体版本的一个最终 JAR。两版使用相同的模组 ID `modernui`，每次只安装其中一版，切换时替换原 JAR。

- **普通版（standard）**：使用 Inter 和思源黑体等默认字体，提供首选字体、回退字体和字体注册设置。
- **MiSans 版（misans）**：使用 MiSans、MiSans Latin、MiSans TC 与 MiSans L3。缺少所需字体时显示许可及安装入口，由用户选择下载；支持九档字重。

两版的配置、字体安装和实际字重映射见[字体版本说明](docs/font-editions.md)。模组 ID、资源命名空间、Java 包、API 和既有 `config/ModernUI/` 路径保持兼容。

## 维护分支

以下八个分支均在维护。维护目标不代表所有版本、加载器和渲染后端组合已完成运行验收；具体安装范围以对应分支元数据为准。

| 分支 | Minecraft 维护范围 | 状态 |
| --- | --- | --- |
| `1.21.1` | 1.21–1.21.1 | 维护中 |
| `1.21.8` | 1.21.6–1.21.8 | 维护中 |
| `1.21.9` | 1.21.9 | 维护中 |
| `1.21.10` | 1.21.10 | 维护中 |
| `1.21.11` | 1.21.11 | 维护中 |
| `26.1` | 26.1–26.1.2 | 维护中 |
| `26.2` | 26.2 | 维护中 |
| `master` | 26.3 | 维护中 |

## 1.21.9–1.21.11 加载器与后端

这三个分支分别构建各自 Minecraft 版本的 Fabric、NeoForge 普通版和 MiSans 版。不要跨版本使用成品。

| Minecraft | Fabric OpenGL | NeoForge OpenGL | Fabric Vulkan 依赖 | NeoForge Vulkan 依赖 |
| --- | --- | --- | --- | --- |
| 1.21.9 | 支持目标 | 支持目标 | VulkanMod 0.6.6 | 无对应发行包 |
| 1.21.10 | 支持目标 | 支持目标 | VulkanMod 0.6.6 | 无对应发行包 |
| 1.21.11 | 支持目标 | 支持目标 | VulkanMod 0.6.8 | VulkanMod Reforged 0.3.0-ALPHA |

Vulkan 集成使用外部 VulkanMod 后端及其 Vulkan 1.2 设备。0.6.6 的包元数据同时支持 1.21.9 和 1.21.10，文件名包含 `1.21.10` 不代表只支持该版本。缺少对应依赖包的组合不列为可运行组合。此表说明兼容目标和依赖，不宣称完整运行矩阵已经通过。

## 构建

本分支目标为 **Minecraft 1.21.9**，使用 **JDK 21**，默认构建 Fabric 和 NeoForge。1.21.9 与 1.21.10 使用不同 Loom 分层映射缓存，必须分配独立 `GRADLE_USER_HOME`；建议所有版本按分支隔离缓存。

```sh
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
mc_version=$(sed -n 's/^minecraft_version=//p' gradle.properties)
export GRADLE_USER_HOME="$HOME/.gradle-modernui-$mc_version"
./gradlew clean build -Pfont_variant=standard
# 先复制普通版成品，再清理构建另一版。
./gradlew clean build -Pfont_variant=misans
```

成品位于 `fabric/build/libs` 与 `neoforge/build/libs`：

- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal.jar`
- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal-misans.jar`

安装最终重映射成品，避免使用 `-shadow.jar` 或开发用 JAR。CI 上传 `modernui-lumen-release-jars` 构建产物；普通分支推送不会发布 Release。标签推送或手动运行工作流时填写 `tag` 会创建或更新 GitHub Release。

稳定 Gradle 项目和任务名（如 `ModernUI-Fabric`、`ModernUI-NeoForge`）以及 Maven 坐标保持兼容；历史 Forge 项目名不表示本分支维护 Forge 成品。本 Fork 不声称新版本已发布到上游 Maven 仓库。接口参数、uniform 布局与检查任务以对应分支源码为准。构建和 CPU 检查不能替代物理显卡上的界面与交互验收。

## 许可证与归属

Modern UI for Minecraft：Copyright (C) 2019–2026 BloCamLimb et al.，采用 [LGPL-3.0-or-later](https://www.gnu.org/licenses/lgpl-3.0.en.html)。本仓库的许可证原文及各资源版权声明适用于相应代码和资源。

附加字体包括 Adobe 的 [Source Han Sans](https://github.com/adobe-fonts/source-han-sans)、JetBrains 的 [JetBrains Mono](https://www.jetbrains.com/lp/mono/) 和 RSMS 的 [Inter](https://github.com/rsms/inter)，采用 SIL OFL。随附修改版 Inter 保留原许可证及版权声明。MiSans 的许可与下载来源见[字体说明](docs/font-editions.md)。

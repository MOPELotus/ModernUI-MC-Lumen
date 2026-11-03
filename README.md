[CN](README.md) | [EN](README.en.md)

# ModernUI Lumen

**ModernUI Lumen** 是 **MOPELotus** 独立维护的 [Modern UI for Minecraft](https://github.com/BloCamLimb/ModernUI-MC) Fork，原作者为 **BloCamLimb**，基于 [ModernUI Framework](https://github.com/BloCamLimb/ModernUI)。保留上游作者署名和许可证；本项目不是上游官方发行版。

当前模组版本：**3.13.0.10**。仓库名为 **ModernUI-MC-Lumen**，游戏内显示 **ModernUI Lumen**，MiSans 版显示 **ModernUI Lumen (MiSans)**。

[源码](https://github.com/MOPELotus/ModernUI-MC-Lumen) · [问题反馈](https://github.com/MOPELotus/ModernUI-MC-Lumen/issues) · [发行页](https://github.com/MOPELotus/ModernUI-MC-Lumen/releases) · [更新记录](changelogs.md)

## 功能

为 Minecraft 提供 ModernUI 界面运行环境及开发 API，并增强原版 GUI 的文字排版和渲染：TrueType/OpenType 字体、字体回退、抗锯齿、SDF、Unicode 换行、双向文字和彩色 emoji。具体字形表现仍取决于所用字体与渲染路径。

可配置功能包括背景模糊与动画、圆角/渐变 tooltip、平滑滚动、GUI 缩放、窗口模式、失焦/最小化帧率和音量衰减、背包暂停、文本编辑撤销/重做、聊天 emoji 短码，以及本地音乐播放与频谱。缩放快捷键属于提供该上游扩展的加载器，Fabric 不提供该功能。

[普通版与 MiSans 版](docs/font-editions.md) 提供相同的 Minecraft 适配和修复，字体策略不同。每次只安装一版，切换时替换 JAR。模组 ID `modernui`、资源命名空间、Java 包名、公开 API、Maven 坐标及 `config/ModernUI/` 路径保留兼容。

## 维护分支

下表是维护范围；不代表每个加载器、后端及字体版本组合均已完成运行验收。安装时以所选分支的模组元数据和兼容说明为准。

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

`1.21.8` 分支共用适配 1.21.6、1.21.7 和 1.21.8；各分支保留各自工具链与依赖，维护范围不等于所有版本共用一个 JAR。

## 渲染后端和兼容性

本分支面向 Minecraft **1.21–1.21.1**，提供 Fabric 和 NeoForge 的 OpenGL 适配。Vulkan 组合需要准确匹配 Minecraft 版本和加载器的 VulkanMod 包；缺包组合不视为已支持或测试通过。本分支使用旧版 GLFW 和即时 GUI API，不能将 26.3 的 SDL、RenderPearl 或原生 Vulkan 接口直接套用。

## 构建

本分支使用 **JDK 21**，默认构建 Fabric 和 NeoForge。Minecraft、加载器和依赖的准确版本以 `gradle.properties` 与 Gradle wrapper 为准；其他分支使用各自的工具链。

```sh
./gradlew clean build -Pfont_variant=standard
# 清理前先收集普通版产物
./gradlew clean build -Pfont_variant=misans
```

最终 JAR 位于各加载器的 `build/libs/`：

- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal.jar`
- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal-misans.jar`

本分支保留 **Build and Release** 工作流（JDK 21），构建两版并上传 `modernui-lumen-release-jars` artifact。自动分支推送/PR 触发仅配置于 `master`，因此推送 `1.21.1` 本身不触发构建；可在 Actions 手动运行时选择本分支。推送标签或手动填写 release tag 会创建或更新 Release；留空时仅构建 artifact。Gradle 项目/任务名与 Maven 坐标保持兼容，本 Fork 不宣称新版已发布到上游 Maven。

## 验证边界

移植会分别核对 1.21 与 1.21.1 的实际 tooltip 参数顺序、键盘/鼠标回调、窗口切换及 GPU 资源所有权。共用分支或 JAR 不代替逐版本运行验证。

CPU 检查和编译通过不能代替物理显卡上的实际页面操作、截图、渲染器日志及正常退出验证。跨版本矩阵按准确 JAR、加载器、Minecraft 版本和后端分别记录，本文不作全矩阵已通过的声明。

## 许可证与致谢

Modern UI for Minecraft：Copyright (C) 2019–2026 BloCamLimb et al.，采用 [LGPL-3.0-or-later](LICENSE)。Fork 维护：MOPELotus。

附加字体资源包括 Adobe [Source Han Sans](https://github.com/adobe-fonts/source-han-sans)、JetBrains [JetBrains Mono](https://www.jetbrains.com/lp/mono/) 和 RSMS [Inter](https://github.com/rsms/inter)，均保留各自 OFL 许可。随附的修改版 Inter 保留原许可及字体内版权声明。MiSans 使用其官方许可，下载和使用前需阅读对应条款。

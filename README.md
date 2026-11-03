[CN](README.md) | [EN](README.en.md)

# ModernUI Lumen

**ModernUI Lumen** 是 **MOPELotus** 维护的 [Modern UI for Minecraft](https://github.com/BloCamLimb/ModernUI-MC) Fork，原作者为 **BloCamLimb**，基于 [ModernUI 框架](https://github.com/BloCamLimb/ModernUI)。保留原作者署名与许可证；本项目独立维护。当前模组版本：**3.13.0.10**。

[源码](https://github.com/MOPELotus/ModernUI-MC-Lumen) · [问题反馈](https://github.com/MOPELotus/ModernUI-MC-Lumen/issues) · [下载](https://github.com/MOPELotus/ModernUI-MC-Lumen/releases) · [字体版本](docs/font-editions.md) · [更新记录](changelogs.md)

## 安装与版本

本分支面向 Minecraft 1.21.11，提供 Fabric、NeoForge 的 OpenGL 适配，以及 Fabric VulkanMod 0.6.8、NeoForge VulkanMod Reforged 0.3.0-ALPHA 的 Vulkan 适配。Vulkan 使用外部 VulkanMod 的 Vulkan 1.2 设备。相邻 1.21.9、1.21.10 分支的 Fabric 目标为 VulkanMod 0.6.6（发行包文件名虽含 1.21.10，元数据也覆盖 1.21.9）；这两个版本没有匹配的 NeoForge Vulkan 发行包。

普通版显示为 **ModernUI Lumen**，MiSans 版显示为 **ModernUI Lumen (MiSans)**。按游戏版本与加载器选择 JAR，每次安装一种字体版本。模组 ID `modernui`、资源命名空间、Java 包、API、Maven 坐标及 `config/ModernUI/` 路径保持兼容。

以下为本 Fork 的维护范围。维护状态不代表所有加载器、字体版本及渲染后端组合均已完成实机验收。

| 分支 | Minecraft | 状态 |
| --- | --- | --- |
| `1.21.1` | 1.21–1.21.1 | 维护中 |
| `1.21.8` | 1.21.6–1.21.8 | 维护中 |
| `1.21.9` | 1.21.9 | 维护中 |
| `1.21.10` | 1.21.10 | 维护中 |
| `1.21.11` | 1.21.11 | 维护中 |
| `26.1` | 26.1 / 26.1.1 / 26.1.2 | 维护中 |
| `26.2` | 26.2 | 维护中 |
| `master` | 26.3 | 维护中 |

`master` 面向 26.3；各维护分支使用各自的构建工具链和加载器依赖。兼容范围以对应分支元数据为准。

## 功能

本模组在 Minecraft 中提供 ModernUI 运行环境及模组 API，让 ModernUI 应用原生运行；也为原版 GUI 和使用原版 GUI 的模组提供文字引擎增强。应用可独立于 Minecraft 运行，也可通过本模组集成；开发者可结合 ModernUI 与原版界面。

文字系统支持：

- TrueType/OpenType 字体实时预览、重载与字体回退。
- FreeType 字体 hinting、抗锯齿、2D/3D SDF 与批量文字绘制，以及按设备空间计算字形尺寸。
- Noto Color Emoji、Unicode 16.0 emoji，以及聊天中的 Discord、Slack、GitHub、IamCal、JoyPixels 短代码。
- 双向文字方向启发式、可配置阴影与原始字号、Unicode 换行及 CSS line-break / word-break。
- 异步 Unicode 排版、字形图集矩形打包、每像素 1 字节的 alpha mask，减少 GUI、告示牌和发光告示牌的绘制开销与对象分配。
- Minecraft JSON 字体定义（位图和 TTF），以及对应版本的 OptiFine、Sodium/Rubidium、Iris/Oculus 等兼容路径。

界面与体验功能包括：

- 背景颜色、渐入与高斯模糊；无边框全屏和无边框最大化等窗口模式。
- 失焦或最小化时的帧率限制、主音量渐变；单人游戏打开背包时暂停。
- GUI 缩放滑块与提示、进入主菜单的提示音、原版列表和 Forge 滚动面板平滑滚动。
- 文本框撤销/重做和 Unicode 词边界；本地音乐播放、进度控制及频谱。
- 上游缩放扩展适用分支/加载器的 C 键缩放；Fabric 不提供此功能。
- Tooltip 圆角或普通抗锯齿边框、标题分隔和行距、标题居中与 RTL、像素网格定位、渐变及动画边框/背景。
- 原生文字与 Canvas 文字的分数像素移动、字形裁剪边缘和缓存稳定性适配。

参数顺序、uniform 字节布局、输入及渲染适配按游戏版本维护。编译和 CPU 检查不能替代实际客户端页面、交互与物理显卡渲染验收。

## 已知兼容性问题

Minecraft 26.3 / Fabric 的玩家反馈表明，Remove Reloading Screen 设置 `hideOverlays = "ALL"` 时可能报找不到 ModernUI 着色器并崩溃，即使 JAR 内存在文件。在该模组配置的 `[global]` 下将已有项改为以下值并重启，可保留首次加载画面、隐藏后续重载画面：

```toml
[global]
    hideOverlays = "RELOADING"
```

这是玩家实机反馈的配置规避方法；初始化时序原因尚未确认，适用报告范围为 26.3 / Fabric。

## 构建

本分支使用 **JDK 21**；26.3 的 `master` 使用 **JDK 25**。构建工具及依赖以对应 checkout 为准。默认加载器为 Fabric 和 NeoForge。

```sh
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
mc_version=$(sed -n 's/^minecraft_version=//p' gradle.properties)
export GRADLE_USER_HOME="$HOME/.gradle-modernui-$mc_version"
./gradlew clean build -Pfont_variant=standard
# 先收集普通版 JAR，再 clean 构建另一版。
./gradlew clean build -Pfont_variant=misans
```

不同游戏版本使用独立 Gradle 缓存，尤其避免 1.21.9 与 1.21.10 共享 Loom 分层映射缓存。最终文件位于各加载器的 `build/libs/`：

- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal.jar`
- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal-misans.jar`

安装最终 universal JAR。CI 为两种字体版本构建并上传 `modernui-lumen-release-jars` artifact；普通分支推送不发布 Release；推送标签或手动运行工作流并填写 `tag` 时，会创建或更新对应 GitHub Release。稳定 Gradle 项目名 `ModernUI-Fabric`、`ModernUI-NeoForge`、`ModernUI-Forge` 保持兼容；本 Fork 不宣称已发布到上游 Maven 仓库。

## 许可证与署名

* Modern UI for Minecraft
  - Copyright (C) 2019-2026 BloCamLimb et al.
  - [![License](https://img.shields.io/badge/License-LGPL--3.0--or--later-blue.svg?style=flat-square)](https://www.gnu.org/licenses/lgpl-3.0.en.html)
* Additional Assets
  - [source-han-sans](https://github.com/adobe-fonts/source-han-sans) by Adobe, licensed under the OFL-1.1
  - [jetbrains-mono](https://www.jetbrains.com/lp/mono/) by JetBrains, licensed under the OFL-1.1
  - [inter](https://github.com/rsms/inter) by RSMS, licensed under the OFL-1.1
    + Modern UI for Minecraft contains a modified version of Inter font family, the original license and
      copyright notice were retained in the font file

## 上游历史截图

以下截图展示上游功能，不作为本 Fork 当前版本的验收证据。

居中界面  
![2024-03-30_16.17.11.png](https://cdn.modrinth.com/data/3sjzyvGR/images/2571f7372b1f9bbb116c118f29a93255338f4e41.png)
Tooltip 样式  
![new tooltip.png](https://s2.loli.net/2024/03/30/VhyoFPAD2Js1HWO.png)
Markdown  
![markdown](https://cdn.modrinth.com/data/3sjzyvGR/images/989a77ba61c62ff580a30dcf158e391080b949bd.png)  
原版文字增强  
![fast text](https://cdn.modrinth.com/data/3sjzyvGR/images/d27f5d77555fd3f45392f5b8eb28efcb80f0b677.png)
![new4](https://s2.loli.net/2022/03/06/TM5dVKnpqNvDiJH.png)  
导航  
![new5](https://s2.loli.net/2022/03/06/hwAoHTgZNWBvEdq.png)  
图形  
![new3.gif](https://i.loli.net/2021/09/27/yNsL98XtpKP7UVA.gif)  
音频可视化  
![new2](https://i.loli.net/2021/09/24/TJjyzd6oOf5pPcq.png)  
早期控件  
![a](https://i.loli.net/2020/05/15/fYAow29d4JtqaGu.png)
![b](https://i.loli.net/2020/04/10/LDBFc1qo5wtnS8u.png)

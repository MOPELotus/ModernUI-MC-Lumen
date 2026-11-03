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

Fabric、NeoForge 均提供 OpenGL 适配。Minecraft 26.2/26.3 使用游戏自带的 Vulkan 后端，无需 VulkanMod；启动器的**游戏参数**使用 `--graphicsBackend VULKAN` 或 `--graphicsBackend OPENGL`，不要放入 JVM 参数。更早版本的 Vulkan 适配取决于对应 Minecraft/加载器是否有兼容的 VulkanMod 包；缺包组合不视为已支持或测试通过。

### NeoForge 早期加载窗口

Minecraft 26.2 / FML 11.0.16 使用原生 Vulkan 时，在实例 `config/fml.toml` 中设置以下顶层键（放在任何 TOML 节外），保存并重启：

```toml
earlyWindowControl = false
```

文件不存在时可创建并写入该行，或先用 OpenGL 启动生成配置。26.2 的早期窗口是 OpenGL 窗口，不能直接用作 Vulkan surface（`GLFW_NO_API` / 65540）；26.3 已改用 SDL，不适用同一 GLFW 原因。

在已测试的 Linux / NVIDIA 580.178.04 / Tesla T10 环境，NeoForge **26.3.0.101 / FML 12.0.12** 的 OpenGL 和原生 Vulkan 也需关闭此窗口。开启时，OpenGL 可在 NVIDIA 驱动中启动崩溃；Vulkan 退出时会遗留早期界面的 `ScreenSize UBO` 与 `shared_error` GPU buffer。两者均在未安装 ModernUI 的对照中复现；OpenGL 的准确上游原因及其他驱动表现仍未确定。关闭窗口是该测试配置的兼容条件，不能据此推断全部功能或其他设备已通过。

### Remove Reloading Screen（26.3 / Fabric）

玩家反馈与该模组同时使用且其 `hideOverlays = "ALL"` 时，可能启动崩溃并报告缺失实际存在的着色器。已反馈有效的规避方法是在 **Remove Reloading Screen 的配置**中修改已有的 `[global]` 项并重启：

```toml
[global]
    hideOverlays = "RELOADING"
```

这会保留首次加载屏幕，仅隐藏后续资源重载屏幕。该说明是玩家实机调查的配置规避方案，初始化顺序原因仍属推测，尚无代码修复或其他版本/加载器的验证结论。

## 构建

使用所选分支要求的 JDK；`master`（26.3）使用 **JDK 25**，默认构建 Fabric 和 NeoForge。当前固定 NeoForge **26.3.0.101**；更早的 26.3 beta 配置 API 不兼容。实际版本以 `gradle.properties` 和 Gradle wrapper 为准。

```sh
./gradlew clean build -Pfont_variant=standard
# 清理前先收集普通版产物
./gradlew clean build -Pfont_variant=misans
```

最终 JAR 位于各加载器的 `build/libs/`：

- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal.jar`
- `ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal-misans.jar`

CI 构建两版并上传 `modernui-lumen-release-jars` artifact。普通分支推送不会发布 Release；标签和发行仍需单独的发布指令。Gradle 项目/任务名与 Maven 坐标保持兼容，本 Fork 不宣称新版已发布到上游 Maven。

## 验证边界

移植会按目标版本实际 API 核对 tooltip 参数与 uniform 布局、键盘/鼠标输入、窗口切换及 GPU 资源所有权。26.3 使用 SDL 和 RenderPearl，不能直接沿用旧版 GLFW 或 VulkanMod 接口。

CPU 检查和编译通过不能代替物理显卡上的实际页面操作、截图、渲染器日志及正常退出验证。跨版本矩阵按准确 JAR、加载器、Minecraft 版本和后端分别记录，本文不作全矩阵已通过的声明。

## 许可证与致谢

Modern UI for Minecraft：Copyright (C) 2019–2026 BloCamLimb et al.，采用 [LGPL-3.0-or-later](LICENSE)。Fork 维护：MOPELotus。

附加字体资源包括 Adobe [Source Han Sans](https://github.com/adobe-fonts/source-han-sans)、JetBrains [JetBrains Mono](https://www.jetbrains.com/lp/mono/) 和 RSMS [Inter](https://github.com/rsms/inter)，均保留各自 OFL 许可。随附的修改版 Inter 保留原许可及字体内版权声明。MiSans 使用其官方许可，下载和使用前需阅读对应条款。

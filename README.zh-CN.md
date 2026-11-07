[EN](README.md) | [CN](README.zh-CN.md)

# ModernUI Lumen

ModernUI Lumen 是 [Modern UI for Minecraft](https://github.com/BloCamLimb/ModernUI-MC) 的维护分支，由 **MOPELotus** 维护，原作者是 **BloCamLimb**。

Modern UI 把自己的界面框架带进了 Minecraft，也为原版界面提供了新的文字排版和渲染。你可以换字体、调整背景和物品提示的样式，其他模组也可以用它来做界面。

Lumen 主要继续维护下面这些 Minecraft 版本，并修复滚动歌词的文字抖动、tooltip、输入和不同渲染后端的问题。当前发布版本是 **3.13.0.10**。

[下载](https://github.com/MOPELotus/ModernUI-MC-Lumen/releases/tag/v3.13.0.10) · [更新记录](changelogs.md) · [问题反馈](https://github.com/MOPELotus/ModernUI-MC-Lumen/issues)

## 功能

大部分功能和上游相同，安装后按 **Ctrl+K** 打开操作中心，在「偏好设置」里调整：

- 在「字体」页选择「首选字体家族」，设置「回退字体家族列表」和「字体注册列表」。支持 TrueType / OpenType 字体。
- 「现代文本引擎」提供 Unicode 排版、「抗锯齿」和「彩色表情符号」；聊天中也可以使用「表情符号简码」。
- 调整「模糊效果」「背景淡入时长」和「背景不透明度」。
- 为物品提示设置「边框样式」「圆角化」「标题居中对齐」和「动态颜色」。
- 开启「平滑滚动」「物品栏暂停」和「游戏加载提示音」，或调整「窗口模式」、窗口失焦后的帧率和音量。

还保留了上游的文本框撤销 / 重做、本地音乐播放和频谱显示。

## 安装

下载与你的 Minecraft 版本、加载器对应的 JAR，放进 `mods` 文件夹。支持 Fabric 和 NeoForge。

选文件时看下方表格中的兼容范围：文件名中的 `1.21.8` 对应 1.21.6–1.21.8，`26.1.2` 对应 26.1–26.1.2。

- **Fabric** 需要 Fabric API。
- 配置功能已内置，**不需要 Forge Config API Port**。ModernUI Core 也已打包在 JAR 里，无需另装。
- 不能与上游 Modern UI 同时安装：两者使用相同的 `modernui` 模组 ID。切换时替换 JAR 即可。

GitHub 提供普通版和 MiSans 版，向 CurseForge 提交的只有普通版。普通版和 MiSans 版的区别见[字体版本说明](docs/font-editions.md)。

## 支持版本

| Minecraft | 分支 | 加载器 | Java | 状态 |
| --- | --- | --- | --- | --- |
| 1.21–1.21.1 | `1.21.1` | Fabric / NeoForge | 21+ | 维护中 |
| 1.21.6–1.21.8 | `1.21.8` | Fabric / NeoForge | 21+ | 维护中 |
| 1.21.9 | `1.21.9` | Fabric / NeoForge | 21+ | 维护中 |
| 1.21.10 | `1.21.10` | Fabric / NeoForge | 21+ | 维护中 |
| 1.21.11 | `1.21.11` | Fabric / NeoForge | 21+ | 维护中 |
| 26.1–26.1.2 | `26.1` | Fabric / NeoForge | 25+ | 维护中 |
| 26.2 | `26.2` | Fabric / NeoForge | 25+ | 维护中 |
| 26.3 | `master` | Fabric / NeoForge | 25+ | 维护中 |

## Vulkan 和已知兼容问题

26.2 / 26.3 可以使用 Minecraft 自带的 Vulkan，不需要 VulkanMod。在启动器的**游戏参数**中填写 `--graphicsBackend VULKAN`；切回 OpenGL 用 `--graphicsBackend OPENGL`。不要把它们填进 JVM 参数。

更早的版本需要对应 Minecraft 和加载器的 VulkanMod。没有合适的包时，使用 OpenGL 即可。

<details>
<summary>NeoForge 早期加载窗口</summary>

26.2 的原生 Vulkan 需要关闭早期加载窗口。在实例的 `config/fml.toml` 中添加下面这一行，放在所有 TOML 节之前，然后重启：

```toml
earlyWindowControl = false
```

我们在 Linux / NVIDIA 上测试的 NeoForge 26.3.0.101 也需要这个设置，否则可能启动崩溃或在退出时报 GPU 资源未释放。其他系统不一定有同样表现。

</details>

<details>
<summary>Remove Reloading Screen（26.3 / Fabric）</summary>

如果同时使用 Remove Reloading Screen，并在启动时遇到缺少着色器的报错，可以把它配置中的 `[global]` → `hideOverlays` 从 `"ALL"` 改成 `"RELOADING"`，然后重启。这样会保留首次加载画面，只隐藏之后的资源重载画面。

</details>

## 构建

使用对应分支要求的 JDK，在仓库根目录运行：

```sh
./gradlew build -Pfont_variant=standard
```

成品在 `fabric/build/libs/` 和 `neoforge/build/libs/`。构建 MiSans 版时将参数改为 `-Pfont_variant=misans`。

## 许可证与致谢

原模组及 [ModernUI Framework](https://github.com/BloCamLimb/ModernUI) 由 **BloCamLimb 及贡献者**开发，采用 [LGPL-3.0-or-later](LICENSE)。Lumen 分支由 **MOPELotus** 维护。

随附的 [思源黑体](https://github.com/adobe-fonts/source-han-sans)、[JetBrains Mono](https://www.jetbrains.com/lp/mono/) 和 [Inter](https://github.com/rsms/inter) 使用 OFL 许可；原许可证和字体内版权声明均予以保留。MiSans 使用小米官方许可。

[CN](README.md) | [EN](README.en.md)

# ModernUI Lumen

**ModernUI Lumen** 是 **MOPELotus** 独立维护的 [Modern UI for Minecraft](https://github.com/BloCamLimb/ModernUI-MC) 分支，原作者为 **BloCamLimb**，基于 [ModernUI 框架](https://github.com/BloCamLimb/ModernUI)。项目保留上游署名和许可证，不属于上游官方发行版。当前模组版本为 **3.13.0.10**。

[源码](https://github.com/MOPELotus/ModernUI-MC-Lumen) · [问题反馈](https://github.com/MOPELotus/ModernUI-MC-Lumen/issues) · [发行页面](https://github.com/MOPELotus/ModernUI-MC-Lumen/releases) · [更新记录](changelogs.md)

## 功能

模组为 Minecraft 提供 ModernUI 界面运行环境、开发 API，以及原版 GUI 的现代文字排版和渲染扩展。主要功能包括字体选择与回退、TrueType/OpenType 字体加载、抗锯齿文字、双向文本与换行处理、文字编辑撤销/重做、平滑滚动、可配置 tooltip、背景模糊、窗口模式、失焦帧率与音量控制、本地音乐播放和频谱显示。

走马灯保留浮点位移、采样与裁剪适配。Minecraft 文字引擎与 Core 控件有不同的字形路径，不能将原版彩色 emoji 渲染能力等同于所有 Core 控件都支持彩色 emoji 或 IME 预编辑。NeoForge 提供 C 键缩放扩展；Fabric 不提供此扩展。与其他渲染模组的兼容性须按具体版本、加载器及后端验证。

## 字体版本与安装

- **普通版（standard）**：默认首选 `Inter Frozen Medium`，回退包含 `Source Han Sans CN Medium`（思源黑体）等；可修改首选、回退及注册字体列表，无需安装 MiSans。
- **MiSans 版**：显示名为 `ModernUI Lumen (MiSans)`，使用 MiSans、MiSans L3、MiSans Latin、MiSans TC 字体组合。字体缺失时提供许可查看、明确点击下载、进度、失败重试和退出重启入口；从小米获取四套字体包中的 28 个 TTF、许可 PDF，并保存下载记录。

两版模组 ID 都是 `modernui`，**每次只安装其中一版**，切换时替换 JAR。资源命名空间、Java 包、API 和 `config/ModernUI/` 路径保持兼容。配置路径、字重映射和下载行为见[字体版本说明](docs/font-editions.md)。

## 维护分支

下列八个版本系列均在维护。维护范围不表示所有 Minecraft／加载器／后端组合已经完成运行验收；当前仍在逐组合核对功能、样式和物理显卡渲染结果。安装时以对应分支的模组元数据为准。

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

`master` 跟进 26.3，各分支保留自己的工具链与加载器依赖，不保证跨系列共用 JAR。

## 本分支：Minecraft 1.21.10

提供 Fabric 与 NeoForge、普通版与 MiSans 版，共四种构建产物。两种加载器以 OpenGL 为目标后端；Fabric 的 Vulkan 组合使用外部 VulkanMod 0.6.6，其包元数据包含 1.21.9 和 1.21.10。当前记录的 NeoForge 1.21.10 Vulkan 组合缺少对应发行包，明确列为缺包组合，不计作已测试通过。

Vulkan 集成使用外部后端的设备；不能仅凭构建成功、程序启动或检测到显卡判断完成验收。实际图形验收须确认客户端日志中的物理设备，并操作和查看目标页面。tooltip、输入事件和 uniform 接口仍需按版本分别核对。

## 构建

使用 **JDK 21**，依赖及 Gradle wrapper 以本分支为准。请为 1.21.9 与 1.21.10 使用独立 `GRADLE_USER_HOME`，避免 Loom 分层映射缓存混用。

```sh
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="$HOME/.gradle-modernui-1.21.10"
./gradlew clean build -Pfont_variant=standard
# 先复制普通版最终 JAR，再进行下一次 clean。
./gradlew clean build -Pfont_variant=misans
```

最终重映射产物位于 `fabric/build/libs` 和 `neoforge/build/libs`：

- 普通版：`ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal.jar`
- MiSans 版：`ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal-misans.jar`

安装最终产物，不使用 `-shadow.jar` 或开发 JAR。省略 `font_variant` 时构建普通版。本分支工作流在推送到 `1.21.10`、向该分支提交 PR 或手动运行时构建，也接受标签推送。CI 构建两种字体版本并上传 `modernui-lumen-release-jars`；普通分支推送只构建产物；标签推送或手动工作流填写非空 release tag 时，工作流创建或更新 GitHub Release。

Gradle 项目名 `ModernUI-Fabric`、`ModernUI-NeoForge`、`ModernUI-Forge` 及 Maven 坐标保持兼容；项目名存在不代表本分支启用了全部加载器，也不表示本分支已发布到上游 Maven 仓库。

## 署名与许可证

- Modern UI for Minecraft：Copyright (C) 2019–2026 BloCamLimb et al.，采用 [LGPL-3.0-or-later](https://www.gnu.org/licenses/lgpl-3.0.en.html)。
- [Source Han Sans](https://github.com/adobe-fonts/source-han-sans)：Adobe，OFL-1.1。
- [JetBrains Mono](https://www.jetbrains.com/lp/mono/)：JetBrains，OFL-1.1。
- [Inter](https://github.com/rsms/inter)：RSMS，OFL-1.1；所含修改版 Inter 保留原始许可证与版权声明。
- MiSans 字体版权归 Xiaomi，使用须遵守其字体知识产权许可协议；MiSans 版提供官方许可入口和下载流程，不将 MiSans 字体作为仓库内自由再许可资产。

仓库中的许可证原文保持适用；本说明不替代任何许可证。

[CN](changelogs.md) | [EN](changelogs.en.md)

# 更新记录

## ModernUI Lumen 3.13.0.10

- 修复在当前界面切换模糊效果后未即时生效的问题，并保留屏幕排除规则与背景淡入。
- 项目与游戏内显示名称使用 ModernUI Lumen；MiSans 版使用 ModernUI Lumen (MiSans)。保留原作者署名、许可证及兼容标识。
- 提供普通版、MiSans 版和各自配置，支持 Fabric 与 NeoForge。
- 改善低速文字滚动、字形采样、边缘裁剪与缓存复用，并按目标 Minecraft API 适配输入、提示框与资源格式。
- 完善图形后端之间的资源交接、音量淡出和窗口/缩放行为；具体兼容条件及能力限制见 [README](README.md)。
- 提供 MiSans 字体检查、用户主动下载、校验、重试与字重选择，详见[字体版本说明](docs/font-editions.md)。

## 上游历史概览

以下是上游功能发展的中文概览。**完整逐版本历史保留在[英文页的上游历史归档](changelogs.en.md#full-upstream-history)，该归档未逐条翻译**。历史记录中的旧 Minecraft 版本、加载器和功能描述反映当时范围，不表示当前维护承诺。

| 历史阶段 | 主要变化 |
| --- | --- |
| Modern UI 3.13 | Vulkan 后端、全局音量选项、主题与资源 API、文字和图形框架改进；包含 Beryl 兼容及 UI 线程加载修复。 |
| Modern UI 3.12–3.8 | 持续适配 Minecraft，完善文字排版与渲染、提示框、界面控件、资源加载和兼容性。 |
| Modern UI 3.7–3.2 | 改进原生界面、布局与图形框架，修复字体、输入、窗口和生命周期问题。 |
| Modern UI 3.1–3.0 | Minecraft 1.17.1 适配及框架、界面和文字系统演进。 |
| 1.16.x 系列 | 字体与 Unicode 排版、GUI 缩放、模糊、加载音效、文本输入和版本兼容修复。 |
| 1.15.2 系列 | 初始发布、动画与控件、背景效果、字体渲染和界面 API；随后持续修复布局与交互。 |

上游历史与本 Fork 的维护范围不同。当前维护分支见[分支表](README.md#维护分支)。

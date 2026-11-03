[CN](changelogs.md) | [EN](changelogs.en.md)

# 更新记录

## ModernUI Lumen 3.13.0.10

- 统一 ModernUI Lumen 项目、游戏内名称及成品命名，保留原作者署名和许可证；模组 ID 与配置路径保持兼容。
- 提供普通版与 MiSans 版，分别使用独立客户端配置；MiSans 版提供用户确认的字体安装与九档字重。
- 适配 Minecraft 1.21.9 的 Fabric 与 NeoForge，包含 tooltip 参数与 uniform、输入修饰键、文字采样、界面测量及窗口行为适配。
- 完善 VulkanMod 集成中的同步、着色器布局、运行时 ABI 与资源生命周期处理。后端和依赖范围见 [README](README.md)。
- 构建与 CPU 检查覆盖关键接口；完整客户端、物理显卡及交互验收另行记录，不由本更新记录宣称完成。

## 上游历史概览

本 Fork 基于 BloCamLimb 的 Modern UI for Minecraft 与 ModernUI 框架，保留上游署名和许可证。

- 3.13 系列：扩展渲染后端、主题、资源系统、文字与窗口接口，并完善多个加载器适配。
- 3.12 系列：持续更新界面框架、文字引擎、图形与着色器编译器。
- 更早版本：逐步完善 Unicode 排版、字体回退、控件、tooltip、渲染性能及 Minecraft 版本适配。

完整上游版本条目按原文保留在[英文更新记录的上游历史归档](changelogs.en.md#upstream-history-archive)，未逐条翻译。历史条目中的版本、加载器和功能范围仅指对应上游版本，不代表本分支当前支持或验收范围。

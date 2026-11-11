[CN](changelogs.md) | [EN](changelogs.en.md)

# 更新记录

## ModernUI Lumen 3.13.0.13

- Fabric Loader 的最低要求改为 0.20.1，各分支的 Fabric API 要求也与全量测试使用的版本对齐。

## ModernUI Lumen 3.13.0.12

- 修复多段逐字歌词等场景中，替换 Span 测量后续段落时的越界崩溃。
- 修复截取文本时段落 Span 的范围，以及复制大量 Span 后查找不存在的 Span 时返回错误结果的问题。
- 修复空缩进和左右缩进列表不等长时的排版问题，避免布局缓存反复存储同一个超长单词。
- 文本布局删除对象或回收临时布局时，清除对旧对象的引用。
- 修复深层双向文字的光标移动，以及同一布局使用超过 256 种字体时选错字形字体的问题。

## ModernUI Lumen 3.13.0.11

- 修复 Vulkan 退出时的资源释放顺序：先等待 GPU 完成并回收 Lumen 自己的资源，再关闭分配器。
- 退出时关闭剩余界面、取消待执行的帧回调，并忽略迟到的输入。旧界面的回调也不会再关闭新界面。
- 修复 Span 列表扩容时丢失范围和标记的问题；OpenGL 纹理销毁后跳过排队中的标签更新。
- Fabric 内置 MixinExtras 0.5.5，无需另装即可使用新版的重定向注解。
- 原生 TextView 和 Canvas 支持彩色 Emoji，包括组合 Emoji；重载资源后，保留的文本也能正常显示。

## ModernUI Lumen 3.13.0.10

- 修复在当前界面切换模糊效果后未即时生效的问题，并保留屏幕排除规则与背景淡入。
- Fork 更名为 ModernUI Lumen，仓库使用 ModernUI-MC-Lumen；保留 BloCamLimb 原作者署名并添加维护者 MOPELotus。模组 ID、API 和配置路径保持兼容。
- 提供普通版与 MiSans 版，采用各自的客户端配置文件；详见[字体版本说明](docs/font-editions.md)。
- 维护 README 所列八个 Minecraft 版本分支，包括由 `1.21.8` 统一覆盖的 1.21.6–1.21.8。
- 包含走马灯分数像素布局与采样、tooltip uniform 布局、输入修饰键及逐版本后端适配。兼容条件和验证边界见 [README](README.zh-CN.md)。
- 当前版本仍按准确 Minecraft / 加载器 / 后端 / 字体版本进行验收；版本号和本记录不代表已发布 Release 或全部组合已通过。

## 上游历史概览

上游 3.13 系列完善 Vulkan 渲染、主题、字体与文字排版、界面和加载兼容；3.12 及更早版本持续改进 ModernUI Core、文本引擎和各加载器扩展。

此处是中文历史概览，**不是逐条译文**。完整英文上游记录从 Modern UI 3.13.0.5 起保留在[英文历史档案](changelogs.en.md#upstream-history)，其日期、版本和功能描述属于对应历史版本，不代表本 Fork 当前已验收或发布的状态。

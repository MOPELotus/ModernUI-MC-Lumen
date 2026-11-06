[CN](README.md) | [EN](README.en.md)

# 项目字段

| 字段 | 建议填写 |
| --- | --- |
| Game | Minecraft |
| Class | Mods |
| Name | ModernUI Lumen |
| Slug | `modernui-lumen`（以创建时可用性为准） |
| Summary | Modern UI framework and enhanced text rendering, with maintained Minecraft ports, smoother scrolling text, customizable fonts, tooltips and UI effects. |
| Main category | Cosmetic |
| Additional categories | API and Library；Utility & QoL（以网站实际分类为准） |
| License | LGPLv3；完整许可为 LGPL-3.0-or-later，链接仓库 LICENSE |
| Source | https://github.com/MOPELotus/ModernUI-MC-Lumen |
| Issues | https://github.com/MOPELotus/ModernUI-MC-Lumen/issues |
| Description | [description.md](description.md) 全文 |
| Logo / images | [assets/README.md](assets/README.md) |
| Distribution | Client mod；Fabric / NeoForge；仅普通版 |

英文摘要为 151 个字符，保持单行。项目成员填实际管理账号；正文署名保留原作者 **BloCamLimb** 和 Fork 维护者 **MOPELotus**，无需把原作者添加为项目管理成员。

发布文件名：`ModernUI-Lumen-{Loader}-{Minecraft}-3.13.0.10-universal.jar`。不要上传 `-misans.jar`、源码 JAR、开发包或测试组件。每个分支分别提供 Fabric 和 NeoForge 文件；共八个维护分支、十三个 Minecraft 版本范围、十六个普通版 JAR，不能将所有版本合并标记到一个文件。

| 源码分支 | 文件适用 Minecraft | Java | Fabric 必需附加模组 |
| --- | --- | --- | --- |
| `1.21.1` | 1.21、1.21.1 | 21+ | Fabric API |
| `1.21.8` | 1.21.6、1.21.7、1.21.8 | 21+ | Fabric API |
| `1.21.9` | 1.21.9 | 21+ | Fabric API |
| `1.21.10` | 1.21.10 | 21+ | Fabric API |
| `1.21.11` | 1.21.11 | 21+ | Fabric API |
| `26.1` | 26.1、26.1.1、26.1.2 | 25+ | Fabric API |
| `26.2` | 26.2 | 25+ | Fabric API |
| `master` | 26.3 | 25+ | Fabric API |

所有维护分支均内置配置实现，无需 Forge Config API Port。Fabric 只需额外安装对应 Minecraft 的 Fabric API，ModernUI Core 已打包。加载器最低版本及具体范围以每个 JAR 的元数据为准。此处是维护与上传范围，不表示任意其他模组、驱动和后端组合都兼容。

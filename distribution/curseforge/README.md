[CN](README.md) | [EN](README.en.md)

# CurseForge 上架材料

这里准备 ModernUI Lumen **普通版**的项目介绍、更新说明和项目字段。当前版本为 **3.13.0.10**。MiSans 版不向 CurseForge 上传；本目录及工作流准备本身不代表已经发布。

- [项目字段](project-fields.md)：创建项目时填写。
- [项目介绍](description.md)：全文复制到项目介绍，英文在前，中文在后。
- [3.13.0.10 更新说明](changelog-3.13.0.10.md)：上传对应版本文件时使用。
- [图标与截图](assets/README.md)：上传图标和展示图片。

仓库使用中文为默认文档语言；CurseForge 的公开介绍按其提交指南使用英文在前、中文在后。

## 创建与后续自动发布

先在 CurseForge 网页创建 Minecraft Mods 项目，填写项目字段并提交审核。创建后取得**数字 Project ID**；项目 slug 不能替代这个 ID。项目创建和平台审核不能仅靠上传工作流完成。

之后在 GitHub 仓库 Settings → Secrets and variables → Actions 中设置：

| 类型 | 名称 | 内容 |
| --- | --- | --- |
| Secret | `CURSEFORGE_TOKEN` | 具有该项目上传权限的 CurseForge API token |
| Variable | `CURSEFORGE_PROJECT_ID` | 项目的数字 ID |
| Variable | `CURSEFORGE_ENABLED` | 确认准备发布后设置为 `true`；未设置时禁止上传 |

不要把 token 写进文件或提交记录。工作流在仓库的 Actions → **CurseForge 普通版** 中，选择 `master`，保留 `publish=false` 即可试跑：它会构建 8 个分支的 16 个普通版 JAR，并检查版本、加载器和依赖，不上传。

准备正式发布时，再设置 `CURSEFORGE_ENABLED=true`。通过网页发布 GitHub Release 会自动触发上传；仓库的 Build and Release 工作流创建 Release 后也会调用它。手动上传时选择 `publish=true`，填写已有 GitHub Release 的标签 `3.13.0.10` 或 `v3.13.0.10`。首次上传前确认 CurseForge 已提供所有目标 Minecraft 版本标签；缺少任何标签都会停止整批上传。

发布清单在 [`release/curseforge-targets.json`](../../release/curseforge-targets.json)，固定了各分支的源码提交和支持范围。下次更新版本时，同时更新清单、源码提交和更新说明，再试跑一次。

上传开始前，工作流会向 GitHub Release 添加版本预留文件，上传回执保存在 Actions 产物中。遇到中途失败时，先查看回执和 CurseForge 已收到的文件，再处理剩余文件；直接重跑会被拦截，以免重复上传。

Fabric 文件需要将 Fabric API 设为 Required Dependency。各维护分支的配置功能均已内置，不需要 Forge Config API Port；NeoForge 文件也不需要额外配置模组。ModernUI Core 已随成品打包。

## 提交依据

[CurseForge 项目提交指南](https://support.curseforge.com/support/solutions/articles/9000199552) · [审核与 Fork 规则](https://blog.curseforge.com/how-to-pass-moderation-review-on-curseforge-2/)。材料按 2026-11-06 查阅的规则准备，实际审核由平台决定。

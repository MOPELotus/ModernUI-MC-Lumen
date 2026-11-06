[CN](README.md) | [EN](README.en.md)

# CurseForge submission materials

This directory prepares **standard-edition** ModernUI Lumen submissions for version **3.13.0.10**. MiSans artifacts must not be uploaded to CurseForge. Preparing these files or the workflow does not publish a release.

- [Project fields](project-fields.md): values for project creation.
- [Project description](description.md): copy the entire English-first, Chinese-second text into the description.
- [3.13.0.10 changelog](changelog-3.13.0.10.md): notes for the matching files.
- [Icon and screenshots](assets/README.md): submission graphics.

Repository guides default to Chinese. The public CurseForge description starts with English, following the platform's submission guidance.

## Project creation and later automated uploads

Create a Minecraft Mods project on the CurseForge website, fill in its fields, and submit it for review. Obtain its **numeric Project ID** afterward; a slug is not a replacement. An upload workflow does not create or approve a project.

Then configure GitHub repository Settings → Secrets and variables → Actions:

| Type | Name | Value |
| --- | --- | --- |
| Secret | `CURSEFORGE_TOKEN` | CurseForge API token with upload permission for the project |
| Variable | `CURSEFORGE_PROJECT_ID` | Numeric project ID |
| Variable | `CURSEFORGE_ENABLED` | Set to `true` only when ready to enable uploads; unset blocks uploads |

Keep the token out of files and commits. Open Actions → **CurseForge 普通版**, select `master`, and leave `publish=false` for a dry run. It builds the 16 standard-edition JARs from all eight branches and checks their versions, loaders, and dependencies without uploading.

When ready to publish, set `CURSEFORGE_ENABLED=true`. Publishing a GitHub Release through the website triggers uploads; the repository’s Build and Release workflow also calls this workflow after creating a release. For a manual upload, select `publish=true` and enter the tag of an existing GitHub Release: `3.13.0.10` or `v3.13.0.10`. All required Minecraft version labels must exist on CurseForge; a missing label stops the entire batch before upload.

[`release/curseforge-targets.json`](../../release/curseforge-targets.json) pins each branch to a source commit and lists its supported versions. For the next release, update the plan, source pins, and changelog together, then run another dry run.

Before uploading, the workflow adds a reservation file to the GitHub Release. Upload receipts are kept as Actions artifacts. If an upload fails partway through, inspect the receipt and the files already on CurseForge before handling the remaining files. A direct rerun is blocked to prevent duplicates.

Fabric files require Fabric API as a Required Dependency. Configuration is built in on every maintained branch; Forge Config API Port is not required. NeoForge needs no additional configuration mod. ModernUI Core is bundled.

## References

[CurseForge submission guide](https://support.curseforge.com/support/solutions/articles/9000199552) · [Moderation and fork rules](https://blog.curseforge.com/how-to-pass-moderation-review-on-curseforge-2/). Prepared against guidance checked on November 6, 2026. Submission remains subject to platform review.

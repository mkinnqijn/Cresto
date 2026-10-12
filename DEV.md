# Cresto Dev：独立安装、独立更新通道

这个 fork 里同时存在两条构建/更新通道，它们互不影响：

| | 官方 Cresto | Cresto Dev（个人开发版） |
|---|---|---|
| 构建类型 | `release` | `debug` |
| applicationId | `com.nevoit.cresto` | `com.nevoit.cresto.dev` |
| 显示名称 | `Cresto` | `Cresto Dev` |
| versionName | `1.0` | `1.0-dev` |
| 更新清单 | `https://nevodev.github.io/cresto/update/latest.json`（upstream 原样，未改动） | `https://github.com/mkinnqijn/Cresto/releases/latest/download/latest.json` |
| 数据目录 | 自己的私有目录 | 自己的私有目录（与官方完全隔离） |

两者可以同时安装；Dev 版不会读取、修改或卸载官方版的数据。更新检查与安装流程复用项目原有的
`UpdateChecker` / `UpdateBottomSheet`（点“下载更新”会用系统浏览器打开 APK 链接），没有另写一套更新系统。

## 相关的几处改动

- `app/build.gradle.kts`
  - `debug` build type：`applicationIdSuffix = ".dev"`、`versionNameSuffix = "-dev"`，并把
    `UPDATE_MANIFEST_URL` 覆盖为上面的 Dev 清单地址（`release` 仍用 `defaultConfig` 里的官方地址）。
  - 新增可选开关 `-PdevCompileSdk=36`：API 37 的 SDK platform 目前尚未公开发布，需要时可临时用 36 编译，
    不传该参数时仍然是仓库原本的 37。
  - Dev APK 的输出文件名固定为 `cresto-dev-<versionCode>.apk`。
- `.github/workflows/dev-release.yml`：打 tag 后自动构建并发布 Dev 版本（见下）。
- `tools/dev_manifest.py`：生成 Dev 更新清单（字段与 `UpdateManifest.kt` 一致，CI 与手动发布共用）。
- `.gitignore`：新增 `*.jks` / `*.keystore` / `*.p12` / `signing.properties` / `keystore.properties`，
  防止签名材料被误提交。

## 一次性准备

1. **创建 Dev 专用签名密钥库**（只做一次，之后所有 Dev 版本都用它，才能相互覆盖升级）：

   ```powershell
   keytool -genkeypair -v -keystore cresto-dev.jks -alias cresto-dev `
     -keyalg RSA -keysize 4096 -validity 10000
   ```

   把 `cresto-dev.jks` 保存在仓库**之外**（例如 `%USERPROFILE%\keys\cresto-dev.jks`）。

2. **在 GitHub 仓库里配置 4 个 Secrets**（Settings → Secrets and variables → Actions）：

   | Secret | 值 |
   |---|---|
   | `DEV_KEYSTORE_BASE64` | `[Convert]::ToBase64String([IO.File]::ReadAllBytes("cresto-dev.jks"))` 的输出 |
   | `DEV_KEYSTORE_PASSWORD` | 密钥库口令 |
   | `DEV_KEY_ALIAS` | `cresto-dev` |
   | `DEV_KEY_PASSWORD` | 密钥口令 |

   这些值只存在于 Secrets 中，不会进入仓库。

3. **（可选）让本机构建与 CI 使用同一签名**。在 `~/.gradle/gradle.properties`（位于仓库之外）里写：

   ```properties
   MOMENTO_SIGNING_STORE_FILE=C:/Users/<你>/keys/cresto-dev.jks
   MOMENTO_SIGNING_STORE_PASSWORD=...
   MOMENTO_SIGNING_KEY_ALIAS=cresto-dev
   MOMENTO_SIGNING_KEY_PASSWORD=...
   ```

   项目原有的签名逻辑会读取这四个值（Gradle 属性或同名环境变量），Dev 与官方 release 都用它；
   只在本地构建 Dev 时也可以不配置，那样会用本机 debug keystore（但与 CI 产物签名不同，二者不能相互覆盖）。

## 发布一个新的 Dev 版本

### 方式 A：CI 一键发布（推荐）

```powershell
cd <repo>
# 1. 提高 versionCode（复用项目自带的 Gradle 任务）
.\gradlew :app:incrementVersionCode
# 2. 提交并推送
git add app/version.properties; git commit -m "chore: bump dev version code"
git push origin personal
# 3. 打 tag 并推送，触发 Dev 发布工作流
$code = (Select-String -Path app\version.properties -Pattern '^VERSION_CODE=(\d+)').Matches.Groups[1].Value
git tag "dev-v$code"; git push origin "dev-v$code"
```

工作流会：校验 tag 与 `version.properties` 一致 → 校验 versionCode 高于上一个 Dev 版本 → 用 Secrets 里的密钥库签名
→ 构建 `:app:assembleDebug` → 校验产物包名是 `com.nevoit.cresto.dev` 且已签名 → 生成 Dev 清单
→ 创建 `dev-v<code>` Release 并上传 APK 与 `latest.json`（标记为 latest）。

发布完成后，手机上的 Cresto Dev 打开「设置 → 关于 → 检查更新」即可看到新版本并下载安装。

### 方式 B：手动发布

```powershell
# 1. 本地构建（设好 MOMENTO_SIGNING_* 或直接用 debug keystore）
.\gradlew :app:assembleDebug -PdevCompileSdk=36 -x :app:checkDebugAarMetadata
# 2. 生成 Dev 清单
python tools/dev_manifest.py --repo mkinnqijn/Cresto --version-code <code> --version-name 1.0-dev --output .dev-update/latest.json
# 3. 创建 Release 并上传 APK + 清单（latest.json 的资产名必须是 latest.json）
gh release create "dev-v<code>" "app/build/outputs/apk/debug/cresto-dev-<code>.apk" ".dev-update/latest.json" --title "Cresto Dev <code>" --latest
```

## 规则与注意事项

- **versionCode 必须递增**：Dev 清单与新版本比较的是 `versionCode`。用 `:app:incrementVersionCode`
  （或手改 `app/version.properties`）后提交，tag 号与之一致；工作流里有双重校验。
- **同一个签名**：所有 Dev APK 必须用同一个密钥库，否则安装更新时会 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
  （只需卸载 Dev 版重装，官方版不受影响）。若换过密钥，请先卸载 `com.nevoit.cresto.dev`。
- **APK 包名固定为 `com.nevoit.cresto.dev`**：工作流会校验，避免误发成官方包名而覆盖官方版。
- **不要把 `cresto-dev.jks`、口令、API Key 提交到仓库**；CI 只通过 Secrets 读取。
- **官方通道不受影响**：`release` 构建的 applicationId、版本号、显示名与更新清单地址都与 upstream 一致。
- Dev 版与官方版数据、AI 配置、通知、小组件都是各自独立的；Dev 版首次使用需要单独配置 AI Key 与 Shizuku 授权。

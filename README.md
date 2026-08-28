<div align="center">
  <h1>BITS Box</h1>
  <p>
    <a href="https://bits.co.id">
      <img src="https://img.shields.io/badge/Banten%20IT%20Solutions-BITS%20Box-00C853?style=for-the-badge&logo=android&logoColor=white" alt="BITS Box" />
    </a>
  </p>
  <p>
    Multi-protocol proxy / VPN client for Android powered by the <a href="https://github.com/SagerNet/sing-box">sing-box</a> kernel
  </p>
  <br>
  <p>
    <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Android" />
    <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
    <img src="https://img.shields.io/badge/Core-Go-00ADD8?logo=go&logoColor=white" alt="Go" />
    <img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="GPLv3 License" />
  </p>
</div>

---

## ✨ Features

| Feature             | Description                                                                                                                    |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| **Multi-protocol**  | Single client for `VMess`, `VLESS`, `Trojan`, `Shadowsocks`, `TUIC`, `Hysteria`, `WireGuard`, `HTTP/SOCKS`, `SSH`, `TUN` mode. |
| **sing-box kernel** | High-performance core with `gVisor`, `QUIC`, `UTLS`, `Clash API`, and `conntrack` support.                                     |
| **Professional UX** | Material Design interface with themed styling, config editor, and profile management.                                          |
| **Privacy first**   | No telemetry, no ads — all connectivity is explicit and user-controlled.                                                       |
| **Multi-ABI**       | Optimized builds for `armeabi-v7a`, `arm64-v8a`.                                                                               |

## 🛠️ Tech Stack

| Layer            | Technology                                              |
| ---------------- | ------------------------------------------------------- |
| **Frontend**     | Android, Kotlin, Material Design                        |
| **Networking**   | sing-box, libcore (Go bindings)                         |
| **Native build** | Android NDK, Go 1.25, gomobile toolchain                |
| **Build system** | Gradle (Kotlin DSL), buildSrc                           |
| **CI/CD**        | GitHub Actions (Preview & Release)                      |
| **Tooling**      | Prettier, EditorConfig, Husky + lint-staged, Dependabot |

## 📁 Project Structure

```text
BITS-Box/
├── app/                     # Android application (Kotlin, UI, services)
│   ├── src/main/java/       # Application source code
│   ├── src/main/res/        # Resources (layouts, strings, drawables)
│   ├── libs/                # Prebuilt bitscore.aar (generated)
│   └── build.gradle.kts     # Module build script
├── libcore/                 # Go bindings shared with upstream sing-box
├── buildScript/             # Native build & CI scripts (Go, Android, assets)
├── buildSrc/                # Gradle build logic (flavors, metadata, signing, APK naming)
├── .github/workflows/       # CI/CD workflows
├── .husky/                  # Git hooks (pre-commit, pre-push)
├── build.gradle.kts         # Top-level Gradle build script
├── settings.gradle.kts      # Gradle module settings
├── bitsbox.properties       # Build metadata (version, package name)
├── .prettierrc.json         # Code formatter config
├── .editorconfig            # Editor style config
├── package.json             # Formatter & hooks tooling
└── run                      # Build helper CLI
```

## 🚀 Quick Start

### Prerequisites

| Tool        | Version                                          |
| ----------- | ------------------------------------------------ |
| JDK         | **21** (pinned; JDK 17 also accepted for Gradle) |
| Android SDK | `platforms;android-37`, `build-tools;37.0.0`     |
| Android NDK | `29.0.14206865`                                  |
| Go          | 1.25+ (go.mod requires 1.25.0)                   |
| Node.js     | 20+ (optional, for formatter & hooks)            |
| pnpm        | 10+ (optional, for formatter & hooks)            |

### Build locally

```bash
# 1. Fetch pinned external sources + init gomobile toolchain
./run lib core init

# 2. Build native core library (bitscore.aar)
./run lib core build

# 3. Download runtime assets (GeoIP / GeoSite releases)
./run lib assets

# 4. Build APK
make debug-oss        # debug, unsigned
make release-oss      # release, unsigned (signed with keystore if configured)
```

Artifacts are written to `app/build/outputs/apk/`.

The project has three product flavors — `oss` (default, FOSS), `play` (Google Play, AAB via `bundlePlayRelease`), and `preview` (preview channel, what the Preview CI workflow builds). Swap the suffix on any Make target, e.g. `make debug-play`, `make release-preview`, or build all with `make debug-all` / `make release-all`.

### Full build from scratch

```bash
make build-full      # clean → fetch sources → init → build libcore → assets → release APK
```

Or using the CLI helper directly:

```bash
./run lib core init    # fetch sources + gomobile init
./run lib core build   # build bitscore.aar
./run lib assets       # download GeoIP/GeoSite
./gradlew assembleOssRelease  # build APK
```

## 💻 Development

### Commands

| Command                 | Description                               |
| ----------------------- | ----------------------------------------- |
| `make debug-oss`        | Build debug APK (oss flavor)              |
| `make release-oss`      | Build release APK (oss)                   |
| `make bundle-play`      | Build Play AAB (`bundlePlayRelease`)      |
| `make debug-all`        | Build all debug flavors                   |
| `make release-all`      | Build all release flavors                 |
| `make build-full`       | Full clean build from scratch             |
| `make lint`             | Run Android lint                          |
| `make format`           | Format all files (Prettier, via Makefile) |
| `make format-check`     | Check formatting (Makefile)               |
| `make check`            | `format-check` + `KSP` + `AAR metadata`   |
| `make wrapper`          | Update Gradle wrapper                     |
| `pnpm format`           | Format all files with Prettier            |
| `pnpm format:check`     | Check formatting without writing          |
| `pnpm check`            | Format check + Gradle tasks               |
| `./gradlew tasks --all` | List all Gradle tasks                     |

### Code Style & Git Hooks

- **Formatter:** Prettier 3 (`prettier --write .`) with `.prettierrc.json` (`printWidth: 100, singleQuote, semi, 2 spaces`) and `.prettierignore` (ignores `build/`, `app/libs/`, `external/`, `gradle-wrapper.jar`, etc.)
- **EditorConfig:** `.editorconfig` enforces `lf`, `utf-8`, `trim_trailing_whitespace`, `2 spaces` (4 for `*.kt`/`*.kts`/`*.java`), `tab` for `Makefile`/`*.go`
- **Git Hooks:** Husky 9 + lint-staged (auto-installed via `prepare`):
  - `pre-commit`: `lint-staged` → `prettier --write` for `*.{js,ts,json,css,md,yml,yaml,kt,kts,xml,gradle}`
  - `pre-push`: `npm run format:check` (formatting must pass)
- **Usage:**

```bash
pnpm install           # install formatter & hooks (runs husky prepare)
pnpm format            # manual format
pnpm format:check      # CI check
# hooks run automatically on `git commit` / `git push`
# skip if needed: HUSKY=0 git commit -m "..."
```

### Dependabot

Automated dependency updates via `.github/dependabot.yml`:

| Ecosystem        | Directory | Schedule            |
| ---------------- | --------- | ------------------- |
| `github-actions` | `/`       | Weekly Monday 02:30 |
| `gradle`         | `/`       | Weekly Monday 03:00 |
| `npm`            | `/`       | Weekly Monday 03:30 |

- Groups `minor`+`patch` updates, limits PRs (10 for Actions, 5 for Gradle/npm)
- Ignores major bumps for `com.android.tools.build:gradle` and `kotlin-gradle-plugin`
- Labels: `dependencies`, `github-actions` / `gradle` / `npm`
- `gomod` (libcore) is **disabled** — Go deps use local `replace` (`../external/sing-box`) and are pinned via `COMMIT_SING_BOX` / `COMMIT_BITSBOX_CORE` in `buildScript/lib/core/get_source_env.sh`

## ⚙️ Configuration

### Signing (release builds)

Provide a keystore via `local.properties`:

```properties
KEYSTORE_PASS=...
ALIAS_NAME=bitsbox
ALIAS_PASS=...
```

Or via the `LOCAL_PROPERTIES` environment variable (base64-encoded).

### CI/CD workflows

| Workflow                           | Trigger                                                    | Output                                                   |
| ---------------------------------- | ---------------------------------------------------------- | -------------------------------------------------------- |
| `👁️ Preview Build` (`preview.yml`) | `workflow_dispatch`                                        | Preview APKs (artifact)                                  |
| `🏷️ Release Build` (`release.yml`) | `workflow_dispatch` (with `tag`, `publish`, `play` inputs) | Signed release APKs + optional GitHub release / Play AAB |

Both workflows use icon-prefixed jobs and steps for clarity:

- `🔧 Native Build (LibCore)` → `📥 Checkout`, `☕ Setup JDK 21`, `🤖 Setup Android SDK & NDK`, `📦 Install NDK`, `📂 Fetch pinned native sources`, `🐹 Setup Go`, `🏗️ Build LibCore`
- `📦 Build BITS Box APK` → `📥 Restore LibCore AAR`, `🔑 Restore Keystore`, `💾 Gradle Cache`, `🏗️ Gradle Build`, `📤 Upload`

See `.github/workflows/` for details.

### External build inputs

| Input                          | How obtained                                                                | Purpose                      |
| ------------------------------ | --------------------------------------------------------------------------- | ---------------------------- |
| `external/sing-box`            | `./run lib core get source` clones `SagerNet/sing-box` @ `v1.13.19`         | upstream core module         |
| `external/bits-box-core`       | `./run lib core get source` clones `bitscoid/BITS-Box-Core` @ pinned commit | BITS Box Go core module      |
| `gomobile-bits`, `gobind-bits` | `libcore/init.sh` clones `bitscoid/gomobile`                                | Android Go binding toolchain |
| GeoIP / GeoSite assets         | `./run lib assets` downloads the latest **minimal** variants                | runtime databases            |

## 🌐 Route Assets (GeoIP / GeoSite)

BITS Box bundles small GeoIP/GeoSite databases at build time and can refresh or upgrade them in-app without reinstalling the APK.

### Variants

| Variant               | GeoSite categories                 | GeoIP         | Bundled in APK | Notes                                   |
| --------------------- | ---------------------------------- | ------------- | -------------- | --------------------------------------- |
| **Minimal** (default) | `id`, `rule-ads`, `rule-indo`      | `id`          | ✅ ~56 KB      | Enough for the default profile rules    |
| **Full**              | All v2fly categories + extra lists | All countries | ❌ on demand   | Needed for any rule beyond the defaults |

The active variant is selected in **Settings → Rule Assets Variant**. The **Minimal** databases are packed into the APK (`assets/sing-box/`); the **Full** databases are downloaded from the [BITS-GeoIP](https://github.com/bitscoid/BITS-GeoIP) / [BITS-GeoSite](https://github.com/bitscoid/BITS-GeoSite) releases the first time you choose the Full variant.

### In-app updates

On **Settings → Manage Route Assets** each database shows its local version and an **Update** button. Updates pull the latest GitHub release for the currently selected variant and always store the file under its canonical name (`geoip.db` / `geosite.db`), so the core engine never needs to know which variant is in use.

### Rule categories (Full variant)

GeoSite rules use `geosite:<category>`; GeoIP rules use `geoip:<country-code>` (ISO 3166-1 alpha-2, e.g. `id`, `sg`, `us`, `cn`).

**Platform / brand categories:** `apple`, `google`, `google-play`, `facebook`, `instagram`, `twitter`, `telegram`, `netflix`, `youtube`, `spotify`, `discord`, `steam`, `epicgames`, `microsoft`, `amazon`, `cloudflare`, `github`, `openai`, ...

**Grouped `category-*` categories:**

| Category                                  | Covers                    |
| ----------------------------------------- | ------------------------- |
| `category-ads-all`                        | Ad / tracking domains     |
| `category-ai-!cn`, `category-ai-cn`       | AI services (non-CN / CN) |
| `category-games-!cn`, `category-games-cn` | Gaming                    |
| `category-media-!cn`, `category-media-cn` | Streaming & media         |
| `category-social-media-!cn`, `-cn`        | Social media              |
| `category-cryptocurrency`                 | Crypto services           |
| `category-communication`                  | Messaging / comms         |
| `category-porn`                           | Adult content             |
| `category-ip-geo-detect`                  | IP geolocation detection  |
| `geolocation-!cn`, `geolocation-cn`       | All domains (non-CN / CN) |

**Extra lists:** `oisd-full`, `oisd-small`, `oisd-nsfw`, `d3ward`, `rule-ads`, `antiscam`, `rule-doh`, `rule-gaming`, `rule-indo`, `rule-playstore`, `rule-sosmed`, `rule-streaming`, `rule-umum`, `rule-ipcheck`, `rule-speedtest`, `videoconference`, `rule-malicious`, `urltest`.

### Writing rules

| Pattern                 | Effect                            |
| ----------------------- | --------------------------------- |
| `geosite:rule-ads`      | Match ad domains (block)          |
| `geosite:rule-indo`     | Match Indonesian domains (bypass) |
| `geoip:id`              | Match Indonesian IPs (bypass)     |
| `geosite:netflix`       | Match Netflix domains (proxy)     |
| `port=443, network=udp` | Match QUIC traffic (block)        |

> **Note:** with the **Minimal** variant only `id`, `rule-ads`, `rule-indo` and `geoip:id` are available. Rules referencing other categories are silently skipped. Choose **Full** + **Update** to enable all categories.

Default rules for a new profile:

```text
1. Block QUIC            port = 443, network = udp        → block
2. Block ads             geosite:rule-ads                 → block
3. Bypass Indonesia      geosite:rule-indo                → direct
4. Bypass Indonesia IP   geoip:id                         → direct
```

## 📄 Project Identity

| Item             | Value                                                    |
| ---------------- | -------------------------------------------------------- |
| App name         | BITS Box                                                 |
| Package ID       | `id.bits.box`                                            |
| Deep link scheme | `bitsbox://`                                             |
| Website          | [https://bits.co.id](https://bits.co.id)                 |
| Support          | [admin@bits.co.id](mailto:admin@bits.co.id)              |
| Privacy policy   | [https://bits.co.id/privacy](https://bits.co.id/privacy) |
| Terms of service | [https://bits.co.id/terms](https://bits.co.id/terms)     |

## 📦 Related Repositories

| Repository                                                  | Purpose                   |
| ----------------------------------------------------------- | ------------------------- |
| [BITS-Box](https://github.com/Banten-IT-Solutions/BITS-Box) | This application          |
| [BITS-Box-Core](https://github.com/bitscoid/BITS-Box-Core)  | Core engine library (Go)  |
| [sing-box](https://github.com/SagerNet/sing-box)            | sing-box kernel           |
| [BITS-GeoIP](https://github.com/bitscoid/BITS-GeoIP)        | GeoIP database releases   |
| [BITS-GeoSite](https://github.com/bitscoid/BITS-GeoSite)    | GeoSite database releases |

## 📄 License

This project is free software, released under the **GNU General Public License v3** (or later). See [LICENSE](LICENSE) for the full text.

The project is a fork of the SagerNet / Matsuri projects (NekoBoxForAndroid), and all upstream copyright notices and licenses are preserved. Contribution, fork, and redistribution must comply with the GPLv3 license and retain the original attribution.

---

<div align="center">
  <strong>BITS Box</strong> — Developed with ❤️ by <a href="https://bits.co.id"><strong>Banten IT Solutions</strong></a>
</div>

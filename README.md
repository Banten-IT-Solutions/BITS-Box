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

| Feature | Description |
|---------|-------------|
| **Multi-protocol** | Single client for `VMess`, `VLESS`, `Trojan`, `Shadowsocks`, `TUIC`, `Hysteria`, `WireGuard`, `HTTP/SOCKS`, `SSH`, `TUN` mode. |
| **sing-box kernel** | High-performance core with `gVisor`, `QUIC`, `UTLS`, `Clash API`, and `conntrack` support. |
| **Professional UX** | Material Design interface with themed styling, config editor, and profile management. |
| **Privacy first** | No telemetry, no ads — all connectivity is explicit and user-controlled. |
| **Multi-ABI** | Optimized builds for `armeabi-v7a`, `arm64-v8a`. |

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| **Frontend** | Android, Kotlin, Material Design |
| **Networking** | sing-box, libcore (Go bindings) |
| **Native build** | Android NDK, Go 1.23, gomobile toolchain |
| **Build system** | Gradle (Kotlin DSL), buildSrc |
| **CI/CD** | GitHub Actions |

## 📁 Project Structure

```text
BITS-Box/
├── app/                     # Android application (Kotlin, UI, services)
│   ├── src/main/java/       # Application source code
│   ├── src/main/res/        # Resources (layouts, strings, drawables)
│   ├── libs/                # Prebuilt bitscore.aar
│   └── build.gradle.kts     # Module build script
├── libcore/                 # Go bindings shared with upstream sing-box
├── buildScript/             # Native build & CI scripts (Go, Android, assets)
├── buildSrc/                # Gradle build logic (metadata, signing, APK naming)
├── .github/workflows/       # CI/CD workflows
├── build.gradle.kts         # Top-level Gradle build script
├── settings.gradle.kts      # Gradle module settings
├── bitsbox.properties       # Build metadata (version, package name)
└── run                      # Build helper CLI
```

## 🚀 Quick Start

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

### Prerequisites

| Tool | Version |
|------|---------|
| JDK | 17+ (built & tested with 21) |
| Android SDK | `platforms;android-36`, `build-tools;37.0.0` |
| Android NDK | `29.0.14206865` |
| Go | 1.25+ (go.mod requires 1.25.0) |

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

| Workflow | Trigger | Output |
|----------|---------|--------|
| `preview.yml` | `workflow_dispatch` | Preview APKs (artifact) |
| `release.yml` | `workflow_dispatch` + tag | Signed release APKs + optional GitHub release / Play AAB |

Both workflows fetch pinned external sources, build `bitscore.aar` from source with caching, and download GeoIP / GeoSite release assets before Gradle builds the APK. See `.github/workflows/` for details.

### External build inputs

Full local builds use a subdirectory under this repository:

| Input | How obtained | Purpose |
|-------|-------------|---------|
| `external/sing-box` | `./run lib core get source` clones `SagerNet/sing-box` @ `v1.13.19` | upstream core module |
| `external/bits-box-core` | `./run lib core get source` clones `bitscoid/BITS-Box-Core` @ pinned commit | BITS Box Go core module |
| `gomobile-bits`, `gobind-bits` | `libcore/init.sh` clones `bitscoid/gomobile` | Android Go binding toolchain |
| GeoIP / GeoSite assets | `./run lib assets` downloads the latest **minimal** variants | runtime databases |

## 🌐 Route Assets (GeoIP / GeoSite)

BITS Box bundles small GeoIP/GeoSite databases at build time and can refresh or
upgrade them in-app without reinstalling the APK.

### Variants

| Variant | GeoSite categories | GeoIP | Bundled in APK | Notes |
|---------|--------------------|-------|----------------|-------|
| **Minimal** (default) | `id`, `rule-ads`, `rule-indo` | `id` | ✅ ~56 KB | Enough for the default profile rules |
| **Full** | All v2fly categories + extra lists | All countries | ❌ on demand | Needed for any rule beyond the defaults |

The active variant is selected in **Settings → Rule Assets Variant**. The **Minimal**
databases are packed into the APK (`assets/sing-box/`); the **Full** databases are
downloaded from the [BITS-GeoIP](https://github.com/bitscoid/BITS-GeoIP) /
[BITS-GeoSite](https://github.com/bitscoid/BITS-GeoSite) releases the first time you
choose the Full variant.

### In-app updates

On **Settings → Manage Route Assets** each database shows its local version and an
**Update** button. Updates pull the latest GitHub release for the currently selected
variant and always store the file under its canonical name (`geoip.db` / `geosite.db`),
so the core engine never needs to know which variant is in use. Switching the variant
triggers a re-download even when the upstream release tag is unchanged.

### Rule categories (Full variant)

GeoSite rules use `geosite:<category>`; GeoIP rules use `geoip:<country-code>`
(ISO 3166-1 alpha-2, e.g. `id`, `sg`, `us`, `cn`).

**Platform / brand categories:** `apple`, `google`, `google-play`, `facebook`,
`instagram`, `twitter`, `telegram`, `netflix`, `youtube`, `spotify`, `discord`,
`steam`, `epicgames`, `microsoft`, `amazon`, `cloudflare`, `github`, `openai`, ...

**Grouped `category-*` categories:**

| Category | Covers |
|----------|--------|
| `category-ads-all` | Ad / tracking domains |
| `category-ai-!cn`, `category-ai-cn` | AI services (non-CN / CN) |
| `category-games-!cn`, `category-games-cn` | Gaming |
| `category-media-!cn`, `category-media-cn` | Streaming & media |
| `category-social-media-!cn`, `-cn` | Social media |
| `category-cryptocurrency` | Crypto services |
| `category-communication` | Messaging / comms |
| `category-porn` | Adult content |
| `category-ip-geo-detect` | IP geolocation detection |
| `geolocation-!cn`, `geolocation-cn` | All domains (non-CN / CN) |

**Extra lists added by the BITS generator:** `oisd-full`, `oisd-small`, `oisd-nsfw`,
`d3ward`, `rule-ads`, `antiscam`, `rule-doh`, `rule-gaming`, `rule-indo`,
`rule-playstore`, `rule-sosmed`, `rule-streaming`, `rule-umum`, `rule-ipcheck`,
`rule-speedtest`, `videoconference`, `rule-malicious`, `urltest`.

### Writing rules

Rules are edited in the in-app **Rules** screen. Each rule matches a traffic field
against a pattern and sends it to an outbound (or block).

| Pattern | Effect |
|---------|--------|
| `geosite:rule-ads` | Match ad domains (block) |
| `geosite:rule-indo` | Match Indonesian domains (bypass) |
| `geoip:id` | Match Indonesian IPs (bypass) |
| `geosite:netflix` | Match Netflix domains (proxy) |
| `geosite:category-porn` | Match adult content (block) |
| `geosite:category-ads-all` | Match all ad domains (block) |
| `port=443, network=udp` | Match QUIC traffic (block) |

> **Note:** with the **Minimal** variant only `id`, `rule-ads`, `rule-indo` and
> `geoip:id` are available. Rules referencing any other category are silently
> skipped (no error). Choose **Full** + **Update** from Settings to enable all
> categories at runtime.

Default rules created for a new profile:

```text
1. Block QUIC            port = 443, network = udp        → block
2. Block ads             geosite:rule-ads                 → block
3. Bypass Indonesia      geosite:rule-indo                → direct
4. Bypass Indonesia IP   geoip:id                         → direct
```

## 📄 Project Identity

| Item | Value |
|------|-------|
| App name | BITS Box |
| Package ID | `id.bits.box` |
| Deep link scheme | `bitsbox://` |
| Website | [https://bits.co.id](https://bits.co.id) |
| Support | [admin@bits.co.id](mailto:admin@bits.co.id) |
| Privacy policy | [https://bits.co.id/privacy](https://bits.co.id/privacy) |
| Terms of service | [https://bits.co.id/terms](https://bits.co.id/terms) |

## 📦 Related Repositories

| Repository | Purpose |
|------------|---------|
| [BITS-Box](https://github.com/Banten-IT-Solutions/BITS-Box) | This application |
| [BITS-Box-Core](https://github.com/bitscoid/BITS-Box-Core) | Core engine library (Go) |
| [sing-box](https://github.com/SagerNet/sing-box) | sing-box kernel |
| [BITS-GeoIP](https://github.com/bitscoid/BITS-GeoIP) | GeoIP database releases |
| [BITS-GeoSite](https://github.com/bitscoid/BITS-GeoSite) | GeoSite database releases |

## 📄 License

This project is free software, released under the **GNU General Public License v3** (or later).
See [LICENSE](LICENSE) for the full text.

The project is a fork of the SagerNet / Matsuri projects (NekoBoxForAndroid), and all upstream
copyright notices and licenses are preserved. Contribution, fork, and redistribution must comply
with the GPLv3 license and retain the original attribution.

---

<div align="center">
  <strong>BITS Box</strong> — Developed with ❤️ by <a href="https://bits.co.id"><strong>Banten IT Solutions</strong></a>
</div>

# ---------------------------------------------------------------------------
# BITS-Box build targets
#
# Standard: GNU Make, bash, tabs for recipes, `?=` for overridable vars.
# Gradle is invoked through the wrapper. By default the Gradle daemon is used
# so subsequent builds are fast (incremental + warm daemon).
# For CI/containers where a lingering daemon is unwanted, override GRADLE_FLAGS:
#   make debug-oss GRADLE_FLAGS=--no-daemon
# Node tooling (Prettier/Husky) requires `pnpm install` once.
# ---------------------------------------------------------------------------

SHELL       := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c
.DEFAULT_GOAL := help

GRADLE       := ./gradlew
GRADLE_FLAGS ?=

.PHONY: help assets \
	install-resources build-full \
	debug-oss debug-play debug-preview debug-all \
	release-oss release-play release-all bundle-play \
	format format-check check \
	clean lint wrapper install-hooks

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-18s\033[0m %s\n", $$1, $$2}'

# --- Assets & Resources ----------------------------------------------------

assets: ## Download geoip/geosite databases to assets/sing-box/ (minimal)
	bash buildScript/lib/assets.sh

install-resources: ## Install all build prerequisites (JDK 21, Android SDK 37, NDK 29, Go 1.25)
	bash buildScript/install-deps.sh

install-hooks: ## Install git hooks & formatter deps (husky + prettier)
	pnpm install

build-full: ## Full build from scratch: clean → sources → libcore → assets → release APK
	$(GRADLE) clean $(GRADLE_FLAGS)
	bash buildScript/lib/core/init.sh
	bash buildScript/lib/core/build.sh
	bash buildScript/lib/assets.sh
	$(GRADLE) assembleOssRelease $(GRADLE_FLAGS)

# --- Debug builds ----------------------------------------------------------

debug-oss: ## Build debug APK (oss flavor)
	$(GRADLE) assembleOssDebug $(GRADLE_FLAGS)

debug-play: ## Build debug APK (play flavor)
	$(GRADLE) assemblePlayDebug $(GRADLE_FLAGS)

debug-preview: ## Build debug APK (preview flavor)
	$(GRADLE) assemblePreviewDebug $(GRADLE_FLAGS)

debug-all: ## Build all debug flavors (oss + play + preview)
	$(GRADLE) assembleOssDebug assemblePlayDebug assemblePreviewDebug $(GRADLE_FLAGS)

# --- Release builds --------------------------------------------------------

release-oss: ## Build release APK (oss flavor)
	$(GRADLE) assembleOssRelease $(GRADLE_FLAGS)

release-play: ## Build release APK (play flavor)
	$(GRADLE) assemblePlayRelease $(GRADLE_FLAGS)

release-all: ## Build all release flavors (oss + play + preview)
	$(GRADLE) assembleOssRelease assemblePlayRelease assemblePreviewRelease $(GRADLE_FLAGS)

bundle-play: ## Build Play AAB (bundlePlayRelease)
	$(GRADLE) bundlePlayRelease $(GRADLE_FLAGS)

# --- Code style & Checks ---------------------------------------------------

format: ## Format all files with Prettier
	npx prettier --write .

format-check: ## Check formatting (no write)
	npx prettier --check .

check: ## Run checks (format-check + KSP + AAR metadata)
	npx prettier --check .
	$(GRADLE) :app:kspOssDebugKotlin :app:checkOssDebugAarMetadata $(GRADLE_FLAGS)

# --- Maintenance -----------------------------------------------------------

clean: ## Clean build outputs
	$(GRADLE) clean $(GRADLE_FLAGS)

lint: ## Run Android lint for all variants
	$(GRADLE) lint $(GRADLE_FLAGS)

wrapper: ## Update Gradle wrapper (e.g. make wrapper GRADLE_VERSION=9.7.1)
	$(GRADLE) wrapper --gradle-version $(or $(GRADLE_VERSION),9.7.1) $(GRADLE_FLAGS)

#!/usr/bin/env bash
# Install all build prerequisites for BITS Box.
# Installs: JDK 21, Android SDK packages (platform, build-tools, NDK), Go 1.25+
set -euo pipefail

source buildScript/init/env_ndk.sh 2>/dev/null || true

NDK_VERSION="29.0.14206865"
ANDROID_PLATFORM="android-36"
BUILD_TOOLS="37.0.0"
GO_VERSION="1.25"

#####
## JDK 21
#####
install_jdk() {
    if command -v java &>/dev/null; then
        local ver
        ver=$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')
        if [ "$ver" -ge 21 ] 2>/dev/null; then
            echo ">> JDK $ver already installed"
            return 0
        fi
    fi

    echo ">> Installing JDK 21 (Temurin)..."
    if [ -f /etc/debian_version ]; then
        apt-get update -qq
        apt-get install -y -qq temurin-21-jdk
    elif command -v sdkman &>/dev/null; then
        sdk install java 21.0.2-tem
    else
        echo ">> Please install JDK 21 manually (Temurin recommended)"
        echo "   https://adoptium.net/temurin/releases/?version=21"
        exit 1
    fi
}

#####
## Go 1.25+
#####
install_go() {
    if command -v go &>/dev/null; then
        local ver
        ver=$(go version | awk '{print $3}' | sed 's/go//')
        # version-sort check: passes when ver >= GO_VERSION (e.g. 1.26.5 >= 1.25)
        if [ -n "$ver" ] && [ "$(printf '%s\n' "$GO_VERSION" "$ver" | sort -V | head -1)" = "$GO_VERSION" ]; then
            echo ">> Go $ver already installed"
            return 0
        fi
        echo ">> Go $ver found but Go $GO_VERSION+ is required"
    fi

    echo ">> Installing Go $GO_VERSION..."
    # Official tarball on all distros: apt repos ship Go versions that are
    # too old for libcore/go.mod (requires go 1.25.0).
    rm -rf /usr/local/go
    curl -fsSL "https://go.dev/dl/go${GO_VERSION}.4.linux-amd64.tar.gz" | tar -C /usr/local -xz
    export PATH="/usr/local/go/bin:$PATH"
    echo ">> Go installed: $(go version)"
}

#####
## Android SDK packages
#####
install_android_sdk() {
    if [ -z "$ANDROID_HOME" ]; then
        echo "Error: ANDROID_HOME not set."
        echo "Set ANDROID_HOME or install Android SDK first."
        exit 1
    fi

    echo ">> Installing Android SDK packages..."
    local sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
    if [ ! -f "$sdkmanager" ]; then
        sdkmanager="$(find "$ANDROID_HOME" -name sdkmanager -type f 2>/dev/null | head -1)"
    fi
    if [ -z "$sdkmanager" ]; then
        echo "Error: sdkmanager not found in ANDROID_HOME=$ANDROID_HOME"
        exit 1
    fi

    yes | "$sdkmanager" --install "platforms;$ANDROID_PLATFORM" "build-tools;$BUILD_TOOLS" "ndk;$NDK_VERSION" 2>&1 | tail -5
    echo ">> Android SDK packages installed:"
    echo "   - platform-$ANDROID_PLATFORM"
    echo "   - build-tools-$BUILD_TOOLS"
    echo "   - ndk-$NDK_VERSION"
}

#####
## Main
#####
echo "=== Installing build prerequisites ==="
install_jdk
install_go
install_android_sdk
echo "=== Done ==="
echo "Java:  $(java -version 2>&1 | head -1)"
echo "Go:    $(go version)"
echo "NDK:   $(cat "$ANDROID_NDK_HOME/source.properties" 2>/dev/null | grep Pkg.Revision || echo 'see ANDROID_NDK_HOME')"
echo ""
echo "Next steps:"
echo "  make build-full   # full build from scratch"

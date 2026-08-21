#!/bin/bash
set -e

chmod -R 777 .build 2>/dev/null || true
rm -rf .build 2>/dev/null || true

if [ -z "$GOPATH" ]; then
    GOPATH=$(go env GOPATH)
fi

# Install gomobile
export GOTOOLCHAIN=${GOTOOLCHAIN:-auto}
if [ ! -f "$GOPATH/bin/gomobile-bits" ]; then
    git clone --branch master https://github.com/bitscoid/gomobile.git gomobile
    pushd gomobile
    pushd cmd
    pushd gomobile
    go install -v
    popd
    pushd gobind
    go install -v
    popd
    popd
    rm -rf gomobile
    mv "$GOPATH/bin/gomobile" "$GOPATH/bin/gomobile-bits"
    mv "$GOPATH/bin/gobind" "$GOPATH/bin/gobind-bits"
fi

export GOBIND="$GOPATH/bin/gobind-bits"
"$GOPATH/bin/gomobile-bits" init

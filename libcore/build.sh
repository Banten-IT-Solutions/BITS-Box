#!/bin/bash

source ../buildScript/init/env_ndk.sh

BUILD=".build"

rm -rf $BUILD/android \
  $BUILD/java \
  $BUILD/javac-output \
  $BUILD/src

if [ -z "$GOPATH" ]; then
  GOPATH=$(go env GOPATH)
fi

export GOBIND=gomobile-bits
"$GOPATH"/bin/gomobile-bits bind -v -target=android/arm,android/arm64 -androidapi 21 -trimpath -ldflags='-s -w' -tags=with_gvisor,with_utls,with_clash_api . || exit 1
rm -r libcore-sources.jar

# Rename output aar from libcore.aar → bitscore.aar
# gomobile uses the package/module name "libcore" for the aar filename
mv -f libcore.aar bitscore.aar

proj=../app/libs
mkdir -p $proj
cp -f bitscore.aar $proj
echo ">> install $(realpath $proj)/bitscore.aar"

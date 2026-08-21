#!/bin/bash
set -e

source "buildScript/init/env.sh"
ENV_BITSBOX=1
source "buildScript/lib/core/get_source_env.sh"
mkdir -p external
pushd external

####

if [ ! -d "sing-box" ]; then
  git clone --no-checkout https://github.com/SagerNet/sing-box.git sing-box
fi
pushd sing-box
git checkout "$COMMIT_SING_BOX"
popd

####

if [ ! -d "bits-box-core" ]; then
  git clone --no-checkout https://github.com/bitscoid/BITS-Box-Core.git bits-box-core
fi
pushd bits-box-core
git checkout "$COMMIT_BITSBOX_CORE"
popd

####

popd

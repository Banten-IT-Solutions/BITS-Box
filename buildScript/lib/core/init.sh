#!/usr/bin/env bash

source "buildScript/init/env.sh"

# Fetch pinned sibling source repositories.
bash buildScript/lib/core/get_source.sh

[ -f libcore/go.mod ] || exit 1
cd libcore

./init.sh || exit 1

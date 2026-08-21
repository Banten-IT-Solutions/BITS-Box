#!/bin/bash

if [ -z "$ANDROID_HOME" ]; then
  if [ -d "$HOME/Android/Sdk" ]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
  elif [ -d "$HOME/.local/lib/android/sdk" ]; then
    export ANDROID_HOME="$HOME/.local/lib/android/sdk"
  elif [ -d "$HOME/Library/Android/sdk" ]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
  fi
fi

if [ -z "$ANDROID_NDK_HOME" ] && [ -z "$NDK" ]; then
  # Search for any installed NDK version
  if [ -d "$ANDROID_HOME/ndk" ]; then
    _NDK=$(find "$ANDROID_HOME/ndk" -mindepth 1 -maxdepth 1 -type d ! -type l -printf '%p\n' | sort -r | head -1)
  fi
  [ -z "$_NDK" ] && _NDK="$ANDROID_HOME/ndk/25.0.8775105"
  [ -f "$_NDK/source.properties" ] || _NDK="$ANDROID_NDK_HOME"
  [ -f "$_NDK/source.properties" ] || _NDK="$NDK"
  [ -f "$_NDK/source.properties" ] || _NDK="$ANDROID_HOME/ndk-bundle"
else
  _NDK="${ANDROID_NDK_HOME:-$NDK}"
fi

if [ ! -f "$_NDK/source.properties" ]; then
  echo "Error: NDK not found."
  exit 1
fi

export ANDROID_NDK_HOME=$_NDK
export NDK=$_NDK

#!/usr/bin/env bash
# Builds the x86_64 emulator libraries (generic CPU) for
# android/app/src/main/jniLibs/x86_64.
#
# ARM64 sets come prebuilt from tools/fetch-llama-android.sh.
#
# Requirements: Android SDK with NDK 29.0.14206865 and cmake;3.22.1, git.
# Usage: tools/build-llama-x86_64.sh
set -euo pipefail

LLAMA_TAG="${LLAMA_TAG:-b11851}"
NDK_VERSION="${NDK_VERSION:-29.0.14206865}"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/AppData/Local/Android/Sdk}}"
BUILD_ROOT="${LLAMA_BUILD_DIR:-$HOME/.cache/agentdeck-llama}"
NDK="$SDK/ndk/$NDK_VERSION"
CMAKE="$SDK/cmake/3.22.1/bin/cmake"
NINJA="$SDK/cmake/3.22.1/bin/ninja"
STRIP="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip"
[ -x "$STRIP" ] || STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
[ -x "$STRIP" ] || STRIP="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-strip"

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$REPO_ROOT/android/app/src/main/jniLibs/x86_64"
SRC="$BUILD_ROOT/llama.cpp"

if [ ! -x "$CMAKE" ] || [ ! -x "$NINJA" ]; then
  echo "CMake/Ninja not found. Install with: sdkmanager \"cmake;3.22.1\"" >&2
  exit 1
fi

mkdir -p "$BUILD_ROOT"
if [ ! -d "$SRC/.git" ]; then
  git clone --depth 1 --branch "$LLAMA_TAG" https://github.com/ggml-org/llama.cpp.git "$SRC"
fi

BUILD="$BUILD_ROOT/build-x86_64"
rm -rf "$BUILD"
"$CMAKE" -S "$SRC" -B "$BUILD" -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$NINJA" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=x86_64 \
  -DANDROID_PLATFORM=android-26 \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=ON \
  -DLLAMA_BUILD_TESTS=OFF \
  -DLLAMA_BUILD_EXAMPLES=OFF \
  -DLLAMA_BUILD_SERVER=OFF \
  -DLLAMA_BUILD_TOOLS=OFF \
  -DGGML_NATIVE=OFF \
  -DGGML_OPENMP=OFF \
  -DGGML_LLAMAFILE=ON
"$CMAKE" --build "$BUILD" --target llama

mkdir -p "$DEST"
for lib in libllama.so libggml.so libggml-base.so libggml-cpu.so; do
  cp "$BUILD/bin/$lib" "$DEST/$lib"
  "$STRIP" "$DEST/$lib"
done

echo "Updated x86_64 libraries in $DEST."

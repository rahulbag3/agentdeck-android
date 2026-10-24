#!/usr/bin/env bash
# Rebuilds the prebuilt llama.cpp libraries that ship in
# android/app/src/main/jniLibs/<abi>/.
#
# Requirements:
#   - Android SDK with NDK $NDK_VERSION and cmake;3.22.1 installed
#     (sdkmanager "cmake;3.22.1")
#   - git, curl, network access
#
# Usage:
#   ANDROID_SDK_ROOT=~/Android/Sdk tools/build-llama-android.sh
#
# Override the pinned version with LLAMA_TAG=b12345.
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
DEST="$REPO_ROOT/android/app/src/main"
SRC="$BUILD_ROOT/llama.cpp"

if [ ! -x "$CMAKE" ] || [ ! -x "$NINJA" ]; then
  echo "CMake/Ninja not found. Install with: sdkmanager \"cmake;3.22.1\"" >&2
  exit 1
fi

mkdir -p "$BUILD_ROOT"
if [ ! -d "$SRC/.git" ]; then
  git clone --depth 1 --branch "$LLAMA_TAG" https://github.com/ggml-org/llama.cpp.git "$SRC"
else
  echo "Using existing checkout at $SRC (expected tag $LLAMA_TAG)"
fi

build_abi() {
  local abi="$1"
  local dir="$BUILD_ROOT/build-$abi"
  rm -rf "$dir"
  "$CMAKE" -S "$SRC" -B "$dir" -G Ninja \
    -DCMAKE_MAKE_PROGRAM="$NINJA" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" \
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
  # Only the core library is needed; tool targets can fail on some hosts.
  "$CMAKE" --build "$dir" --target llama
  mkdir -p "$DEST/jniLibs/$abi"
  for lib in libggml-base.so libggml-cpu.so libggml.so libllama.so; do
    cp "$dir/bin/$lib" "$DEST/jniLibs/$abi/$lib"
    "$STRIP" "$DEST/jniLibs/$abi/$lib"
  done
}

build_abi arm64-v8a
build_abi x86_64

mkdir -p "$DEST/cpp/llama/include"
cp "$SRC/include/llama.h" "$DEST/cpp/llama/include/"
cp "$SRC"/ggml/include/*.h "$DEST/cpp/llama/include/"

echo "Updated llama.cpp libraries in $DEST/jniLibs and headers in $DEST/cpp/llama/include."
echo "Rebuild the app with: cd android && ./gradlew :app:assembleDebug"

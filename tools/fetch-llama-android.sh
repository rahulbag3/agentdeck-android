#!/usr/bin/env bash
# Fetches the official llama.cpp Android packages and installs the two arm64
# native sets used by the app:
#
#   src/standard/jniLibs/arm64-v8a    runtime-dispatch CPU (armv8.0 -> armv9.2)
#   src/snapdragon/jniLibs/arm64-v8a  CPU + Adreno OpenCL + Hexagon NPU
#
# Requires: gh (authenticated), curl-free, Android NDK for llvm-strip.
# Usage: tools/fetch-llama-android.sh [tag]   (default b11966)
set -euo pipefail

TAG="${1:-${LLAMA_TAG:-b11966}}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/android/app/src"
WORK="${LLAMA_PKG_DIR:-$HOME/.cache/agentdeck-llama-pkgs}"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/AppData/Local/Android/Sdk}}"
NDK_VERSION="${NDK_VERSION:-29.0.14206865}"

find_tool() {
  local tool="$1"
  for dir in \
    "$SDK/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/windows-x86_64/bin" \
    "$SDK/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64/bin" \
    "$SDK/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/darwin-x86_64/bin"; do
    if [ -x "$dir/$tool" ]; then echo "$dir/$tool"; return; fi
    if [ -x "$dir/$tool.exe" ]; then echo "$dir/$tool.exe"; return; fi
  done
  command -v "$tool"
}
STRIP="$(find_tool llvm-strip)"

mkdir -p "$WORK" "$APP/standard/jniLibs/arm64-v8a" "$APP/snapdragon/jniLibs/arm64-v8a"
cd "$WORK"

for pattern in "llama-${TAG}-bin-android-arm64.tar.gz" "llama-${TAG}-bin-android-arm64-snapdragon.tar.gz"; do
  if [ ! -f "$pattern" ]; then
    echo "Downloading $pattern"
    gh release download "$TAG" --repo ggml-org/llama.cpp --pattern "$pattern" --clobber
  fi
done

rm -rf cpu snapdragon
mkdir cpu snapdragon
tar -xzf "llama-${TAG}-bin-android-arm64.tar.gz" -C cpu
tar -xzf "llama-${TAG}-bin-android-arm64-snapdragon.tar.gz" -C snapdragon
CPU_DIR="$(find cpu -maxdepth 2 -name libllama.so -printf '%h' | head -1)"
SD_DIR="$(find snapdragon -maxdepth 3 -name libllama.so -printf '%h' | head -1)"
CPU_INC="$(dirname "$CPU_DIR")/include"
HEADER_INC="$(dirname "$SD_DIR")/include"
if [ ! -f "$HEADER_INC/llama.h" ] && [ -f "$CPU_INC/llama.h" ]; then
  HEADER_INC="$CPU_INC"
fi

install_lib() {
  local src="$1" dest="$2" name
  name="$(basename "$src")"
  cp "$src" "$dest/$name"
  "$STRIP" "$dest/$name"
}

# Standard: runtime-dispatch CPU variants. RPC is intentionally excluded: ggml
# loads every backend in the directory and the RPC backend hangs in-app.
rm -f "$APP/standard/jniLibs/arm64-v8a/"*.so
for lib in libllama.so libggml.so libggml-base.so; do
  install_lib "$CPU_DIR/$lib" "$APP/standard/jniLibs/arm64-v8a"
done
for lib in "$CPU_DIR"/libggml-cpu-android_*.so; do
  install_lib "$lib" "$APP/standard/jniLibs/arm64-v8a"
done
rm -f "$APP/standard/jniLibs/arm64-v8a/libggml-rpc.so"

# Snapdragon: statically linked CPU + OpenCL + Hexagon backends.
rm -f "$APP/snapdragon/jniLibs/arm64-v8a/"*.so
for lib in libllama.so libggml.so libggml-base.so libggml-cpu.so libggml-rpc.so \
           libggml-opencl.so libggml-hexagon.so; do
  install_lib "$SD_DIR/$lib" "$APP/snapdragon/jniLibs/arm64-v8a"
done
for lib in "$SD_DIR"/libggml-htp-v*.so; do
  install_lib "$lib" "$APP/snapdragon/jniLibs/arm64-v8a"
done

mkdir -p "$APP/main/cpp/llama/include"
if [ -f "$HEADER_INC/llama.h" ]; then
  cp "$HEADER_INC/llama.h" "$APP/main/cpp/llama/include/"
  cp "$HEADER_INC"/ggml*.h "$APP/main/cpp/llama/include/" 2>/dev/null || true
  cp "$HEADER_INC/gguf.h" "$APP/main/cpp/llama/include/" 2>/dev/null || true
  cp "$HEADER_INC/llama-cpp.h" "$APP/main/cpp/llama/include/" 2>/dev/null || true
fi

echo "Installed standard and Snapdragon arm64 libraries for $TAG."
echo "x86_64 emulator libraries are built by tools/build-llama-x86_64.sh."

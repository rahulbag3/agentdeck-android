cmake_minimum_required(VERSION 3.22)
project(agentdeck_llama CXX)

# Prebuilt llama.cpp libraries live in per-flavor jniLibs directories.
# The Gradle build passes LLAMA_LIBS_BASE; x86_64 falls back to main.
if(NOT DEFINED LLAMA_LIBS_BASE)
    set(LLAMA_LIBS_BASE "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs")
endif()
set(LLAMA_LIBS "${LLAMA_LIBS_BASE}/${ANDROID_ABI}")
if(NOT EXISTS "${LLAMA_LIBS}/libllama.so")
    set(LLAMA_LIBS "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
endif()

add_library(agentdeck_llama SHARED llama_jni.cpp)
target_include_directories(agentdeck_llama PRIVATE llama/include)
target_compile_features(agentdeck_llama PRIVATE cxx_std_17)
target_compile_options(agentdeck_llama PRIVATE -O2 -fvisibility=hidden)

target_link_libraries(agentdeck_llama PRIVATE
    "${LLAMA_LIBS}/libllama.so"
    "${LLAMA_LIBS}/libmtmd.so"
    "${LLAMA_LIBS}/libggml.so"
    "${LLAMA_LIBS}/libggml-base.so"
    log)

# Needle (tool-calling foundation model) — arm64 static engine only.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_include_directories(agentdeck_llama PRIVATE needle)
    target_sources(agentdeck_llama PRIVATE needle_jni.cpp)
    target_link_libraries(agentdeck_llama PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/needle/arm64-v8a/libneedle.a")
endif()

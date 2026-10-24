cmake_minimum_required(VERSION 3.22)
project(agentdeck_llama CXX)

# Prebuilt llama.cpp shared libraries live in jniLibs so Gradle packages them.
set(LLAMA_LIBS "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

add_library(agentdeck_llama SHARED llama_jni.cpp)
target_include_directories(agentdeck_llama PRIVATE llama/include)
target_compile_features(agentdeck_llama PRIVATE cxx_std_17)
target_compile_options(agentdeck_llama PRIVATE -O2 -fvisibility=hidden)

target_link_libraries(agentdeck_llama PRIVATE
    "${LLAMA_LIBS}/libllama.so"
    "${LLAMA_LIBS}/libggml.so"
    "${LLAMA_LIBS}/libggml-base.so"
    "${LLAMA_LIBS}/libggml-cpu.so"
    log)

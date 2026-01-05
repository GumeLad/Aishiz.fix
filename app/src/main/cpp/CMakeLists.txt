cmake_minimum_required(VERSION 3.18)
project(aishiz_native VERSION 1.0.0 LANGUAGES C CXX)

# ============================================================================
# AISHIZ Native Build Configuration
# 100% Offline LLM Inference with llama.cpp
# ============================================================================
# This CMake configuration builds the native layer for offline, on-device
# LLM inference. No network capabilities are included or required.
# ============================================================================

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# Configure llama.cpp for Android
if(DEFINED ANDROID_ABI)
    message(STATUS "Building for Android ABI: ${ANDROID_ABI}")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_CPU_KLEIDIAI ON CACHE INTERNAL "Enable KleidiAI for ARM64")
        set(GGML_OPENMP ON CACHE INTERNAL "Enable OpenMP for ARM64")
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(GGML_CPU_KLEIDIAI OFF CACHE INTERNAL "Disable KleidiAI for x86_64")
        set(GGML_OPENMP OFF CACHE INTERNAL "Disable OpenMP for x86_64")
    else()
        message(WARNING "Unsupported ABI: ${ANDROID_ABI}, using defaults")
    endif()
endif()

# Add llama.cpp as subdirectory
set(LLAMA_SRC ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)

# Build our native library
add_library(aishiz_native SHARED
        native-lib.cpp
)

# Include directories
target_include_directories(aishiz_native PRIVATE
        ${LLAMA_SRC}
        ${LLAMA_SRC}/common
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include
        ${LLAMA_SRC}/ggml/src
)

# Find required Android libraries
find_library(log-lib log)
find_library(android-lib android)

# Link libraries
target_link_libraries(aishiz_native
        llama
        common
        ${android-lib}
        ${log-lib}
)

cmake_minimum_required(VERSION 3.22.1)
project(aishiz_native LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Keep llama/ggml internal. Shipping a single JNI .so avoids duplicate native
# runtimes and makes 16 KB ELF alignment deterministic on modern Android.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static native dependencies" FORCE)
set(GGML_STATIC ON CACHE BOOL "Statically link ggml" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "Disable dynamic ggml backends" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "Do not tune for build host" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "Disable OpenMP on Android" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "Disable KleidiAI dependency" FORCE)

set(LLAMA_BUILD_COMMON ON CACHE BOOL "Build llama common helpers" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "Disable CURL" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Disable llama tests" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Disable llama examples" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Disable llama server" FORCE)

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/llama.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)

add_library(aishiz_native SHARED
    native-lib.cpp
)

target_include_directories(aishiz_native PRIVATE
    ${LLAMA_SRC}
    ${LLAMA_SRC}/common
    ${LLAMA_SRC}/include
    ${LLAMA_SRC}/ggml/include
)

find_library(log_lib log)
find_library(android_lib android)

target_link_libraries(aishiz_native PRIVATE
    llama
    common
    ${android_lib}
    ${log_lib}
)

# Android 15+ devices may use 16 KB memory pages. Align the only packaged
# native library accordingly.
target_link_options(aishiz_native PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)

target_compile_options(aishiz_native PRIVATE
    -O3
    -ffunction-sections
    -fdata-sections
)

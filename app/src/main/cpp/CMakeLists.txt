cmake_minimum_required(VERSION 3.22.1)
project("llama-android")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ── Pull llama.cpp ────────────────────────────────────────────────────────────
include(FetchContent)
FetchContent_Declare(
        llama
        GIT_REPOSITORY https://github.com/ggerganov/llama.cpp
        GIT_TAG b3938
)

set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE       OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)
set(GGML_CUDA            OFF CACHE BOOL "" FORCE)
set(GGML_METAL           OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN          OFF CACHE BOOL "" FORCE)
set(GGML_BLAS            OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(llama)

# ── Our JNI wrapper library — stand-alone, no common/ dependency ──────────────
add_library(${CMAKE_PROJECT_NAME} SHARED llama-android.cpp)

set_target_properties(${CMAKE_PROJECT_NAME} PROPERTIES
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN ON)

target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
        -O3
        -ffp-contract=fast
        -fno-math-errno
        -fno-signed-zeros
        -fno-trapping-math
        -fomit-frame-pointer
        -ffunction-sections
        -fdata-sections)

target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
        -Wl,--gc-sections
        -Wl,-z,max-page-size=16384)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${llama_SOURCE_DIR}/include
        ${llama_SOURCE_DIR}/ggml/include)

target_link_libraries(${CMAKE_PROJECT_NAME}
        llama
        log
        android)
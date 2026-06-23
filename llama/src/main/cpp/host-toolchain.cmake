# Host toolchain used to build ggml's vulkan-shaders-gen tool during an Android
# cross-compile. The generator runs on the BUILD machine (the CI runner), not the
# phone, so it must be compiled with the host compiler rather than the NDK.
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_C_COMPILER cc)
set(CMAKE_CXX_COMPILER c++)

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "sg.act.domain.llama"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = "26.3.11579264"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        // arm64 only: keeps the native payload small; arm64 is universal among
        // devices with enough RAM to run an LLM.
        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"

                // GPU build is opt-in via env so the default CPU cross-compile
                // needs no extra SDKs. CI sets ORACLE_GPU=1 and provides the
                // cross-built OpenCL ICD loader paths; glslc must be on PATH.
                if (System.getenv("ORACLE_GPU") == "1") {
                    arguments += "-DORACLE_GPU=ON"
                    // The Android toolchain limits find_package to the NDK sysroot;
                    // BOTH lets ggml find the GPU SDKs (SPIRV-Headers, etc.) we
                    // cross-installed into ORACLE_GPU_PREFIX.
                    arguments += "-DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH"
                    System.getenv("ORACLE_GPU_PREFIX")?.let {
                        arguments += "-DCMAKE_PREFIX_PATH=$it"
                    }
                    System.getenv("ORACLE_OPENCL_INCLUDE")?.let {
                        arguments += "-DOpenCL_INCLUDE_DIR=$it"
                    }
                    System.getenv("ORACLE_OPENCL_LIB")?.let {
                        arguments += "-DOpenCL_LIBRARY=$it"
                    }
                    // Use the full Vulkan-Hpp headers (with vulkan.hpp) but link
                    // the NDK's arm64 libvulkan.so.
                    System.getenv("ORACLE_VULKAN_INCLUDE")?.let {
                        arguments += "-DVulkan_INCLUDE_DIR=$it"
                    }
                    System.getenv("ORACLE_VULKAN_LIB")?.let {
                        arguments += "-DVulkan_LIBRARY=$it"
                    }
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}

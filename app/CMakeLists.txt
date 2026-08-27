cmake_minimum_required(VERSION 3.22.1)
project("piper_phonemizer_bridge")

# Locate the Native App Glue (optional, for logging)
find_library(log-lib log)

# Source files for JNI bridge (Option C Hybrid)
add_library(
    piper_phonemizer
    SHARED
    src/main/jni/piper_phonemize_bridge.cpp
)

target_link_libraries(piper_phonemizer ${log-lib})

# Include dirs for piper-phonemize headers (to be provided by CI/downloaded SDK)
include_directories(${CMAKE_SOURCE_DIR}/src/main/assets/include)

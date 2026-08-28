cmake_minimum_required(VERSION 3.22.1)
project("piper_phonemizer_bridge")

# C++ Source File का पाथ
file(GLOB_RECURSE CPP_SOURCES "src/main/jni/*.cpp" "src/main/cpp/*.cpp")

add_library(piper_phonemizer SHARED ${CPP_SOURCES})

target_link_libraries(piper_phonemizer
    log
)

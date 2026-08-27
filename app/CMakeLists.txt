cmake_minimum_required(VERSION 3.22.1)
project("piper_phonemizer_bridge")

add_library(piper_phonemize_lib SHARED IMPORTED)
set_target_properties(piper_phonemize_lib PROPERTIES 
    IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libpiper_phonemize.so"
)

add_library(piper_phonemizer SHARED src/main/jni/piper_phonemize_bridge.cpp)

target_link_libraries(piper_phonemizer 
    piper_phonemize_lib 
    log
)

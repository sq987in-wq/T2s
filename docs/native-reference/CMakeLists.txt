cmake_minimum_required(VERSION 3.22.1)
project(piper_phonemizer_bridge)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# PIN an exact piper-phonemize tag (not "master" — reproducibility).
# piper-phonemize statically links espeak-ng; use its prebuilt espeak-ng-data
# as app assets (cross-compiling espeak data requires a host build).
FetchContent_Declare(
    piper-phonemize
    GIT_REPOSITORY https://github.com/rhasspy/piper-phonemize
    GIT_TAG        <PINNED-TAG>   # REPLACE with a release commit hash
    GIT_SUBMODULES_RECURSE true
)
FetchContent_MakeAvailable(piper-phonemize)

add_library(piper_phonemizer SHARED piper_phonemize_bridge.cpp)
target_link_libraries(piper_phonemizer PRIVATE piper-phonemize::piper-phonemize log)

# ABI: arm64-v8a + x86_64 only.
set(ANDROID_ABI arm64-v8a)

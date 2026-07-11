cmake_minimum_required(VERSION 3.22.1)
project(llamabridge)

# llama.cpp подключается как git-сабмодуль в app/src/main/cpp/llama.cpp
#   git submodule add https://github.com/ggerganov/llama.cpp app/src/main/cpp/llama.cpp
# Рекомендуется зафиксировать проверенный тег (см. README, раздел «On-device движок»).
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)

add_subdirectory(llama.cpp)

add_library(llamabridge SHARED llama_bridge.cpp)

target_link_libraries(llamabridge
    llama
    android
    log)

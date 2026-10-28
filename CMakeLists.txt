cmake_minimum_required(VERSION 4.0)

find_package(cmake-bare REQUIRED PATHS node_modules/cmake-bare)
find_package(cmake-fetch REQUIRED PATHS node_modules/cmake-fetch)

set(ANDROID_STL c++_static)

project(bare_kit_kotlin C CXX)

fetch_package("github:holepunchto/bare-kit#v3")

add_library(bare_kit_kotlin SHARED)

set_target_properties(
  bare_kit_kotlin
  PROPERTIES
  C_STANDARD 11
  OUTPUT_NAME bare-kit
)

target_sources(
  bare_kit_kotlin
  PRIVATE
    src/androidMain/jni/bridge.c
)

target_link_libraries(
  bare_kit_kotlin
  PRIVATE
    bare_kit
)

target_link_options(
  bare_kit_kotlin
  PRIVATE
    "-Wl,-z,global"

    # Applications without the NDK can't strip the library when packaging it.
    $<$<CONFIG:Release>:-Wl,--strip-all>
)

install(TARGETS bare_kit_kotlin LIBRARY DESTINATION ${ANDROID_ABI})

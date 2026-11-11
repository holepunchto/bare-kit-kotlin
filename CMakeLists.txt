cmake_minimum_required(VERSION 4.0)

find_package(cmake-bare REQUIRED PATHS node_modules/cmake-bare)
find_package(cmake-fetch REQUIRED PATHS node_modules/cmake-fetch)
find_package(cmake-java REQUIRED PATHS node_modules/cmake-java)

set(ANDROID_STL c++_static)

project(bare_kit_kotlin C CXX)

use_java()

set(BARE_KIT_KOTLIN_ENGINE "v8" CACHE STRING "The JavaScript engine to build Bare Kit with, either v8 or quickjs")

if(BARE_KIT_KOTLIN_ENGINE STREQUAL "quickjs")
  set(BARE_ENGINE "github:holepunchto/libqjs#3481410")
elseif(NOT BARE_KIT_KOTLIN_ENGINE STREQUAL "v8")
  message(FATAL_ERROR "Unknown engine '${BARE_KIT_KOTLIN_ENGINE}'")
endif()

fetch_package("github:holepunchto/bare-kit@3.0.1")

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
    src/commonMain/jni/bridge.c
)

target_link_libraries(
  bare_kit_kotlin
  PRIVATE
    JNI::JNI
    bare::kit
    bare::kit::exports
)

# Applications can't strip the library when packaging it.
if(APPLE)
  target_link_options(
    bare_kit_kotlin
    PRIVATE
      "-Wl,-exported_symbols_list,${CMAKE_CURRENT_LIST_DIR}/src/commonMain/jni/bridge.exp"
      $<$<CONFIG:Release>:-Wl,-dead_strip,-S,-x>
  )
elseif(ANDROID OR LINUX)
  target_link_options(
    bare_kit_kotlin
    PRIVATE
      "-Wl,--version-script=${CMAKE_CURRENT_LIST_DIR}/src/commonMain/jni/bridge.map"
      $<$<CONFIG:Release>:-Wl,--gc-sections,--strip-all>
  )
endif()

if(ANDROID)
  target_link_options(
    bare_kit_kotlin
    PRIVATE
      "-Wl,-z,global"
  )

  install(TARGETS bare_kit_kotlin LIBRARY DESTINATION ${BARE_KIT_KOTLIN_ENGINE}/android/${ANDROID_ABI})
else()
  bare_target(target)

  install(
    TARGETS bare_kit_kotlin
    LIBRARY DESTINATION ${BARE_KIT_KOTLIN_ENGINE}/jvm/${target}
    RUNTIME DESTINATION ${BARE_KIT_KOTLIN_ENGINE}/jvm/${target}
  )
endif()

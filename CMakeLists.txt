cmake_minimum_required(VERSION 4.0)

find_package(cmake-bare REQUIRED PATHS node_modules/cmake-bare)
find_package(cmake-fetch REQUIRED PATHS node_modules/cmake-fetch)
find_package(cmake-java REQUIRED PATHS node_modules/cmake-java)
find_package(cmake-prelude REQUIRED PATHS node_modules/cmake-prelude)

set(ANDROID_STL c++_static)

project(bare_kit_kotlin C CXX)

use_java()

set(BARE_KIT_KOTLIN_ENGINE "v8" CACHE STRING "The JavaScript engine to build Bare Kit with, either v8 or quickjs")

if(BARE_KIT_KOTLIN_ENGINE STREQUAL "quickjs")
  set(BARE_ENGINE "github:holepunchto/libqjs#da91a16")
elseif(NOT BARE_KIT_KOTLIN_ENGINE STREQUAL "v8")
  message(FATAL_ERROR "Unknown engine '${BARE_KIT_KOTLIN_ENGINE}'")
endif()

fetch_package("github:holepunchto/bare-kit@3.4.0")

add_library_targets(
  bare_kit_kotlin
  FEATURES
    c_std_11
  SOURCES
    src/commonMain/jni/bridge.c
  LINK
    PRIVATE
      JNI::JNI
      $<LINK_LIBRARY:WHOLE_ARCHIVE,bare_kit::static>
      bare_kit::exports
  TYPE SHARED
  EXPORTS_FROM
    src/commonMain/jni/exports.json
  STRIP ON
  INSTALL OFF
  EXPORT OFF
)

set_target_properties(
  bare_kit_kotlin_shared
  PROPERTIES
  OUTPUT_NAME bare-kit
)

if(ANDROID)
  target_link_options(
    bare_kit_kotlin_shared
    PRIVATE
      "-Wl,-z,global"
  )

  set(destination ${BARE_KIT_KOTLIN_ENGINE}/android/${ANDROID_ABI})
else()
  bare_target(target)

  set(destination ${BARE_KIT_KOTLIN_ENGINE}/jvm/${target})
endif()

install(FILES $<TARGET_FILE:bare_kit_kotlin::shared> DESTINATION ${destination})

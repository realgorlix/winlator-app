cmake_minimum_required(VERSION 3.22.1)

project(MIDIHandler)

set(JNILIBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../jniLibs/arm64-v8a)

find_package(OpenMP REQUIRED)

add_library(midihandler SHARED
            midihandler.c)

target_include_directories(midihandler PRIVATE fluidsynth/include)

target_link_directories(midihandler PRIVATE ${JNILIBS_DIR})

target_link_libraries(midihandler
                      FLAC
                      fluidsynth-assetloader
                      gio-2.0
                      glib-2.0
                      gmodule-2.0
                      gobject-2.0
                      gthread-2.0
                      instpatch-1.0
                      oboe
                      ogg
                      opus
                      pcre
                      pcreposix
                      sndfile
                      vorbis
                      vorbisenc
                      vorbisfile
                      fluidsynth
                      OpenMP::OpenMP_CXX
                      log)
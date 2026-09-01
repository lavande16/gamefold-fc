cmake_minimum_required(VERSION 3.22.1)

project(gamefold_emu)

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(FCEUMM_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/libretro-fceumm")
set(FCEUMM_SRC "${FCEUMM_ROOT}/src")
set(LIBRETRO_COMMON "${FCEUMM_SRC}/drivers/libretro/libretro-common")

file(GLOB FCEUMM_BOARD_SOURCES CONFIGURE_DEPENDS "${FCEUMM_SRC}/boards/*.c")
file(GLOB FCEUMM_INPUT_SOURCES CONFIGURE_DEPENDS "${FCEUMM_SRC}/input/*.c")

set(FCEUMM_SOURCES
    ${FCEUMM_BOARD_SOURCES}
    ${FCEUMM_INPUT_SOURCES}
    "${FCEUMM_SRC}/ntsc/nes_ntsc.c"
    "${FCEUMM_SRC}/drivers/libretro/libretro.c"
    "${FCEUMM_SRC}/drivers/libretro/libretro_dipswitch.c"
    "${FCEUMM_SRC}/cart.c"
    "${FCEUMM_SRC}/cheat.c"
    "${FCEUMM_SRC}/crc32.c"
    "${FCEUMM_SRC}/fceu-endian.c"
    "${FCEUMM_SRC}/fceu-memory.c"
    "${FCEUMM_SRC}/fceu.c"
    "${FCEUMM_SRC}/fds.c"
    "${FCEUMM_SRC}/fds_apu.c"
    "${FCEUMM_SRC}/file.c"
    "${FCEUMM_SRC}/filter.c"
    "${FCEUMM_SRC}/general.c"
    "${FCEUMM_SRC}/input.c"
    "${FCEUMM_SRC}/md5.c"
    "${FCEUMM_SRC}/nsf.c"
    "${FCEUMM_SRC}/palette.c"
    "${FCEUMM_SRC}/ppu.c"
    "${FCEUMM_SRC}/sound.c"
    "${FCEUMM_SRC}/state.c"
    "${FCEUMM_SRC}/video.c"
    "${FCEUMM_SRC}/vsuni.c"
    "${FCEUMM_SRC}/ines.c"
    "${FCEUMM_SRC}/unif.c"
    "${FCEUMM_SRC}/x6502.c"
    "${LIBRETRO_COMMON}/compat/compat_posix_string.c"
    "${LIBRETRO_COMMON}/compat/compat_snprintf.c"
    "${LIBRETRO_COMMON}/compat/compat_strcasestr.c"
    "${LIBRETRO_COMMON}/compat/compat_strl.c"
    "${LIBRETRO_COMMON}/compat/fopen_utf8.c"
    "${LIBRETRO_COMMON}/encodings/encoding_utf.c"
    "${LIBRETRO_COMMON}/file/file_path.c"
    "${LIBRETRO_COMMON}/file/file_path_io.c"
    "${LIBRETRO_COMMON}/streams/file_stream.c"
    "${LIBRETRO_COMMON}/streams/file_stream_transforms.c"
    "${LIBRETRO_COMMON}/streams/memory_stream.c"
    "${LIBRETRO_COMMON}/string/stdstring.c"
    "${LIBRETRO_COMMON}/time/rtime.c"
    "${LIBRETRO_COMMON}/vfs/vfs_implementation.c"
)

add_library(gamefold_emu SHARED
    gamefold_emu.cpp
    ${FCEUMM_SOURCES}
)

target_compile_definitions(gamefold_emu PRIVATE
    __LIBRETRO__
    PATH_MAX=1024
    FCEU_VERSION_NUMERIC=9813
    FRONTEND_SUPPORTS_RGB565
    HAVE_NTSC_FILTER
)

target_include_directories(gamefold_emu PRIVATE
    "${FCEUMM_SRC}/drivers/libretro"
    "${LIBRETRO_COMMON}/include"
    "${FCEUMM_SRC}"
    "${FCEUMM_SRC}/input"
    "${FCEUMM_SRC}/boards"
    "${FCEUMM_SRC}/ntsc"
)

target_link_libraries(gamefold_emu
    android
    log
    m
)

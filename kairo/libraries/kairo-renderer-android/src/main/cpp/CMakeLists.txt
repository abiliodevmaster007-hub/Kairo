cmake_minimum_required(VERSION 3.22.1)
project(kairo_render LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(kairo_render SHARED
    kairo_render_engine.cpp
)

target_compile_options(kairo_render PRIVATE
    -Wall
    -Wextra
    -O2
    -fno-exceptions
)

if(ANDROID)
    find_library(android-lib android)
    find_library(jnigraphics-lib jnigraphics)
    find_library(log-lib log)

    target_link_libraries(kairo_render
        ${android-lib}
        ${jnigraphics-lib}
        ${log-lib}
    )
endif()

cmake_minimum_required(VERSION 3.16)
if(CMAKE_CURRENT_SOURCE_DIR STREQUAL CMAKE_SOURCE_DIR)
    project(videodl_c VERSION 0.1.0 LANGUAGES C)
endif()

include(CTest)
find_package(CURL REQUIRED)

add_library(videodl_c STATIC src/videodl.c)
add_library(VideoDL::C ALIAS videodl_c)

target_include_directories(videodl_c PUBLIC include)
target_link_libraries(videodl_c PUBLIC CURL::libcurl)
target_compile_features(videodl_c PUBLIC c_std_11)
set_target_properties(videodl_c PROPERTIES
    C_STANDARD 11
    C_STANDARD_REQUIRED YES
    C_EXTENSIONS NO
    POSITION_INDEPENDENT_CODE YES
)

if(MSVC)
    target_compile_options(videodl_c PRIVATE /W4)
else()
    target_compile_options(videodl_c PRIVATE -Wall -Wextra -Wpedantic)
endif()

add_executable(videodl-c src/main.c)
target_link_libraries(videodl-c PRIVATE VideoDL::C)
target_compile_features(videodl-c PRIVATE c_std_11)

option(VIDEODL_ENABLE_SANITIZERS "Enable AddressSanitizer and UndefinedBehaviorSanitizer" OFF)
if(VIDEODL_ENABLE_SANITIZERS AND CMAKE_C_COMPILER_ID MATCHES "GNU|Clang")
    target_compile_options(videodl_c PRIVATE -fsanitize=address,undefined -fno-omit-frame-pointer)
    target_link_options(videodl_c PRIVATE -fsanitize=address,undefined)
    target_compile_options(videodl-c PRIVATE -fsanitize=address,undefined -fno-omit-frame-pointer)
    target_link_options(videodl-c PRIVATE -fsanitize=address,undefined)
endif()

if(BUILD_TESTING)
    add_executable(videodl-c-tests tests/test_videodl.c)
    target_link_libraries(videodl-c-tests PRIVATE VideoDL::C)
    target_compile_features(videodl-c-tests PRIVATE c_std_11)
    add_test(NAME videodl-c-unit COMMAND videodl-c-tests)
endif()

install(TARGETS videodl_c videodl-c
    ARCHIVE DESTINATION lib
    RUNTIME DESTINATION bin
)
install(FILES include/videodl.h DESTINATION include)

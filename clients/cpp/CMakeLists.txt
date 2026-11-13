cmake_minimum_required(VERSION 3.16)
project(videodl_cpp LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# C is the shared low-level transport layer. C++ builds on its stable C ABI.
add_subdirectory(../../native/c-client videodl-c-client)

find_package(nlohmann_json REQUIRED)

add_executable(videodl-cpp src/main.cpp)
target_link_libraries(videodl-cpp PRIVATE VideoDL::C nlohmann_json::nlohmann_json)

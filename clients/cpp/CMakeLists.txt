cmake_minimum_required(VERSION 3.16)
project(videodl_cpp LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(CURL REQUIRED)
find_package(nlohmann_json REQUIRED)

add_executable(videodl-cpp src/main.cpp)
target_link_libraries(videodl-cpp PRIVATE CURL::libcurl nlohmann_json::nlohmann_json)

cmake_minimum_required(VERSION 3.22.1)
project(chart_engine LANGUAGES CXX)

add_library(
    chart_engine
    SHARED
    native_chart_engine.cpp
)

find_library(log-lib log)

target_compile_features(chart_engine PRIVATE cxx_std_17)
target_compile_options(chart_engine PRIVATE -Wall -Wextra -Werror)
target_link_libraries(chart_engine ${log-lib})

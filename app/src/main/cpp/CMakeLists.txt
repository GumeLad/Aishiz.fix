cmake_minimum_required(VERSION 3.4.1)
project(aishiz_native)

add_library(aishiz_native SHARED
        native-lib.cpp
)

find_library(log-lib log)

target_link_libraries(aishiz_native
        ${log-lib}
)

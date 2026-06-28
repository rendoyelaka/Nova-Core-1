cmake_minimum_required(VERSION 3.22.1)
project("companionguard")

# OpenSSL prebuilts — pass via -DOPENSSL_ANDROID_ROOT=<path>
# where <path> contains include/ and lib/libcrypto.a for the target ABI.
# Layout expected under app/:
#   third_party/openssl/<ABI>/include/
#   third_party/openssl/<ABI>/lib/libcrypto.a

if(NOT DEFINED OPENSSL_ANDROID_ROOT)
    message(FATAL_ERROR "OPENSSL_ANDROID_ROOT must be set to the OpenSSL folder for the target ABI")
endif()

set(OPENSSL_INCLUDE "${OPENSSL_ANDROID_ROOT}/include")
set(OPENSSL_CRYPTO  "${OPENSSL_ANDROID_ROOT}/lib/libcrypto.a")

if(NOT EXISTS "${OPENSSL_CRYPTO}")
    message(FATAL_ERROR "libcrypto.a not found at ${OPENSSL_CRYPTO}")
endif()

add_library(companionguard SHARED companion_decrypt.cpp)

target_include_directories(companionguard PRIVATE "${OPENSSL_INCLUDE}")

find_library(log-lib log)

target_link_libraries(companionguard "${OPENSSL_CRYPTO}" ${log-lib})

target_compile_options(companionguard PRIVATE
    -O2
    -fstack-protector-strong
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
)

target_link_options(companionguard PRIVATE
    -Wl,--gc-sections
    -Wl,-s
    -Wl,--exclude-libs,ALL
)

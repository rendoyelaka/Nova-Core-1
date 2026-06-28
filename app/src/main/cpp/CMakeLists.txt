cmake_minimum_required(VERSION 3.22.1)
project("novaprotection")

if(NOT DEFINED OPENSSL_ANDROID_ROOT)
    message(FATAL_ERROR "OPENSSL_ANDROID_ROOT must be set to the OpenSSL folder for the target ABI")
endif()

set(OPENSSL_INCLUDE "${OPENSSL_ANDROID_ROOT}/include")
set(OPENSSL_CRYPTO  "${OPENSSL_ANDROID_ROOT}/lib/libcrypto.a")

if(NOT EXISTS "${OPENSSL_CRYPTO}")
    message(FATAL_ERROR "libcrypto.a not found at ${OPENSSL_CRYPTO}")
endif()

find_library(log-lib log)

# ── Hardening flags applied to both libs ─────────────────────────────────────
set(HARDENING_FLAGS
    -O2
    -fstack-protector-strong
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
)
set(HARDENING_LINK
    -Wl,--gc-sections
    -Wl,-s
    -Wl,--exclude-libs,ALL
)

# ── lib 1: companionguard — decrypts companion.enc → file ────────────────────
add_library(companionguard SHARED companion_decrypt.cpp)
target_include_directories(companionguard PRIVATE "${OPENSSL_INCLUDE}")
target_link_libraries(companionguard "${OPENSSL_CRYPTO}" ${log-lib})
target_compile_options(companionguard PRIVATE ${HARDENING_FLAGS})
target_link_options(companionguard PRIVATE ${HARDENING_LINK})

# ── lib 2: novaguard — decrypts nova.enc → ByteArray for ClassLoader ─────────
add_library(novaguard SHARED nova_dex_decrypt.cpp)
target_include_directories(novaguard PRIVATE "${OPENSSL_INCLUDE}")
target_link_libraries(novaguard "${OPENSSL_CRYPTO}" ${log-lib})
target_compile_options(novaguard PRIVATE ${HARDENING_FLAGS})
target_link_options(novaguard PRIVATE ${HARDENING_LINK})

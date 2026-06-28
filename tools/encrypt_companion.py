#!/usr/bin/env python3
"""
encrypt_companion.py — Build-time encryptor for Nova Launcher.

Reads companion.apk as a raw binary blob, encrypts it with AES-256-GCM,
writes assets/companion.enc, and auto-patches AES_KEY_HEX into
app/src/main/cpp/companion_decrypt.cpp so the NDK build always has
the correct key without any manual copy-paste.

Blob layout:  [ 12 bytes IV ][ ciphertext ][ 16 bytes GCM tag ]

Usage (called automatically by Gradle encryptCompanion task):
    python3 tools/encrypt_companion.py <companion.apk> <assets_dir>

Dependencies:
    pip install pycryptodome --break-system-packages
"""

import re
import secrets
import sys
from pathlib import Path

try:
    from Crypto.Cipher import AES
except ImportError:
    print("[X] pycryptodome not found. Run: pip install pycryptodome --break-system-packages")
    sys.exit(1)

COMPANION_APK = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("companion.apk")
NOVA_ASSETS   = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("app/src/main/assets")
OUTPUT_ENC    = NOVA_ASSETS / "companion.enc"
KEY_FILE      = Path("build/companion_key.txt")

# C++ source to auto-patch with the new key
CPP_SOURCE    = Path(__file__).parent.parent / "app" / "src" / "main" / "cpp" / "companion_decrypt.cpp"

KEY_SIZE = 32
IV_SIZE  = 12
TAG_SIZE = 16


def encrypt_blob(data: bytes, key: bytes) -> bytes:
    iv = secrets.token_bytes(IV_SIZE)
    cipher = AES.new(key, AES.MODE_GCM, nonce=iv)
    ciphertext, tag = cipher.encrypt_and_digest(data)
    return iv + ciphertext + tag


def patch_cpp_key(cpp_path: Path, key_hex: str) -> bool:
    if not cpp_path.exists():
        print(f"[!] C++ source not found at {cpp_path} — skipping auto-patch.")
        print(f"[!] Paste this key manually into AES_KEY_HEX: {key_hex}")
        return False

    src = cpp_path.read_text(encoding="utf-8")
    pattern = re.compile(
        r'(static const char \*AES_KEY_HEX\s*=\s*\n?\s*")'
        r'([0-9a-fA-F]{64}|KEY_PLACEHOLDER_32_BYTES_HEX_64_CHARS)'
        r'(";)'
    )
    if not pattern.search(src):
        print(f"[!] AES_KEY_HEX pattern not found in {cpp_path}")
        print(f"[!] Paste manually: {key_hex}")
        return False

    cpp_path.write_text(pattern.sub(rf'\g<1>{key_hex}\g<3>', src), encoding="utf-8")
    print(f"[OK] Patched AES_KEY_HEX in {cpp_path}")
    return True


def main():
    if not COMPANION_APK.exists():
        print(f"[X] companion.apk not found at: {COMPANION_APK}")
        sys.exit(1)

    data = COMPANION_APK.read_bytes()
    print(f"[*] Read companion.apk — {len(data):,} bytes")

    if data[:2] != b'PK':
        print("[X] Not a valid APK — PK magic check failed")
        sys.exit(1)
    print("[*] PK magic verified ✓")

    key     = secrets.token_bytes(KEY_SIZE)
    key_hex = key.hex()
    blob    = encrypt_blob(data, key)
    print(f"[*] Encrypted blob: {len(blob):,} bytes")

    NOVA_ASSETS.mkdir(parents=True, exist_ok=True)
    OUTPUT_ENC.write_bytes(blob)
    print(f"[OK] Written → {OUTPUT_ENC}")

    KEY_FILE.parent.mkdir(parents=True, exist_ok=True)
    KEY_FILE.write_text(key_hex + "\n", encoding="utf-8")
    print(f"[OK] Key saved → {KEY_FILE}  (BUILD ARTIFACT — do not commit)")

    patch_cpp_key(CPP_SOURCE, key_hex)

    print()
    print("=" * 55)
    print("  ENCRYPTION COMPLETE")
    print(f"  companion.enc → {OUTPUT_ENC}")
    print(f"  Key           → {KEY_FILE}  (delete after NDK build)")
    print("=" * 55)


if __name__ == "__main__":
    main()

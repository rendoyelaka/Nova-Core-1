#!/usr/bin/env python3
"""
encrypt_nova_dex.py
────────────────────────────────────────────────────────────────
Build-time tool — encrypts Nova Launcher's compiled classes.dex
into assets/nova.enc using AES-256-GCM.

What it does:
  1. Reads classes.dex from the Gradle build output
  2. Encrypts with AES-256-GCM (fresh key per build)
  3. Writes → app/src/main/assets/nova.enc
  4. Auto-patches AES_KEY_HEX into nova_dex_decrypt.cpp
  5. Saves key to build/nova_dex_key.txt (build artifact only)

Blob layout: [12 bytes IV][ciphertext][16 bytes GCM tag]

Usage (called by Gradle encryptNovaDex task):
    python3 tools/encrypt_nova_dex.py <classes.dex> <assets_dir> <cpp_path>

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

CLASSES_DEX  = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("build/classes.dex")
ASSETS_DIR   = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("app/src/main/assets")
CPP_SOURCE   = Path(sys.argv[3]) if len(sys.argv) > 3 else Path("app/src/main/cpp/nova_dex_decrypt.cpp")
OUTPUT_ENC   = ASSETS_DIR / "nova.enc"
KEY_FILE     = Path("build/nova_dex_key.txt")

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
        print(f"[!] C++ source not found at {cpp_path}")
        print(f"[!] Paste this key manually into AES_KEY_HEX: {key_hex}")
        return False

    src = cpp_path.read_text(encoding="utf-8")
    pattern = re.compile(
        r'(static const char \*NOVA_DEX_KEY_HEX\s*=\s*\n?\s*")'
        r'([0-9a-fA-F]{64}|NOVA_DEX_KEY_PLACEHOLDER_64_CHARS)'
        r'(";)'
    )
    if not pattern.search(src):
        print(f"[!] NOVA_DEX_KEY_HEX pattern not found in {cpp_path}")
        print(f"[!] Paste manually: {key_hex}")
        return False

    cpp_path.write_text(pattern.sub(rf'\g<1>{key_hex}\g<3>', src), encoding="utf-8")
    print(f"[OK] Patched NOVA_DEX_KEY_HEX in {cpp_path}")
    return True


def main():
    if not CLASSES_DEX.exists():
        print(f"[X] classes.dex not found at: {CLASSES_DEX}")
        sys.exit(1)

    data = CLASSES_DEX.read_bytes()
    print(f"[*] Read classes.dex — {len(data):,} bytes ({len(data)/1024/1024:.2f} MB)")

    # Verify DEX magic bytes: 64 65 78 0a (dex\n)
    if data[:3] != b'dex':
        print(f"[X] Not a valid DEX file — magic check failed")
        sys.exit(1)
    print(f"[*] DEX magic verified ✓")

    key     = secrets.token_bytes(KEY_SIZE)
    key_hex = key.hex()
    blob    = encrypt_blob(data, key)
    print(f"[*] Encrypted blob: {len(blob):,} bytes")

    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    OUTPUT_ENC.write_bytes(blob)
    print(f"[OK] Written → {OUTPUT_ENC}")

    KEY_FILE.parent.mkdir(parents=True, exist_ok=True)
    KEY_FILE.write_text(key_hex + "\n", encoding="utf-8")
    print(f"[OK] Key → {KEY_FILE} (BUILD ARTIFACT — do not commit)")

    patch_cpp_key(CPP_SOURCE, key_hex)

    print()
    print("=" * 55)
    print("  NOVA DEX ENCRYPTION COMPLETE")
    print(f"  nova.enc → {OUTPUT_ENC}")
    print(f"  Key      → {KEY_FILE} (delete after NDK build)")
    print("=" * 55)


if __name__ == "__main__":
    main()

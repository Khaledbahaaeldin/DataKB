#!/usr/bin/env python3
"""
Compare two Android APK files entry-by-entry to verify deterministic and reproducible builds.
Compares zip entry names and SHA-256 hashes of uncompressed entry bytes, ignoring META-INF/.
Handles case-sensitive entry names (such as resource obfuscation collisions) that unzip + diff -r drops.
"""

import sys
import os
import hashlib
import zipfile


def hash_entry(zf: zipfile.ZipFile, name: str) -> str:
    data = zf.read(name)
    return hashlib.sha256(data).hexdigest()


def file_sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


def compare_apks(apk1_path: str, apk2_path: str) -> bool:
    if not os.path.exists(apk1_path):
        print(f"Error: {apk1_path} does not exist", file=sys.stderr)
        return False
    if not os.path.exists(apk2_path):
        print(f"Error: {apk2_path} does not exist", file=sys.stderr)
        return False

    size1 = os.path.getsize(apk1_path)
    size2 = os.path.getsize(apk2_path)
    sha1 = file_sha256(apk1_path)
    sha2 = file_sha256(apk2_path)

    print(f"APK 1: {apk1_path} ({size1} bytes, SHA-256: {sha1})")
    print(f"APK 2: {apk2_path} ({size2} bytes, SHA-256: {sha2})")

    with zipfile.ZipFile(apk1_path, "r") as z1, zipfile.ZipFile(apk2_path, "r") as z2:
        entries1 = z1.namelist()
        entries2 = z2.namelist()

        print(f"Total entries: {len(entries1)} vs {len(entries2)}")

        non_meta1 = sorted([e for e in entries1 if not e.startswith("META-INF/")])
        non_meta2 = sorted([e for e in entries2 if not e.startswith("META-INF/")])

        set1 = set(non_meta1)
        set2 = set(non_meta2)

        only1 = sorted(list(set1 - set2))
        only2 = sorted(list(set2 - set1))

        if only1:
            print(f"Entries only in {apk1_path} ({len(only1)}): {only1[:10]}")
        if only2:
            print(f"Entries only in {apk2_path} ({len(only2)}): {only2[:10]}")

        common = sorted(list(set1 & set2))
        print(f"Non-META-INF entries compared: {len(common)}")

        differing = []
        for name in common:
            h1 = hash_entry(z1, name)
            h2 = hash_entry(z2, name)
            if h1 != h2:
                differing.append((name, h1, h2))

        if differing:
            print(f"Differing entries ({len(differing)}):")
            for name, h1, h2 in differing[:10]:
                print(f"  {name}: {h1} vs {h2}")
            return False

        if only1 or only2:
            return False

    print("All non-META-INF entries are byte-for-byte identical!")
    if sha1 == sha2:
        print("Entire APK archive is byte-identical!")
    return True


def main():
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} <apk1> <apk2>")
        sys.exit(2)

    apk1 = sys.argv[1]
    apk2 = sys.argv[2]
    identical = compare_apks(apk1, apk2)
    sys.exit(0 if identical else 1)


if __name__ == "__main__":
    main()

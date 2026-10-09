# Emberbyte Encrypted Backup Format (.emberbyte)

This document describes the binary specification of the `.emberbyte` backup file format as implemented in Milestone 4 ("Insight") of Emberbyte, conforming to API Contract Section 11.2 and Section 7.

The format is designed to be completely self-contained and auditable: users can inspect, decrypt, and recover their data independently without requiring the Android application or any proprietary tools.

---

## 1. Binary Layout

An `.emberbyte` file consists of a 48-byte authenticated binary header followed by AES-256-GCM ciphertext with an appended 16-byte authentication tag:

```
+-------------------------------------------------------------------------------+
|                               HEADER (48 bytes)                               |
+-------------------+-------------------+-------------------+-------------------+
|  Magic (8 bytes)  |  Salt (16 bytes)  |   memKiB (4 B)    |   iters (4 B)     |
+-------------------+-------------------+-------------------+-------------------+
|  parallel (4 B)   |  Nonce (12 bytes) |                                       |
+-------------------+-------------------+---------------------------------------+
|                         PAYLOAD (Variable length)                             |
+-------------------------------------------------------+-----------------------+
|              Ciphertext (N - 64 bytes)                |     Tag (16 bytes)    |
+-------------------------------------------------------+-----------------------+
```

### Byte-by-Byte Field Layout

| Offset (Bytes) | Field | Size | Type / Encoding | Description |
|---|---|---|---|---|
| `0x00 .. 0x07` | **Magic** | 8 bytes | US-ASCII | Format identifier: ASCII string `EMBBKP01` (`0x45 0x4D 0x42 0x42 0x4B 0x50 0x30 0x31`) |
| `0x08 .. 0x17` | **Salt** | 16 bytes | Binary | Cryptographically secure random salt for Argon2id key derivation |
| `0x18 .. 0x1B` | **memoryKiB** | 4 bytes | Int32 (Big-Endian) | Argon2id memory cost in KiB (default: `65536` = 64 MiB) |
| `0x1C .. 0x1F` | **iterations** | 4 bytes | Int32 (Big-Endian) | Argon2id time cost / passes (default: `3`) |
| `0x20 .. 0x23` | **parallelism** | 4 bytes | Int32 (Big-Endian) | Argon2id lanes / threads (default: `1`) |
| `0x24 .. 0x2F` | **Nonce** | 12 bytes | Binary | Cryptographically secure random initialization vector for AES-GCM |
| `0x30 .. N-17` | **Ciphertext** | Variable | Binary | AES-256-GCM encrypted payload (compressed JSON) |
| `N-16 .. N-01` | **Auth Tag** | 16 bytes | Binary | AES-GCM 128-bit authentication tag |

### Associated Authenticated Data (AAD)

The entire 48-byte header (`bytes[0..47]`) is passed as **Associated Authenticated Data (AAD)** to AES-GCM (`cipher.updateAAD(header)`). Any tampering with the magic bytes, salt, KDF parameters, or nonce causes the authentication tag check to fail during decryption.

---

## 2. Key Derivation & Cryptography

### Key Derivation: Argon2id

- **Algorithm**: Argon2id v1.3 (RFC 9106, `ARGON2_id` variant).
- **Passphrase**: UTF-8 encoded string. The application requires a minimum of 8 characters.
- **Salt**: 16 bytes read from header offset `0x08`.
- **Derived Key Length**: 32 bytes (256 bits) for AES-256.
- **Creation Defaults**:
  - `memoryKiB = 65536` (64 MiB)
  - `iterations = 3`
  - `parallelism = 1`
- **Validation Bounds on Restore**:
  To protect against memory exhaustion or denial-of-service attacks from hostile files, the parameters stored in the header are validated before deriving the key:
  - `memoryKiB`: must be between `8 * parallelism` and `65536` KiB (RFC 9106 requires `memoryKiB >= 8 * parallelism`; upper bound 64 MiB, matching what this version writes).
  - `iterations`: must be between `1` and `10` inclusive.
  - `parallelism`: must be between `1` and `4` inclusive.
  If any parameter exceeds these bounds, decryption aborts immediately with an invalid file error without running Argon2id.

### Encryption: AES-256-GCM

- **Cipher**: `AES/GCM/NoPadding` (AES-256 in Galois/Counter Mode).
- **Key**: 32 bytes derived from Argon2id.
- **Nonce (IV)**: 12 bytes read from header offset `0x24`.
- **Tag**: 128 bits (16 bytes), verified by the AEAD cipher.
- **Zeroing Memory**: The key and the passphrase array are zeroed by this app after use; copies made by the cryptography library, the Compose text field and the JVM string pool cannot be guaranteed to be wiped.
- **Error Indistinguishability**: A wrong passphrase and a corrupted file or tag both result in an authentication failure (`Wrong passphrase or damaged file`) to prevent padding/verification oracles.

---

## 3. Plaintext Format

The decrypted plaintext is a standard **GZIP** stream (`gzip(exportJson)`). Once decompressed, the payload is a UTF-8 JSON document adhering to Emberbyte Contract Version 1:

```json
{
  "contractVersion": 1,
  "exportedAt": "2026-10-06T12:00:00Z",
  "plans": [
    {
      "id": 1,
      "name": "Primary Mobile",
      "subscriptionId": 1,
      "capBytes": 10737418240,
      "cycle": {
        "type": "monthlyOnDay",
        "dayOfMonth": 1,
        "time": "00:00",
        "zone": "Africa/Cairo"
      },
      "rollover": {
        "type": "capped",
        "maxBytes": 2147483648
      },
      "archived": false,
      "addOns": [
        {
          "id": 1,
          "planId": 1,
          "label": "Extra 2GB",
          "bytes": 2147483648,
          "validFrom": "2026-10-01T00:00:00Z",
          "validUntil": "2026-10-15T00:00:00Z"
        }
      ],
      "freeRules": [
        {
          "id": 1,
          "planId": 1,
          "label": "Night Data",
          "days": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"],
          "start": "00:00",
          "end": "06:00",
          "package": null
        }
      ],
      "alertRules": [
        {
          "id": 1,
          "planId": 1,
          "thresholdPercent": 80,
          "enabled": true
        }
      ]
    }
  ],
  "appBudgets": [
    {
      "id": 1,
      "package": "com.example.video",
      "limitBytes": 5368709120,
      "period": "DAILY",
      "planId": null,
      "warnAtPercent": 80,
      "enabled": true
    }
  ],
  "usageHourly": [
    {
      "hourStart": "2026-10-06T10:00:00Z",
      "package": "com.example.video",
      "network": "MOBILE",
      "subscriptionId": 1,
      "rxBytes": 45120394,
      "txBytes": 3120593
    }
  ]
}
```

### JSON Fields

- `contractVersion` (Int): Currently `1`.
- `exportedAt` (ISO-8601 String): Timestamp when backup was generated.
- `plans` (Array): Configured data plans, including their cycle definition, rollover rules, `archived` status, child `addOns`, zero-rated `freeRules`, and threshold `alertRules`.
- `appBudgets` (Array): Per-app data budgets with package name, limit, period (`DAILY` or `CYCLE`), optional `planId`, warning percentage (`warnAtPercent`), and active status.
- `usageHourly` (Array): Streamed list of 2-hour windowed usage rows keyed by package name (or `"uid:<n>"` if package unknown), network (`MOBILE` or `WIFI`), SIM subscription ID, and rx/tx byte counters.

---

## 4. Restore Semantics

- **Atomic Replacement**: Restoring a backup replaces all plans, add-ons, free rules, alert rules, app budgets, and hourly usage in **one single SQLite transaction**. If any record is invalid or parsing fails, the entire transaction rolls back cleanly.
- **Package & UID Mapping**: Because Android UIDs are device-specific, hourly usage rows are restored by resolving the package name against installed packages on the target device. Rows for packages not currently installed are safely skipped and reported in `skippedUsageRows`.
- **Transient State Cleared**: Notification and debounce state tables (`alert_fired`, `budget_state`, `spike_event`, `spike_notice`) are cleared on restore.

---

## 5. Standalone Python Decryption Tool

The following Python 3 script can decrypt any `.emberbyte` backup independently.

### Prerequisites

```bash
pip install argon2-cffi cryptography
```

### Script (`decrypt_emberbyte.py`)

```python
#!/usr/bin/env python3
"""
Emberbyte Backup Decryption Tool
Decrypts .emberbyte files using Argon2id and AES-256-GCM.
"""

import gzip
import struct
import sys
from argon2 import low_level
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAGIC = b"EMBBKP01"
HEADER_SIZE = 48
TAG_SIZE = 16

def decrypt_backup(file_path: str, passphrase: str) -> str:
    with open(file_path, "rb") as f:
        data = f.read()

    if len(data) < HEADER_SIZE + TAG_SIZE:
        raise ValueError("File is too small to be an Emberbyte backup")

    if data[:8] != MAGIC:
        raise ValueError(f"Invalid magic header: expected {MAGIC!r}, got {data[:8]!r}")

    header = data[:HEADER_SIZE]
    salt = data[8:24]
    memory_kib, iterations, parallelism = struct.unpack(">3i", data[24:36])
    nonce = data[36:48]
    ciphertext_and_tag = data[48:]

    # Enforce parameter bounds
    if not (8 * parallelism <= memory_kib <= 65_536):
        raise ValueError(f"Memory parameter out of bounds: {memory_kib} KiB")
    if not (1 <= iterations <= 10):
        raise ValueError(f"Iterations parameter out of bounds: {iterations}")
    if not (1 <= parallelism <= 4):
        raise ValueError(f"Parallelism parameter out of bounds: {parallelism}")

    # Derive 32-byte key via Argon2id v1.3
    key = low_level.hash_secret_raw(
        secret=passphrase.encode("utf-8"),
        salt=salt,
        time_cost=iterations,
        memory_cost=memory_kib,
        parallelism=parallelism,
        hash_len=32,
        type=low_level.Type.ID,
    )

    # AES-256-GCM decrypt with header as Additional Authenticated Data (AAD)
    aesgcm = AESGCM(key)
    compressed_plaintext = aesgcm.decrypt(nonce, ciphertext_and_tag, header)

    # Decompress GZIP payload
    json_bytes = gzip.decompress(compressed_plaintext)
    return json_bytes.decode("utf-8")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} <backup.emberbyte> <passphrase>", file=sys.stderr)
        sys.exit(1)
    print(decrypt_backup(sys.argv[1], sys.argv[2]))
```

Verified against a real backup produced on device/emulator during Milestone 4 QA.

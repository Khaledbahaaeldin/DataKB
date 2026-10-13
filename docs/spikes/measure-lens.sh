#!/usr/bin/env bash
set -euo pipefail

SERIAL="${SERIAL:-emulator-5554}"
HOST="${HOST:-10.0.2.2}"
PORT="${PORT:-8000}"
PKG="io.github.khaledbahaaeldin.emberbyte"
CURL="/data/local/tmp/curl"

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_CMD="python3"
if ! command -v python3 >/dev/null 2>&1; then
  PYTHON_CMD="python"
fi

if [[ "${1:-}" == "--battery" ]]; then
  echo "Battery measurement mode:"
  echo "Run 30 minutes of video playback on a physical device and record dumpsys batterystats --charged before/after."
  echo "On emulator, battery drain cannot be measured."
  exit 0
fi

# Run the reproducible python harness
SERIAL="$SERIAL" HOST="$HOST" PORT="$PORT" "$PYTHON_CMD" "$DIR/measure_lens.py"

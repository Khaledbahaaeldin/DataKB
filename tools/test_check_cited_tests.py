#!/usr/bin/env python3
"""
Fixture tests for tools/check-cited-tests.py. Run from the repository root:

    python tools/test_check_cited_tests.py

Each case writes a small markdown file into a temp folder, runs the checker on it and compares the exit code.
A case that is expected to fail must fail, so the checker cannot silently pass vacuous documents.
"""
import os
import subprocess
import sys
import tempfile

CHECKER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "check-cited-tests.py")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

REAL_TEST = "home_touch_targets_are_at_least_48dp"          # a real @Test function
HELPER = "TileContrastTest.contrast"                         # a private helper, not a test
WRONG_CLASS = "HistoryAccessibilityTest.home_touch_targets_are_at_least_48dp"  # real test, wrong class
FAKE = "this_test_does_not_exist_anywhere"
CASES = [
    ("a real test passes", f"| OK ({REAL_TEST}) |\n", 0),
    ("a nonexistent test fails", f"| OK ({FAKE}) |\n", 1),
    ("a private helper is not a test", f"`{HELPER}`\n", 1),
    ("a real test cited with the wrong class fails", f"`{WRONG_CLASS}`\n", 1),
    ("a missing image fails", f"See `docs/does-not-exist.png`\n{REAL_TEST}`\n".replace("`\n", "`\n").replace(REAL_TEST + "`", "`" + REAL_TEST + "`"), 1),
    ("a private .superpowers image fails", f"`.superpowers/sdd/x/shot.png` and `{REAL_TEST}`\n", 1),
    ("a document with no citation fails", "Nothing is cited here.\n", 1),
]


def run(args, cwd=ROOT):
    return subprocess.run([sys.executable, CHECKER] + args, cwd=cwd, capture_output=True, text=True)


def main() -> int:
    failures = 0
    with tempfile.TemporaryDirectory() as tmp:
        for i, (name, body, expected) in enumerate(CASES):
            path = os.path.join(tmp, f"case{i}.md")
            with open(path, "w", encoding="utf-8") as f:
                f.write(body)
            result = run([path])
            ok = result.returncode == expected
            print(("PASS" if ok else "FAIL") + f": {name} (exit {result.returncode}, expected {expected})")
            failures += 0 if ok else 1
        missing = run([os.path.join(tmp, "no-such-file.md")])
        ok = missing.returncode != 0
        print(("PASS" if ok else "FAIL") + f": a missing input file fails (exit {missing.returncode})")
        failures += 0 if ok else 1
        empty = os.path.join(tmp, "empty.md")
        open(empty, "w").close()
        allowed = run([empty, "--allow-empty"])
        ok = allowed.returncode == 0
        print(("PASS" if ok else "FAIL") + f": --allow-empty accepts an empty document (exit {allowed.returncode})")
        failures += 0 if ok else 1
    print(f"{failures} failure(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

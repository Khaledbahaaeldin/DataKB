#!/usr/bin/env python3
"""
Verify that test names cited in markdown documents exist in the codebase.
Checks for functions or classes under */src/test or */src/androidTest.

Usage:
    python tools/check-cited-tests.py <file...>
"""

import sys
import os
import re
from typing import Set, Dict, List, Tuple

# Known non-test identifiers, system properties, and Compose/Android APIs
IGNORED_TOKENS = {
    # System & environment properties
    "font_scale", "animator_duration_scale", "window_animation_scale",
    "transition_animation_scale", "system_locales", "persist.sys.locale",
    "debug.force_rtl", "LocalDensity.current.fontScale", "LocalReduceMotion",
    # Gradle tasks & CLI commands
    "testDebugUnitTest", "testReleaseUnitTest", "lintDebug", "lintRelease",
    "assembleDebug", "assembleRelease", "verifyReleaseApk",
    # Database tables & columns
    "app_budget", "spike_event", "budget_state", "spike_notice",
    "usage_hourly", "total_minute", "package_name", "hour_start",
    "plan_id", "subscription_id", "rx_bytes", "tx_bytes",
    # Common APIs & types with dots
    "Role.Tab", "Role.Switch", "Role.RadioButton",
    "LiveRegionMode.Polite", "SemanticsProperties.Error",
    "NotificationCompat.Builder", "Notification.Builder",
    "ProgressStyle.Segment", "Notification.ProgressStyle",
    "Modifier.padding", "PaddingValues", "UsageBarRow",
    "MorphingNumber", "ScreenHeader", "HomeScreen", "AppsScreen",
    "AppDetail", "BudgetEditorDialog", "History", "PlansScreen",
    "PlanEditorScreen", "SettingsScreen", "LiveNotificationBuilder",
    "NotificationChannels", "BackupCodec", "ForecastEngine",
    "SpikeDetector", "BudgetCalculator", "CycleCalculator",
    "PlanCalculator", "AppAggregator", "UsageRetention",
    "UsageStores", "PlanStores", "AppPreferences",
    "LocalContext.current",
}

FILE_EXTENSIONS = {
    ".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg",
    ".kt", ".kts", ".java", ".md", ".txt", ".xml",
    ".json", ".csv", ".pro", ".yml", ".yaml", ".zip",
    ".apk", ".html", ".sh", ".bat", ".py", ".bin",
}


def find_test_definitions(repo_root: str) -> Tuple[Set[str], Set[str], Dict[str, Set[str]]]:
    """Scan */src/test and */src/androidTest for Kotlin/Java functions and classes."""
    all_funs: Set[str] = set()
    all_classes: Set[str] = set()
    class_funs: Dict[str, Set[str]] = {}

    for root, dirs, files in os.walk(repo_root):
        norm_root = root.replace("\\", "/")
        if "/src/test" not in norm_root and "/src/androidTest" not in norm_root:
            continue

        for f in files:
            if not (f.endswith(".kt") or f.endswith(".java")):
                continue

            file_path = os.path.join(root, f)
            try:
                with open(file_path, "r", encoding="utf-8", errors="ignore") as fp:
                    content = fp.read()
            except Exception:
                continue

            # Names that appear only in comments are not tests
            content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)
            content = re.sub(r'(?<!:)//[^' + chr(10) + r']*', '', content)

            # Find classes, interfaces, objects
            file_classes = set()
            for m in re.finditer(r'(?:class|interface|object)\s+([A-Za-z0-9_]+)', content):
                cls_name = m.group(1)
                all_classes.add(cls_name)
                file_classes.add(cls_name)
                if cls_name not in class_funs:
                    class_funs[cls_name] = set()

            # Find functions annotated with @Test
            # Matches @Test ... fun <name> or @Test ... fun `<name>`
            for m in re.finditer(r'@(?:[A-Za-z0-9_.]+\.)?Test\b(?:[^\{;]*?)\bfun\s+(?:`([^`\n]+)`|([A-Za-z0-9_]+))', content, re.DOTALL):
                fn_name = m.group(1) or m.group(2)
                all_funs.add(fn_name)
                for cls_name in file_classes:
                    class_funs[cls_name].add(fn_name)

    return all_funs, all_classes, class_funs


def is_test_candidate(token: str) -> bool:
    """Determine if a token looks like a cited test identifier."""
    token = token.strip()
    if not token:
        return False

    # Remove trailing parentheses if present e.g. test_foo()
    if token.endswith("()"):
        token = token[:-2]

    # Ignore whitespace, slashes, URLs
    if any(c.isspace() for c in token) or "/" in token or "\\" in token:
        return False

    # Ignore file names
    lower = token.lower()
    if any(lower.endswith(ext) for ext in FILE_EXTENSIONS):
        return False

    # Ignore hyphens, colons, or punctuation (other than dot or underscore)
    if "-" in token or ":" in token or "," in token or ";" in token or "(" in token or ")" in token:
        return False

    if token in IGNORED_TOKENS:
        return False

    # Ignore ALL_CAPS constants or permissions
    if token.isupper():
        return False

    # 1. Class.method form (Class must end in Test e.g. SomeTest.some_method)
    if "." in token:
        parts = token.split(".")
        if len(parts) == 2 and parts[0] and parts[0][0].isupper() and parts[0].endswith("Test") and parts[1] and parts[1][0].islower():
            if re.match(r'^[a-z][a-zA-Z0-9_]*$', parts[1]):
                return True
        return False

    # 2. Ends in Test (Class name, starts with uppercase)
    if token.endswith("Test") and len(token) > 4 and token[0].isupper():
        return True

    # 3. Contains underscore: Kotlin test function (must start with lowercase letter)
    if "_" in token:
        if re.match(r'^[a-z][a-zA-Z0-9]*(_[a-zA-Z0-9]+)+$', token):
            return True

    return False


def clean_candidate(token: str) -> str:
    token = token.strip().strip("`").strip()
    if token.endswith("()"):
        token = token[:-2]
    return token


def extract_cited_tests(file_path: str) -> List[Tuple[str, int]]:
    """Extract test candidates from markdown file along with line numbers."""
    candidates: List[Tuple[str, int]] = []

    try:
        with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
            lines = f.readlines()
    except Exception as e:
        print(f"Error reading {file_path}: {e}", file=sys.stderr)
        return []

    in_diff_block = False
    for line_idx, line in enumerate(lines, start=1):
        stripped = line.strip()
        if stripped.startswith("```diff"):
            in_diff_block = True
            continue
        elif in_diff_block and stripped.startswith("```"):
            in_diff_block = False
            continue

        if in_diff_block and line.startswith("-"):
            continue

        # 1. Backticked tokens
        for m in re.finditer(r'`([^`\n]+)`', line):
            raw = m.group(1).strip()
            # If multiple tokens separated by commas
            sub_tokens = [clean_candidate(t) for t in raw.split(",")]
            for st in sub_tokens:
                if is_test_candidate(st):
                    candidates.append((st, line_idx))

        # 2. Table cells with OK(...) or FIX(...) evidence
        if "|" in line:
            for m in re.finditer(r'(?:OK|FIX)\s*\(([^)]+)\)', line):
                evidence = m.group(1)
                # Split by commas or semicolons
                for part in re.split(r'[,;]\s*', evidence):
                    token = clean_candidate(part)
                    if is_test_candidate(token):
                        candidates.append((token, line_idx))

        # 3. Test runner output lines e.g. ClassName > test_name PASSED|FAILED
        for m in re.finditer(r'>\s*([a-zA-Z0-9_]+)\s+(?:PASSED|FAILED)', line):
            token = clean_candidate(m.group(1))
            if is_test_candidate(token):
                candidates.append((token, line_idx))

    return candidates


IMAGE_RE = re.compile(r'[A-Za-z0-9_./\-]+\.(?:png|jpg|jpeg|webp)')


def tracked_files(repo_root: str) -> Set[str]:
    """Repository files that a reader of the repository can open (git-ignored folders excluded)."""
    found: Set[str] = set()
    skip = {".git", ".superpowers", "build", ".gradle", "node_modules"}
    for root, dirs, files in os.walk(repo_root):
        dirs[:] = [d for d in dirs if d not in skip]
        for f in files:
            rel = os.path.relpath(os.path.join(root, f), repo_root).replace("\\", "/")
            found.add(rel)
    return found


def check_image_citations(file_path: str, repo_files: Set[str]) -> List[Tuple[str, int, str]]:
    """Image paths cited by a shipped document must exist in the repository (private QA folders cannot be cited)."""
    problems: List[Tuple[str, int, str]] = []
    norm = file_path.replace("\\", "/")
    if ".superpowers/" in norm:
        return problems  # working reports may cite their own private screenshots
    names = {os.path.basename(f) for f in repo_files}
    with open(file_path, "r", encoding="utf-8", errors="ignore") as fp:
        for line_idx, line in enumerate(fp, start=1):
            for m in IMAGE_RE.finditer(line):
                cited = m.group(0).replace("\\", "/")
                if cited.startswith(".superpowers/") or "/.superpowers/" in cited:
                    problems.append((cited, line_idx, "points into the git-ignored .superpowers folder"))
                elif "/" in cited:
                    if cited not in repo_files:
                        problems.append((cited, line_idx, "does not exist in the repository"))
                elif cited not in names:
                    problems.append((cited, line_idx, "no file of that name exists in the repository"))
    return problems


def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <markdown-file...>")
        sys.exit(2)

    repo_root = os.path.abspath(".")
    all_funs, all_classes, class_funs = find_test_definitions(repo_root)

    total_checked = 0
    missing: List[Tuple[str, str, int]] = []
    missing_files: List[str] = []
    bad_images: List[Tuple[str, str, int, str]] = []
    repo_files = tracked_files(repo_root)

    for file_arg in [a for a in sys.argv[1:] if a != "--allow-empty"]:
        if not os.path.exists(file_arg):
            print(f"File not found: {file_arg}", file=sys.stderr)
            missing_files.append(file_arg)
            continue

        for cited_path, line_num, why in check_image_citations(file_arg, repo_files):
            bad_images.append((cited_path, file_arg, line_num, why))

        cited = extract_cited_tests(file_arg)
        for token, line_num in cited:
            total_checked += 1
            # Check existence
            exists = False
            if "." in token:
                cls_name, method_name = token.split(".", 1)
                if cls_name in class_funs and method_name in class_funs[cls_name]:
                    exists = True
                else:
                    exists = False
            elif token in all_funs:
                exists = True
            elif token in all_classes:
                exists = True

            if not exists:
                missing.append((token, file_arg, line_num))

    if total_checked == 0 and not missing_files and "--allow-empty" not in sys.argv:
        print("FAIL: no test citation was found in the input file(s); a document with nothing to check proves nothing "
              "(pass --allow-empty to accept that).", file=sys.stderr)
        sys.exit(1)

    if missing_files or missing or bad_images:
        if missing_files:
            print(f"FAIL: {len(missing_files)} input file(s) not found: {missing_files}", file=sys.stderr)
        if missing:
            print(f"FAIL: Found {len(missing)} missing cited test(s) among {total_checked} checked:")
            for token, fpath, line_num in missing:
                print(f"  Missing: `{token}` cited at {fpath}:{line_num}")
        if bad_images:
            print(f"FAIL: {len(bad_images)} cited image(s) a reader cannot open:")
            for cited_path, fpath, line_num, why in bad_images:
                print(f"  Image `{cited_path}` cited at {fpath}:{line_num} {why}")
        sys.exit(1)
    else:
        print(f"PASS: All {total_checked} cited test(s) verified across {len(sys.argv) - 1} file(s).")
        sys.exit(0)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Validate that locale translations preserve technical link targets.

The .lng format is plain text replacement. File names, servlet endpoints,
paths and URLs inside a source string must therefore remain literal in the
translation. This script detects translated or damaged targets such as
Network.html -> Netzwerk.html, servletshare.json, or URLs with inserted spaces.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


TARGET_EXTENSIONS = (
    "html",
    "inc",
    "json",
    "xml",
    "rss",
    "css",
    "js",
    "pac",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate literal link targets in YaCy .lng locale files.",
    )
    parser.add_argument(
        "--locales",
        type=Path,
        default=Path("locales"),
        help="Locale directory containing .lng files (default: locales).",
    )
    parser.add_argument(
        "--source",
        type=Path,
        default=Path("htroot"),
        help="YaCy htroot directory used to identify valid source targets (default: htroot).",
    )
    parser.add_argument(
        "--include",
        action="append",
        default=[],
        metavar="FILE",
        help="Only validate this .lng file name. Can be used multiple times.",
    )
    parser.add_argument(
        "--exclude",
        action="append",
        default=[],
        metavar="FILE",
        help="Skip this .lng file name. Can be used multiple times.",
    )
    return parser.parse_args()


def collect_known_targets(source_dir: Path, locale_files: list[Path]) -> set[str]:
    known: set[str] = set()

    if source_dir.exists():
        suffixes = {f".{ext}" for ext in TARGET_EXTENSIONS}
        for path in source_dir.rglob("*"):
            if path.is_file() and path.suffix.lower() in suffixes:
                relative = path.relative_to(source_dir).as_posix()
                known.add(relative)
                known.add(path.name)

    for locale_file in locale_files:
        for line in locale_file.read_text(encoding="utf-8", errors="ignore").splitlines():
            if line.startswith("#File:"):
                target = line[6:].strip()
                known.add(target)
                known.add(Path(target).name)

    return known


def locale_files(locales_dir: Path, include: list[str], exclude: list[str]) -> list[Path]:
    include_set = set(include)
    exclude_set = set(exclude)
    files = sorted(locales_dir.glob("*.lng"))
    if include_set:
        files = [path for path in files if path.name in include_set]
    if exclude_set:
        files = [path for path in files if path.name not in exclude_set]
    return files


def main() -> int:
    args = parse_args()
    files = locale_files(args.locales, args.include, args.exclude)
    known_targets = collect_known_targets(args.source, files)

    extensions = "|".join(TARGET_EXTENSIONS)
    file_token = re.compile(
        rf"(?<![\w./:-])([A-Za-z0-9_./-]+\.(?:{extensions}))(?![\w./-])",
        re.IGNORECASE,
    )
    value_token = re.compile(
        rf"(?<![\w./:-])([^\s\"'<>(),;]+\.(?:{extensions}))(?![\w./-])",
        re.IGNORECASE,
    )
    url_token = re.compile(r"https?://[^\s\"'<>),]*")

    failures = 0
    for locale_file in files:
        current_section: str | None = None
        missing_key_targets: list[tuple[int, str | None, list[str], str]] = []
        unknown_value_targets: list[tuple[int, str | None, str, str]] = []

        for line_number, line in enumerate(
            locale_file.read_text(encoding="utf-8", errors="ignore").splitlines(),
            1,
        ):
            if line.startswith("#File:"):
                current_section = line[6:].strip()
                continue
            if "==" not in line or line.startswith("#"):
                continue

            source, target = line.split("==", 1)
            source_targets = set(file_token.findall(source)) | set(url_token.findall(source))
            target_targets = set(file_token.findall(target)) | set(url_token.findall(target))

            missing = sorted(token for token in source_targets if token not in target_targets)
            if missing:
                missing_key_targets.append((line_number, current_section, missing, line))

            source_file_targets = set(value_token.findall(source))
            for token in value_token.findall(target):
                clean = token.strip(".,:;!?")
                if clean in known_targets or clean in source_file_targets:
                    continue
                if clean.startswith(("http://", "https://")):
                    continue
                unknown_value_targets.append((line_number, current_section, clean, line))

        if missing_key_targets or unknown_value_targets:
            failures += len(missing_key_targets) + len(unknown_value_targets)
            print(f"\n## {locale_file.name}")
            for line_number, section, missing, line in missing_key_targets:
                print(f"{line_number}: missing technical target(s) {missing} in {section}")
                print(f"  {line}")
            for line_number, section, token, line in unknown_value_targets:
                print(f"{line_number}: unknown translated/damaged target {token!r} in {section}")
                print(f"  {line}")

    if failures:
        print(f"\nFAILED: {failures} locale link target issue(s) found.")
        return 1

    print(f"OK: checked {len(files)} locale file(s), no link target issues found.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

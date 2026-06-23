#!/usr/bin/env python3
"""Generate ru/de/zh locale files from canonical en/*.json."""

from __future__ import annotations

import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LOCALES_DIR = ROOT / "apps" / "web-console" / "src" / "locales"
SOURCE = "en"
TARGETS = ("ru", "de", "zh")
LANG_CODES = {"ru": "ru", "de": "de", "zh": "zh-CN"}
BATCH_SIZE = 40
PLACEHOLDER_RE = re.compile(r"\{\{[^}]+\}\}|\$\{[^}]+\}|`\w+`|`\{\{[^}]+\}\}`")


def protect_placeholders(text: str) -> tuple[str, list[str]]:
    tokens: list[str] = []

    def repl(match: re.Match[str]) -> str:
        tokens.append(match.group(0))
        return f"__PH_{len(tokens) - 1}__"

    return PLACEHOLDER_RE.sub(repl, text), tokens


def restore_placeholders(text: str, tokens: list[str]) -> str:
    for index, token in enumerate(tokens):
        text = text.replace(f"__PH_{index}__", token)
    return text


def translate_batch(texts: list[str], target: str) -> list[str]:
    if not texts:
        return []
    protected = [protect_placeholders(text) for text in texts]
    payload = "\n".join(item[0] for item in protected)
    lang = LANG_CODES[target]
    url = (
        "https://translate.googleapis.com/translate_a/single?"
        + urllib.parse.urlencode(
            {
                "client": "gtx",
                "sl": "en",
                "tl": lang,
                "dt": "t",
                "q": payload,
            }
        )
    )
    request = urllib.request.Request(url, headers={"User-Agent": "ispf-i18n/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        raw = json.loads(response.read().decode("utf-8"))
    translated_lines = "".join(part[0] for part in raw[0]).split("\n")
    if len(translated_lines) != len(texts):
        translated_lines = (translated_lines + texts)[: len(texts)]
    result: list[str] = []
    for index, line in enumerate(translated_lines):
        result.append(restore_placeholders(line, protected[index][1]))
    return result


def translate_namespace(source: dict[str, str], target: str, existing: dict[str, str]) -> dict[str, str]:
    out = dict(existing)
    pending_keys: list[str] = []
    pending_values: list[str] = []
    for key, value in source.items():
        if key in out and out[key] != value and out[key]:
            continue
        if key in out and out[key] == source[key]:
            pending_keys.append(key)
            pending_values.append(value)
        elif key not in out:
            pending_keys.append(key)
            pending_values.append(value)
    for start in range(0, len(pending_keys), BATCH_SIZE):
        batch_keys = pending_keys[start : start + BATCH_SIZE]
        batch_values = pending_values[start : start + BATCH_SIZE]
        try:
            translated = translate_batch(batch_values, target)
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
            print(f"  translate error ({target}): {error}", file=sys.stderr)
            translated = batch_values
        for key, value in zip(batch_keys, translated, strict=True):
            out[key] = value
        time.sleep(0.3)
    for key in source:
        out.setdefault(key, source[key])
    return dict(sorted(out.items()))


def main() -> int:
    en_dir = LOCALES_DIR / SOURCE
    if not en_dir.is_dir():
        print(f"Missing {en_dir}", file=sys.stderr)
        return 1
    for target in TARGETS:
        target_dir = LOCALES_DIR / target
        target_dir.mkdir(parents=True, exist_ok=True)
        for source_file in sorted(en_dir.glob("*.json")):
            source_data = json.loads(source_file.read_text(encoding="utf-8"))
            target_file = target_dir / source_file.name
            existing: dict[str, str] = {}
            if target_file.is_file():
                existing = json.loads(target_file.read_text(encoding="utf-8"))
            print(f"Translating {source_file.name} -> {target} …")
            translated = translate_namespace(source_data, target, existing)
            target_file.write_text(json.dumps(translated, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Done.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

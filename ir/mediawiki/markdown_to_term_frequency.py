#!/usr/bin/env python3
"""Count terms per Markdown article in a YaCy Pack and stream them to JSONL."""

import os
import sys
import json
import time
import argparse
import multiprocessing
from collections import Counter
from extract_wikipedia_dump_to_markdown import (BASE_DIR, decompressed, dump_date, find_containers,
                                      language_code)
from concurrent.futures import FIRST_COMPLETED, ProcessPoolExecutor, wait

try:
    import regex
except ImportError:
    regex = None

# A word starts with a letter/number; combining marks stay attached to it.
WORD = regex.compile(r"[\p{L}\p{N}][\p{L}\p{M}\p{N}]*", regex.VERSION1) if regex else None


def word_frequencies(text):
    if WORD is None:
        raise ValueError("Install dependencies with: python3 -m pip install -r requirements.txt")
    return Counter(match[0] for match in WORD.finditer(text.lower()))


def available_cores():
    if hasattr(os, "process_cpu_count"):
        return os.process_cpu_count() or 1
    if hasattr(os, "sched_getaffinity"):
        return len(os.sched_getaffinity(0)) or 1
    return os.cpu_count() or 1


def raw_pack_batches(parts, size=64, max_bytes=262144):
    """Read unchanged bytes; keep Bulk action/document pairs in bounded batches."""
    for part in parts:
        batch, byte_count, first_line = [], 0, 1
        with decompressed(part) as stream:
            for number, line in enumerate(stream, 1):
                batch.append(line)
                byte_count += len(line)
                # One oversized article is allowed; never split a Bulk pair.
                if number % 2 == 0 and (len(batch) >= size * 2 or byte_count >= max_bytes):
                    yield part, first_line, batch
                    batch, byte_count, first_line = [], 0, number + 1
        if batch:
            yield part, first_line, batch


def count_batch(batch):
    """Decode, validate, count and encode in a worker with private article counters."""
    source, first_line, raw_lines = batch
    output = []
    for offset, line in enumerate(raw_lines):
        try:
            if offset % 2 == 0:
                if line != b'{"index":{}}\n' and json.loads(line.decode('utf-8')) != {'index': {}}:
                    raise ValueError('expected {"index":{}} action')
                continue
            article = json.loads(line.decode('utf-8'))
            if (not isinstance(article, dict) or not isinstance(article.get('title'), list)
                    or len(article['title']) != 1 or not isinstance(article['title'][0], str)
                    or not article['title'][0] or not isinstance(article.get('text_t'), str)
                    or not isinstance(article.get('url_s'), str) or not article['url_s']):
                raise ValueError('expected url_s, one title, and text_t')
        except (ValueError, TypeError) as error:
            raise ValueError(f'Invalid pack record at {source}:{first_line + offset}: {error}') from error
        counts = word_frequencies(article['text_t'])
        output.append(json.dumps({article['title'][0]: counts}, ensure_ascii=False,
                                 separators=(',', ':')) + '\n')
    if len(raw_lines) % 2:
        raise ValueError(f'Invalid pack record at {source}:{first_line + len(raw_lines) - 1}: '
                         'missing document after index action')
    return len(output), ''.join(output).encode('utf-8')


def write_term_frequencies(destination, batches, jobs):
    completed, last_report, started = 0, 0, time.monotonic()
    pending = set()
    try:
        with destination.open("xb", buffering=1024 * 1024) as stream:
            with ProcessPoolExecutor(max_workers=jobs,
                                     mp_context=multiprocessing.get_context("spawn")) as pool:
                try:
                    exhausted = False
                    while pending or not exhausted:
                        while not exhausted and len(pending) < jobs * 2:
                            batch = next(batches, None)
                            if batch is None:
                                exhausted = True
                            else:
                                pending.add(pool.submit(count_batch, batch))
                        if not pending:
                            break
                        done, pending = wait(pending, return_when=FIRST_COMPLETED)
                        for future in done:
                            count, encoded = future.result()
                            stream.write(encoded)
                            completed += count
                        if completed - last_report >= 1000:
                            print(f"  {completed:,} articles counted "
                                  f"({completed / (time.monotonic() - started):.0f}/s)", flush=True)
                            last_report = completed
                finally:
                    for future in pending:
                        future.cancel()
    finally:
        batches.close()
    return completed


def compute(language, snapshot, jobs=None, base=BASE_DIR):
    if WORD is None:
        raise ValueError("Install dependencies with: python3 -m pip install -r requirements.txt")
    parts = find_containers(base, language, snapshot, '_markdown')
    jobs = available_cores() if jobs is None else jobs
    if jobs < 1:
        raise ValueError("Worker count must be positive")
    output = base / "_statistics"
    output.mkdir(parents=True, exist_ok=True)
    prefix = f"{language.replace('-', '_')}wiki-{snapshot}"
    target = output / f"{prefix}-term-frequency.jsonl"
    partial = target.with_suffix(target.suffix + ".part")
    for path in (target, partial):
        if path.exists():
            raise ValueError(f"Output already exists: {path}. Move it aside before rerunning.")
    batches = raw_pack_batches(parts)
    started = time.monotonic()
    print(f"Counting article terms with {jobs} worker processes into {partial}", flush=True)
    count = write_term_frequencies(partial, batches, jobs)
    partial.replace(target)
    print(f"Complete: {count:,} documents in {time.monotonic() - started:.1f}s\n{target}", flush=True)
    return count


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Count terms per Markdown article and write term-frequency JSONL.",
        epilog="Example: python3 markdown_to_term_frequency.py de 2026-09-01\n"
               "Reads matching _markdown/YaCy-Pack_*.jsonl.gz; writes _statistics/<language>wiki-<date>-term-frequency.jsonl.\n"
               "Each line maps an article title to its term counts. Output remains .part until complete.",
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("language", type=language_code)
    parser.add_argument("date", type=dump_date)
    parser.add_argument("--jobs", type=int, default=None,
                        help="worker processes (default: all available logical CPU cores)")
    args = sys.argv[1:] if argv is None else argv
    if not args:
        parser.print_help()
        return 0
    args = parser.parse_args(args)
    if args.jobs is not None and args.jobs < 1:
        parser.error("--jobs must be positive")
    try:
        compute(args.language, args.date, args.jobs)
    except KeyboardInterrupt:
        print("Interrupted; outputs ending in .part are unfinished.", file=sys.stderr)
        return 130
    except (OSError, ValueError, RuntimeError) as error:
        print(f"Error: {error}. Outputs ending in .part are unfinished.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

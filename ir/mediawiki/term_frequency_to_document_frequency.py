#!/usr/bin/env python3
"""Transform per-article term-frequency JSONL into document-frequency CSV."""

import os
import sys
import csv
import time
import json
import argparse
import multiprocessing
from collections import Counter
from concurrent.futures import FIRST_COMPLETED, ProcessPoolExecutor, wait
from extract_wikipedia_dump_to_markdown import BASE_DIR, dump_date, language_code

DOCUMENT_COUNT_TERM = "_"  # Reserved for corpus size; the tokenizer never emits it.


def available_cores():
    if hasattr(os, 'process_cpu_count'):
        return os.process_cpu_count() or 1
    if hasattr(os, 'sched_getaffinity'):
        return len(os.sched_getaffinity(0)) or 1
    return os.cpu_count() or 1


def raw_term_batches(source, size=512, max_bytes=1048576):
    """Bound queued input by document count and bytes, allowing one large record."""
    batch, byte_count, first_line = [], 0, 1
    with source.open('rb') as stream:
        for number, line in enumerate(stream, 1):
            batch.append(line)
            byte_count += len(line)
            if len(batch) >= size or byte_count >= max_bytes:
                yield source, first_line, batch
                batch, byte_count, first_line = [], 0, number + 1
    if batch:
        yield source, first_line, batch


def count_batch(batch):
    """Decode and count document presence in a worker-local Counter."""
    source, first_line, lines = batch
    frequencies = Counter()
    for number, line in enumerate(lines, first_line):
        try:
            record = json.loads(line.decode('utf-8'))
            if not isinstance(record, dict) or len(record) != 1:
                raise ValueError("expected one article object")
            title, counts = next(iter(record.items()))
            if not title or not isinstance(counts, dict):
                raise ValueError("expected an article title and word-count object")
            if any(not word or word == DOCUMENT_COUNT_TERM or type(count) is not int or count <= 0
                   for word, count in counts.items()):
                raise ValueError("word counts must be positive integers with nonempty, non-reserved words")
        except (ValueError, TypeError) as error:
            raise ValueError(f"Invalid term-frequency record at {source}:{number}: {error}") from error
        # Count each key once per document, regardless of its term frequency.
        frequencies.update(term for term in counts
                           if not term.replace('.', '').replace(',', '').isdecimal())
    return frequencies, len(lines)


def partial_counts(batches, jobs):
    """Map bounded batches to processes, returning completed counters promptly."""
    if jobs == 1:
        for batch in batches:
            yield count_batch(batch)
        return
    with ProcessPoolExecutor(max_workers=jobs,
                             mp_context=multiprocessing.get_context('spawn')) as pool:
        pending = set()
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
                    yield future.result()
        finally:
            for future in pending:
                future.cancel()


def document_frequencies(source, jobs=None):
    """Merge partial counters in one process; no shared mutable counter or locks."""
    jobs = available_cores() if jobs is None else jobs
    if jobs < 1:
        raise ValueError('Worker count must be positive')
    frequencies = Counter()
    documents, last_report = 0, 0
    batches = raw_term_batches(source)
    results = partial_counts(batches, jobs)
    try:
        for counts, records in results:
            frequencies.update(counts)
            documents += records
            if documents - last_report >= 10000:
                print(f"  {documents:,} JSONL records read; {len(frequencies):,} distinct terms", flush=True)
                last_report = documents
    finally:
        results.close()
        batches.close()
    return frequencies, documents


def write_document_frequencies(stream, frequencies, documents):
    """Write retained terms by ascending document frequency, then corpus size."""
    terms = sorted((term for term, count in frequencies.items() if (count >= 10 and term >= 'aa') or count > 100),
                   key=lambda term: (frequencies[term], term))
    writer = csv.writer(stream, delimiter=";", lineterminator="\n")
    writer.writerow(('term', 'count'))
    writer.writerows((term, frequencies[term]) for term in terms)
    writer.writerow((DOCUMENT_COUNT_TERM, documents))
    return len(terms)


def compute(language, snapshot, base=BASE_DIR, jobs=None):
    jobs = available_cores() if jobs is None else jobs
    if jobs < 1:
        raise ValueError('Worker count must be positive')
    output = base / "_statistics"
    prefix = f"{language.replace('-', '_')}wiki-{snapshot}"
    source = output / f"{prefix}-term-frequency.jsonl"
    if source.with_suffix(source.suffix + ".part").exists():
        raise ValueError(f"Term-frequency input is incomplete: {source}")
    if not source.is_file():
        raise ValueError(f"No term-frequency input: {source}")
    target = output / f"{prefix}-document-frequency.csv"
    partial = target.with_suffix(target.suffix + ".part")
    for path in (target, partial):
        if path.exists():
            raise ValueError(f"Output already exists: {path}. Move it aside before rerunning.")
    started = time.monotonic()
    print(f"Reading {source} to count document frequencies with {jobs} workers", flush=True)
    # Reserve the output before reading; a failed run leaves only a .part file.
    with partial.open("x", encoding="utf-8", newline="") as stream:
        frequencies, records = document_frequencies(source, jobs)
        retained = write_document_frequencies(stream, frequencies, records)
    partial.replace(target)
    print(f"Complete: {records:,} documents, {len(frequencies):,} distinct terms, "
          f"{retained:,} terms stored in CSV "
          f"in {time.monotonic() - started:.1f}s\n{target}", flush=True)
    return records, len(frequencies)


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Transform term-frequency JSONL into document-frequency CSV.",
        epilog="Example: python3 term_frequency_to_document_frequency.py de 2026-09-01\n"
               "Reads _statistics/<language>wiki-<date>-term-frequency.jsonl;\n"
               "writes _statistics/<language>wiki-<date>-document-frequency.csv.\n"
               "CSV starts with the header term;count and uses _ for total documents.\n"
               "Retain terms with count >= 10 and term >= 'aa', or count > 100.\n"
               "Rows sort by ascending document frequency, alphabetically for ties; _ comes last.\n"
               "Numeric terms (decimal digits with optional dots/commas) are excluded.",
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("language", type=language_code)
    parser.add_argument("date", type=dump_date)
    parser.add_argument('--jobs', type=int, default=None,
                        help='worker processes (default: all available logical CPU cores; 1 runs sequentially)')
    args = sys.argv[1:] if argv is None else argv
    if not args:
        parser.print_help()
        return 0
    args = parser.parse_args(args)
    if args.jobs is not None and args.jobs < 1:
        parser.error('--jobs must be positive')
    try:
        compute(args.language, args.date, jobs=args.jobs)
    except KeyboardInterrupt:
        print("Interrupted; outputs ending in .part are unfinished.", file=sys.stderr)
        return 130
    except (OSError, ValueError, RuntimeError) as error:
        print(f"Error: {error}. Outputs ending in .part are unfinished.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

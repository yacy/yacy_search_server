#!/usr/bin/env python3
"""Convert document-frequency CSV to Parquet sorted by term, with prefix encoding."""

import argparse
import sys
import time
from extract_wikipedia_dump_to_markdown import BASE_DIR, dump_date, language_code

try:
    import pyarrow as pa
    import pyarrow.csv as csv
    import pyarrow.parquet as pq
except ImportError:
    pa = None


def convert(language, snapshot, base=BASE_DIR, compression_level=22):
    if pa is None:
        raise ValueError('Install dependencies with: python3 -m pip install -r requirements.txt')
    if not 1 <= compression_level <= 22:
        raise ValueError('ZSTD compression level must be between 1 and 22')
    prefix = f"{language.replace('-', '_')}wiki-{snapshot}"
    source = base / '_statistics' / f'{prefix}-document-frequency.csv'
    if source.with_suffix('.csv.part').exists():
        raise ValueError(f'Document-frequency input is incomplete: {source}')
    if not source.is_file():
        raise ValueError(f'No document-frequency input: {source}')
    target = source.with_suffix('.parquet')
    partial = target.with_suffix('.parquet.part')
    for path in (target, partial):
        if path.exists():
            raise ValueError(f'Output already exists: {path}. Move it aside before rerunning.')
    started = time.monotonic()
    # Reserve the output before reading; never publish a failed conversion.
    with partial.open('xb') as stream:
        print(f'Reading {source}', flush=True)
        table = csv.read_csv(
            source,
            parse_options=csv.ParseOptions(delimiter=';'),
            convert_options=csv.ConvertOptions(
                column_types={'term': pa.string(), 'count': pa.int64()},
                strings_can_be_null=False,
            ),
        )
        if table.column_names != ['term', 'count']:
            raise ValueError('Expected CSV header: term;count')
        if any(column.null_count for column in table.columns):
            raise ValueError('Terms and counts must not be null')
        print(f'Sorting {table.num_rows:,} rows by term in memory', flush=True)
        table = table.sort_by([('term', 'ascending')])
        print(f'Writing {partial} with ZSTD level {compression_level}', flush=True)
        pq.write_table(
            table, stream,
            compression='zstd', compression_level=compression_level,
            use_dictionary=False,
            column_encoding={'term': 'DELTA_BYTE_ARRAY', 'count': 'DELTA_BINARY_PACKED'},
            row_group_size=262144,
            sorting_columns=pq.SortingColumn.from_ordering(table.schema, [('term', 'ascending')]),
        )
    partial.replace(target)
    print(f'Complete: {table.num_rows:,} rows in {time.monotonic() - started:.1f}s\n{target}', flush=True)
    return table.num_rows


def main(argv=None):
    parser = argparse.ArgumentParser(
        description='Convert document-frequency CSV to term-sorted, ZSTD-compressed Parquet.',
        epilog='Example: python3 write_parquet.py de 2026-09-01\n'
               'Reads _statistics/<language>wiki-<date>-document-frequency.csv (header: term;count).\n'
               'Writes the same filename with .parquet; the CSV is unchanged.\n'
               'Loads and sorts the full table in RAM, including additional sorting workspace.\n'
               'Unfinished output ends in .part; existing outputs are not overwritten.',
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument('language', type=language_code)
    parser.add_argument('date', type=dump_date)
    parser.add_argument('--compression-level', type=int, default=22, metavar='1..22',
                        help='ZSTD compression level (default: 22; 22 is slowest)')
    args = sys.argv[1:] if argv is None else argv
    if not args:
        parser.print_help()
        return 0
    args = parser.parse_args(args)
    if not 1 <= args.compression_level <= 22:
        parser.error('--compression-level must be between 1 and 22')
    try:
        convert(args.language, args.date, compression_level=args.compression_level)
    except KeyboardInterrupt:
        print('Interrupted; output ending in .part is unfinished.', file=sys.stderr)
        return 130
    except (OSError, ValueError, RuntimeError, MemoryError) as error:
        print(f'Error: {error}. Output ending in .part is unfinished.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())

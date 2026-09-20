#!/usr/bin/env python3
"""Stream XML dump pages through parallel workers directly into one YaCy Pack."""

import sys
import json
import time
import queue
import argparse
import threading
import multiprocessing
import xml.etree.ElementTree as ET
from xml.parsers import expat
from xml.sax.saxutils import quoteattr
from collections import Counter
from concurrent.futures import FIRST_COMPLETED, ProcessPoolExecutor, wait

import bz2
import gzip
import io
import os
import re
import shutil
import subprocess
import tempfile
from contextlib import contextmanager
from datetime import date, datetime
from pathlib import Path
from urllib.parse import quote

try:
    import mwparserfromhell as mw
except ImportError:
    mw = None

BASE_DIR = Path(__file__).resolve().parent
PACK_TIERS = ('common', 'uncommon', 'rare', 'epic', 'legendary')


def available_cores():
    if hasattr(os, 'process_cpu_count'):
        return os.process_cpu_count() or 1
    if hasattr(os, 'sched_getaffinity'):
        return len(os.sched_getaffinity(0)) or 1
    return os.cpu_count() or 1


def language_code(value):
    if not re.fullmatch(r'[a-z][a-z0-9]*(?:[-_][a-z0-9]+)*', value):
        raise argparse.ArgumentTypeError('expected a lowercase Wikipedia language code')
    return value.replace('_', '-')


def dump_date(value):
    try:
        if not re.fullmatch(r'\d{4}-\d{2}-\d{2}', value):
            raise ValueError()
        date.fromisoformat(value)
    except ValueError:
        raise argparse.ArgumentTypeError('expected a valid snapshot date, YYYY-MM-DD') from None
    return value


def revision_timestamp(value):
    try:
        if not re.fullmatch(r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z', value):
            raise ValueError()
        datetime.strptime(value, '%Y-%m-%dT%H:%M:%SZ')
    except ValueError:
        raise ValueError(f'Invalid revision timestamp: {value!r}') from None
    return value


def snapshot_prefix(language, snapshot):
    return f"{language_code(language).replace('-', '_')}wiki-{dump_date(snapshot)}"


def pack_path(folder, language, snapshot, tier='common'):
    if tier not in PACK_TIERS:
        raise ValueError(f'Unknown pack tier: {tier}')
    return Path(folder) / (f'YaCy-Pack_scroll-{tier}-web_wikipedia-{language_code(language)}_'
                           f'{dump_date(snapshot).replace("-", "")}.jsonl.gz')


def find_parts(folder, language, snapshot):
    folder = Path(folder)
    prefix = snapshot_prefix(language, snapshot)
    pattern = re.compile(re.escape(prefix) + r'-p(\d+)p(\d+)\.xml\.bz2')
    parts = {p.name: p for p in folder.glob(f'{prefix}-*.xml.bz2')
             if pattern.fullmatch(p.name) and p.is_file()}
    if list(folder.glob(f'{prefix}-*.xml.bz2.part')):
        raise ValueError(f'Unfinished dump downloads for {prefix}')
    manifest = folder / f'{prefix}-SHA256SUMS'
    if manifest.is_file():
        expected = set()
        for line in manifest.read_text(encoding='utf-8').splitlines():
            if not line.strip():
                continue
            fields = line.split()
            if (len(fields) != 2 or not re.fullmatch(r'[a-fA-F0-9]{64}', fields[0])
                    or not pattern.fullmatch(fields[1])):
                raise ValueError(f'Invalid checksum manifest entry: {line!r}')
            if fields[1] in expected:
                raise ValueError(f'Duplicate dump part in {manifest}: {fields[1]}')
            expected.add(fields[1])
        if not expected or expected != set(parts):
            raise ValueError(f'Dump parts do not match {manifest}; rerun the downloader')
    if not parts:
        raise ValueError(f'No dump parts for {prefix} in {folder}')
    ordered = sorted(parts.values(), key=lambda p: int(pattern.fullmatch(p.name)[1]))
    previous_end = None
    for part in ordered:
        start, end = map(int, pattern.fullmatch(part.name).groups())
        # Deleted/nonexistent page IDs can leave gaps even in a complete export.
        if start > end or (previous_end is not None and start <= previous_end):
            raise ValueError(f'Invalid or overlapping dump ranges at {part.name}')
        previous_end = end
    return ordered


def find_containers(base, language, snapshot, folder='_markdown'):
    directory = Path(base) / folder
    legacy_marker = directory / f'{snapshot_prefix(language, snapshot)}.incomplete'
    if legacy_marker.exists():
        raise ValueError(f'Incomplete Markdown input: {legacy_marker}')
    parts = []
    for tier in PACK_TIERS:
        target = pack_path(directory, language, snapshot, tier)
        for suffix in ('.part', '.incomplete'):
            if target.with_name(target.name + suffix).exists():
                raise ValueError(f'Incomplete Markdown input: {target}')
        if target.is_file():
            parts.append(target)
    if len(parts) != 1:
        raise ValueError(f'Expected exactly one combined Markdown pack for '
                         f'{language}/{snapshot}, found {len(parts)}')
    return parts


@contextmanager
def decompressed(path):
    """Yield a binary stream; check subprocess exit codes and gzip/bzip2 checksums."""
    path = Path(path)
    names = ('lbzip2', 'bzip2') if path.suffix == '.bz2' else ('pigz', 'gzip')
    executable = next((found for name in names if (found := shutil.which(name))), None)
    if path.suffix not in ('.bz2', '.gz'):
        with path.open('rb') as stream:
            yield stream
    elif executable:
        with tempfile.TemporaryFile() as errors:
            process = subprocess.Popen([executable, '-dc', str(path)],
                                       stdout=subprocess.PIPE, stderr=errors)
            try:
                yield process.stdout
                process.stdout.close()
                if process.wait():
                    errors.seek(0)
                    raise ValueError(f'Cannot decompress {path}: '
                                     f'{errors.read().decode("utf-8", "replace").strip()}')
            finally:
                process.stdout.close()
                if process.poll() is None:
                    process.terminate()
                process.wait()
    else:
        opener = bz2.open if path.suffix == '.bz2' else gzip.open
        with opener(path, 'rb') as stream:
            yield stream


@contextmanager
def compressed_output(target):
    """Restart an unfinished pack; atomically replace the final file on success only."""
    target = Path(target)
    partial = target.with_name(target.name + '.part')
    executable = shutil.which('pigz') or shutil.which('gzip')
    if executable:
        with partial.open('wb') as destination, tempfile.TemporaryFile() as errors:
            process = subprocess.Popen([executable, '-c'], stdin=subprocess.PIPE,
                                       stdout=destination, stderr=errors)
            stream = io.TextIOWrapper(process.stdin, encoding='utf-8', newline='\n')
            try:
                yield stream
                stream.close()
                if process.wait():
                    errors.seek(0)
                    raise ValueError(f'Cannot compress {target}: '
                                     f'{errors.read().decode("utf-8", "replace").strip()}')
            finally:
                if process.poll() is None:
                    process.terminate()
                try:
                    stream.close()
                except BrokenPipeError:
                    pass
                process.wait()
    else:
        with gzip.open(partial, 'wt', encoding='utf-8', newline='\n') as stream:
            yield stream
    partial.replace(target)


def render_wikitext(text, hidden_namespaces=()):
    """Render useful article text without fetching templates or external resources."""
    if mw is None:
        raise ValueError('Install dependencies with: python3 -m pip install -r requirements.txt')
    hidden = {'file', 'image', 'media', 'category'} | {
        name.replace('_', ' ').strip().casefold() for name in hidden_namespaces}
    reference_sections = {
        'references', 'notes', 'footnotes', 'external links', 'bibliography',
        'literature', 'sources', 'further reading', 'see also',
        'einzelnachweise', 'anmerkungen', 'weblinks', 'literatur', 'quellen', 'siehe auch',
        'références', 'notes et références', 'liens externes', 'bibliographie', 'voir aussi',
        'note', 'bibliografia', 'collegamenti esterni', 'voci correlate',
        'referencias', 'enlaces externos', 'bibliografía', 'véase también',
    }

    def render(code, sections=False):
        output, skipped_level = [], None
        for node in code.nodes:
            if isinstance(node, mw.nodes.Heading):
                title = render(node.title).strip()
                if sections:
                    if skipped_level is not None and node.level > skipped_level:
                        continue
                    skipped_level = None
                    if title.casefold() in reference_sections:
                        skipped_level = node.level
                        continue
                output.append('\n\n' + '#' * max(2, node.level) + ' ' + title + '\n\n')
                continue
            if skipped_level is not None:
                continue
            if isinstance(node, mw.nodes.Text):
                output.append(str(node))
            elif isinstance(node, mw.nodes.HTMLEntity):
                output.append(node.normalize())
            elif isinstance(node, mw.nodes.Wikilink):
                target = str(node.title).strip()
                prefix = target.split(':', 1)[0].replace('_', ' ').casefold()
                if ':' in target and not target.startswith(':') and prefix in hidden:
                    continue
                output.append(render(node.text) if node.text is not None
                              else target.lstrip(':').replace('_', ' '))
            elif isinstance(node, mw.nodes.ExternalLink):
                output.append(render(node.title) if node.title is not None else '')
            elif isinstance(node, mw.nodes.Argument):
                output.append(render(node.default) if node.default is not None else '')
            elif isinstance(node, mw.nodes.Template):
                name = str(node.name).replace('_', ' ').strip().casefold()
                # A deliberately small set of presentation-only templates.
                parameter = '2' if name == 'lang' else '1'
                if name in {'lang', 'nowrap', 'nobr', 'small', 'smaller', 'big', 'larger',
                            'math', 'mvar', 'var', 'abbr'} and node.has(parameter):
                    value = render(node.get(parameter).value)
                    output.append('$' + value + '$' if name in {'math', 'mvar'} else value)
                else:
                    output.append(' ')
            elif isinstance(node, mw.nodes.Tag):
                tag = str(node.tag).strip().casefold()
                classes = str(node.get('class').value).casefold().split() if node.has('class') else []
                if (tag in {'ref', 'references', 'gallery', 'imagemap', 'timeline', 'score',
                            'script', 'style', 'img', 'noinclude'}
                        or any(c in {'navbox', 'vertical-navbox', 'metadata', 'infobox',
                                     'toc', 'noprint', 'mw-editsection'} for c in classes)):
                    continue
                if tag == 'math':
                    output.append('$' + str(node.contents).strip() + '$')
                    continue
                if tag in {'nowiki', 'pre', 'syntaxhighlight', 'source', 'code'}:
                    output.append(str(node.contents))
                    continue
                if node.wiki_markup in {'*', '#', ';', ':'}:
                    output.append('\x00' + node.wiki_markup)
                    continue
                value = render(node.contents)
                if tag in {'b', 'strong', 'i', 'em'}:
                    marker = '**' if tag in {'b', 'strong'} else '*'
                    output.append(marker + value + marker if value else '')
                elif tag in {'br', 'hr'}:
                    output.append('\n')
                elif tag in {'td', 'th'}:
                    if str(node.wiki_markup) == '|' and value.startswith('+'):
                        value = value[1:]
                    output.append(value.strip() + ' | ')
                elif tag == 'li':
                    output.append('\n- ' + value.strip() + '\n')
                elif tag in {'table', 'tr', 'caption', 'p', 'div', 'ul', 'ol', 'dl', 'dt', 'dd'}:
                    output.append('\n' + value.strip() + '\n')
                else:
                    output.append(value)
            # Comments and unknown node types contribute no text.
        return ''.join(output)

    rendered = render(mw.parse(text), sections=True)

    def list_prefix(match):
        marks = match[1].replace('\x00', '')
        marker = '1. ' if marks[-1] == '#' else '- '
        return '  ' * (len(marks) - 1) + marker

    rendered = re.sub(r'(?m)^(\x00[*#;:](?:\x00[*#;:])*)[ \t]*', list_prefix, rendered)
    rendered = rendered.replace('\x00', '')
    rendered = re.sub(r'__[A-Z][A-Z0-9_]*__', '', rendered)
    rendered = re.sub(r'[ \t]+\n', '\n', rendered)
    return re.sub(r'\n{3,}', '\n\n', rendered).strip()


def convert_batch(articles, language):
    """Return newline-terminated Bulk action/document pairs for one worker batch."""
    language = language_code(language)
    lines = []
    for article in articles:
        title = article['title']
        body = render_wikitext(article['text'], article.get('hidden_namespaces', ()))
        document = {
            'url_s': f'https://{language}.wikipedia.org/wiki/' + quote(title.replace(' ', '_'), safe='/:'),
            'title': [title],
            'text_t': '# ' + title + '\n' + ('\n' + body + '\n' if body else ''),
            'language_s': language,
        }
        if article.get('last_modified'):
            document['last_modified'] = revision_timestamp(article['last_modified'])
        lines.extend(('{"index":{}}\n', json.dumps(document, ensure_ascii=False, separators=(',', ':')) + '\n'))
    return lines


class Stopped(Exception):
    """Unblock queue operations when another stage fails."""


def put(items, value, stopped):
    while not stopped.is_set():
        try:
            items.put(value, timeout=0.1)
            return
        except queue.Full:
            pass
    raise Stopped()


def get(items, stopped):
    while not stopped.is_set():
        try:
            return items.get(timeout=0.1)
        except queue.Empty:
            pass
    raise Stopped()


def frame_pages(stream, emit, stopped, chunk_size=1024 * 1024):
    """Validate XML boundaries without building trees or interpreting articles."""
    parser = expat.ParserCreate(encoding='UTF-8')
    buffer = bytearray()
    base, depth, page_start, number = 0, 0, None, 0
    opening, closing, siteinfo = b'', b'', b''

    def start(name, attributes):
        nonlocal depth, page_start, opening, closing
        if depth == 0:
            if name.split(':')[-1] != 'mediawiki':
                raise ValueError('Expected a MediaWiki XML export')
            # Carry inherited namespaces into each standalone page fragment.
            attrs = ''.join(f' {key}={quoteattr(value)}' for key, value in attributes.items())
            opening = f'<{name}{attrs}>'.encode('utf-8')
            closing = f'</{name}>'.encode('utf-8')
        elif depth == 1 and name.split(':')[-1] in {'page', 'siteinfo'}:
            page_start = parser.CurrentByteIndex
        depth += 1

    def end(name):
        nonlocal depth, page_start, number, siteinfo
        depth -= 1
        if depth == 1 and page_start is not None:
            position = parser.CurrentByteIndex - base
            # For <page/>, Expat reports the position after the whole tag.
            end_tag = ('</' + name).encode('utf-8')
            if buffer[position:position + len(end_tag)] == end_tag:
                position = buffer.index(b'>', position) + 1
            raw = bytes(buffer[page_start - base:position])
            if name.split(':')[-1] == 'siteinfo':
                metadata = ET.fromstring(opening + raw + closing)[0]
                # Keep only namespace metadata needed to suppress localized file/category links.
                for child in list(metadata):
                    if child.tag.rsplit('}', 1)[-1] not in {'namespaces', 'namespacealiases'}:
                        metadata.remove(child)
                siteinfo = ET.tostring(metadata, encoding='utf-8')
            else:
                number += 1
                emit(number, opening + siteinfo + raw + closing)
            page_start = None

    def reject_doctype(*args):
        raise ValueError('MediaWiki dumps with DTD declarations are not supported')

    parser.StartElementHandler = start
    parser.EndElementHandler = end
    parser.StartDoctypeDeclHandler = reject_doctype
    while True:
        if stopped.is_set():
            raise Stopped()
        chunk = stream.read(chunk_size)
        buffer.extend(chunk)
        parser.Parse(chunk, not chunk)
        if not chunk:
            break
        keep = page_start if page_start is not None else parser.CurrentByteIndex
        del buffer[:keep - base]
        base = keep
    return number


def article_record(data, counts):
    """Select namespace-zero wikitext and preserve the current revision timestamp."""
    root = ET.fromstring(data)
    namespace = root.tag.removesuffix('mediawiki')
    page = root.find(namespace + 'page')
    if page is None:
        raise ValueError('Expected a page in the MediaWiki export namespace')
    q = lambda name: namespace + name
    counts['pages'] += 1
    page_namespace = page.findtext(q('ns'))
    if page_namespace is None:
        raise ValueError('Page is missing its namespace')
    if page_namespace != '0':
        counts['other namespaces'] += 1
        return None
    title, page_id = page.findtext(q('title')), page.findtext(q('id'))
    if not title or not page_id or not page_id.isascii() or not page_id.isdecimal():
        raise ValueError('Article is missing a title or numeric page ID')
    revisions = page.findall(q('revision'))
    if len(revisions) != 1:
        raise ValueError(f'{title!r}: expected one current revision, got {len(revisions)}')
    content = revisions[0]
    slots = content.find(q('slots'))
    if slots is not None:
        main = [slot for slot in slots if slot.get('role') == 'main']
        if len(main) != 1:
            raise ValueError(f'{title!r}: expected one main content slot')
        content = main[0]
    if content.findtext(q('model')) not in (None, 'wikitext'):
        counts['other content models'] += 1
        return None
    text = content.find(q('text'))
    if text is None or 'deleted' in text.attrib:
        counts['unavailable text'] += 1
        return None
    if len(text):
        raise ValueError(f'{title!r}: unexpected XML elements inside article text')
    record = {'id': int(page_id), 'title': title, 'text': text.text or ''}
    siteinfo = root.find(q('siteinfo'))
    if siteinfo is not None:
        record['hidden_namespaces'] = [element.text for element in siteinfo.iter()
                                       if element.get('key', element.get('id')) in {'6', '14'}
                                       and element.text]
    timestamp = revisions[0].findtext(q('timestamp'))
    if timestamp is not None:
        record['last_modified'] = revision_timestamp(timestamp)
    counts['articles'] += 1
    return record


def convert_pages(batch, language):
    counts, lines = Counter(), []
    for source, number, data in batch:
        try:
            article = article_record(data, counts)
            if article is not None:
                lines.extend(convert_batch([article], language))
        except Exception as error:
            raise ValueError(f'{source}, page {number}: {error}') from error
    return counts, ''.join(lines)


def read_dumps(parts, inputs, stopped):
    batch, byte_count = [], 0

    def emit(number, data):
        nonlocal batch, byte_count
        batch.append((part.name, number, data))
        byte_count += len(data)
        if len(batch) >= 32 or byte_count >= 262144:
            put(inputs, batch, stopped)
            batch, byte_count = [], 0

    for index, part in enumerate(parts, 1):
        print(f'[{index}/{len(parts)}] Reading {part.name}', flush=True)
        try:
            with decompressed(part) as stream:
                frame_pages(stream, emit, stopped)
        except (expat.ExpatError, ValueError) as error:
            raise ValueError(f'{part.name}: {error}') from error
    if batch:
        put(inputs, batch, stopped)
    # FIFO: all batches precede the reader's poison pill.
    put(inputs, None, stopped)


def write_pack(target, outputs, stopped, counts, legacy_markers):
    last_report = 0
    started = time.monotonic()
    with compressed_output(target) as stream:
        for marker in legacy_markers:
            marker.unlink(missing_ok=True)
        while True:
            result = get(outputs, stopped)
            if result is None:
                break
            batch_counts, lines = result
            stream.write(lines)
            counts.update(batch_counts)
            if counts['pages'] - last_report >= 10000:
                rate = counts['articles'] / max(time.monotonic() - started, 1e-9)
                print(f"  {counts['pages']:,} pages processed; {counts['articles']:,} articles written "
                      f'({rate:.0f}/s)',
                      flush=True)
                last_report = counts['pages']


def run_pipeline(parts, target, language, jobs, legacy_markers=()):
    stopped = threading.Event()
    inputs, outputs = queue.Queue(maxsize=jobs * 2), queue.Queue(maxsize=jobs * 2)
    errors, counts = queue.Queue(), Counter()

    def guarded(function, *args):
        try:
            function(*args)
        except Stopped:
            pass
        except BaseException as error:
            errors.put(error)
            stopped.set()

    reader = threading.Thread(target=guarded, args=(read_dumps, parts, inputs, stopped),
                              name='dump-reader')
    writer = threading.Thread(target=guarded,
                              args=(write_pack, target, outputs, stopped, counts, legacy_markers),
                              name='pack-writer')
    pending = set()
    reader.start()
    writer.start()
    try:
        with ProcessPoolExecutor(max_workers=jobs,
                                 mp_context=multiprocessing.get_context('spawn')) as pool:
            try:
                exhausted = False
                while pending or not exhausted:
                    if stopped.is_set():
                        raise Stopped()
                    while not exhausted and len(pending) < jobs * 2:
                        batch = get(inputs, stopped)
                        if batch is None:
                            exhausted = True
                        else:
                            pending.add(pool.submit(convert_pages, batch, language))
                    if pending:
                        done, pending = wait(pending, timeout=0.1, return_when=FIRST_COMPLETED)
                        for future in done:
                            put(outputs, future.result(), stopped)
            except BaseException:
                stopped.set()
                for future in pending:
                    future.cancel()
                raise
        # Executor shutdown sends worker sentinels and joins the processes.
        # Only then may the writer consume its poison pill and publish the pack.
        reader.join()
        put(outputs, None, stopped)
        writer.join()
        if not errors.empty():
            raise errors.get()
    except Stopped:
        if not errors.empty():
            raise errors.get() from None
        raise RuntimeError('Conversion stopped before completion') from None
    finally:
        stopped.set()
        reader.join()
        writer.join()
    return counts


def convert(language, snapshot, jobs=None, base=BASE_DIR, tier='common', overwrite=False):
    if mw is None:
        raise ValueError('Install dependencies with: python3 -m pip install -r requirements.txt')
    jobs = available_cores() if jobs is None else jobs
    if jobs < 1:
        raise ValueError('Worker count must be positive')
    parts = find_parts(base / '_dumps', language, snapshot)
    output = base / '_markdown'
    target = pack_path(output, language, snapshot, tier)
    marker = target.with_name(target.name + '.incomplete')
    legacy_marker = output / f'{snapshot_prefix(language, snapshot)}.incomplete'
    if (target.is_file() and not overwrite and not marker.exists()
            and not target.with_name(target.name + '.part').exists()):
        legacy_marker.unlink(missing_ok=True)
        print(f'Already complete; skipping {target.name}', flush=True)
        return Counter()
    output.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    print(f'Using {jobs} worker processes; writing to {target}', flush=True)
    counts = run_pipeline(parts, target, language, jobs, (marker, legacy_marker))
    print(f"Complete: {counts['pages']:,} pages, {counts['articles']:,} articles "
          f'in {time.monotonic() - started:.1f}s\n{target}', flush=True)
    return counts


def main(argv=None):
    parser = argparse.ArgumentParser(
        description='Convert Wikipedia XML dumps directly to a Markdown YaCy Pack.',
        epilog='Example: python3 extract_wikipedia_dump_to_markdown.py de 2026-09-01\n'
               'Reads _dumps/*.xml.bz2; writes one _markdown/YaCy-Pack_*.jsonl.gz.\n'
               'Creates no intermediate wikitext files. Completed packs are skipped;\n'
               'unfinished .part packs restart in full. Install dependencies from requirements.txt.',
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument('language', type=language_code)
    parser.add_argument('date', type=dump_date)
    parser.add_argument('--jobs', type=int, default=None,
                        help='worker processes (default: all available logical CPU cores)')
    parser.add_argument('--tier', choices=PACK_TIERS, default='common')
    parser.add_argument('--overwrite', action='store_true', help='regenerate a completed pack')
    args = sys.argv[1:] if argv is None else argv
    if not args:
        parser.print_help()
        return 0
    args = parser.parse_args(args)
    if args.jobs is not None and args.jobs < 1:
        parser.error('--jobs must be positive')
    try:
        convert(args.language, args.date, args.jobs, tier=args.tier, overwrite=args.overwrite)
    except KeyboardInterrupt:
        print('Interrupted; output ending in .part is unfinished.', file=sys.stderr)
        return 130
    except (OSError, ValueError, RuntimeError) as error:
        print(f'Error: {error}. Output ending in .part is unfinished.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())

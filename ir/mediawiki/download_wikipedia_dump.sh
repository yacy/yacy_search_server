#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: download_wikipedia_dump.sh LANGUAGE

Download the latest completed Wikipedia current-content dump for a language.

Examples:
  ./download_wikipedia_dump.sh de      German Wikipedia
  ./download_wikipedia_dump.sh en      English Wikipedia
  ./download_wikipedia_dump.sh simple  Simple English Wikipedia

Files stay compressed directly in _dumps/ next to this script.
Requires curl and shasum. Reruns skip verified files and resume partial downloads.
No arguments, -h, and --help all show this help.
EOF
}

if [[ $# -eq 0 ]] || [[ $# -eq 1 && ( $1 == -h || $1 == --help ) ]]; then
    usage
    exit 0
fi
if [[ $# -ne 1 ]] || [[ ! $1 =~ ^[a-z][a-z0-9]*([-_][a-z0-9]+)*$ ]]; then
    echo "Error: supply one lowercase Wikipedia language code, such as de or en." >&2
    usage >&2
    exit 1
fi
for command in curl shasum; do
    command -v "$command" >/dev/null || { echo "Error: $command is required." >&2; exit 1; }
done

wiki="${1//-/_}wiki"
base="https://dumps.wikimedia.org/other/mediawiki_content_current/$wiki"
dump_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/_dumps"
mkdir -p "$dump_dir"
manifest=$(mktemp "$dump_dir/.SHA256SUMS.XXXXXX")
trap 'rm -f "$manifest"' EXIT

# Wikimedia publishes SHA256SUMS only when an export is complete.
# https://wikitech.wikimedia.org/wiki/MediaWiki_Content_File_Exports
dates=$(curl --fail --silent --show-error --location "$base/" |
    sed -n 's/.*href="\([0-9]\{4\}-[0-9]\{2\}-[0-9]\{2\}\)\/".*/\1/p' |
    sort -ru)
export_url=
for date in $dates; do
    candidate="$base/$date/xml/bzip2"
    status=$(curl --silent --show-error --location --output "$manifest" \
        --write-out '%{http_code}' "$candidate/SHA256SUMS")
    case "$status" in
        200) export_url="$candidate"; break ;;
        404) continue ;;
        *) echo "Error: HTTP $status fetching $candidate/SHA256SUMS" >&2; exit 1 ;;
    esac
done
if [[ -z "$export_url" || ! -s "$manifest" ]]; then
    echo "Error: no completed dump found for $wiki." >&2
    exit 1
fi

checksum_file="$wiki-$date-SHA256SUMS"
cp "$manifest" "$dump_dir/$checksum_file"
cd "$dump_dir"
echo "Downloading $wiki ($date) to $dump_dir"
while read -r checksum filename; do
    [[ -n "$filename" ]] || continue
    # Export filenames are plain basenames, not paths.
    if [[ ! $filename =~ ^[a-zA-Z0-9_-]+\.xml\.bz2$ ]]; then
        echo "Error: unexpected dump filename: $filename" >&2
        exit 1
    fi
    if [[ -f "$filename" ]]; then
        echo "Checking $filename"
        if printf '%s  %s\n' "$checksum" "$filename" | shasum -a 256 --check; then
            echo "Already downloaded; skipping $filename"
            continue
        fi
        echo "Checksum mismatch; downloading $filename again"
    fi
    echo "Downloading $filename"
    curl --fail --location --show-error --continue-at - \
        --output "$filename.part" "$export_url/$filename"
    printf '%s  %s\n' "$checksum" "$filename.part" | shasum -a 256 --check
    mv "$filename.part" "$filename"
done < "$checksum_file"
echo "Download complete: $dump_dir"

#!/usr/bin/env sh

# Exercises YaCy's Apache-HttpClient-backed RemoteInstance against an external
# Solr collection. This is an opt-in integration gate.

set -eu

[ -n "${YACY_REMOTE_SOLR_URL:-}" ] || {
    echo "SKIP: set YACY_REMOTE_SOLR_URL (for example http://host:8983/solr/)" >&2
    exit 2
}

repo_root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$repo_root"
[ -d build/classes/java/main ] || { echo "FAIL: run ant compile first" >&2; exit 1; }

work=$(mktemp -d "${TMPDIR:-/tmp}/yacy-remote-solr-smoke.XXXXXX")
trap 'rm -rf "$work"' EXIT HUP INT TERM
classpath=build/classes/java/main
for archive in lib/*.jar; do
    case $(basename "$archive") in yacycore.jar) continue ;; esac
    classpath="$classpath:$archive"
done

javac --release 17 -cp "$classpath" -d "$work" \
    test/java/net/yacy/cora/federate/solr/connector/RemoteSolrSmoke.java
java -cp "$work:$classpath" net.yacy.cora.federate.solr.connector.RemoteSolrSmoke \
    "$YACY_REMOTE_SOLR_URL"

echo "PASS: remote Solr request through YaCy RemoteInstance"

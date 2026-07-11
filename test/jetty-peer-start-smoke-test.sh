#!/usr/bin/env sh

# Lifecycle gate owned by an external isolated-peer harness. Commands are
# explicit because this script must never start or stop the developer's normal
# DATA directory by guessing.

set -eu

[ -n "${YACY_SMOKE_START_COMMAND:-}" ] && [ -n "${YACY_SMOKE_STOP_COMMAND:-}" ] || {
    echo "SKIP: set YACY_SMOKE_START_COMMAND and YACY_SMOKE_STOP_COMMAND for an isolated DATA directory" >&2
    exit 2
}

base_url=${YACY_SMOKE_BASE_URL:-http://127.0.0.1:8090}
timeout=${YACY_SMOKE_TIMEOUT:-60}
started=false
cleanup() {
    if [ "$started" = true ]; then
        sh -c "$YACY_SMOKE_STOP_COMMAND"
    fi
}
trap cleanup EXIT HUP INT TERM

sh -c "$YACY_SMOKE_START_COMMAND"
started=true

attempt=0
while [ "$attempt" -lt "$timeout" ]; do
    if curl --silent --fail --max-time 2 "$base_url/api/version.xml" >/dev/null 2>&1; then
        break
    fi
    attempt=$((attempt + 1))
    sleep 1
done
[ "$attempt" -lt "$timeout" ] || { echo "FAIL: isolated peer did not start" >&2; exit 1; }

test/jetty-smoke-test.sh "$base_url"
curl --silent --show-error --fail --max-time 30 \
    "$base_url/solr/collection1/select?q=*:*&rows=0&wt=json" \
    | grep -F 'numFound' >/dev/null || {
        echo "FAIL: embedded Solr query failed" >&2
        exit 1
    }
echo "ok - embedded Solr query"

cleanup
started=false
trap - EXIT HUP INT TERM
echo "PASS: isolated peer start, embedded Solr and clean stop"

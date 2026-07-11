#!/usr/bin/env sh

# Black-box HTTP contract for the embedded web server.
#
# The checks intentionally use only externally visible behavior so the same
# script can be run before and after an embedded Jetty migration.
#
# Usage:
#   test/jetty-smoke-test.sh [base-url]
#
# The default base URL is read from DATA/SETTINGS/yacy.conf when available,
# and otherwise falls back to http://127.0.0.1:8090.

set -eu

if ! command -v curl >/dev/null 2>&1; then
    echo "curl is required to run the Jetty smoke test." >&2
    exit 2
fi

default_port=8090
if [ -f DATA/SETTINGS/yacy.conf ]; then
    configured_port=$(sed -n 's/^port=//p' DATA/SETTINGS/yacy.conf | head -n 1)
    if [ -n "$configured_port" ]; then
        default_port=$configured_port
    fi
fi

base_url=${1:-"http://127.0.0.1:$default_port"}
base_url=${base_url%/}
curl_timeout=${YACY_SMOKE_TIMEOUT:-30}

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/yacy-jetty-smoke.XXXXXX")
trap 'rm -rf "$work_dir"' EXIT HUP INT TERM

response_headers=$work_dir/headers
response_body=$work_dir/body
checks=0

fail() {
    echo "FAIL: $*" >&2
    if [ -s "$response_headers" ]; then
        echo "Response headers:" >&2
        sed 's/^/  /' "$response_headers" >&2
    fi
    if [ -s "$response_body" ]; then
        echo "Response body (first 20 lines):" >&2
        sed -n '1,20{s/^/  /;p;}' "$response_body" >&2
    fi
    exit 1
}

request() {
    method=$1
    path=$2
    shift 2

    : > "$response_headers"
    : > "$response_body"

    status=$(curl --silent --show-error \
        --max-time "$curl_timeout" \
        --request "$method" \
        --dump-header "$response_headers" \
        --output "$response_body" \
        --write-out '%{http_code}' \
        "$@" \
        "$base_url$path") || fail "$method $path could not be requested"

    [ "$status" = "200" ] || fail "$method $path returned HTTP $status"
}

assert_header_contains() {
    header_name=$1
    expected=$2
    actual=$(tr -d '\r' < "$response_headers" | awk -F ': *' -v name="$header_name" '
        tolower($1) == tolower(name) {
            value = $0
            sub(/^[^:]*:[[:space:]]*/, "", value)
            print value
        }
    ' | tail -n 1)

    printf '%s' "$actual" | grep -F "$expected" >/dev/null 2>&1 || \
        fail "header $header_name does not contain '$expected' (actual: '$actual')"
}

assert_body_contains() {
    expected=$1
    grep -F "$expected" "$response_body" >/dev/null 2>&1 || \
        fail "response body does not contain '$expected'"
}

pass() {
    checks=$((checks + 1))
    echo "ok $checks - $1"
}

echo "YaCy embedded-server smoke test: $base_url"

# Wait briefly for a peer that has just been started by an external harness.
attempt=0
while [ "$attempt" -lt 30 ]; do
    if curl --silent --fail --max-time 2 "$base_url/api/version.xml" >/dev/null 2>&1; then
        break
    fi
    attempt=$((attempt + 1))
    sleep 1
done
[ "$attempt" -lt 30 ] || fail "YaCy did not become ready within 30 seconds"

request GET /api/version.xml
assert_header_contains Content-Type text/xml
assert_body_contains '<version>'
assert_body_contains '<buildVersion>'
pass "reflection-backed XML endpoint"

request GET /env/grafics/YaCyLogo2012.svg
assert_header_contains Content-Type image/svg+xml
assert_body_contains '<svg'
pass "static resource"

request GET /index.html
assert_header_contains Content-Type text/html
assert_body_contains '<!DOCTYPE html>'
assert_body_contains '<html'
pass "rendered HTML template"

request GET '/suggest.json?q=jetty-smoke'
assert_header_contains Content-Type application/json
assert_header_contains Access-Control-Allow-Origin '*'
assert_body_contains '["jetty-smoke",['
pass "reflection-backed JSON endpoint and CORS header"

request POST /api/version.xml \
    --header 'Content-Type: application/x-www-form-urlencoded' \
    --data 'smoke=post'
assert_header_contains Content-Type text/xml
assert_body_contains '<version>'
pass "URL-encoded POST dispatch"

request OPTIONS /api/version.xml
assert_header_contains Allow 'GET,HEAD,POST,OPTIONS'
pass "OPTIONS method contract"

echo "PASS: $checks embedded-server checks succeeded."

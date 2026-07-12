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
    expected_status=$3
    shift 3

    : > "$response_headers"
    : > "$response_body"

    if [ "$method" = HEAD ]; then
        status=$(curl --silent --show-error \
            --max-time "$curl_timeout" \
            --head \
            --dump-header "$response_headers" \
            --output /dev/null \
            --write-out '%{http_code}' \
            "$@" \
            "$base_url$path") || fail "$method $path could not be requested"
        : > "$response_body"
    else
        status=$(curl --silent --show-error \
            --max-time "$curl_timeout" \
            --request "$method" \
            --dump-header "$response_headers" \
            --output "$response_body" \
            --write-out '%{http_code}' \
            "$@" \
            "$base_url$path") || fail "$method $path could not be requested"
    fi

    [ "$status" = "$expected_status" ] || \
        fail "$method $path returned HTTP $status (expected $expected_status)"
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

request GET /api/version.xml 200
assert_header_contains Content-Type text/xml
assert_body_contains '<version>'
assert_body_contains '<buildVersion>'
pass "reflection-backed XML endpoint"

request GET /env/grafics/YaCyLogo2012.svg 200
assert_header_contains Content-Type image/svg+xml
assert_body_contains '<svg'
pass "static resource"

cp "$response_body" "$work_dir/static-full"
static_length=$(wc -c < "$work_dir/static-full" | tr -d ' ')
request HEAD /env/grafics/YaCyLogo2012.svg 200
assert_header_contains Content-Type image/svg+xml
assert_header_contains Content-Length "$static_length"
[ ! -s "$response_body" ] || fail "HEAD response contains a body"
pass "HEAD without response body"

request GET /env/grafics/YaCyLogo2012.svg 206 --header 'Range: bytes=0-9'
assert_header_contains Content-Range "bytes 0-9/$static_length"
[ "$(wc -c < "$response_body" | tr -d ' ')" -eq 10 ] || fail "range body is not 10 bytes"
head -c 10 "$work_dir/static-full" > "$work_dir/static-prefix"
cmp "$work_dir/static-prefix" "$response_body" >/dev/null 2>&1 || fail "range body has unexpected bytes"
pass "single byte range"

request GET /env/grafics/YaCyLogo2012.svg 206 --header 'Range: bytes=0-4,48-51'
assert_header_contains Content-Type 'multipart/byteranges; boundary='
assert_body_contains "Content-Range: bytes 0-4/$static_length"
assert_body_contains "Content-Range: bytes 48-51/$static_length"
assert_body_contains '<?xml'
assert_body_contains 'YaCy'
multipart_boundary=$(tr -d '\r' < "$response_headers" | awk -F 'boundary=' '
    tolower($0) ~ /^content-type: multipart\/byteranges/ { print $2 }
' | tail -n 1)
[ -n "$multipart_boundary" ] || fail "multipart range response has no boundary"
tail -c 80 "$response_body" | grep -F -- "--$multipart_boundary--" >/dev/null 2>&1 || \
    fail "multipart range response has no closing boundary"
pass "multipart byte ranges"

request GET /env/grafics/YaCyLogo2012.svg 416 --header "Range: bytes=$static_length-"
assert_header_contains Content-Range "bytes */$static_length"
pass "unsatisfiable byte range"

# YaCy deliberately suppresses Last-Modified on static responses to control its
# cache policy, but still implements If-Modified-Since against the resource.
request GET /env/grafics/YaCyLogo2012.svg 304 \
    --header 'If-Modified-Since: Thu, 31 Dec 2099 23:59:59 GMT'
[ ! -s "$response_body" ] || fail "304 response contains a body"
pass "If-Modified-Since"

request GET /index.html 200
assert_header_contains Content-Type text/html
assert_body_contains '<!DOCTYPE html>'
assert_body_contains '<html'
pass "rendered HTML template"

request GET '/suggest.json?q=jetty-smoke' 200
assert_header_contains Content-Type application/json
assert_header_contains Access-Control-Allow-Origin '*'
assert_body_contains '["jetty-smoke",['
pass "reflection-backed JSON endpoint and CORS header"

request POST /api/version.xml 200 \
    --header 'Content-Type: application/x-www-form-urlencoded' \
    --data 'smoke=post'
assert_header_contains Content-Type text/xml
assert_body_contains '<version>'
pass "URL-encoded POST dispatch"

request OPTIONS /api/version.xml 200
assert_header_contains Allow 'GET,HEAD,POST,OPTIONS'
pass "OPTIONS method contract"

request GET /this-resource-must-not-exist-yacy-jetty-smoke 404
assert_header_contains Content-Type text/html
assert_body_contains 'YaCy '
pass "YaCy 404 error page"

request GET /env/grafics/YaCyLogo2012.svg 200 \
    --header 'Accept-Encoding: gzip'
assert_header_contains Content-Encoding gzip
gzip -dc "$response_body" > "$work_dir/gzip-decoded" || fail "gzip response cannot be decompressed"
cmp "$work_dir/static-full" "$work_dir/gzip-decoded" >/dev/null 2>&1 || \
    fail "decompressed response differs from the uncompressed resource"
pass "gzip response compression"

printf 'q=jetty-gzip-smoke' > "$work_dir/gzip-request-form"
gzip -c "$work_dir/gzip-request-form" > "$work_dir/gzip-request-body"
request POST /suggest.json 200 \
    --header 'Content-Type: application/x-www-form-urlencoded' \
    --header 'Content-Encoding: gzip' \
    --data-binary "@$work_dir/gzip-request-body"
assert_body_contains '["jetty-gzip-smoke",['
pass "gzip request decompression"

echo "PASS: $checks embedded-server checks succeeded."

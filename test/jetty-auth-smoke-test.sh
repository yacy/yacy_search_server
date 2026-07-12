#!/usr/bin/env sh

# Authentication contract for the embedded server. A remote URL must really
# reach the peer through a non-loopback socket; forwarded headers are not used.

set -eu

base_url=${YACY_SMOKE_BASE_URL:-http://127.0.0.1:8090}
remote_base_url=${YACY_SMOKE_REMOTE_BASE_URL:-}
protected_path=${YACY_SMOKE_PROTECTED_PATH:-/ConfigAccounts_p.html}
conf=${YACY_SMOKE_CONF:-DATA/SETTINGS/yacy.conf}
timeout=${YACY_SMOKE_TIMEOUT:-30}
require_complete=${YACY_SMOKE_REQUIRE_COMPLETE:-false}

case "$require_complete" in
    true|false) ;;
    *) echo "FAIL: YACY_SMOKE_REQUIRE_COMPLETE must be true or false" >&2; exit 2 ;;
esac

[ -f "$conf" ] || { echo "FAIL: missing peer configuration: $conf" >&2; exit 2; }

status() {
    curl --silent --show-error --max-time "$timeout" --output /dev/null \
        --write-out '%{http_code}' "$@"
}

localhost_access=$(sed -n 's/^adminAccountForLocalhost=//p' "$conf" | head -n 1)
local_status=$(status "$base_url$protected_path")
if [ "$localhost_access" = "true" ]; then
    [ "$local_status" = 200 ] || { echo "FAIL: localhost bypass returned $local_status" >&2; exit 1; }
else
    [ "$local_status" = 401 ] || { echo "FAIL: protected localhost request returned $local_status" >&2; exit 1; }
fi
echo "ok 1 - configured localhost access rule"

YACY_DATA_PATH=$(CDPATH= cd -- "$(dirname "$conf")/.." && pwd)
export YACY_DATA_PATH
bin/apicall.sh 'ConfigAccounts_p.html' >/dev/null
echo "ok 2 - bin/apicall.sh localhost authentication"

if [ -n "${YACY_SMOKE_ADMIN_USER:-}" ] && [ -n "${YACY_SMOKE_ADMIN_PASSWORD:-}" ]; then
    authenticated_status=$(status --anyauth \
        --user "$YACY_SMOKE_ADMIN_USER:$YACY_SMOKE_ADMIN_PASSWORD" \
        "$base_url$protected_path")
    [ "$authenticated_status" = 200 ] || { echo "FAIL: administrator login returned $authenticated_status" >&2; exit 1; }
    echo "ok 3 - administrator credentials"
else
    if [ "$require_complete" = true ]; then
        echo "FAIL: complete acceptance requires YACY_SMOKE_ADMIN_USER and YACY_SMOKE_ADMIN_PASSWORD" >&2
        exit 1
    fi
    echo "ok 3 # SKIP - set YACY_SMOKE_ADMIN_USER and YACY_SMOKE_ADMIN_PASSWORD"
fi

if [ -n "$remote_base_url" ]; then
    remote_status=$(status "$remote_base_url$protected_path")
    [ "$remote_status" = 401 ] || { echo "FAIL: remote protected request returned $remote_status" >&2; exit 1; }
    echo "ok 4 - remote request does not receive localhost bypass"
else
    if [ "$require_complete" = true ]; then
        echo "FAIL: complete acceptance requires YACY_SMOKE_REMOTE_BASE_URL over a real non-loopback path" >&2
        exit 1
    fi
    echo "ok 4 # SKIP - set YACY_SMOKE_REMOTE_BASE_URL to a real non-loopback path"
fi

echo "PASS: embedded-server authentication contract"

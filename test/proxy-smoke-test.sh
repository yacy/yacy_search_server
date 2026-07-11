#!/usr/bin/env sh

# Live HTTP proxy and CONNECT contract. The peer must be started with the
# transparent proxy enabled and both targets must be controlled test services.

set -eu

proxy=${YACY_SMOKE_PROXY:-}
http_target=${YACY_SMOKE_PROXY_HTTP_TARGET:-}
https_target=${YACY_SMOKE_PROXY_HTTPS_TARGET:-}
[ -n "$proxy" ] && [ -n "$http_target" ] && [ -n "$https_target" ] || {
    echo "SKIP: set YACY_SMOKE_PROXY, YACY_SMOKE_PROXY_HTTP_TARGET and YACY_SMOKE_PROXY_HTTPS_TARGET" >&2
    exit 2
}

timeout=${YACY_SMOKE_TIMEOUT:-30}
curl --silent --show-error --fail --max-time "$timeout" --proxy "$proxy" \
    --noproxy '' \
    "$http_target" >/dev/null
echo "ok 1 - HTTP proxy traffic"

# An HTTPS request through an HTTP proxy necessarily establishes a CONNECT tunnel.
curl --silent --show-error --fail --max-time "$timeout" --proxy "$proxy" \
    --noproxy '' \
    "$https_target" >/dev/null
echo "ok 2 - HTTPS CONNECT tunnel"

echo "PASS: proxy and CONNECT contract"

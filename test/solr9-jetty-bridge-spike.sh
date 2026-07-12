#!/usr/bin/env sh

# Exercise the generated Solr 9.10 bridge. Run `ant compile` first. An optional
# JETTY12_CLASSPATH verifies coexistence with already resolved Jetty 12 jars.

set -eu

repo_root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$repo_root"

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

[ -d build/classes/java/main ] || fail "missing compiled YaCy classes; run ant compile first"
test/jetty-solr-dependency-guard.sh

work=$(mktemp -d "${TMPDIR:-/tmp}/yacy-solr9-bridge-test.XXXXXX")
trap 'rm -rf "$work"' EXIT HUP INT TERM

classpath=build/classes/java/main
for archive in lib/*.jar libt/*.jar; do
    case $(basename "$archive") in
        yacycore.jar) continue ;;
    esac
    classpath="$classpath:$archive"
done
if [ -n "${JETTY12_CLASSPATH:-}" ]; then
    classpath="$classpath:$JETTY12_CLASSPATH"
fi

javac --release 17 -cp "$classpath" -d "$work" \
    test/java/net/yacy/cora/federate/solr/connector/EmbeddedSolrConnectorTest.java

java -cp "$work:$classpath" org.junit.runner.JUnitCore \
    net.yacy.cora.federate.solr.connector.EmbeddedSolrConnectorTest

echo "PASS: integrated Solr 9.10 bridge starts, updates, queries and closes."

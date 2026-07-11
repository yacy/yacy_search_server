#!/usr/bin/env sh

# Guard the classpath boundary needed to migrate YaCy's embedded server from
# Jetty 9 to Jetty 12 while Solr 9's Jetty client is relocated into a private
# package and kept out of YaCy source code.

set -eu

repo_root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$repo_root"

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

if grep -R -n -E \
    --include='*.java' \
    '(Http2SolrClient|CloudHttp2SolrClient|ConcurrentUpdateHttp2SolrClient|LBHttp2SolrClient|JettySolrRunner)' \
    source >/dev/null 2>&1; then
    grep -R -n -E \
        --include='*.java' \
        '(Http2SolrClient|CloudHttp2SolrClient|ConcurrentUpdateHttp2SolrClient|LBHttp2SolrClient|JettySolrRunner)' \
        source >&2
    fail "YaCy source must not use Solr's Jetty-backed clients or runner"
fi

if grep -E \
    'name="(jetty-deploy|jetty-jmx)"' \
    ivy.xml >/dev/null 2>&1; then
    fail "ivy.xml contains a forbidden direct Jetty dependency"
fi

grep -E 'name="jetty-client".*conf="solr9-bridge->master"' ivy.xml >/dev/null 2>&1 || \
    fail "jetty-client must only be a direct input of the Solr 9 bridge"

grep -E 'org="org.eclipse.jetty" name="jetty-io"' ivy.xml >/dev/null 2>&1 || \
    fail "jetty-io must be an explicit dependency because YaCy imports its API"

for artifact in http2-client http2-common http2-http-client-transport; do
    grep -E "org=\"org.eclipse.jetty.http2\" name=\"$artifact\".*conf=\"solr9-bridge->master\"" ivy.xml >/dev/null 2>&1 || \
        fail "$artifact must only be an input of the Solr 9 bridge"
done

if [ -d lib ]; then
    for pattern in \
        'jetty-deploy-*.jar' \
        'jetty-jmx-*.jar' \
        'solr-core-*.jar' \
        'solr-solrj-*.jar' \
        'solr-scripting-*.jar' \
        'http2-*.jar'; do
        for artifact in lib/$pattern; do
            [ -e "$artifact" ] || continue
            fail "forbidden resolved artifact: $artifact"
        done
    done

    for artifact in \
        solr-core-9.0.0 \
        solr-solrj-9.0.0 \
        solr-scripting-9.0.0 \
        jetty-client-9.4.58.v20250814 \
        jetty-http-9.4.58.v20250814 \
        jetty-io-9.4.58.v20250814 \
        jetty-util-9.4.58.v20250814 \
        http2-client-9.4.58.v20250814 \
        http2-common-9.4.58.v20250814 \
        http2-http-client-transport-9.4.58.v20250814; do
        jar="lib/solr9-bridge-$artifact.jar"
        [ -f "$jar" ] || fail "missing generated bridge artifact: $jar"
        if jar tf "$jar" | grep '^org/eclipse/jetty/' >/dev/null 2>&1; then
            fail "unrelocated Jetty class in $jar"
        fi
        if zipgrep -a -E 'org(/|\.)eclipse(/|\.)jetty' "$jar" >/dev/null 2>&1; then
            fail "unrelocated Jetty reference in $jar"
        fi
    done
fi

echo "PASS: Solr 9 uses only the relocated Jetty client island."

#!/usr/bin/env sh

# Guard the production Jetty 12 classpath while Solr 9's Jetty 9 client remains
# relocated into a private package and out of YaCy source code.

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

if grep -R -n -E \
    --include='YaCyDefaultServlet.java' \
    --include='*ServletResource.java' \
    'org\.eclipse\.jetty|Jetty9ServletResource' \
    source/net/yacy/http/servlets >/dev/null 2>&1; then
    fail "YaCyDefaultServlet resources must remain servlet-container neutral"
fi

if grep -E \
    'name="(jetty-deploy|jetty-jmx)"' \
    ivy.xml >/dev/null 2>&1; then
    fail "ivy.xml contains a forbidden direct Jetty dependency"
fi

grep -E 'name="jetty-client".*conf="solr9-bridge->master"' ivy.xml >/dev/null 2>&1 || \
    fail "jetty-client must only be a direct input of the Solr 9 bridge"

server_jetty_version=12.0.37

for artifact in jetty-http jetty-io jetty-proxy jetty-security jetty-server jetty-util; do
    grep -E "org=\"org.eclipse.jetty\" name=\"$artifact\" rev=\"$server_jetty_version\" conf=\"compile->default\"" ivy.xml >/dev/null 2>&1 || \
        fail "$artifact $server_jetty_version must be on the production compile classpath"
done

for artifact in jetty-ee8-nested jetty-ee8-security jetty-ee8-servlet jetty-ee8-servlets jetty-ee8-webapp; do
    grep -E "org=\"org.eclipse.jetty.ee8\" name=\"$artifact\" rev=\"$server_jetty_version\" conf=\"compile->default\"" ivy.xml >/dev/null 2>&1 || \
        fail "$artifact $server_jetty_version must be an explicit production EE8 dependency"
done
if grep -E 'org="org.eclipse.jetty.compression"|name="jetty-compression-' ivy.xml >/dev/null 2>&1; then
    fail "Jetty 12.1 compression modules must not be declared on the Jetty 12.0 classpath"
fi

if grep -E 'conf="jetty12-migration|rev="9\.4\.58\.v20250814" conf="compile' ivy.xml >/dev/null 2>&1; then
    fail "ivy.xml still contains an isolated migration configuration or public Jetty 9 dependency"
fi

if grep -R -n -E --include='*.java' \
    '(Jetty9HttpServerImpl|AbstractRemoteHandler|YaCyLoginService|YaCySecurityHandler|YaCyDigestCredential|YacyDomainHandler|InetPathAccessHandler)' \
    source >/dev/null 2>&1; then
    fail "obsolete Jetty 9 adapter references remain in production source"
fi

for artifact in http2-client http2-common http2-http-client-transport; do
    grep -E "org=\"org.eclipse.jetty.http2\" name=\"$artifact\".*conf=\"solr9-bridge->master\"" ivy.xml >/dev/null 2>&1 || \
        fail "$artifact must only be an input of the Solr 9 bridge"
done

for artifact in slf4j-api slf4j-jdk14; do
    grep -E "name=\"$artifact\" rev=\"1.7.36\" conf=\"solr9-bridge->master\"" ivy.xml >/dev/null 2>&1 || \
        fail "$artifact 1.7.36 must only be an input of the Solr 9 bridge"
done

grep -E 'org="org.eclipse.jetty.toolchain" name="jetty-servlet-api" rev="4.0.9"' ivy.xml >/dev/null 2>&1 || \
    fail "Jetty's EE8 Servlet 4 API must be an explicit dependency"
grep -E 'exclude org="javax.servlet" module="javax.servlet-api"' ivy.xml >/dev/null 2>&1 || \
    fail "transitive javax.servlet-api artifacts must be excluded"

if [ -d lib ]; then
    public_jetty_count=0
    for artifact in lib/jetty-*.jar; do
        [ -e "$artifact" ] || continue
        case $(basename "$artifact") in
            jetty-servlet-api-*.jar)
                # Jetty's Servlet 4 toolchain has its own version line.
                continue
                ;;
        esac
        public_jetty_count=$((public_jetty_count + 1))
        case $(basename "$artifact") in
            *-"$server_jetty_version".jar) ;;
            *) fail "production Jetty artifact is not on version $server_jetty_version: $artifact" ;;
        esac
    done
    [ "$public_jetty_count" -gt 0 ] || \
        fail "no production Jetty $server_jetty_version artifacts found"

    servlet_api_count=0
    for artifact in lib/*servlet-api-*.jar; do
        [ -e "$artifact" ] || continue
        servlet_api_count=$((servlet_api_count + 1))
        [ "$(basename "$artifact")" = "jetty-servlet-api-4.0.9.jar" ] || \
            fail "unexpected Servlet API artifact: $artifact"
    done
    [ "$servlet_api_count" -eq 1 ] || \
        fail "expected exactly one public Servlet API artifact, found $servlet_api_count"
    jar tf lib/jetty-servlet-api-4.0.9.jar | grep '^javax/servlet/resources/web-app_4_0.xsd$' >/dev/null 2>&1 || \
        fail "Jetty Servlet API is missing the EE8 web.xml schema"

    for pattern in \
        'jetty-deploy-*.jar' \
        'jetty-jmx-*.jar' \
        'jetty-slf4j-impl-*.jar' \
        'jetty-jakarta-servlet-api-*.jar' \
        'jakarta.servlet-api-*.jar' \
        'jetty-ee9-*.jar' \
        'jetty-ee10-*.jar' \
        'jetty-ee11-*.jar' \
        'solr-core-*.jar' \
        'solr-solrj-*.jar' \
        'solr-scripting-*.jar' \
        'http2-*.jar' \
        'slf4j-*-1.7.36.jar'; do
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
        http2-http-client-transport-9.4.58.v20250814 \
        slf4j-api-1.7.36 \
        slf4j-jdk14-1.7.36; do
        jar="lib/solr9-bridge-$artifact.jar"
        [ -f "$jar" ] || fail "missing generated bridge artifact: $jar"
        if jar tf "$jar" | grep '^org/eclipse/jetty/' >/dev/null 2>&1; then
            fail "unrelocated Jetty class in $jar"
        fi
        if zipgrep -a -E 'org(/|\.)eclipse(/|\.)jetty' "$jar" >/dev/null 2>&1; then
            fail "unrelocated Jetty reference in $jar"
        fi
        if jar tf "$jar" | grep '^org/slf4j/' >/dev/null 2>&1; then
            fail "unrelocated SLF4J class in $jar"
        fi
        if zipgrep -a -E 'org(/|\.)slf4j' "$jar" >/dev/null 2>&1; then
            fail "unrelocated SLF4J reference in $jar"
        fi
    done
fi

echo "PASS: production Jetty 12 and the relocated Solr 9 Jetty 9 dependencies are separated."

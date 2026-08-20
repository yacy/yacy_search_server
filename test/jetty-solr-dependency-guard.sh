#!/usr/bin/env sh

# Guard the production Jetty 12 classpath while Solr 9's Jetty 10 client remains
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
bridge_jetty_version=10.0.26
solr_version=9.10.1
lucene_version=9.12.3
metrics_version=4.2.26
jackson_annotations_version=2.22
jackson_version=2.22.1

for artifact in core api scripting solrj solrj-streaming solrj-zookeeper; do
    grep -E "org=\"org.apache.solr\" name=\"solr-$artifact\" rev=\"$solr_version\" conf=\"solr9-bridge->master\"" ivy.xml >/dev/null 2>&1 || \
        fail "solr-$artifact $solr_version must be an explicit Solr bridge input"
done

for artifact in analysis-common backward-codecs classification codecs expressions grouping highlighter join memory misc queries queryparser spatial-extras suggest; do
    grep -E "org=\"org.apache.lucene\" name=\"lucene-$artifact\" rev=\"$lucene_version\"" ivy.xml >/dev/null 2>&1 || \
        fail "lucene-$artifact must be on version $lucene_version"
done

for artifact in core jmx; do
    grep -E "org=\"io.dropwizard.metrics\" name=\"metrics-$artifact\" rev=\"$metrics_version\"" ivy.xml >/dev/null 2>&1 || \
        fail "metrics-$artifact must match Solr 9.10.1's tested version $metrics_version"
done

grep -E "org=\"com.fasterxml.jackson.core\" name=\"jackson-annotations\" rev=\"$jackson_annotations_version\"" ivy.xml >/dev/null 2>&1 || \
    fail "jackson-annotations must be on version $jackson_annotations_version"
for coordinate in \
    'com.fasterxml.jackson.core jackson-core' \
    'com.fasterxml.jackson.core jackson-databind' \
    'com.fasterxml.jackson.dataformat jackson-dataformat-cbor' \
    'com.fasterxml.jackson.module jackson-module-jakarta-xmlbind-annotations'; do
    set -- $coordinate
    grep -E "org=\"$1\" name=\"$2\" rev=\"$jackson_version\"" ivy.xml >/dev/null 2>&1 || \
        fail "$2 must be on version $jackson_version"
done

commons_lang_declarations=$(grep -c 'org="org.apache.commons" name="commons-lang3"' ivy.xml || true)
[ "$commons_lang_declarations" -eq 1 ] || \
    fail "commons-lang3 must have exactly one direct declaration"

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
    fail "ivy.xml still contains an isolated migration configuration or obsolete public Jetty dependency"
fi

if grep -R -n -E --include='*.java' \
    '(Jetty9HttpServerImpl|AbstractRemoteHandler|YaCyLoginService|YaCySecurityHandler|YaCyDigestCredential|YacyDomainHandler|InetPathAccessHandler)' \
    source >/dev/null 2>&1; then
    fail "obsolete pre-Jetty-12 adapter references remain in production source"
fi

for artifact in http2-client http2-common http2-http-client-transport; do
    grep -E "org=\"org.eclipse.jetty.http2\" name=\"$artifact\".*conf=\"solr9-bridge->master\"" ivy.xml >/dev/null 2>&1 || \
        fail "$artifact must only be an input of the Solr 9 bridge"
done

if grep -E 'name="slf4j-(api|jdk14)" rev="1\.7\.36"' ivy.xml >/dev/null 2>&1; then
    fail "Solr 9.10 must use YaCy's shared SLF4J 2 runtime"
fi

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

    public_lucene_count=0
    for artifact in lib/lucene-*.jar; do
        [ -e "$artifact" ] || continue
        public_lucene_count=$((public_lucene_count + 1))
        case $(basename "$artifact") in
            *-"$lucene_version".jar) ;;
            *) fail "Lucene artifact is not on version $lucene_version: $artifact" ;;
        esac
    done
    [ "$public_lucene_count" -gt 0 ] || \
        fail "no Lucene $lucene_version artifacts found"

    for artifact in metrics-core metrics-jmx; do
        count=0
        for jar_path in lib/$artifact-*.jar; do
            [ -e "$jar_path" ] || continue
            count=$((count + 1))
            [ "$(basename "$jar_path")" = "$artifact-$metrics_version.jar" ] || \
                fail "unexpected $artifact revision on the runtime classpath: $jar_path"
        done
        [ "$count" -eq 1 ] || \
            fail "expected exactly one $artifact $metrics_version jar, found $count"
    done

    for artifact in jackson-core jackson-databind jackson-dataformat-cbor jackson-module-jakarta-xmlbind-annotations; do
        count=0
        for jar_path in lib/$artifact-*.jar; do
            [ -e "$jar_path" ] || continue
            count=$((count + 1))
            [ "$(basename "$jar_path")" = "$artifact-$jackson_version.jar" ] || \
                fail "unexpected $artifact revision on the runtime classpath: $jar_path"
        done
        [ "$count" -eq 1 ] || \
            fail "expected exactly one $artifact $jackson_version jar, found $count"
    done

    jackson_annotations_count=0
    for jar_path in lib/jackson-annotations-*.jar; do
        [ -e "$jar_path" ] || continue
        jackson_annotations_count=$((jackson_annotations_count + 1))
        [ "$(basename "$jar_path")" = "jackson-annotations-$jackson_annotations_version.jar" ] || \
            fail "unexpected jackson-annotations revision on the runtime classpath: $jar_path"
    done
    [ "$jackson_annotations_count" -eq 1 ] || \
        fail "expected exactly one jackson-annotations $jackson_annotations_version jar, found $jackson_annotations_count"

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
        'solr-api-*.jar' \
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
        solr-api-$solr_version \
        solr-core-$solr_version \
        solr-scripting-$solr_version \
        solr-solrj-$solr_version \
        solr-solrj-streaming-$solr_version \
        solr-solrj-zookeeper-$solr_version \
        jetty-alpn-java-client-$bridge_jetty_version \
        jetty-client-$bridge_jetty_version \
        jetty-http-$bridge_jetty_version \
        jetty-io-$bridge_jetty_version \
        jetty-util-$bridge_jetty_version \
        http2-client-$bridge_jetty_version \
        http2-common-$bridge_jetty_version \
        http2-http-client-transport-$bridge_jetty_version; do
        jar="lib/solr9-bridge-$artifact.jar"
        [ -f "$jar" ] || fail "missing generated bridge artifact: $jar"
        grep -F "<classpathentry kind=\"lib\" path=\"$jar\"/>" .classpath >/dev/null 2>&1 || \
            fail "Eclipse classpath is missing generated bridge artifact: $jar"
        if jar tf "$jar" | grep '^org/eclipse/jetty/' >/dev/null 2>&1; then
            fail "unrelocated Jetty class in $jar"
        fi
        if zipgrep -a -E 'org(/|\.)eclipse(/|\.)jetty' "$jar" >/dev/null 2>&1; then
            fail "unrelocated Jetty reference in $jar"
        fi
        if jar tf "$jar" | grep '^org/slf4j/' >/dev/null 2>&1; then
            fail "bridge artifact must not bundle SLF4J classes: $jar"
        fi
    done
fi

echo "PASS: production Jetty 12 and the relocated Solr 9 Jetty 10 dependencies are separated."

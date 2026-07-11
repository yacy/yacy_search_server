# Jetty 12 Migration Contract

## Scope

YaCy will migrate its embedded HTTP server from Jetty 9 to Jetty 12 while
remaining on Java 17 and keeping the existing Servlet 4.0 `javax.servlet`
source API. Jetty 12's EE8 environment provides this compatibility layer; the
migration does not require converting YaCy to `jakarta.servlet`.

This contract covers the dependency boundary only. It does not yet migrate the
Jetty-specific adapters under `source/net/yacy/http`.

## Solr Boundary

YaCy embeds Solr 9 but does not directly use Solr's embedded `JettySolrRunner`
or its Jetty-based HTTP/2 clients. Remote Solr access is implemented with
`HttpSolrClient` and `ConcurrentUpdateSolrClient`, both configured with Apache
HttpClient in `RemoteInstance`.

The three Solr artifacts in `ivy.xml` therefore belong to the private
`solr9-bridge` configuration and explicitly exclude the `org.eclipse.jetty`
and `org.eclipse.jetty.http2` families. They are retrieved below
`build/solr9-bridge/input`, not directly into the runtime `lib` directory.

Solr 9.0 nevertheless has an eager internal dependency that cannot be removed:
`CoreContainer` constructs both `HttpShardHandlerFactory` and
`UpdateShardHandler`, which create `Http2SolrClient` instances during startup
even in YaCy's standalone embedded configuration. The build therefore rewrites
Solr's Jetty references and the minimal Jetty 9 client/HTTP2 implementation to
the private `net.yacy.solr9.jetty` package. The generated artifacts have the
`solr9-bridge-` prefix under `lib/`; unrelocated Solr and HTTP2 jars are removed
from that directory before compilation.

Run the boundary guard after resolving dependencies:

```sh
ant clean compile
test/jetty-solr-dependency-guard.sh
```

## Current Jetty 9 Roots

The direct Jetty dependencies reflect APIs imported by YaCy source code.
Dependencies needed only by another Jetty module remain transitive.

| Responsibility | Direct Jetty 9 artifact |
| --- | --- |
| HTTP primitives | `jetty-http` |
| Connection and output APIs | `jetty-io` |
| Server and handlers | `jetty-server` |
| Utility, resource, and TLS APIs | `jetty-util` |
| CONNECT and proxy handlers | `jetty-proxy` |
| Authentication and constraints | `jetty-security` |
| Servlet container | `jetty-servlet` |
| QoS and servlet helpers | `jetty-servlets` |
| Web application context | `jetty-webapp` |

The public `jetty-client` is owned transitively by `jetty-proxy`. YaCy has no
direct client API imports. `jetty-deploy` and `jetty-jmx` are not part of the
current root graph. HTTP/2 exists only inside the relocated Solr island, not as
an embedded-server feature.

## Jetty 12 EE8 Target Graph

The minimal target keeps Jetty Core separate from the EE8 Servlet layer.

| Current responsibility | Jetty 12 target family |
| --- | --- |
| HTTP, IO, server, utilities | Jetty Core `jetty-http`, `jetty-io`, `jetty-server`, `jetty-util` |
| Proxy handlers | Jetty Core `jetty-proxy` and its client transitive |
| Servlet container | `org.eclipse.jetty.ee8:jetty-ee8-servlet` |
| Servlet helpers and QoS | `org.eclipse.jetty.ee8:jetty-ee8-servlets` |
| Web application context | `org.eclipse.jetty.ee8:jetty-ee8-webapp` |
| Servlet authentication | `org.eclipse.jetty.ee8:jetty-ee8-security` plus Jetty Core security transitives |
| Servlet API | Servlet 4.0 in the `javax.servlet` namespace, aligned with the EE8 environment |

Do not add Jetty deploy, JMX, server-side HTTP/2, or Jakarta EE modules unless
a concrete YaCy feature requires them and its contract is verified separately.
The private Solr 9 HTTP/2 island above is the only current exception.

### Solr 9 Isolation Gate (P1)

Jetty 9 client classes and Jetty 12 server classes cannot safely share the
same application classloader because they use overlapping
`org.eclipse.jetty.*` packages with incompatible APIs.

The P1 investigation rules out the two initially attractive shortcuts:

1. **A Solr upgrade is not part of this migration.** YaCy remains on Solr
   9.0.0. A later Solr upgrade may provide another migration option, but it is
   deliberately not a P1 implementation path. A comparison with Solr 9.10.1
   also showed that upgrading within the Solr 9 line would not remove the
   boundary: it selects Jetty 10.0.26 and still eagerly constructs
   `Http2SolrClient` instances.
2. **Whole-Solr classloader isolation is not a minimal boundary for YaCy.**
   Solr API objects are part of the application boundary: 88 files under
   `source/` and `test/` currently import `org.apache.solr` types. A child
   classloader would either create incompatible class identities or require a
   broad new facade and data conversion layer.

Solr 10 is also outside this migration contract: although it moves to Jetty
12, the Solr 10 server requires Java 21 and uses the Jakarta Servlet namespace.

P1 implements a **relocated Solr 9.0.0 Jetty client bridge**. The bridge keeps
the public `org.apache.solr.*` classes visible to YaCy while
rewriting Solr 9.0.0's internal `org.eclipse.jetty.*` references and the
required Jetty 9 client/HTTP2 implementation into a private package. It is
built reproducibly by Ant; no edited jar is stored in the repository. P1 does
not change the Solr or Lucene versions.

P1 is complete only when a bridge proof passes all of these checks:

1. only the selected embedded-server Jetty line uses the public
   `org.eclipse.jetty` package;
2. no unrelocated Jetty 9/10 class is packaged by the bridge;
3. `EmbeddedSolrConnectorTest` starts and closes a `CoreContainer`;
4. an embedded update followed by a query succeeds;
5. the bridge dependency set and relocation rules are generated by the build;
6. the bridge can be removed without changing YaCy's Solr-facing source API.

The integrated bridge passes `EmbeddedSolrConnectorTest` (`OK (4 tests)`) with
the original Solr and HTTP2 jars removed. It also passes when
Jetty 12.1.11 Core client, HTTP, IO, proxy, security, server, and utility jars
are present in the same application classpath. The reduced private island is:

- Solr Core, SolrJ, and Solr Scripting 9.0.0 with only their Jetty references
  rewritten;
- Jetty 9.4 client, HTTP, IO, and utility;
- Jetty 9.4 HTTP/2 client, common, and HTTP-client transport.

Jetty server, servlet, security, proxy, webapp, and XML are not included in
the Solr island. The proof can be repeated with:

```sh
ant clean compile
test/solr9-jetty-bridge-spike.sh
```

Set `JETTY12_CLASSPATH` to a colon-separated set of resolved Jetty 12 jars to
repeat the coexistence variant. The script compiles only its focused test into
a temporary directory; the bridge itself is already produced by `ant compile`.

All six P1 checks pass. The Solr isolation gate is therefore closed for the
Jetty 12 server migration. Reconsidering Solr remains a separate future
decision, not an automatic part of this migration.

The optional version comparison that established the limitation of the Solr 9
line can be repeated without changing YaCy's production dependencies with:

```sh
javap -classpath ~/.ivy2/cache/org.apache.solr/solr-core/jars/solr-core-9.10.1.jar \
  -private -c org.apache.solr.update.UpdateShardHandler
javap -classpath ~/.ivy2/cache/org.apache.solr/solr-core/jars/solr-core-9.10.1.jar \
  -private -c org.apache.solr.handler.component.HttpShardHandlerFactory
```

## Completion Gates

The dependency phase is complete when all of these checks pass with one public
Jetty version on the resolved classpath and the private Solr island:

1. `ant clean compile`
2. `test/jetty-solr-dependency-guard.sh`
3. Startup with embedded Solr enabled
4. A proven resolution for the Solr 9 isolation gate
5. A remote Solr request through the Apache-based client
6. Proxy traffic including CONNECT
7. `test/jetty-smoke-test.sh`

The live gates are split by the environment they require:

- `test/jetty-smoke-test.sh` checks HTTP methods, ranges, conditional requests,
  error dispatch, and gzip request/response handling against a running peer;
- `test/jetty-auth-smoke-test.sh` checks localhost, `bin/apicall.sh`, optional
  credentials, and an optional real non-loopback path;
- `test/jetty-peer-start-smoke-test.sh` starts and stops an isolated peer through
  explicit harness commands and queries its embedded Solr core;
- `test/remote-solr-smoke-test.sh` queries an explicitly configured external
  Solr instance through YaCy's Apache-HttpClient-backed `RemoteInstance`;
- `test/proxy-smoke-test.sh` checks HTTP proxy traffic and an HTTPS CONNECT
  tunnel against explicitly configured controlled targets.

The environment-dependent gates exit with status 2 when their required target
or isolated-peer harness has not been supplied. This is a reported skip, not a
successful verification.

The final migration acceptance must run the authentication gate with no skips:

```sh
YACY_SMOKE_REQUIRE_COMPLETE=true \
YACY_SMOKE_ADMIN_USER=admin \
YACY_SMOKE_ADMIN_PASSWORD='the configured password' \
YACY_SMOKE_REMOTE_BASE_URL='http://a-real-non-loopback-peer-address:8090' \
test/jetty-auth-smoke-test.sh
```

The HTTP range contract includes a single satisfiable range (`206`), multiple
satisfiable ranges as `multipart/byteranges`, and an unsatisfiable range
(`416`).

### Switch-time logging tests

`Slf4jJulBridgeTest` is version-neutral and must pass both before and after the
server switch. It proves that the public SLF4J 2 provider routes the
`org.eclipse.jetty` logger namespace into `java.util.logging` and therefore the
YaCy logging configuration.

`Jetty9LoggingFacadeTest` is deliberately a Jetty 9 baseline test. It imports
Jetty 9's removed `org.eclipse.jetty.util.log.Log` API and asserts the old
`Slf4jLog` facade. Remove it together with `Jetty9HttpServerImpl` during the
switch and replace it with a Jetty 12 integration test that starts and stops a
real server while capturing an `org.eclipse.jetty` record through JUL. The
Jetty 12 test must not assert an internal logger implementation class.

The following implementation phase may then replace `Jetty9HttpServerImpl` and
the remaining Jetty adapter APIs without changing the Solr dependency graph.

## P2.1 Helper Removal

`YaCyDefaultServlet` no longer imports Jetty HTTP header/method constants,
MIME lookup, URI joining, writer adaptation, inclusive byte ranges, multipart
output, or `Resource`. The small operations use Servlet/JDK APIs or local
implementations. Static resources are exposed through the container-neutral
`ServletResource` interface; `Jetty9ServletResource` is the only Jetty 9
adapter for the existing resource behavior.

`YaCyQoSFilter` and `YaCyDigestCredential` remain explicit container adapters
rather than being replaced by simplified local implementations that could
change request priority or authentication behavior.

## P2.2 Authentication And Access Rules

The request-level administrator decision is represented by
`AdminAccessPolicy`: public access, the configured localhost bypass, or the
administrator role. It combines the existing pure `AdminSecurity` checks
without depending on Servlet or Jetty APIs. In particular, the localhost
without account option and the localhost-only stored-hash authentication used
by `bin/apicall.sh` remain supported.

`AdminAuthenticationContext` carries the true socket peer IP only for the
duration of the current authentication call. `YaCySecurityHandler` publishes
and clears that context, and `YaCyDigestCredential` only adapts Jetty's BASIC
and DIGEST credential objects to the container-neutral password check.

The portable address/path syntax of `serverClient` is represented by
`InetPathAccessRule`. `InetPathAccessHandler` remains the Jetty 9 matcher
adapter; Jetty 12 can consume the normalized `address|path` rules with its
native path-aware access handler.

## P2.3 Handler Boundaries

Proxy request processing and cache processing no longer receive Jetty's
`Request`. `RequestCompletion` is the container-neutral signal that processing
is complete; `AbstractRemoteHandler` adapts it to Jetty 9's
`Request.setHandled(true)`. Consequently `ProxyHandler` and
`ProxyCacheHandler` have no Jetty imports.

The `proxyClient` regular-expression list is evaluated by the pure
`ProxyAccessPolicy`. The local virtual-host cache used by proxy detection is a
concurrent set because it is populated by both the discovery thread and
request threads.

The remaining Jetty handler classes now have explicit migration roles:

| Jetty 9 adapter | Responsibility to reproduce with Jetty 12 |
| --- | --- |
| `AbstractRemoteHandler` | detect proxy traffic and delegate CONNECT tunnelling |
| `CrashProtectionHandler` | outer exception barrier around proxy and servlet handlers |
| `YacyDomainHandler` | rewrite `.yacy` destinations and redispatch into the proxy chain |
| `YaCyErrorHandler` | render the container error page |
| `YaCyQoSFilter` | optional request prioritization when enabled in `web.xml` |

These classes intentionally remain container adapters. They must be ported
against the corresponding Jetty 12 APIs rather than replaced with servlet-only
approximations that would change CONNECT, error dispatch, or prioritization.

## P2.4 Embedded Server Bootstrap Contract

`HttpServerBootstrapConfig` is the common immutable input for Jetty 9 and the
future Jetty 12 implementation. It fixes the following startup values:

| Concern | Contract |
| --- | --- |
| HTTP binding | constructor host and port |
| Acceptor threads | half the available processors, clamped to 1 through 4 |
| Request header limit | 16,384 bytes |
| Connector idle timeout | 9,000 ms |
| HTTP accept queue | 128 |
| HTTPS | `server.https`, configured SSL port, initialized SSL context only |
| Web root | configured `htRootPath` below the application directory |
| Descriptors | `defaults/web.xml`, optionally `DATA/SETTINGS/web.xml` |
| Request decompression | Gzip inflate buffer of 4,096 bytes |
| Response compression | controlled by `server.response.compress.gzip` |
| Form limit | unlimited at the proxy-handler context boundary |
| Proxy handlers | present only when transparent proxy is enabled |
| Network access | configured `serverClient` address/path rules plus loopback |
| Authentication realm | configured administrator realm, unchanged for DIGEST hashes |

TLS preparation remains a YaCy bootstrap responsibility because it may import
a configured PKCS#12 file, create/update the JKS file, clear the one-shot
import settings, and construct the JDK `SSLContext`. The container adapter only
attaches that context to its HTTPS connector.

The request pipeline order is a behavioral requirement:

1. optional server-client address/path gate;
2. outer crash-protection barrier;
3. `.yacy` domain rewrite;
4. cached proxy response, when transparent proxy is enabled;
5. live HTTP proxy and CONNECT tunnel, when enabled;
6. root web application with monitor filter, admin security, gzip/inflate, and
   `YaCyDefaultServlet`;
7. container default handler for requests left unhandled.

The connection-close listener must remove the matching `ConnectionInfo` entry
created by `MonitorFilter`. The default servlet and monitor filter remain
hard-coded mandatory components; additional servlet mappings come from the
merged web descriptors.

`YaCyHttpServer` defines the runtime contract used outside the adapter:

- synchronous start;
- synchronous stop followed by join;
- asynchronous delayed port reconnect without rebuilding the handler graph;
- HTTPS availability and bound-port reporting;
- administrator identity eviction/reload after credential changes;
- container version reporting;
- current non-idle worker-thread count.

A Jetty 12 implementation must first be added beside `Jetty9HttpServerImpl`
and satisfy this complete contract before the construction site in `yacy.java`
is switched. No caller outside the HTTP package should need a Jetty type or a
Jetty-version condition.

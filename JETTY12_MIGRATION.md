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

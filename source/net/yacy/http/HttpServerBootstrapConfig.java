/**
 *  HttpServerBootstrapConfig
 *  Copyright 2026 by Michael Peter Christen
 *  First released 12.07.2026 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.http;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.utils.PKCS12Tool;

/** Immutable, servlet-container-neutral input for the embedded HTTP server. */
public final class HttpServerBootstrapConfig {

    public static final int REQUEST_HEADER_SIZE = 16_384;
    public static final long CONNECTOR_IDLE_TIMEOUT_MILLIS = 9_000L;
    public static final int ACCEPT_QUEUE_SIZE = 128;
    public static final int REQUEST_INFLATE_BUFFER_SIZE = 4_096;
    public static final int MAX_FORM_CONTENT_SIZE = 200_000;

    private final int httpPort;
    private final String bindHost;
    private final int acceptorCount;
    private final boolean httpsEnabled;
    private final int httpsPort;
    private final String htrootPath;
    private final String defaultsWebXml;
    private final String overrideWebXml;
    private final boolean gzipResponsesEnabled;
    private final boolean transparentProxyEnabled;
    private final String serverClientRules;
    private final String adminRealm;

    private HttpServerBootstrapConfig(final int httpPort, final String bindHost,
            final int acceptorCount, final boolean httpsEnabled, final int httpsPort,
            final String htrootPath, final String defaultsWebXml, final String overrideWebXml,
            final boolean gzipResponsesEnabled, final boolean transparentProxyEnabled,
            final String serverClientRules, final String adminRealm) {
        this.httpPort = httpPort;
        this.bindHost = bindHost;
        this.acceptorCount = acceptorCount;
        this.httpsEnabled = httpsEnabled;
        this.httpsPort = httpsPort;
        this.htrootPath = htrootPath;
        this.defaultsWebXml = defaultsWebXml;
        this.overrideWebXml = overrideWebXml;
        this.gzipResponsesEnabled = gzipResponsesEnabled;
        this.transparentProxyEnabled = transparentProxyEnabled;
        this.serverClientRules = serverClientRules;
        this.adminRealm = adminRealm;
    }

    public int httpPort() { return this.httpPort; }
    public String bindHost() { return this.bindHost; }
    public int acceptorCount() { return this.acceptorCount; }
    public boolean httpsEnabled() { return this.httpsEnabled; }
    public int httpsPort() { return this.httpsPort; }
    public String htrootPath() { return this.htrootPath; }
    public String defaultsWebXml() { return this.defaultsWebXml; }
    public String overrideWebXml() { return this.overrideWebXml; }
    public boolean gzipResponsesEnabled() { return this.gzipResponsesEnabled; }
    public boolean transparentProxyEnabled() { return this.transparentProxyEnabled; }
    public String serverClientRules() { return this.serverClientRules; }
    public String adminRealm() { return this.adminRealm; }
    
    public static HttpServerBootstrapConfig from(final Switchboard switchboard,
            final int httpPort, final String bindHost) {
        final int cores = Runtime.getRuntime().availableProcessors();
        return new HttpServerBootstrapConfig(
                httpPort,
                bindHost,
                acceptorCountFor(cores),
                switchboard.getConfigBool("server.https", false),
                switchboard.getConfigInt(SwitchboardConstants.SERVER_SSLPORT, 8443),
                switchboard.appPath + "/" + switchboard.getConfig(
                        SwitchboardConstants.HTROOT_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT),
                switchboard.appPath + "/defaults/web.xml",
                switchboard.dataPath + "/DATA/SETTINGS/web.xml",
                switchboard.getConfigBool(SwitchboardConstants.SERVER_RESPONSE_COMPRESS_GZIP,
                        SwitchboardConstants.SERVER_RESPONSE_COMPRESS_GZIP_DEFAULT),
                switchboard.getConfigBool(SwitchboardConstants.PROXY_TRANSPARENT_PROXY, false),
                switchboard.getConfig("serverClient", "*"),
                switchboard.getConfig(SwitchboardConstants.ADMIN_REALM, "YaCy"));
    }

    static int acceptorCountFor(final int availableProcessors) {
        return Math.max(1, Math.min(4, availableProcessors / 2));
    }

    /** Container-neutral preparation of the configured server TLS context. */
    final static class ServerTlsContextFactory {

        private ServerTlsContextFactory() {
        }

        static SSLContext create(final Switchboard switchboard) {
            String keyStoreFileName = switchboard.getConfig("keyStore", "").trim();
            String keyStorePassword = switchboard.getConfig("keyStorePassword", "").trim();
            final String pkcs12ImportFile = switchboard.getConfig("pkcs12ImportFile", "").trim();

            if (keyStoreFileName.isEmpty() && keyStorePassword.isEmpty() && pkcs12ImportFile.isEmpty()) {
                keyStoreFileName = "defaults/freeworldKeystore";
                keyStorePassword = "freeworld";
                switchboard.setConfig("keyStore", keyStoreFileName);
                switchboard.setConfig("keyStorePassword", keyStorePassword);
            }

            if (!pkcs12ImportFile.isEmpty()) {
                ConcurrentLog.info("SERVER", "Import certificates from import file '" + pkcs12ImportFile + "'.");
                try {
                    final String pkcs12ImportPassword = switchboard.getConfig("pkcs12ImportPwd", "").trim();
                    final PKCS12Tool pkcsTool = new PKCS12Tool(pkcs12ImportFile, pkcs12ImportPassword);
                    if (keyStoreFileName.isEmpty()) {
                        keyStoreFileName = "DATA/SETTINGS/myPeerKeystore";
                        final KeyStore keyStore = KeyStore.getInstance("JKS");
                        keyStore.load(null, keyStorePassword.toCharArray());
                        try (FileOutputStream output = new FileOutputStream(keyStoreFileName)) {
                            keyStore.store(output, keyStorePassword.toCharArray());
                        }
                        switchboard.setConfig("keyStore", keyStoreFileName);
                    }
                    pkcsTool.importToJKS(keyStoreFileName, keyStorePassword);
                    switchboard.setConfig("pkcs12ImportFile", "");
                    switchboard.setConfig("pkcs12ImportPwd", "");
                } catch (final Exception error) {
                    ConcurrentLog.severe("SERVER",
                            "Unable to import certificate from import file '" + pkcs12ImportFile + "'.", error);
                }
            } else if (keyStoreFileName.isEmpty()) {
                return null;
            }

            try {
                ConcurrentLog.info("SERVER", "Initializing SSL support ...");
                final KeyStore keyStore = KeyStore.getInstance("JKS");
                try (FileInputStream input = new FileInputStream(keyStoreFileName)) {
                    keyStore.load(input, keyStorePassword.toCharArray());
                } catch (final IOException error) {
                    ConcurrentLog.warn("SERVER", "Could not read keystore file " + keyStoreFileName);
                    throw error;
                }
                final KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(
                        KeyManagerFactory.getDefaultAlgorithm());
                keyManagers.init(keyStore, keyStorePassword.toCharArray());
                final SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(keyManagers.getKeyManagers(), null, null);
                return sslContext;
            } catch (final Exception error) {
                final String message = "FATAL ERROR: Unable to initialize the SSL Socket factory. "
                        + error.getMessage();
                ConcurrentLog.severe("SERVER", message);
                System.out.println(message);
                return null;
            }
        }
    }


    /** Container-neutral representation of a server-client address/path rule. */
    public static final class InetPathAccessRule {
    
        private static final String DEFAULT_PATH = "/*";
    
        private final String addressPattern;
        private final String pathPattern;
    
        private InetPathAccessRule(final String addressPattern, final String pathPattern) {
            this.addressPattern = addressPattern;
            this.pathPattern = pathPattern;
        }
    
        public static InetPathAccessRule parse(final String pattern) {
            if (pattern == null || pattern.isEmpty()) {
                throw new IllegalArgumentException("Access rule must not be empty");
            }
            final int separator = pattern.indexOf('|');
            final String address = separator > 0 ? pattern.substring(0, separator) : pattern;
            final String path = separator > 0 && pattern.length() > separator + 1
                    ? pattern.substring(separator + 1)
                    : DEFAULT_PATH;
            if (address.isEmpty()) {
                throw new IllegalArgumentException("Access rule has no address: " + pattern);
            }
            return new InetPathAccessRule(address, path);
        }
    
        public String addressPattern() {
            return this.addressPattern;
        }
    
        public String pathPattern() {
            return this.pathPattern;
        }
    
        public String asJettyPattern() {
            return this.addressPattern + '|' + this.pathPattern;
        }
    }
}

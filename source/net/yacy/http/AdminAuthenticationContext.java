package net.yacy.http;

import net.yacy.cora.protocol.Domains;

/** Request-bound facts needed while the container verifies admin credentials. */
public final class AdminAuthenticationContext {

    private static final ThreadLocal<String> SOCKET_PEER_IP = new ThreadLocal<>();

    private AdminAuthenticationContext() {
    }

    public static void setSocketPeerIp(final String ip) {
        SOCKET_PEER_IP.set(ip);
    }

    public static void clear() {
        SOCKET_PEER_IP.remove();
    }

    public static boolean isLocalhostRequest() {
        final String ip = SOCKET_PEER_IP.get();
        return ip != null && Domains.isLocalhost(ip);
    }
}

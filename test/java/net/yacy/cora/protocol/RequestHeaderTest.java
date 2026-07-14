/**
 *  RequestHeaderTest
 *  part of YaCy
 *  Copyright 2016 by reger24; https://github.com/reger24
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
package net.yacy.cora.protocol;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import javax.servlet.http.HttpServletRequest;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for RequestHeader class.
 *
 * @author reger24
 */
public class RequestHeaderTest {

    /**
     * Build a minimal HttpServletRequest stub answering getRemoteAddr() with the
     * given socket peer address, X-Real-IP header and server-validated effective IP.
     */
    private static HttpServletRequest stubRequest(final String socketPeer, final String xRealIP,
            final String effectiveClientIp) {
        final InvocationHandler h = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                switch (method.getName()) {
                    case "getRemoteAddr":
                        return socketPeer;
                    case "getRemoteHost":
                        return socketPeer;
                    case "getHeader":
                        return RequestHeader.X_Real_IP.equals(args[0]) ? xRealIP : null;
                    case "getAttribute":
                        return RequestHeader.EFFECTIVE_CLIENT_IP_ATTRIBUTE.equals(args[0])
                                ? effectiveClientIp : null;
                    default:
                        return null;
                }
            }
        };
        return (HttpServletRequest) Proxy.newProxyInstance(
                RequestHeaderTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, h);
    }

    /**
     * Authentication must rely on the true socket peer, never on the spoofable
     * X-Real-IP header. A remote client sending "X-Real-IP: 127.0.0.1" must not
     * be treated as localhost.
     */
    @Test
    public void testXRealIpDoesNotAffectAuthentication() {
        final String remoteClient = "203.0.113.7"; // a non-local address (TEST-NET-3)

        // spoofing attempt: remote socket peer, but X-Real-IP claims localhost
        final RequestHeader spoofed = new RequestHeader(
                stubRequest(remoteClient, "127.0.0.1", null));
        // Neither routing nor authentication may honor an unvalidated forwarding header.
        assertEquals(remoteClient, spoofed.getRemoteAddr());
        assertEquals(remoteClient, spoofed.getRemoteSocketAddr());
        assertFalse("spoofed X-Real-IP must not grant localhost access", spoofed.accessFromLocalhost());

        // A trusted HTTP-server decision is visible to P2P routing, but not socket security.
        final RequestHeader proxied = new RequestHeader(
                stubRequest("127.0.0.1", "198.51.100.23", "198.51.100.23"));
        assertEquals("198.51.100.23", proxied.getRemoteAddr());
        assertEquals("198.51.100.23", proxied.getRemoteHost());
        assertEquals("127.0.0.1", proxied.getRemoteSocketAddr());

        // genuine localhost access still works
        final RequestHeader local = new RequestHeader(stubRequest("127.0.0.1", null, null));
        assertEquals("127.0.0.1", local.getRemoteSocketAddr());
        assertTrue(local.accessFromLocalhost());

        // remote client without spoofing stays remote
        final RequestHeader remote = new RequestHeader(stubRequest(remoteClient, null, null));
        assertFalse(remote.accessFromLocalhost());
    }

    /**
     * Test of getServerPort method, of class RequestHeader.
     */
    @Test
    public void testGetServerPort() {
        int portresult;
        RequestHeader hdr = new RequestHeader();

        // test host with port
        hdr.put(HeaderFramework.HOST, "[:1]:8090");
        portresult = hdr.getServerPort();
        assertEquals (8090, portresult);

        hdr.put(HeaderFramework.HOST, "127.0.0.1:8090");
        portresult = hdr.getServerPort();
        assertEquals (8090, portresult);

        hdr.put(HeaderFramework.HOST, "localhost:8090");
        portresult = hdr.getServerPort();
        assertEquals (8090, portresult);

        // test default  port
        hdr.put(HeaderFramework.HOST, "[:1]");
        portresult = hdr.getServerPort();
        assertEquals (80, portresult);

        hdr.put(HeaderFramework.HOST, "127.0.0.1");
        portresult = hdr.getServerPort();
        assertEquals (80, portresult);

        hdr.put(HeaderFramework.HOST, "localhost");
        portresult = hdr.getServerPort();
        assertEquals (80, portresult);
    }

}

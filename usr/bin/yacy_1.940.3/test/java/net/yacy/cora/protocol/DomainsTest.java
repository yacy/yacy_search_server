/**
 *  DomainsTest
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

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for ReferenceContainer class.
 */
public class DomainsTest {

    /**
     * Test of stripToPort method, of class Domains.
     */
    @Test
    public void testStripToPort() {
        Map<String, Integer> testHost = new HashMap<>();
        // key = teststring, value = expected port
        testHost.put("[3ffe:2a00:100:7031::1]:80", 80);
        testHost.put("https://[3ffe:2a00:100:7031::1]:80/test.html", 80);
        testHost.put("[3ffe:2a00:100:7031::1]/test.html", 80);
        testHost.put("http://[3ffe:2a00:100:7031::1]/test.html", 80);
        testHost.put("[3ffe:2a00:100:7031::1]:8090/test.html", 8090);
        testHost.put("ftp://[3ffe:2a00:100:7031::1]/test.html", 21);

        for (String host : testHost.keySet()) {
            int port = Domains.stripToPort(host);
            int expectedPort = testHost.get(host);
            assertEquals(host, expectedPort, port);

        }
    }

    /**
     * Test of stripToHostName method, of class Domains.
     */
    @Test
    public void testStripToHostName() {
        Map<String, String> testHost = new HashMap<>();
        // key = teststring, value = expected host
        testHost.put("[3ffe:2a00:100:7031::1]:80", "3ffe:2a00:100:7031::1");
        testHost.put("https://[3ffe:2a00:100:7032::1]:80/test.html", "3ffe:2a00:100:7032::1");
        testHost.put("[3ffe:2a00:100:7033::1]/test.html", "3ffe:2a00:100:7033::1");
        testHost.put("http://[3ffe:2a00:100:7034::1]/test.html", "3ffe:2a00:100:7034::1");
        testHost.put("[3ffe:2a00:100:7035::1]:8090/test.html", "3ffe:2a00:100:7035::1");
        testHost.put("ftp://[3ffe:2a00:100:7036::1]/test.html", "3ffe:2a00:100:7036::1");

        testHost.put("http://test1.org/test.html", "test1.org");
        testHost.put("http://test2.org:80/test.html", "test2.org");
        testHost.put("http://test3.org:7777/test.html", "test3.org");
        testHost.put("http://www.test4.org/test.html", "www.test4.org");
        testHost.put("http://www.test5.org:80/test.html", "www.test5.org");
        testHost.put("http://www.test6.org:7777/test.html", "www.test6.org");

        testHost.put("test7.org/test.html", "test7.org");
        testHost.put("test8.org:80/test.html", "test8.org");
        testHost.put("test9.org:7777/test.html", "test9.org");
        
        /* Check also host name case incensivity */
        testHost.put("HTTP://TEST10.INFO/test.html", "test10.info");
        testHost.put("http://TEST11.IN:7777/test.html", "test11.in");

        for (String teststr : testHost.keySet()) {
            String host = Domains.stripToHostName(teststr);
            String expectedHost = testHost.get(teststr);
            assertEquals(teststr, expectedHost, host);
        }
    }
}

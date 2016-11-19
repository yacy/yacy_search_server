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
        Map<String, Integer> testHost = new HashMap();

        testHost.put("[3ffe:2a00:100:7031::1]:80", 80);
        testHost.put("https://[3ffe:2a00:100:7031::1]:80/test.html", 80);
        testHost.put("[3ffe:2a00:100:7031::1]/test.html", 80);
        testHost.put("http://[3ffe:2a00:100:7031::1]/test.html", 80);
        testHost.put("[3ffe:2a00:100:7031::1]:8090/test.html", 8090);

        for (String host : testHost.keySet()) {
            int port = Domains.stripToPort(host);
            int expectedPort = testHost.get(host);
            assertEquals(host, expectedPort, port);

        }
    }
}

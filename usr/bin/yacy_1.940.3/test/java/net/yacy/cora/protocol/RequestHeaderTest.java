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

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for RequestHeader class.
 *
 * @author reger24
 */
public class RequestHeaderTest {

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

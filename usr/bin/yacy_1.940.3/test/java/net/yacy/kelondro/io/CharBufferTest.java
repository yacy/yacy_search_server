/**
 *  CharBufferTest
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
package net.yacy.kelondro.io;

import java.util.Properties;
import org.junit.Assert;
import org.junit.Test;

public class CharBufferTest {

    /**
     * Test of propParser method, of class CharBuffer.
     */
    @Test
    public void testPropParser() {
        CharBuffer cb = new CharBuffer(100);

        // test attribute w/o value
        cb.append("class=\"company-name\" itemscope itemtype=\"https://schema.org/Organization\"");
        Properties p = cb.propParser();
        Assert.assertNotNull(p.get("class"));
        Assert.assertNotNull(p.get("itemtype"));
    }
}

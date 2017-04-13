/**
 *  ClassificationTest
 *  part of YaCy
 *  Copyright 2017 by reger24; https://github.com/reger24
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
package net.yacy.cora.document.analysis;

import java.io.File;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for Classification class.
 */
public class ClassificationTest {

    /**
     * Initialize with default file ext to mime file
     */
    @BeforeClass
    public static void setUpClass() {
        Classification.init(new File("defaults/httpd.mime"));
    }

    /**
     * Test of ext2mime method, of class Classification.
     */
    @Test
    public void testExt2mime_String() {
        String mime;
        mime = Classification.ext2mime("Z");
        assertEquals("application/x-compress", mime);
    }

}

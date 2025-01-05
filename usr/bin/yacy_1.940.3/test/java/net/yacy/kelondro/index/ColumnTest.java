/**
 *  ColumnTest.java
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
package net.yacy.kelondro.index;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for ColumnTest class.
 */
public class ColumnTest {


    /**
     * Test of cell definition parsing and cellwidth assignment, of class Column.
     */
    @Test
    public void testCellWidth() {
        // test cell definition
        // key=definition string, value= expected cellwidth
        Map<String, Integer> testcelldefs = new HashMap<String, Integer>();
        testcelldefs.put("long countA {b256}", 8); // simple
        testcelldefs.put("long countB-8 {b256}",8); // redundant cellwidth
        testcelldefs.put("long countC   {b256}", 8); // spaces between definition
        testcelldefs.put("long countD {b256} \"Description\"", 8);
        testcelldefs.put("<long countE {b256} \"Description\">", 8);
        testcelldefs.put("int icountA  {b256}", 4);

        for (String celldef:testcelldefs.keySet()) {
            Column col = new Column(celldef);
            Integer expectedCellWidth = testcelldefs.get(celldef);
            assertEquals (celldef,expectedCellWidth.intValue(), col.cellwidth);
        }
    }

}

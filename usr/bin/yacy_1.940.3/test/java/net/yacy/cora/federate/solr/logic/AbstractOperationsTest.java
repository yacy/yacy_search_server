/**
 *  AbstractOperationsTest
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
package net.yacy.cora.federate.solr.logic;

import net.yacy.search.schema.CollectionSchema;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Unit tests for AbstractOperationsTest class.
 *
 */
public class AbstractOperationsTest {

    /**
     * Test of addOperand method, of class AbstractOperations.
     */
    @Test
    public void testAddOperand() {


        // create a simple query
        Conjunction con = new Conjunction();
        con.addOperand(new LongLiteral(CollectionSchema.httpstatus_i, 200)); // result: con="httpstatus_i:200"

        // create a 2nd empty query
        Disjunction dnf = new Disjunction();
        // add the empty query
        con.addOperand(dnf); // result (shall be unchanged): "httpstatus_i:200"

        // test for wrong result "(httpstatus_i:200 AND )"
        String query = con.toString();
        String testStr = query.replace(")", ""); // remove all ')' for easier testing
        testStr = testStr.replace("(", ""); // remove all '(' for easier testing
        testStr = testStr.trim();
        assertFalse("query ending with operator =" + query, testStr.endsWith(con.operandName));
        assertFalse("query ending with operator =" + query, testStr.endsWith(dnf.operandName));
    }

}

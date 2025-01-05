/**
 *  DomainsTest
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
package net.yacy.document;

import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.util.Date;
import net.yacy.cora.document.id.DigestURL;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for Document class.
 */
public class DocumentTest {

    /**
     * Test of lastmodified calculation after mergeDocuments method, of class
     * Document.
     */
    @Test
    public void testMergeDocuments_lastModified() throws MalformedURLException {

        Date lastmodDateMin = new Date(10*1000); // min test date
        Date lastmodDateMax = new Date(20*1000); // max test date

        DigestURL location = new DigestURL("http://localhost/test.html");
        String mimeType = "test/html";
        String charset = Charset.defaultCharset().name();
        Document[] docs = new Document[2];
        // prepare simple document with min modified-date
        docs[0] = new Document(
                location,
                mimeType,
                charset,
                null,
                null,
                null,
                null, // title
                null, // author
                location.getHost(),
                null,
                null,
                0.0d, 0.0d,
                location.toTokens(),
                null,
                null,
                null,
                false,
                lastmodDateMin);

        // prepare simple document with max modified-date
        docs[1] = new Document(
                location,
                mimeType,
                charset,
                null,
                null,
                null,
                null, // title
                null, // author
                location.getHost(),
                null,
                null,
                0.0d, 0.0d,
                location.toTokens(),
                null,
                null,
                null,
                false,
                lastmodDateMax);

        // get last-modified after merge
        Document result = Document.mergeDocuments(location, charset, docs);

        // expected to be newest test date
        assertEquals("merged last-modified Date",lastmodDateMax.getTime(), result.getLastModified().getTime());

    }

}

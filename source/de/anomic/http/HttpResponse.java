// HttpResponse.java
// (C) 2008 by Daniel Raap; danielr@users.berlios.de
// first published 2.4.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2008-03-14 01:16:04 +0100 (Fr, 14 Mrz 2008) $
// $LastChangedRevision: 4558 $
// $LastChangedBy: orbiter $
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
package de.anomic.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import de.anomic.tools.StreamTools;

/**
 * @author daniel
 *
 */
public interface HttpResponse {

    /**
     * returns the header as {@link de.anomic.http.httpHeader}
     * @return 
     */
    public abstract httpHeader getResponseHeader();

    /**
     * get the body of the response
     * @return the data
     * @throws IOException 
     */
    public abstract byte[] getData() throws IOException;

    /**
     * returns a stream to read the body of the response
     * 
     * <strong>Important: When done call <code>closeStream()</code>!</strong>
     * @return
     * @throws IOException
     */
    public abstract InputStream getDataAsStream() throws IOException;

    /**
     * closes the input stream
     */
    public abstract void closeStream();

    /**
     * "Statuscode SPACE Statusmessage"
     * @return the statusLine
     */
    public abstract String getStatusLine();

    /**
     * the status code of this response
     * @return
     * @ensure result >= 100 && result <= 999 (is a 3-digit integer)
     */
    public abstract int getStatusCode();

    /**
     * HTTP version of the response
     * @return
     */
    public abstract String getHttpVer();
    
    /**
     * save a response to some storage
     * @author daniel
     *
     */
    public static class Saver {

        /**
         * copies the body of the response accordingly to hfos to the destination
         * @param res
         * @param hfos OutputStream (raw bytes) or Writer (decode to characters)
         * @param byteStream additional OutputStream where data is stored
         * @throws IOException
         * @throws UnsupportedEncodingException
         */
        public static void writeContent(HttpResponse res, Object hfos, OutputStream byteStream) throws IOException,
                UnsupportedEncodingException {
            try {
                InputStream data = res.getDataAsStream();
                if (hfos instanceof OutputStream) {
                    OutputStream[] streams = (byteStream == null ? new OutputStream[] { (OutputStream) hfos }
                            : new OutputStream[] { (OutputStream) hfos, byteStream });
                    StreamTools.copyToStreams(data, streams);
                } else if (hfos instanceof Saver) {
                    String charSet = httpHeader.getCharSet(res.getResponseHeader());
                    Writer[] writers = (byteStream == null ? new Writer[] { (Writer) hfos } : new Writer[] { (Writer) hfos,
                            new OutputStreamWriter(byteStream, charSet) });
                    StreamTools.copyToWriters(data, writers, charSet);
                } else {
                    throw new IOException("cannot save data: hfos-type ("+  hfos.getClass().toString() +") not supported!");
                }
            } finally {
                res.closeStream();
            }
        }
        
    }

}
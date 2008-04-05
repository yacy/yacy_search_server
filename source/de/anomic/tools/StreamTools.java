// LowLevelTools.java
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
package de.anomic.tools;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;

/**
 * @author danielr
 *
 */
public class StreamTools {
    /**
     * copies the input stream to all output streams (byte per byte)
     * @param in
     * @param outs
     * @return
     * @throws IOException
     */
    public static int copyToStreams(InputStream in, final OutputStream[] outs) throws IOException {
        if(!(in instanceof BufferedInputStream)) {
            // add buffer
            in = new BufferedInputStream(in);
        }
        
        // check if buffer is used
        int i = 0;
        for(final OutputStream output: outs) {
            if (!(output instanceof BufferedOutputStream)) {
                // add buffer
                outs[i] = new BufferedOutputStream(output);
            }
            i++;
        }
        
        int count = 0;
        // copy bytes
        int b;
        while((b = in.read()) != -1) {
            count++;
            for(final OutputStream out: outs) {
                out.write(b);
            }
        }
        return count;
    }

    /**
     * copies the input stream to all writers (byte per byte)
     * @param data
     * @param writers
     * @param charSet
     * @return
     * @throws IOException
     */
    public static int copyToWriters(final InputStream data, final Writer[] writers, final String charSet) throws IOException {
        // the docs say: "For top efficiency, consider wrapping an InputStreamReader within a BufferedReader."
        final BufferedReader sourceReader = new BufferedReader(new InputStreamReader(data, charSet));
        
        // check if buffer is used. From the documentation:
        // "For top efficiency, consider wrapping an OutputStreamWriter within a BufferedWriter so as to avoid frequent
        // converter invocations"
        int i = 0;
        for(final Writer writer: writers) {
            if (!(writer instanceof BufferedWriter)) {
                // add buffer
                writers[i] = new BufferedWriter(writer);
            }
            i++;
        }
        
        int count = 0;
        // copy bytes
        int b;
        while((b = sourceReader.read()) != -1) {
            count++;
            for(final Writer writer: writers) {
                writer.write(b);
            }
        }
        return count;
    }

}

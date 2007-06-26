// httpSSI.java
// -----------------------------
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 26.06.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import de.anomic.server.serverByteBuffer;

public class httpSSI {

    public static void writeSSI(File referenceFile, serverByteBuffer in, httpChunkedOutputStream out) throws IOException {
        writeSSI(referenceFile, in, 0, out);
    }
    
    public static void writeSSI(File referenceFile, serverByteBuffer in, int start, httpChunkedOutputStream out) throws IOException {
        int p = in.indexOf("<!--#".getBytes(), start);
        if (p == 0) {
            int q = in.indexOf("-->".getBytes(), start + 10);
            assert q >= 0;
            parseSSI(referenceFile, in, start,  q + 3 - start, out);
            writeSSI(referenceFile, in, start + q + 3, out);
        } else if (p > 0) {
            int q = in.indexOf("-->".getBytes(), start + 10);
            out.write(in, start, p - start);
            parseSSI(referenceFile, in, start + p, q + 3 - start - p, out);
            writeSSI(referenceFile, in, start + q + 3, out);
        } else /* p < 0 */ {
            out.write(in, start, in.length() - start);
        }
    }
    
    private static void parseSSI(File referenceFile, serverByteBuffer in, int start, int length, httpChunkedOutputStream out) {
        if (in.startsWith("<!--#include virtual=\"".getBytes(), start)) {
            int q = in.indexOf("\"".getBytes(), start + 22);
            if (q > 0) {
                String path = in.toString(start + 22, q);
                File loadFile = new File(referenceFile.getParentFile(), path);
                try {
                    out.write(new FileInputStream(loadFile));
                } catch (FileNotFoundException e) {
                    // do nothing
                } catch (IOException e) {
                    // do nothing
                }
            }
        }
    }
}

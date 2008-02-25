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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

import de.anomic.server.serverByteBuffer;

public class httpSSI {

    public static void writeSSI(serverByteBuffer in, OutputStream out, String authorization, String requesthost) throws IOException {
        writeSSI(in, 0, out, authorization, requesthost);
    }
    
    public static void writeSSI(serverByteBuffer in, int off, OutputStream out, String authorization, String requesthost) throws IOException {
        int p = in.indexOf("<!--#".getBytes(), off);
        if (p >= 0) {
            int q = in.indexOf("-->".getBytes(), p + 10);
            if (out instanceof httpChunkedOutputStream) {
                ((httpChunkedOutputStream) out).write(in, off, p - off);
            } else {
                out.write(in.getBytes(off, p - off));
            }
            parseSSI(in, p, q + 3 - p, out, authorization, requesthost);
            writeSSI(in, q + 3, out, authorization, requesthost);
        } else /* p < 0 */ {
            if (out instanceof httpChunkedOutputStream) {
                ((httpChunkedOutputStream) out).write(in, off, in.length() - off);
            } else {
                out.write(in.getBytes(off, in.length() - off));
            }
        }
    }
    
    private static void parseSSI(serverByteBuffer in, int off, int len, OutputStream out, String authorization, String requesthost) {
        if (in.startsWith("<!--#include virtual=\"".getBytes(), off)) {
            int q = in.indexOf("\"".getBytes(), off + 22);
            if (q > 0) {
                String path = in.toString(off + 22, q);
                writeContent(path, out, authorization, requesthost);
            }
        }
    }
    
    private static void writeContent(String path, OutputStream out, String authorization, String requesthost) {
        // check if there are arguments in path string
        String args = "";
        int argpos = path.indexOf('?');
        if (argpos > 0) {
            args = path.substring(argpos + 1);
            path = path.substring(0, argpos);
        }
        
        // set up virtual connection properties to call httpdFileHander.doGet()
        Properties conProp = new Properties();
        httpHeader header = new httpHeader(httpd.reverseMappingCache);
        conProp.setProperty(httpHeader.CONNECTION_PROP_METHOD, httpHeader.METHOD_GET);
        conProp.setProperty(httpHeader.CONNECTION_PROP_PATH, path);
        conProp.setProperty(httpHeader.CONNECTION_PROP_ARGS, args);
        conProp.setProperty(httpHeader.CONNECTION_PROP_HTTP_VER, httpHeader.HTTP_VERSION_0_9);
        conProp.setProperty(httpHeader.CONNECTION_PROP_CLIENTIP, requesthost);
        header.put(httpHeader.AUTHORIZATION, authorization);
        httpdFileHandler.doGet(conProp, header, out);
    }
}

// ServerSideIncludes.java
// -----------------------------
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 26.06.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package de.anomic.http.server;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.util.ByteBuffer;


public class ServerSideIncludes {

    public static void writeSSI(final ByteBuffer in, final OutputStream out, final String authorization, final String requesthost) throws IOException {
        writeSSI(in, 0, out, authorization, requesthost);
    }
    
    public static void writeSSI(final ByteBuffer in, int off, final OutputStream out, final String authorization, final String requesthost) throws IOException {
        int p = in.indexOf(UTF8.getBytes("<!--#"), off);
        int q;
        while (p >= 0) {
            q = in.indexOf(UTF8.getBytes("-->"), p + 10);
            if (out instanceof ChunkedOutputStream) {
                ((ChunkedOutputStream) out).write(in, off, p - off);
            } else {
                out.write(in.getBytes(off, p - off));
            }
            parseSSI(in, p, out, authorization, requesthost);
            off = q + 3;
            p = in.indexOf(UTF8.getBytes("<!--#"), off);
        }
        if (out instanceof ChunkedOutputStream) {
            ((ChunkedOutputStream) out).write(in, off, in.length() - off);
        } else {
            out.write(in.getBytes(off, in.length() - off));
        }
    }
    
    private static void parseSSI(final ByteBuffer in, final int off, final OutputStream out, final String authorization, final String requesthost) {
        if (in.startsWith(UTF8.getBytes("<!--#include virtual=\""), off)) {
            final int q = in.indexOf(UTF8.getBytes("\""), off + 22);
            if (q > 0) {
                final String path = in.toString(off + 22, q - off - 22);
                writeContent(path, out, authorization, requesthost);
            }
        }
    }
    
    private static void writeContent(String path, final OutputStream out, final String authorization, final String requesthost) {
        // check if there are arguments in path string
        String args = "";
        final int argpos = path.indexOf('?');
        if (argpos > 0) {
            args = path.substring(argpos + 1);
            path = path.substring(0, argpos);
        }
        
        // set up virtual connection properties to call httpdFileHander.doGet()
        final Properties conProp = new Properties();
        final RequestHeader header = new RequestHeader(HTTPDemon.reverseMappingCache);
        conProp.setProperty(HeaderFramework.CONNECTION_PROP_METHOD, HeaderFramework.METHOD_GET);
        conProp.setProperty(HeaderFramework.CONNECTION_PROP_PATH, path);
        conProp.setProperty(HeaderFramework.CONNECTION_PROP_ARGS, args);
        conProp.setProperty(HeaderFramework.CONNECTION_PROP_HTTP_VER, HeaderFramework.HTTP_VERSION_0_9);
        conProp.setProperty(HeaderFramework.CONNECTION_PROP_CLIENTIP, requesthost);
        header.put(RequestHeader.AUTHORIZATION, authorization);
        HTTPDFileHandler.doGet(conProp, header, out);
    }
}

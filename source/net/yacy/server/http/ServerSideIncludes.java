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

package net.yacy.server.http;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ByteBuffer;


public class ServerSideIncludes {

    public static void writeSSI(final ByteBuffer in, final OutputStream out, final String authorization, final String requesthost, final RequestHeader requestHeader) throws IOException {
        writeSSI(in, 0, out, authorization, requesthost, requestHeader);
    }

    private static void writeSSI(final ByteBuffer in, int off, final OutputStream out, final String authorization, final String requesthost, final RequestHeader requestHeader) throws IOException {
        int p = in.indexOf(ASCII.getBytes("<!--#"), off);
        int q;
        while (p >= 0) {
            q = in.indexOf(ASCII.getBytes("-->"), p + 10);
            if (out instanceof ChunkedOutputStream) {
                ((ChunkedOutputStream) out).write(in, off, p - off);
            } else {
                out.write(in.getBytes(off, p - off));
            }
            parseSSI(in, p, out, authorization, requesthost, requestHeader);
            off = q + 3;
            p = in.indexOf(ASCII.getBytes("<!--#"), off);
        }
        if (out instanceof ChunkedOutputStream) {
            ((ChunkedOutputStream) out).write(in, off, in.length() - off);
        } else {
            out.write(in.getBytes(off, in.length() - off));
        }
    }

    private static void parseSSI(final ByteBuffer in, final int off, final OutputStream out, final String authorization, final String requesthost, final RequestHeader requestHeader) {
        if (in.startsWith(ASCII.getBytes("<!--#include virtual=\""), off)) {
            final int q = in.indexOf(ASCII.getBytes("\""), off + 22);
            if (q > 0) {
                final String path = in.toString(off + 22, q - off - 22);
                writeContent(path, out, authorization, requesthost, requestHeader);
            }
        }
    }

    public static void writeContent(String path, final OutputStream out, final String authorization, final String requesthost, final RequestHeader requestHeader) {
        // check if there are arguments in path string
        String args = "";
        final int argpos = path.indexOf('?');
        if (argpos > 0) {
            args = path.substring(argpos + 1);
            path = path.substring(0, argpos);
        }

        // set up virtual connection properties to call httpdFileHander.doGet()
        final HashMap<String, Object> conProp = new HashMap<String, Object>();
        final RequestHeader header = new RequestHeader(HTTPDemon.reverseMappingCache);
        conProp.put(HeaderFramework.CONNECTION_PROP_METHOD, HeaderFramework.METHOD_GET);
        conProp.put(HeaderFramework.CONNECTION_PROP_PATH, path);
        conProp.put(HeaderFramework.CONNECTION_PROP_ARGS, args);
        conProp.put(HeaderFramework.CONNECTION_PROP_HTTP_VER, HeaderFramework.HTTP_VERSION_0_9);
        conProp.put(HeaderFramework.CONNECTION_PROP_CLIENTIP, requesthost);
        header.put(RequestHeader.AUTHORIZATION, authorization);
        if (requestHeader.containsKey(RequestHeader.COOKIE)) header.put(RequestHeader.COOKIE, requestHeader.get(RequestHeader.COOKIE));
        header.put(RequestHeader.REFERER, requestHeader.get(RequestHeader.REFERER));
        HTTPDFileHandler.doGet(conProp, header, out);
    }
}

// SMBLoader.java
// (C) 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 10.03.2010 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based search engine
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


package net.yacy.crawler.retrieval;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.protocol.ftp.FTPClient;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.document.TextParser;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.search.Switchboard;

public class SMBLoader {

    public  static final long   DEFAULT_MAXFILESIZE = 1024 * 1024 * 10;

    private final Switchboard sb;
    private final ConcurrentLog log;
    private final long maxFileSize;

    public SMBLoader(final Switchboard sb, final ConcurrentLog log) {
        this.sb = sb;
        this.log = log;
        this.maxFileSize = sb.getConfigLong("crawler.smb.maxFileSize", -1l);
    }


    public Response load(final Request request, boolean acceptOnlyParseable) throws IOException {
        DigestURL url = request.url();
        if (!url.getProtocol().equals("smb")) throw new IOException("wrong loader for SMBLoader: " + url.getProtocol());

        RequestHeader requestHeader = new RequestHeader();
        if (request.referrerhash() != null) {
            DigestURL ur = this.sb.getURL(request.referrerhash());
            if (ur != null) requestHeader.put(RequestHeader.REFERER, ur.toNormalform(true));
        }

        // process directories: transform them to html with meta robots=noindex (using the ftpc lib)
        String[] l = null;
        try {l = url.list();} catch (final IOException e) {}
        if (l != null) {
            String u = url.toNormalform(true);
            List<String> list = new ArrayList<String>();
            for (String s: l) {
                if (s.startsWith(".")) continue;
                s = MultiProtocolURL.escape(s).toString();
                if (!s.endsWith("/") && !s.endsWith("\\")) {
                    // check if this is a directory
                    SmbFile sf = new SmbFile(u + s);
                    if (sf.isDirectory()) s = s + "/";
                }
                list.add(u + s);
            }

            StringBuilder content = FTPClient.dirhtml(u, null, null, null, list, true);

            ResponseHeader responseHeader = new ResponseHeader(200);
            responseHeader.put(HeaderFramework.LAST_MODIFIED, HeaderFramework.formatRFC1123(new Date()));
            responseHeader.put(HeaderFramework.CONTENT_TYPE, "text/html");
            final CrawlProfile profile = this.sb.crawler.get(ASCII.getBytes(request.profileHandle()));
            Response response = new Response(
                    request,
                    requestHeader,
                    responseHeader,
                    profile,
                    false,
                    UTF8.getBytes(content.toString()));

            return response;
        }

        // create response header
        String mime = Classification.ext2mime(MultiProtocolURL.getFileExtension(url.getFileName()));
        ResponseHeader responseHeader = new ResponseHeader(200);
        responseHeader.put(HeaderFramework.LAST_MODIFIED, HeaderFramework.formatRFC1123(new Date(url.lastModified())));
        responseHeader.put(HeaderFramework.CONTENT_TYPE, mime);

        // check mime type and availability of parsers
        // and also check resource size and limitation of the size
        long size;
        try {
            size = url.length();
        } catch (final Exception e) {
            size = -1;
        }
        String parserError = null;
        if ((acceptOnlyParseable && (parserError = TextParser.supports(url, mime)) != null) ||
            (size > this.maxFileSize && this.maxFileSize >= 0)) {
            // we know that we cannot process that file before loading
            // only the metadata is returned

            if (parserError != null) {
                this.log.info("No parser available in SMB crawler: '" + parserError + "' for URL " + request.url().toString() + ": parsing only metadata");
            } else {
                this.log.info("Too big file in SMB crawler with size = " + size + " Bytes for URL " + request.url().toString() + ": parsing only metadata");
            }

            // create response with metadata only
            responseHeader.put(HeaderFramework.CONTENT_TYPE, "text/plain");
            final CrawlProfile profile = this.sb.crawler.get(request.profileHandle().getBytes());
            Response response = new Response(
                    request,
                    requestHeader,
                    responseHeader,
                    profile,
                    false,
                    url.toTokens().getBytes());
            return response;
        }

        // load the resource
        InputStream is = url.getInputStream(ClientIdentification.yacyInternetCrawlerAgent, null, null);
        byte[] b = FileUtils.read(is);
        is.close();

        // create response with loaded content
        final CrawlProfile profile = this.sb.crawler.get(request.profileHandle().getBytes());
        Response response = new Response(
                request,
                requestHeader,
                responseHeader,
                profile,
                false,
                b);
        return response;
    }

    public static void main(String[] args) {
        //jcifs.Config.setProperty( "jcifs.netbios.wins", "192.168.1.220" );
        //NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication("domain", "username", "password");
        SmbFileInputStream in;
        try {
            SmbFile sf = new SmbFile(args[0]);
            if (sf.isDirectory()) {
                String[] s = sf.list();
                for (String t: s) System.out.println(t);
            } else {
                in = new SmbFileInputStream(sf);
                byte[] b = new byte[8192];
                int n;
                while(( n = in.read( b )) > 0 ) {
                    System.out.write( b, 0, n );
                }
            }
        } catch (final SmbException e) {
            ConcurrentLog.logException(e);
        } catch (final MalformedURLException e) {
            ConcurrentLog.logException(e);
        } catch (final UnknownHostException e) {
            ConcurrentLog.logException(e);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
    }
}

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


package de.anomic.crawler.retrieval;

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

import de.anomic.search.Segments;
import de.anomic.search.Switchboard;
import de.anomic.crawler.CrawlProfile;
import de.anomic.data.MimeTable;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.protocol.ftp.FTPClient;
import net.yacy.document.TextParser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;

public class SMBLoader {

    public  static final long   DEFAULT_MAXFILESIZE = 1024 * 1024 * 10;
    
    private final Switchboard sb;
    private final Log log;
    private final long maxFileSize;

    public SMBLoader(final Switchboard sb, final Log log) {
        this.sb = sb;
        this.log = log;
        maxFileSize = sb.getConfigLong("crawler.smb.maxFileSize", -1l);
    }
    
    
    public Response load(final Request request, boolean acceptOnlyParseable) throws IOException {
        DigestURI url = request.url();
        if (!url.getProtocol().equals("smb")) throw new IOException("wrong loader for SMBLoader: " + url.getProtocol());

        RequestHeader requestHeader = new RequestHeader();
        if (request.referrerhash() != null) {
            DigestURI ur = sb.getURL(Segments.Process.LOCALCRAWLING, request.referrerhash());
            if (ur != null) requestHeader.put(RequestHeader.REFERER, ur.toNormalform(true, false));
        }
        
        // process directories: transform them to html with meta robots=noindex (using the ftpc lib)
        String[] l = null;
        try {l = url.list();} catch (IOException e) {}
        if (l != null) {
            /*
            if (l == null) {
                // this can only happen if there is no connection or the directory does not exist
                //log.logInfo("directory listing not available. URL = " + request.url().toString());
                sb.crawlQueues.errorURL.push(request, this.sb.peers.mySeed().hash.getBytes(), new Date(), 1, "directory listing not available. URL = " + request.url().toString());
                throw new IOException("directory listing not available. URL = " + request.url().toString());
            }
            */
            String u = url.toNormalform(true, true);
            List<String> list = new ArrayList<String>();
            for (String s: l) {
                if (s.startsWith(".")) continue;
                s = MultiProtocolURI.escape(s).toString();
                if (!s.endsWith("/") && !s.endsWith("\\")) {
                    // check if this is a directory
                    SmbFile sf = new SmbFile(u + s);
                    if (sf.isDirectory()) s = s + "/";
                }
                list.add(u + s);
            }
         
            StringBuilder content = FTPClient.dirhtml(u, null, null, null, list, true);
            
            ResponseHeader responseHeader = new ResponseHeader();
            responseHeader.put(HeaderFramework.LAST_MODIFIED, HeaderFramework.formatRFC1123(new Date()));
            responseHeader.put(HeaderFramework.CONTENT_TYPE, "text/html");
            final CrawlProfile profile = sb.crawler.getActive(request.profileHandle().getBytes());
            Response response = new Response(
                    request, 
                    requestHeader,
                    responseHeader,
                    "200",
                    profile,
                    content.toString().getBytes());
            
            return response;
        }
        
        // create response header
        String mime = MimeTable.ext2mime(url.getFileExtension());
        ResponseHeader responseHeader = new ResponseHeader();
        responseHeader.put(HeaderFramework.LAST_MODIFIED, HeaderFramework.formatRFC1123(new Date(url.lastModified())));
        responseHeader.put(HeaderFramework.CONTENT_TYPE, mime);
        
        // check mime type and availability of parsers
        // and also check resource size and limitation of the size
        long size;
        try {
            size = url.length();
        } catch (Exception e) {
            size = -1;
        }
        String parserError = null;
        if ((acceptOnlyParseable && (parserError = TextParser.supports(url, mime)) != null) ||
            (size > maxFileSize && maxFileSize >= 0)) {
            // we know that we cannot process that file before loading
            // only the metadata is returned
            
            if (parserError != null) {
                log.logInfo("No parser available in SMB crawler: '" + parserError + "' for URL " + request.url().toString() + ": parsing only metadata");
            } else {
                log.logInfo("Too big file in SMB crawler with size = " + size + " Bytes for URL " + request.url().toString() + ": parsing only metadata");
            }
            
            // create response with metadata only
            responseHeader.put(HeaderFramework.CONTENT_TYPE, "text/plain");
            final CrawlProfile profile = sb.crawler.getActive(request.profileHandle().getBytes());
            Response response = new Response(
                    request, 
                    requestHeader,
                    responseHeader,
                    "200",
                    profile,
                    url.toTokens().getBytes());
            return response;
        }
        
        // load the resource
        InputStream is = url.getInputStream(null, -1);
        byte[] b = FileUtils.read(is);
        is.close();
        
        // create response with loaded content
        final CrawlProfile profile = sb.crawler.getActive(request.profileHandle().getBytes());
        Response response = new Response(
                request, 
                requestHeader,
                responseHeader,
                "200",
                profile,
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
        } catch (SmbException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

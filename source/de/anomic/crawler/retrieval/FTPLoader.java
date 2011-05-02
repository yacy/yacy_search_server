// FTPLoader.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
//
// This file ist contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.protocol.ftp.FTPClient;
import net.yacy.document.TextParser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.Latency;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;

public class FTPLoader {

    public  static final long   DEFAULT_MAXFILESIZE = 1024 * 1024 * 10;
    
    private final Switchboard sb;
    private final Log log;
    private final long maxFileSize;

    public FTPLoader(final Switchboard sb, final Log log) {
        this.sb = sb;
        this.log = log;
        this.maxFileSize = sb.getConfigLong("crawler.ftp.maxFileSize", -1l);
    }

    /**
     * Loads the entry from a ftp-server
     * 
     * @param request
     * @return
     */
    public Response load(final Request request, boolean acceptOnlyParseable) throws IOException {
        
        long start = System.currentTimeMillis();
        final DigestURI entryUrl = request.url();
        final String fullPath = getPath(entryUrl);

        // the return value
        Response response = null;

        // determine filename and path
        String file, path;
        if (fullPath.endsWith("/")) {
            file = "";
            path = fullPath;
        } else {
            final int pos = fullPath.lastIndexOf("/");
            if (pos == -1) {
                file = fullPath;
                path = "/";
            } else {
                path = fullPath.substring(0, pos + 1);
                file = fullPath.substring(pos + 1);
            }
        }
        assert path.endsWith("/") : "FTPLoader: path is not a path: '" + path + "'";

        // stream for ftp-client errors
        final ByteArrayOutputStream berr = new ByteArrayOutputStream();

        // create new ftp client
        final FTPClient ftpClient = new FTPClient();
        
        // get a connection
        if (openConnection(ftpClient, entryUrl)) {
            // test if the specified file is a directory
            if (file.length() > 0) {
                ftpClient.exec("cd \"" + path + "\"", false);

                final boolean isFolder = ftpClient.isFolder(file);
                if (isFolder) {
                    path = fullPath + "/";
                    file = "";
                }
            }

            if (file.length() == 0) {
                // directory -> get list of files
                RequestHeader requestHeader = new RequestHeader();
                if (request.referrerhash() != null) {
                    DigestURI u = sb.getURL(Segments.Process.LOCALCRAWLING, request.referrerhash());
                    if (u != null) requestHeader.put(RequestHeader.REFERER, u.toNormalform(true, false));
                }
                
                StringBuilder dirList = ftpClient.dirhtml(path);

                if (dirList == null) {
                    response = null;
                } else {
                    ResponseHeader responseHeader = new ResponseHeader();
                    responseHeader.put(HeaderFramework.LAST_MODIFIED, HeaderFramework.formatRFC1123(new Date()));
                    responseHeader.put(HeaderFramework.CONTENT_TYPE, "text/html");
                    final CrawlProfile profile = sb.crawler.getActive(request.profileHandle().getBytes());
                    response = new Response(
                            request, 
                            requestHeader,
                            responseHeader,
                            "200",
                            profile,
                            dirList.toString().getBytes());
                }
            } else {
                // file -> download
                try {
                    response = getFile(ftpClient, request, acceptOnlyParseable);
                } catch (final Exception e) {
                    // add message to errorLog
                    e.printStackTrace();
                    (new PrintStream(berr)).print(e.getMessage());
                }
            }
            closeConnection(ftpClient);
        }

        // pass the downloaded resource to the cache manager
        if (berr.size() > 0 || response == null) {
            // some error logging
            final String detail = (berr.size() > 0) ? "Errorlog: " + berr.toString() : "";
            sb.crawlQueues.errorURL.push(request, sb.peers.mySeed().hash.getBytes(), new Date(), 1, " ftp server download, " + detail, -1);
            throw new IOException("FTPLoader: Unable to download URL '" + request.url().toString() + "': " + detail);
        }
        
        Latency.update(request.url(), System.currentTimeMillis() - start);
        return response;
    }

    /**
     * @param ftpClient
     */
    private void closeConnection(final FTPClient ftpClient) {
        // closing connection
        ftpClient.exec("close", false);
        ftpClient.exec("exit", false);
    }

    /**
     * establish a connection to the ftp server (open, login, set transfer mode)
     */
    private boolean openConnection(final FTPClient ftpClient, final DigestURI entryUrl) {
        // get username and password
        final String userInfo = entryUrl.getUserInfo();
        String userName = "anonymous", userPwd = "anonymous";
        if (userInfo != null) {
            final int pos = userInfo.indexOf(":");
            if (pos != -1) {
                userName = userInfo.substring(0, pos);
                userPwd = userInfo.substring(pos + 1);
            }
        }

        // get server name and port
        final String host = entryUrl.getHost();
        final int port = entryUrl.getPort();
        // open a connection to the ftp server
        if (port == -1) {
            ftpClient.exec("open " + host, false);
        } else {
            ftpClient.exec("open " + host + " " + port, false);
        }
        if (ftpClient.notConnected()) {
            return false;
        }

        // login to the server
        ftpClient.exec("user " + userName + " " + userPwd, false);

        if (ftpClient.isLoggedIn()) {
            // change transfer mode to binary
            ftpClient.exec("binary", false);
        } else {
            return false;
        }
        return true;
    }

    private Response getFile(final FTPClient ftpClient, final Request request, boolean acceptOnlyParseable) throws IOException {
        // determine the mimetype of the resource
        final DigestURI url = request.url();
        final String mime = TextParser.mimeOf(url);
        final String path = getPath(url);

        // determine the file date
        final Date fileDate = ftpClient.entryDate(path);
        
        // create response header
        RequestHeader requestHeader = new RequestHeader();
        if (request.referrerhash() != null) {
            DigestURI refurl = sb.getURL(Segments.Process.LOCALCRAWLING, request.referrerhash());
            if (refurl != null) requestHeader.put(RequestHeader.REFERER, refurl.toNormalform(true, false));
        }
        ResponseHeader responseHeader = new ResponseHeader();
        responseHeader.put(HeaderFramework.LAST_MODIFIED, HeaderFramework.formatRFC1123(fileDate));
        responseHeader.put(HeaderFramework.CONTENT_TYPE, mime);
        
        // if the mimetype and file extension is supported we start to download the file
        final long size = ftpClient.fileSize(path);
        responseHeader.put(HeaderFramework.CONTENT_LENGTH, String.valueOf(size));
        String parserError = null;
        if ((acceptOnlyParseable && (parserError = TextParser.supports(url, mime)) != null) ||
            (size > maxFileSize && maxFileSize >= 0)) {
            // we know that we cannot process that file before loading
            // only the metadata is returned
            
            if (parserError != null) {
                log.logInfo("No parser available in FTP crawler: '" + parserError + "' for URL " + request.url().toString() + ": parsing only metadata");
            } else {
                log.logInfo("Too big file in FTP crawler with size = " + size + " Bytes for URL " + request.url().toString() + ": parsing only metadata");
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
                    null);
            return response;
        }
        
        // download the remote file
        byte[] b = ftpClient.get(path);
        
        // create a response
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

    /**
     * gets path suitable for FTP (url-decoded, double-quotes escaped)
     * 
     * @param entryUrl
     * @return
     */
    private String getPath(final MultiProtocolURI entryUrl) {
        return MultiProtocolURI.unescape(entryUrl.getPath()).replace("\"", "\"\"");
    }

}

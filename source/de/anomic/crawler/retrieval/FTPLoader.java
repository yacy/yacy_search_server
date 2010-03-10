// FTPLoader.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
//
// This file ist contributed by Martin Thelian
//
// $LastChangedDate: 2006-02-20 23:57:42 +0100 (Mo, 20 Feb 2006) $
// $LastChangedRevision: 1715 $
// $LastChangedBy: theli $
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

import net.yacy.document.TextParser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.DateFormatter;

import de.anomic.crawler.Latency;
import de.anomic.http.server.HeaderFramework;
import de.anomic.http.server.RequestHeader;
import de.anomic.http.server.ResponseHeader;
import de.anomic.net.ftpc;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;

public class FTPLoader {

    private final Switchboard sb;
    private final Log log;
    private final int maxFileSize;

    public FTPLoader(final Switchboard sb, final Log log) {
        this.sb = sb;
        this.log = log;
        this.maxFileSize = (int) sb.getConfigLong("crawler.ftp.maxFileSize", -1l);
    }

    /**
     * Loads the entry from a ftp-server
     * 
     * @param request
     * @return
     */
    public Response load(final Request request) throws IOException {
        
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
        final ftpc ftpClient = createFTPClient(berr);

        if (openConnection(ftpClient, entryUrl)) {
            // ftp stuff
            //try {
                // testing if the specified file is a directory
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
                    
                    byte[] dirList = generateDirlist(ftpClient, request, path);

                    if (dirList == null) {
                        response = null;
                    } else {
                        ResponseHeader responseHeader = new ResponseHeader();
                        responseHeader.put(HeaderFramework.LAST_MODIFIED, DateFormatter.formatRFC1123(new Date()));
                        responseHeader.put(HeaderFramework.CONTENT_TYPE, "text/html");
                        response = new Response(
                                request, 
                                requestHeader,
                                responseHeader,
                                "OK",
                                sb.crawler.profilesActiveCrawls.getEntry(request.profileHandle()),
                                dirList);
                    }
                } else {
                    // file -> download
                    try {
                        response = getFile(ftpClient, request);
                    } catch (final Exception e) {
                        // add message to errorLog
                        (new PrintStream(berr)).print(e.getMessage());
                    }
                }
            closeConnection(ftpClient);
        }

        // pass the downloaded resource to the cache manager
        if (berr.size() > 0 || response == null) {
            // some error logging
            final String detail = (berr.size() > 0) ? "\n    Errorlog: " + berr.toString() : "";
            sb.crawlQueues.errorURL.push(request, sb.peers.mySeed().hash, new Date(), 1, "server download" + detail);
            throw new IOException("FTPLoader: Unable to download URL " + request.url().toString() + detail);
        }
        
        Latency.update(request.url().hash().substring(6), request.url().getHost(), System.currentTimeMillis() - start);
        return response;
    }

    /**
     * @param ftpClient
     */
    private void closeConnection(final ftpc ftpClient) {
        // closing connection
        ftpClient.exec("close", false);
        ftpClient.exec("exit", false);
    }

    /**
     * establish a connection to the ftp server (open, login, set transfer mode)
     * 
     * @param ftpClient
     * @param hostname
     * @param port
     * @return success
     */
    private boolean openConnection(final ftpc ftpClient, final DigestURI entryUrl) {
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

    /**
     * @param ftpClient
     * @param request
     * @param htCache
     * @param cacheFile
     * @return
     * @throws Exception
     */
    private Response getFile(final ftpc ftpClient, final Request request) throws Exception {
        // determine the mimetype of the resource
        final DigestURI entryUrl = request.url();
        final String mimeType = TextParser.mimeOf(entryUrl);
        final String path = getPath(entryUrl);

        // if the mimetype and file extension is supported we start to download
        // the file
        Response response = null;
        String supportError = TextParser.supports(entryUrl, mimeType);
        if (supportError != null) {
            // reject file
            log.logInfo("PARSER REJECTED URL " + request.url().toString() + ": " + supportError);
            sb.crawlQueues.errorURL.push(request, this.sb.peers.mySeed().hash, new Date(), 1, supportError);
            throw new Exception(supportError);
        } else {
            // abort the download if content is too long
            final int size = ftpClient.fileSize(path);
            if (size <= maxFileSize || maxFileSize == -1) {
                // timeout for download
                ftpClient.setDataTimeoutByMaxFilesize(size);

                // determine the file date
                final Date fileDate = ftpClient.entryDate(path);

                // download the remote file
                byte[] b = ftpClient.get(path);
                
                // create a cache entry
                RequestHeader requestHeader = new RequestHeader();
                if (request.referrerhash() != null) requestHeader.put(RequestHeader.REFERER, sb.getURL(Segments.Process.LOCALCRAWLING, request.referrerhash()).toNormalform(true, false));
                ResponseHeader responseHeader = new ResponseHeader();
                responseHeader.put(HeaderFramework.LAST_MODIFIED, DateFormatter.formatRFC1123(fileDate));
                responseHeader.put(HeaderFramework.CONTENT_TYPE, mimeType);
                response = new Response(
                        request, 
                        requestHeader,
                        responseHeader,
                        "OK",
                        sb.crawler.profilesActiveCrawls.getEntry(request.profileHandle()),
                        b);
            } else {
                log.logInfo("REJECTED TOO BIG FILE with size " + size + " Bytes for URL " + request.url().toString());
                sb.crawlQueues.errorURL.push(request, this.sb.peers.mySeed().hash, new Date(), 1, "file size limit exceeded");
                throw new Exception("file size exceeds limit");
            }
        }
        return response;
    }

    /**
     * gets path suitable for FTP (url-decoded, double-quotes escaped)
     * 
     * @param entryUrl
     * @return
     */
    private String getPath(final DigestURI entryUrl) {
        return DigestURI.unescape(entryUrl.getPath()).replace("\"", "\"\"");
    }

    /**
     * @param ftpClient
     * @param entry
     * @param cacheFile
     * @return
     */
    private byte[] generateDirlist(final ftpc ftpClient, final Request entry, final String path) {
        // getting the dirlist
        final DigestURI entryUrl = entry.url();

        // generate the dirlist
        final StringBuilder dirList = ftpClient.dirhtml(path);

        if (dirList != null && dirList.length() > 0) {
            try {
                return dirList.toString().getBytes();
            } catch (final Exception e) {
                log.logInfo("Unable to write dirlist for URL " + entryUrl.toString());
            }
        }
        return null;
    }

    /**
     * create a new ftp client
     * 
     * @param berr
     * @return
     */
    private ftpc createFTPClient(final ByteArrayOutputStream berr) {
        // error
        final PrintStream err = new PrintStream(berr);

        final ftpc ftpClient = new ftpc(System.in, null, err);

        // set timeout
        ftpClient.setDataTimeoutByMaxFilesize(maxFileSize);

        return ftpClient;
    }

}

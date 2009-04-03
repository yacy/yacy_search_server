// CrawlerWorker.java 
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

package de.anomic.crawler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;

import de.anomic.http.httpRequestHeader;
import de.anomic.http.httpResponseHeader;
import de.anomic.http.httpdProxyCacheEntry;
import de.anomic.kelondro.util.DateFormatter;
import de.anomic.kelondro.util.Log;
import de.anomic.net.ftpc;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.parser.Document;
import de.anomic.yacy.yacyURL;

public class FTPLoader {

    private final plasmaSwitchboard sb;
    private final Log log;
    private final int maxFileSize;

    public FTPLoader(final plasmaSwitchboard sb, final Log log) {
        this.sb = sb;
        this.log = log;
        maxFileSize = (int) sb.getConfigLong("crawler.ftp.maxFileSize", -1l);
    }

    protected Document createCacheEntry(final CrawlEntry entry, final String mimeType, final Date fileDate) {
        if (entry == null) return null;
        httpRequestHeader requestHeader = new httpRequestHeader();
        if (entry.referrerhash() != null) requestHeader.put(httpRequestHeader.REFERER, sb.getURL(entry.referrerhash()).toNormalform(true, false));
        httpResponseHeader responseHeader = new httpResponseHeader();
        responseHeader.put(httpResponseHeader.LAST_MODIFIED, DateFormatter.formatRFC1123(fileDate));
        responseHeader.put(httpResponseHeader.CONTENT_TYPE, mimeType);
        Document metadata = new httpdProxyCacheEntry(
                entry.depth(), entry.url(), entry.name(), "OK",
                requestHeader, responseHeader,
                entry.initiator(), sb.webIndex.profilesActiveCrawls.getEntry(entry.profileHandle()));
        plasmaHTCache.storeMetadata(responseHeader, metadata);
        return metadata;
    }

    /**
     * Loads the entry from a ftp-server
     * 
     * @param entry
     * @return
     */
    public Document load(final CrawlEntry entry) throws IOException {
        
        long start = System.currentTimeMillis();
        final yacyURL entryUrl = entry.url();
        final String fullPath = getPath(entryUrl);

        // the return value
        Document htCache = null;

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
                    // create a htcache entry
                    htCache = createCacheEntry(entry, "text/html", new Date());
                    byte[] dirList = generateDirlist(ftpClient, entry, path);
                    if (dirList == null) {
                        htCache = null;
                    }
                } else {
                    // file -> download
                    try {
                        htCache = getFile(ftpClient, entry);
                    } catch (final Exception e) {
                        // add message to errorLog
                        (new PrintStream(berr)).print(e.getMessage());
                    }
                }
            closeConnection(ftpClient);
        }

        // pass the downloaded resource to the cache manager
        if (berr.size() > 0 || htCache == null) {
            // some error logging
            final String detail = (berr.size() > 0) ? "\n    Errorlog: " + berr.toString() : "";
            sb.crawlQueues.errorURL.newEntry(entry, sb.webIndex.peers().mySeed().hash, new Date(), 1, "server download" + detail);
            throw new IOException("FTPLoader: Unable to download URL " + entry.url().toString() + detail);
        }
        
        Latency.update(entry.url().hash().substring(6), entry.url().getHost(), System.currentTimeMillis() - start);
        return htCache;
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
    private boolean openConnection(final ftpc ftpClient, final yacyURL entryUrl) {
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
     * @param entry
     * @param htCache
     * @param cacheFile
     * @return
     * @throws Exception
     */
    private Document getFile(final ftpc ftpClient, final CrawlEntry entry) throws Exception {
        // determine the mimetype of the resource
        final yacyURL entryUrl = entry.url();
        final String extension = plasmaParser.getFileExt(entryUrl);
        final String mimeType = plasmaParser.getMimeTypeByFileExt(extension);
        final String path = getPath(entryUrl);

        // if the mimetype and file extension is supported we start to download
        // the file
        Document htCache = null;
        if (plasmaParser.supportedContent(plasmaParser.PARSER_MODE_CRAWLER, entryUrl, mimeType)) {
            // aborting download if content is too long
            final int size = ftpClient.fileSize(path);
            if (size <= maxFileSize || maxFileSize == -1) {
                // timeout for download
                ftpClient.setDataTimeoutByMaxFilesize(size);

                // determine the file date
                final Date fileDate = ftpClient.entryDate(path);

                // create a htcache entry
                htCache = createCacheEntry(entry, mimeType, fileDate);

                // download the remote file
                byte[] b = ftpClient.get(path);
                htCache.setCacheArray(b);
            } else {
                log.logInfo("REJECTED TOO BIG FILE with size " + size + " Bytes for URL " + entry.url().toString());
                sb.crawlQueues.errorURL.newEntry(entry, this.sb.webIndex.peers().mySeed().hash, new Date(), 1, "file size limit exceeded");
                throw new Exception("file size exceeds limit");
            }
        } else {
            // if the response has not the right file type then reject file
            log.logInfo("REJECTED WRONG MIME/EXT TYPE " + mimeType + " for URL " + entry.url().toString());
            sb.crawlQueues.errorURL.newEntry(entry, this.sb.webIndex.peers().mySeed().hash, new Date(), 1, "wrong mime type or wrong extension");
            throw new Exception("response has not the right file type -> rejected");
        }
        return htCache;
    }

    /**
     * gets path suitable for FTP (url-decoded, double-quotes escaped)
     * 
     * @param entryUrl
     * @return
     */
    private String getPath(final yacyURL entryUrl) {
        return yacyURL.unescape(entryUrl.getPath()).replace("\"", "\"\"");
    }

    /**
     * @param ftpClient
     * @param entry
     * @param cacheFile
     * @return
     */
    private byte[] generateDirlist(final ftpc ftpClient, final CrawlEntry entry, final String path) {
        // getting the dirlist
        final yacyURL entryUrl = entry.url();

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

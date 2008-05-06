// CrawlerWorker.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.crawler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Date;

import de.anomic.net.ftpc;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.cache.ftp.ResourceInfo;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;

public class FTPLoader {

    private final plasmaSwitchboard sb;
    private final serverLog log;
    private final int maxFileSize;

    public FTPLoader(final plasmaSwitchboard sb, final serverLog log) {
        this.sb = sb;
        this.log = log;
        maxFileSize = (int) sb.getConfigLong("crawler.ftp.maxFileSize", -1l);
    }

    protected plasmaHTCache.Entry createCacheEntry(final CrawlEntry entry, final String mimeType,
            final Date fileDate) {
        return plasmaHTCache.newEntry(new Date(), entry.depth(), entry.url(), entry.name(), "OK", new ResourceInfo(
                entry.url(), sb.getURL(entry.referrerhash()), mimeType, fileDate), entry.initiator(),
                sb.profilesActiveCrawls.getEntry(entry.profileHandle()));
    }

    /**
     * Loads the entry from a ftp-server
     * 
     * @param entry
     * @return
     */
    public plasmaHTCache.Entry load(final CrawlEntry entry) {
        final yacyURL entryUrl = entry.url();
        final String fullPath = getPath(entryUrl);
        final File cacheFile = createCachefile(entryUrl);

        // the return value
        plasmaHTCache.Entry htCache = null;

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
            try {
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
                    if (!generateDirlist(ftpClient, entry, path, cacheFile)) {
                        htCache = null;
                    }
                } else {
                    // file -> download
                    try {
                        htCache = getFile(ftpClient, entry, cacheFile);
                    } catch (final Exception e) {
                        // add message to errorLog
                        (new PrintStream(berr)).print(e.getMessage());
                    }
                }
            } finally {
                closeConnection(ftpClient);
            }
        }

        // pass the downloaded resource to the cache manager
        if (berr.size() > 0 || htCache == null) {
            // some error logging
            final String detail = (berr.size() > 0) ? "\n    Errorlog: " + berr.toString() : "";
            log.logWarning("Unable to download URL " + entry.url().toString() + detail);
            sb.crawlQueues.errorURL.newEntry(entry, sb.wordIndex.seedDB.mySeed().hash, new Date(), 1, ErrorURL.DENIED_SERVER_DOWNLOAD_ERROR);

            // an error has occured. cleanup
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
        } else {
            // announce the file
            plasmaHTCache.writeFileAnnouncement(cacheFile);
        }

        return htCache;
    }

    /**
     * creating a cache file object
     * 
     * @param entryUrl
     * @return
     */
    private File createCachefile(final yacyURL entryUrl) {
        final File cacheFile = plasmaHTCache.getCachePath(entryUrl);

        // testing if the file already exists
        if (cacheFile.isFile()) {
            // delete the file if it already exists
            plasmaHTCache.deleteURLfromCache(entryUrl);
        } else {
            // create parent directories
            cacheFile.getParentFile().mkdirs();
        }
        return cacheFile;
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
     * @param host
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
    private plasmaHTCache.Entry getFile(final ftpc ftpClient, final CrawlEntry entry, final File cacheFile)
            throws Exception {
        // determine the mimetype of the resource
        final yacyURL entryUrl = entry.url();
        final String extension = plasmaParser.getFileExt(entryUrl);
        final String mimeType = plasmaParser.getMimeTypeByFileExt(extension);
        final String path = getPath(entryUrl);

        // if the mimetype and file extension is supported we start to download
        // the file
        plasmaHTCache.Entry htCache = null;
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
                ftpClient.exec("get \"" + path + "\" \"" + cacheFile.getAbsolutePath() + "\"", false);
            } else {
                log.logInfo("REJECTED TOO BIG FILE with size " + size + " Bytes for URL " + entry.url().toString());
                sb.crawlQueues.errorURL.newEntry(entry, this.sb.wordIndex.seedDB.mySeed().hash, new Date(), 1,
                        ErrorURL.DENIED_FILESIZE_LIMIT_EXCEEDED);
                throw new Exception("file size exceeds limit");
            }
        } else {
            // if the response has not the right file type then reject file
            log.logInfo("REJECTED WRONG MIME/EXT TYPE " + mimeType + " for URL " + entry.url().toString());
            sb.crawlQueues.errorURL.newEntry(entry, null, new Date(), 1, ErrorURL.DENIED_WRONG_MIMETYPE_OR_EXT);
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
    private boolean generateDirlist(final ftpc ftpClient, final CrawlEntry entry, final String path,
            final File cacheFile) {
        // getting the dirlist
        final yacyURL entryUrl = entry.url();

        // generate the dirlist
        final StringBuilder dirList = ftpClient.dirhtml(path);

        if (dirList != null && dirList.length() > 0) {
            try {
                // write it into a file
                final PrintWriter writer = new PrintWriter(new FileOutputStream(cacheFile), false);
                writer.write(dirList.toString());
                writer.flush();
                writer.close();
                return true;
            } catch (final Exception e) {
                log.logInfo("Unable to write dirlist for URL " + entryUrl.toString());
            }
        }
        return false;
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

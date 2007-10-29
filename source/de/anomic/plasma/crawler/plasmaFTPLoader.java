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


package de.anomic.plasma.crawler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Date;

import de.anomic.net.ftpc;
import de.anomic.plasma.plasmaCrawlEURL;
import de.anomic.plasma.plasmaCrawlEntry;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.cache.ftp.ResourceInfo;
import de.anomic.server.logging.serverLog;

public class plasmaFTPLoader {

    private plasmaSwitchboard sb;
    private serverLog log;
    
    public plasmaFTPLoader(plasmaSwitchboard sb, serverLog log) {
        this.sb = sb;
        this.log = log;
    }

    protected plasmaHTCache.Entry createCacheEntry(plasmaCrawlEntry entry, String mimeType, Date fileDate) {
        return plasmaHTCache.newEntry(
                new Date(), 
                entry.depth(), 
                entry.url(), 
                entry.name(),  
                "OK",
                new ResourceInfo(
                        entry.url(),
                        sb.getURL(entry.referrerhash()),
                        mimeType,
                        fileDate
                ), 
                entry.initiator(), 
                sb.profilesActiveCrawls.getEntry(entry.profileHandle())
        );
    }

    public plasmaHTCache.Entry load(plasmaCrawlEntry entry) {
                       
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bout);
        
        ByteArrayOutputStream berr = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(berr);            
        
        // create a new ftp client
        ftpc ftpClient = new ftpc(System.in, out, err);
        
        // get username and password
        String userInfo = entry.url().getUserInfo();
        String userName = "anonymous", userPwd = "anonymous";
        if (userInfo != null) {
            int pos = userInfo.indexOf(":");
            if (pos != -1) {
                userName = userInfo.substring(0,pos);
                userPwd = userInfo.substring(pos+1);
            }
        } 
        
        // get server name, port and file path
        String host = entry.url().getHost();
        String fullPath = entry.url().getPath();
        int port = entry.url().getPort();
        
        plasmaHTCache.Entry htCache = null;
        try {
            // open a connection to the ftp server
            if (port == -1) { 
                ftpClient.exec("open " + host, false);
            } else {
                ftpClient.exec("open " + host + " " + port, false);
            }

            // login to the server
            ftpClient.exec("user " + userName + " " + userPwd, false);   

            // change transfer mode to binary
            ftpClient.exec("binary", false);

            // determine filename and path
            String file, path;              
            if (fullPath.endsWith("/")) {
                file = "";
                path = fullPath;
            } else {
                int pos = fullPath.lastIndexOf("/");
                if (pos == -1) {
                    file = fullPath;
                    path = "/";
                } else {            
                    path = fullPath.substring(0,pos+1);
                    file = fullPath.substring(pos+1);
                }
            }        

            // testing if the specified file is a directory
            if (file.length() > 0) {
                ftpClient.exec("cd \"" + path + "\"", false);

                // testing if the current name is a directoy
                boolean isFolder = ftpClient.isFolder(file);
                if (isFolder) {
                    fullPath = fullPath + "/";
                    file = "";
                }
            }

            // creating a cache file object
            File cacheFile = plasmaHTCache.getCachePath(entry.url());        

            // TODO: aborting download if content is to long ...

            // TODO: invalid file path check    

            // testing if the file already exists
            if (cacheFile.isFile()) {
                // delete the file if it already exists
                plasmaHTCache.deleteURLfromCache(entry.url());
            } else {
                // create parent directories
                cacheFile.getParentFile().mkdirs();
            }

            String mimeType;
            Date fileDate;
            if (file.length() == 0) {            
                // getting the dirlist
                mimeType = "text/html";
                fileDate = new Date();

                // create a htcache entry
                htCache = createCacheEntry(entry, mimeType, fileDate);

                // generate the dirlist
                StringBuffer dirList = ftpClient.dirhtml(fullPath);            

                if (dirList != null && dirList.length() > 0) try {
                    // write it into a file
                    PrintWriter writer = new PrintWriter(new FileOutputStream(cacheFile),false);
                    writer.write(dirList.toString());
                    writer.flush();
                    writer.close();
                } catch (Exception e) {
                    this.log.logInfo("Unable to write dirlist for URL " + entry.url().toString());
                    htCache = null;
                }
            } else {
                // determine the mimetype of the resource
                String extension = plasmaParser.getFileExt(entry.url());
                mimeType = plasmaParser.getMimeTypeByFileExt(extension);

                // if the mimetype and file extension is supported we start to download the file
                if (plasmaParser.supportedContent(plasmaParser.PARSER_MODE_CRAWLER, entry.url(), mimeType)) {

                    // TODO: determine the real file date
                    fileDate = new Date();

                    // create a htcache entry
                    htCache = createCacheEntry(entry, mimeType, fileDate);

                    // change into working directory
                    ftpClient.exec("cd \"" + fullPath + "\"", false);

                    // download the remote file
                    ftpClient.exec("get \"" + file + "\" \"" + cacheFile.getAbsolutePath() + "\"", false);
                } else {
                    // if the response has not the right file type then reject file
                    this.log.logInfo("REJECTED WRONG MIME/EXT TYPE " + mimeType + " for URL " + entry.url().toString());
                    sb.crawlQueues.errorURL.newEntry(entry, null, new Date(), 1, plasmaCrawlEURL.DENIED_WRONG_MIMETYPE_OR_EXT);
                    return null;
                }
            }

            // pass the downloaded resource to the cache manager
            if (berr.size() > 0 || htCache == null) {
                // if the response has not the right file type then reject file
                this.log.logWarning("Unable to download URL " + entry.url().toString() + "\nErrorlog: " + berr.toString());
                sb.crawlQueues.errorURL.newEntry(entry, null, new Date(), 1, plasmaCrawlEURL.DENIED_SERVER_DOWNLOAD_ERROR);

                // an error has occured. cleanup
                if (cacheFile.exists()) cacheFile.delete();            
            } else {
                // announce the file
                plasmaHTCache.writeFileAnnouncement(cacheFile);        
            }
            
            return htCache;
        } finally {
            // closing connection
            ftpClient.exec("close", false);
            ftpClient.exec("exit", false);        
        }       
    }


}

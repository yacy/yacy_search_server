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


package de.anomic.plasma.crawler.ftp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Date;

import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.net.URL;
import de.anomic.net.ftpc;
import de.anomic.plasma.plasmaCrawlEURL;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.cache.IResourceInfo;
import de.anomic.plasma.cache.ftp.ResourceInfo;
import de.anomic.plasma.crawler.AbstractCrawlWorker;
import de.anomic.plasma.crawler.plasmaCrawlWorker;
import de.anomic.plasma.crawler.plasmaCrawlerPool;
import de.anomic.plasma.plasmaHTCache.Entry;
import de.anomic.server.logging.serverLog;

public class CrawlWorker extends AbstractCrawlWorker implements plasmaCrawlWorker {

    public CrawlWorker(ThreadGroup theTG, plasmaCrawlerPool thePool, plasmaSwitchboard theSb, plasmaHTCache theCacheManager, serverLog theLog) {
        super(theTG, thePool, theSb, theCacheManager, theLog);
        
        // this crawler supports ftp
        this.protocol = "ftp";  
    }

    public void close() {
        // TODO: abort a currently established connection
    }

    public void init() {
        // nothing todo here
    }

    protected plasmaHTCache.Entry createCacheEntry(String mimeType, Date fileDate) {
        IResourceInfo resourceInfo = new ResourceInfo(
                this.url,
                this.refererURLString,
                mimeType,
                fileDate
        );        
        
        return this.cacheManager.newEntry(
                new Date(), 
                this.depth, 
                this.url, 
                this.name,  
                "OK",
                resourceInfo, 
                this.initiator, 
                this.profile
        );
    }        
    
    public Entry load() throws IOException {
                       
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bout);
        
        ByteArrayOutputStream berr = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(berr);            
        
        // create a new ftp client
        ftpc ftpClient = new ftpc(System.in, out, err);
        
        // get username and password
        String userInfo = this.url.getUserInfo();
        String userName = "anonymous", userPwd = "anonymous";
        if (userInfo != null) {
            int pos = userInfo.indexOf(":");
            if (pos != -1) {
                userName = userInfo.substring(0,pos);
                userPwd = userInfo.substring(pos+1);
            }
        } 
        
        // get server name, port and file path
        String host = this.url.getHost();
        String fullPath = this.url.getPath();
        int port = this.url.getPort();
        
        // open a connection to the ftp server
        if (port == -1) { 
            ftpClient.exec("open " + host, false);
        } else {
            ftpClient.exec("open " + host + " " + port, false);
        }
        if (berr.size() > 0) {
            this.log.logInfo("Unable to connect to ftp server " + this.url.getHost() + " hosting URL " + this.url.toString() + "\nErrorlog: " + berr.toString());
            addURLtoErrorDB(plasmaCrawlEURL.DENIED_CONNECTION_ERROR);            
        }
        
        // login to the server
        ftpClient.exec("user " + userName + " " + userPwd, false);  
        if (berr.size() > 0) {
            this.log.logInfo("Unable to login to ftp server " + this.url.getHost() + " hosting URL " + this.url.toString() + "\nErrorlog: " + berr.toString());
            addURLtoErrorDB(plasmaCrawlEURL.DENIED_SERVER_LOGIN_FAILED);            
        }        
        
        // change transfer mode to binary
        ftpClient.exec("binary", false);
        if (berr.size() > 0) {
            this.log.logInfo("Unable to set the file transfer mode to binary for URL " + this.url.toString() + "\nErrorlog: " + berr.toString());
            addURLtoErrorDB(plasmaCrawlEURL.DENIED_SERVER_TRASFER_MODE_PROBLEM);            
        }         
        
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
                this.url = new URL(this.url,fullPath);
            }
        }
        
        // creating a cache file object
        File cacheFile = this.cacheManager.getCachePath(this.url);
        cacheFile.getParentFile().mkdirs();
                
        String mimeType;
        Date fileDate;
        plasmaHTCache.Entry htCache = null;
        if (file.length() == 0) {            
            // getting the dirlist
            mimeType = "text/html";
            fileDate = new Date();
            
            // create a htcache entry
            htCache = createCacheEntry(mimeType,fileDate);
            
            // generate the dirlist
            StringBuffer dirList = ftpClient.dirhtml(fullPath);            
            
            // write it into a file
            PrintWriter writer = new PrintWriter(new FileOutputStream(cacheFile),false);
            writer.write(dirList.toString());
            writer.flush();
            writer.close();
        } else {
            // determine the mimetype of the resource
            String extension = plasmaParser.getFileExt(this.url);
            mimeType = plasmaParser.getMimeTypeByFileExt(extension);
            
            // if the mimetype and file extension is supported we start to download the file
            if ((this.acceptAllContent) || (plasmaParser.supportedContent(plasmaParser.PARSER_MODE_CRAWLER,this.url,mimeType))) {
                
                // TODO: determine the real file date
                fileDate = new Date();
                
                // create a htcache entry
                htCache = createCacheEntry(mimeType,fileDate);
                
                // change into working directory
                ftpClient.exec("cd \"" + fullPath + "\"", false);

                // download the remote file
                ftpClient.exec("get \"" + file + "\" \"" + cacheFile.getAbsolutePath() + "\"", false);
            } else {
                // if the response has not the right file type then reject file
                this.log.logInfo("REJECTED WRONG MIME/EXT TYPE " + mimeType + " for URL " + this.url.toString());
                addURLtoErrorDB(plasmaCrawlEURL.DENIED_WRONG_MIMETYPE_OR_EXT);
            }
        }
        
        // closing connection
        ftpClient.exec("close", false);
        ftpClient.exec("exit", false);        

        // pass the downloaded resource to the cache manager
        if (berr.size() > 0 || htCache == null) {
            // if the response has not the right file type then reject file
            this.log.logInfo("Unable to download URL " + this.url.toString() + "\nErrorlog: " + berr.toString());
            addURLtoErrorDB(plasmaCrawlEURL.DENIED_SERVER_DOWNLOAD_ERROR);
            
            // an error has occured. cleanup
            if (cacheFile.exists()) cacheFile.delete();            
        } else {
            // announce the file
            this.cacheManager.writeFileAnnouncement(cacheFile);
            
            // enQueue new entry with response header
            if (this.profile != null) {
                this.cacheManager.push(htCache);
            }        
        }
        
        return htCache;
    }


}

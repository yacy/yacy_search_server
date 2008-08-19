// httpdProxyCacheEntry.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 19.08.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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

package de.anomic.http;

import java.io.File;
import java.util.Date;

import de.anomic.crawler.CrawlProfile;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.cache.IResourceInfo;
import de.anomic.server.serverSystem;
import de.anomic.yacy.yacyURL;

public class httpdProxyCacheEntry {
    
    // doctypes:
    public static final char DT_PDFPS   = 'p';
    public static final char DT_TEXT    = 't';
    public static final char DT_HTML    = 'h';
    public static final char DT_DOC     = 'd';
    public static final char DT_IMAGE   = 'i';
    public static final char DT_MOVIE   = 'm';
    public static final char DT_FLASH   = 'f';
    public static final char DT_SHARE   = 's';
    public static final char DT_AUDIO   = 'a';
    public static final char DT_BINARY  = 'b';
    public static final char DT_UNKNOWN = 'u';

    // the class objects
    private final  int                depth;           // the depth of pre-fetching
    private final  String             responseStatus;
    private final  File               cacheFile;       // the cache file
    private        byte[]             cacheArray;      // or the cache as byte-array
    private final  yacyURL            url;
    private final  String             name;            // the name of the link, read as anchor from an <a>-tag
    private final  Date               lastModified;
    private        char               doctype;
    private final  String             language;
    private final  CrawlProfile.entry profile;
    private final  String             initiator;

    /**
     * protocol specific information about the resource
     */
    private final IResourceInfo resInfo;

    // doctype calculation
    public static char docType(final yacyURL url) {
        final String path = url.getPath().toLowerCase();
        // serverLog.logFinest("PLASMA", "docType URL=" + path);
        char doctype = DT_UNKNOWN;
        if (path.endsWith(".gif"))       { doctype = DT_IMAGE; }
        else if (path.endsWith(".ico"))  { doctype = DT_IMAGE; }
        else if (path.endsWith(".bmp"))  { doctype = DT_IMAGE; }
        else if (path.endsWith(".jpg"))  { doctype = DT_IMAGE; }
        else if (path.endsWith(".jpeg")) { doctype = DT_IMAGE; }
        else if (path.endsWith(".png"))  { doctype = DT_IMAGE; }
        else if (path.endsWith(".html")) { doctype = DT_HTML;  }
        else if (path.endsWith(".txt"))  { doctype = DT_TEXT;  }
        else if (path.endsWith(".doc"))  { doctype = DT_DOC;   }
        else if (path.endsWith(".rtf"))  { doctype = DT_DOC;   }
        else if (path.endsWith(".pdf"))  { doctype = DT_PDFPS; }
        else if (path.endsWith(".ps"))   { doctype = DT_PDFPS; }
        else if (path.endsWith(".avi"))  { doctype = DT_MOVIE; }
        else if (path.endsWith(".mov"))  { doctype = DT_MOVIE; }
        else if (path.endsWith(".qt"))   { doctype = DT_MOVIE; }
        else if (path.endsWith(".mpg"))  { doctype = DT_MOVIE; }
        else if (path.endsWith(".md5"))  { doctype = DT_SHARE; }
        else if (path.endsWith(".mpeg")) { doctype = DT_MOVIE; }
        else if (path.endsWith(".asf"))  { doctype = DT_FLASH; }
        return doctype;
    }

    public static char docType(final String mime) {
        // serverLog.logFinest("PLASMA", "docType mime=" + mime);
        char doctype = DT_UNKNOWN;
        if (mime == null) doctype = DT_UNKNOWN;
        else if (mime.startsWith("image/")) doctype = DT_IMAGE;
        else if (mime.endsWith("/gif")) doctype = DT_IMAGE;
        else if (mime.endsWith("/jpeg")) doctype = DT_IMAGE;
        else if (mime.endsWith("/png")) doctype = DT_IMAGE;
        else if (mime.endsWith("/html")) doctype = DT_HTML;
        else if (mime.endsWith("/rtf")) doctype = DT_DOC;
        else if (mime.endsWith("/pdf")) doctype = DT_PDFPS;
        else if (mime.endsWith("/octet-stream")) doctype = DT_BINARY;
        else if (mime.endsWith("/x-shockwave-flash")) doctype = DT_FLASH;
        else if (mime.endsWith("/msword")) doctype = DT_DOC;
        else if (mime.endsWith("/mspowerpoint")) doctype = DT_DOC;
        else if (mime.endsWith("/postscript")) doctype = DT_PDFPS;
        else if (mime.startsWith("text/")) doctype = DT_TEXT;
        else if (mime.startsWith("image/")) doctype = DT_IMAGE;
        else if (mime.startsWith("audio/")) doctype = DT_AUDIO;
        else if (mime.startsWith("video/")) doctype = DT_MOVIE;
        //bz2     = application/x-bzip2
        //dvi     = application/x-dvi
        //gz      = application/gzip
        //hqx     = application/mac-binhex40
        //lha     = application/x-lzh
        //lzh     = application/x-lzh
        //pac     = application/x-ns-proxy-autoconfig
        //php     = application/x-httpd-php
        //phtml   = application/x-httpd-php
        //rss     = application/xml
        //tar     = application/tar
        //tex     = application/x-tex
        //tgz     = application/tar
        //torrent = application/x-bittorrent
        //xhtml   = application/xhtml+xml
        //xla     = application/msexcel
        //xls     = application/msexcel
        //xsl     = application/xml
        //xml     = application/xml
        //Z       = application/x-compress
        //zip     = application/zip
        return doctype;
    }
    
    public httpdProxyCacheEntry(final int depth,
            final yacyURL url, final String name, final String responseStatus,
            final IResourceInfo resourceInfo, final String initiator,
            final CrawlProfile.entry profile) {
        if (resourceInfo == null) {
            System.out.println("Content information object is null. " + url);
            System.exit(0);
        }
        this.resInfo = resourceInfo;
        this.url = url;
        this.name = name;
        this.cacheFile = plasmaHTCache.getCachePath(this.url);

        // assigned:
        this.depth = depth;
        this.responseStatus = responseStatus;
        this.profile = profile;
        this.initiator = (initiator == null) ? null : ((initiator.length() == 0) ? null : initiator);

        // getting the last modified date
        this.lastModified = resourceInfo.getModificationDate();

        // getting the doctype
        this.doctype = docType(resourceInfo.getMimeType());
        if (this.doctype == DT_UNKNOWN)
            this.doctype = docType(url);
        this.language = yacyURL.language(url);

        // to be defined later:
        this.cacheArray = null;
    }

    public String name() {
        // the anchor name; can be either the text inside the anchor tag or the
        // page description after loading of the page
        return this.name;
    }

    public yacyURL url() {
        return this.url;
    }

    public String urlHash() {
        return this.url.hash();
    }

    public Date lastModified() {
        return this.lastModified;
    }

    public String language() {
        return this.language;
    }

    public CrawlProfile.entry profile() {
        return this.profile;
    }

    public String initiator() {
        return this.initiator;
    }

    public boolean proxy() {
        return initiator() == null;
    }

    public long size() {
        if (this.cacheArray == null)
            return 0;
        return this.cacheArray.length;
    }

    public int depth() {
        return this.depth;
    }

    public yacyURL referrerURL() {
        return (this.resInfo == null) ? null : this.resInfo.getRefererUrl();
    }

    public File cacheFile() {
        return this.cacheFile;
    }

    public void setCacheArray(final byte[] data) {
        this.cacheArray = data;
    }

    public byte[] cacheArray() {
        return this.cacheArray;
    }

    public IResourceInfo getDocumentInfo() {
        return this.resInfo;
    }

    public String getMimeType() {
        return (this.resInfo == null) ? null : this.resInfo.getMimeType();
    }

    public Date ifModifiedSince() {
        return (this.resInfo == null) ? null : this.resInfo.ifModifiedSince();
    }

    public boolean requestWithCookie() {
        return (this.resInfo == null) ? false : this.resInfo.requestWithCookie();
    }

    public boolean requestProhibitsIndexing() {
        return (this.resInfo == null) ? false : this.resInfo.requestProhibitsIndexing();
    }


    // the following three methods for cache read/write granting shall be as loose
    // as possible but also as strict as necessary to enable caching of most items

    /**
     * @return NULL if the answer is TRUE, in case of FALSE, the reason as
     *         String is returned
     */
    public String shallStoreCacheForProxy() {

        // check profile (disabled: we will check this in the plasmaSwitchboard)
        // if (!this.profile.storeHTCache()) { return "storage_not_wanted"; }

        // decide upon header information if a specific file should be stored to
        // the cache or not
        // if the storage was requested by prefetching, the request map is null

        // check status code
        if ((this.resInfo != null)
                && (!this.resInfo.validResponseStatus(this.responseStatus))) {
            return "bad_status_" + this.responseStatus.substring(0, 3);
        }

        // check storage location
        // sometimes a file name is equal to a path name in the same directory;
        // or sometimes a file name is equal a directory name created earlier;
        // we cannot match that here in the cache file path and therefore omit
        // writing into the cache
        if (this.cacheFile.getParentFile().isFile()
                || this.cacheFile.isDirectory()) {
            return "path_ambiguous";
        }
        if (this.cacheFile.toString().indexOf("..") >= 0) {
            return "path_dangerous";
        }
        if (this.cacheFile.getAbsolutePath().length() > serverSystem.maxPathLength) {
            return "path too long";
        }

        // -CGI access in request
        // CGI access makes the page very individual, and therefore not usable
        // in caches
        if (this.url.isPOST() && !this.profile.crawlingQ()) {
            return "dynamic_post";
        }
        if (this.url.isCGI()) {
            return "dynamic_cgi";
        }

        if (this.resInfo != null) {
            return this.resInfo.shallStoreCacheForProxy();
        }

        return null;
    }

    /**
     * decide upon header information if a specific file should be taken from
     * the cache or not
     * 
     * @return whether the file should be taken from the cache
     */
    public boolean shallUseCacheForProxy() {

        // -CGI access in request
        // CGI access makes the page very individual, and therefore not usable
        // in caches
        if (this.url.isPOST()) {
            return false;
        }
        if (this.url.isCGI()) {
            return false;
        }

        if (this.resInfo != null) {
            return this.resInfo.shallUseCacheForProxy();
        }

        return true;
    }

}

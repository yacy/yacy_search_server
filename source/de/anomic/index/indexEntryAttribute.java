// indexEntryAttribute.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 16.05.2006 on http://www.anomic.de
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



package de.anomic.index;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.net.URL;
import de.anomic.server.serverCodings;
import de.anomic.yacy.yacySeedDB;

public class indexEntryAttribute {

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

    // appearance locations: (used for flags)
    public static final int AP_TITLE     =  0; // title tag from html header
    public static final int AP_H1        =  1; // headline - top level
    public static final int AP_H2        =  2; // headline, second level
    public static final int AP_H3        =  3; // headline, 3rd level
    public static final int AP_H4        =  4; // headline, 4th level
    public static final int AP_H5        =  5; // headline, 5th level
    public static final int AP_H6        =  6; // headline, 6th level
    public static final int AP_TEXT      =  7; // word appears in text (used to check validation of other appearances against spam)
    public static final int AP_DOM       =  8; // word inside an url: in Domain
    public static final int AP_PATH      =  9; // word inside an url: in path
    public static final int AP_IMG       = 10; // tag inside image references
    public static final int AP_ANCHOR    = 11; // anchor description
    public static final int AP_ENV       = 12; // word appears in environment (similar to anchor appearance)
    public static final int AP_BOLD      = 13; // may be interpreted as emphasized
    public static final int AP_ITALICS   = 14; // may be interpreted as emphasized
    public static final int AP_WEAK      = 15; // for Text that is small or bareley visible
    public static final int AP_INVISIBLE = 16; // good for spam detection
    public static final int AP_TAG       = 17; // for tagged indexeing (i.e. using mp3 tags)
    public static final int AP_AUTHOR    = 18; // word appears in author name
    public static final int AP_OPUS      = 19; // word appears in name of opus, which may be an album name (in mp3 tags)
    public static final int AP_TRACK     = 20; // word appears in track name (i.e. in mp3 tags)
    
    // URL attributes
    public static final int UA_LOCAL    =  0; // URL was crawled locally
    public static final int UA_TILDE    =  1; // tilde appears in URL
    public static final int UA_REDIRECT =  2; // The URL is a redirection
    
    // local flag attributes
    public static final char LT_LOCAL   = 'L';
    public static final char LT_GLOBAL  = 'G';
    
    // create a word hash
    public static String word2hash(String word) {
        return kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(word.toLowerCase())).substring(0, yacySeedDB.commonHashLength);
    }

    // doctype calculation
    public static char docType(URL url) {
        String path = url.getPath();
        // serverLog.logFinest("PLASMA", "docType URL=" + path);
        char doctype = indexEntryAttribute.DT_UNKNOWN;
        if (path.endsWith(".gif"))       { doctype = indexEntryAttribute.DT_IMAGE; }
        else if (path.endsWith(".jpg"))  { doctype = indexEntryAttribute.DT_IMAGE; }
        else if (path.endsWith(".jpeg")) { doctype = indexEntryAttribute.DT_IMAGE; }
        else if (path.endsWith(".png"))  { doctype = indexEntryAttribute.DT_IMAGE; }
        else if (path.endsWith(".html")) { doctype = indexEntryAttribute.DT_HTML;  }
        else if (path.endsWith(".txt"))  { doctype = indexEntryAttribute.DT_TEXT;  }
        else if (path.endsWith(".doc"))  { doctype = indexEntryAttribute.DT_DOC;   }
        else if (path.endsWith(".rtf"))  { doctype = indexEntryAttribute.DT_DOC;   }
        else if (path.endsWith(".pdf"))  { doctype = indexEntryAttribute.DT_PDFPS; }
        else if (path.endsWith(".ps"))   { doctype = indexEntryAttribute.DT_PDFPS; }
        else if (path.endsWith(".avi"))  { doctype = indexEntryAttribute.DT_MOVIE; }
        else if (path.endsWith(".mov"))  { doctype = indexEntryAttribute.DT_MOVIE; }
        else if (path.endsWith(".qt"))   { doctype = indexEntryAttribute.DT_MOVIE; }
        else if (path.endsWith(".mpg"))  { doctype = indexEntryAttribute.DT_MOVIE; }
        else if (path.endsWith(".md5"))  { doctype = indexEntryAttribute.DT_SHARE; }
        else if (path.endsWith(".mpeg")) { doctype = indexEntryAttribute.DT_MOVIE; }
        else if (path.endsWith(".asf"))  { doctype = indexEntryAttribute.DT_FLASH; }
        return doctype;
    }

    public static char docType(String mime) {
        // serverLog.logFinest("PLASMA", "docType mime=" + mime);
        char doctype = indexEntryAttribute.DT_UNKNOWN;
        if (mime == null) doctype = indexEntryAttribute.DT_UNKNOWN;
        else if (mime.startsWith("image/")) doctype = indexEntryAttribute.DT_IMAGE;
        else if (mime.endsWith("/gif")) doctype = indexEntryAttribute.DT_IMAGE;
        else if (mime.endsWith("/jpeg")) doctype = indexEntryAttribute.DT_IMAGE;
        else if (mime.endsWith("/png")) doctype = indexEntryAttribute.DT_IMAGE;
        else if (mime.endsWith("/html")) doctype = indexEntryAttribute.DT_HTML;
        else if (mime.endsWith("/rtf")) doctype = indexEntryAttribute.DT_DOC;
        else if (mime.endsWith("/pdf")) doctype = indexEntryAttribute.DT_PDFPS;
        else if (mime.endsWith("/octet-stream")) doctype = indexEntryAttribute.DT_BINARY;
        else if (mime.endsWith("/x-shockwave-flash")) doctype = indexEntryAttribute.DT_FLASH;
        else if (mime.endsWith("/msword")) doctype = indexEntryAttribute.DT_DOC;
        else if (mime.endsWith("/mspowerpoint")) doctype = indexEntryAttribute.DT_DOC;
        else if (mime.endsWith("/postscript")) doctype = indexEntryAttribute.DT_PDFPS;
        else if (mime.startsWith("text/")) doctype = indexEntryAttribute.DT_TEXT;
        else if (mime.startsWith("image/")) doctype = indexEntryAttribute.DT_IMAGE;
        else if (mime.startsWith("audio/")) doctype = indexEntryAttribute.DT_AUDIO;
        else if (mime.startsWith("video/")) doctype = indexEntryAttribute.DT_MOVIE;
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

    // language calculation
    public static String language(URL url) {
        String language = "uk";
        String host = url.getHost();
        int pos = host.lastIndexOf(".");
        if ((pos > 0) && (host.length() - pos == 3)) language = host.substring(pos + 1).toLowerCase();
        return language;
    }
    
}

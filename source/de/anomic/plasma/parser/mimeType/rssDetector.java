//rssDetector.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//last major change: 16.05.2005
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.plasma.parser.mimeType;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

import net.sf.jmimemagic.MagicDetector;

public class rssDetector implements MagicDetector {

    public String getDisplayName() {
        return "RSS MimeType Detector";
    }

    public String[] getHandledExtensions() {
        return new String[]{"xml","rss","rdf","atom"};
    }

    public String[] getHandledTypes() {
        return new String[] { "text/rss", "application/rdf+xml", "application/rss+xml", "application/atom+xml" };
    }

    public String getName() {
        return "rssfiledetector";
    }

    public String getVersion() {
        return "0.1";
    }

    @SuppressWarnings("unchecked")
    public String[] process(File file, int offset, int length, long bitmask, char comparator, String mimeType, Map params) {
        FileInputStream fileInput = null;
        try {
            fileInput = new FileInputStream(file);
            return detect(fileInput);
        } catch (Exception e) {
            return null;
        } finally {
            if (fileInput != null) try { fileInput.close(); } catch (Exception e) { /* ignore this */ }
        }
    }

    @SuppressWarnings("unchecked")
    public String[] process(byte[] data, int offset, int length, long bitmask, char comparator, String mimeType, Map params) {
        ByteArrayInputStream input = new ByteArrayInputStream(data);
        return detect(input);
    }
    
    private String[] detect(InputStream input) {
        return new String[]{"application/rss+xml"};
    }

}

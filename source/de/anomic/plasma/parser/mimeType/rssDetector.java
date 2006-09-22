//rssDetector.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
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
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.


package de.anomic.plasma.parser.mimeType;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
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

    public String[] process(byte[] data, int offset, int length, long bitmask, char comparator, String mimeType, Map params) {
        ByteArrayInputStream input = new ByteArrayInputStream(data);
        return detect(input);
    }
    
    private String[] detect(InputStream input) {
        try {

            // getting the format detector class
            Class formatDetector = Class.forName("de.nava.informa.utils.FormatDetector");
            
            // getting the proper method
            Method getFormat = formatDetector.getMethod("getFormat", new Class[]{InputStream.class});
            
            // invoke the method
            Object format = getFormat.invoke(null, new Object[] {input});
            
            if (format == null) return null;
            else if (format.toString().startsWith("RSS ")) return new String[]{"application/rss+xml"};
            else if (format.toString().startsWith("Atom ")) return new String[]{"application/atom+xml"};
            else return null;
        } catch (Exception e) {
            return null;
        } catch (Error e) {
            return null;
        }        
    }

}

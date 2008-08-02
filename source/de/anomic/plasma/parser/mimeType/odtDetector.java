//odtDetector.java 
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.sf.jmimemagic.MagicDetector;
import de.anomic.server.serverFileUtils;

public class odtDetector implements MagicDetector {

    public String getDisplayName() {
        return "ODT MimeType Detector";
    }

    public String[] getHandledExtensions() {
        return new String[]{"zip","odt"};
    }

    public String[] getHandledTypes() {
        return new String[] { "application/vnd.oasis.opendocument.text", "application/x-vnd.oasis.opendocument.text" };
    }

    public String getName() {
        return "odtfiledetector";
    }

    public String getVersion() {
        return "0.1";
    }

    @SuppressWarnings("unchecked")
    public String[] process(final byte[] data, final int offset, final int length, final long bitmask, final char comparator, final String mimeType, final Map params) {
        File dstFile = null;
        try {
            dstFile = File.createTempFile("mimeTypeParser",".tmp");
            serverFileUtils.copy(data,dstFile);
            return process(dstFile, offset, length, bitmask, comparator, mimeType, params);
        } catch (final IOException e) {
            return null;
        } finally {
            if (dstFile != null) {dstFile.delete();}            
        }
    }

    @SuppressWarnings("unchecked")
    public String[] process(final File file, final int offset, final int length, final long bitmask, final char comparator, final String mimeType, final Map params) {
        try {
            // opening the zip file
            final ZipFile zipFile = new ZipFile(file);
            
            // searching for a file named mimetype
            final ZipEntry mimeTypeInfo = zipFile.getEntry("mimetype");
            if (mimeTypeInfo == null) return null;
            
            // read in the content of the file
            final InputStream zippedContent = zipFile.getInputStream(mimeTypeInfo); 
            final String realMimeType = new String(serverFileUtils.read(zippedContent, mimeTypeInfo.getSize()));
            
            return new String[]{realMimeType};
        } catch (final Exception e) {
            return null;
        }
        
    }

}

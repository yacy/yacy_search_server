//odtDetector.java 
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

    public String[] process(byte[] data, int offset, int length, long bitmask, char comparator, String mimeType, Map params) {
        File dstFile = null;
        try {
            dstFile = File.createTempFile("mimeTypeParser",".tmp");
            serverFileUtils.write(data,dstFile);
            return process(dstFile, offset, length, bitmask, comparator, mimeType, params);
        } catch (IOException e) {
            return null;
        } finally {
            if (dstFile != null) {dstFile.delete();}            
        }
    }

    public String[] process(File file, int offset, int length, long bitmask, char comparator, String mimeType, Map params) {
        try {
            // opening the zip file
            ZipFile zipFile = new ZipFile(file);
            
            // searching for a file named mimetype
            ZipEntry mimeTypeInfo = zipFile.getEntry("mimetype");
            if (mimeTypeInfo == null) return null;
            
            // read in the content of the file
            InputStream zippedContent = zipFile.getInputStream(mimeTypeInfo); 
            String realMimeType = new String(serverFileUtils.read(zippedContent, mimeTypeInfo.getSize()));
            
            return new String[]{realMimeType};
        } catch (Exception e) {
            return null;
        }
        
    }

}

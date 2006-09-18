//tarParser.java 
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

package de.anomic.plasma.parser.tar;

import java.io.File;
import java.io.InputStream;
import de.anomic.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

import com.ice.tar.TarEntry;
import com.ice.tar.TarInputStream;

import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverFileUtils;

public class tarParser extends AbstractParser implements Parser {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Hashtable SUPPORTED_MIME_TYPES = new Hashtable();    
    static { 
        SUPPORTED_MIME_TYPES.put("application/x-tar","tar");
        SUPPORTED_MIME_TYPES.put("application/tar","tar");
    }     

    /**
     * a list of library names that are needed by this parser
     * @see Parser#getLibxDependences()
     */
    private static final String[] LIBX_DEPENDENCIES = new String[] {
        "tar.jar"
    };    
    
    public tarParser() {        
        super(LIBX_DEPENDENCIES);
        parserName = "Tape Archive File Parser"; 
    }
    
    public Hashtable getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public plasmaParserDocument parse(URL location, String mimeType, String charset, InputStream source) throws ParserException, InterruptedException {
        
        try {           
            // creating a new parser class to parse the unzipped content
            plasmaParser theParser = new plasmaParser();       
            
            /*
             * If the mimeType was not reported correcly by the webserve we
             * have to decompress it first
             */
            String ext = plasmaParser.getFileExt(location).toLowerCase();
            if (ext.equals("gz") || ext.equals("tgz")) {
                source = new GZIPInputStream(source);
            }
            
            StringBuffer docKeywords = new StringBuffer();
            StringBuffer docShortTitle = new StringBuffer();  
            StringBuffer docLongTitle = new StringBuffer();   
            LinkedList docSections = new LinkedList();
            StringBuffer docAbstrct = new StringBuffer();
            serverByteBuffer docText = new serverByteBuffer();
            Map docAnchors = new HashMap();
            TreeSet docImages = new TreeSet(); 
                        
            // looping through the contained files
            TarEntry entry;
            TarInputStream tin = new TarInputStream(source);                      
            while ((entry = tin.getNextEntry()) !=null) {
                // check for interruption
                checkInterruption();
                
                // skip directories
                if (entry.isDirectory()) continue;
                
                // Get the entry name
                int idx = -1;
                String entryName = entry.getName();
                idx = entryName.lastIndexOf("/");
                if (idx != -1) entryName = entryName.substring(idx+1);
                idx = entryName.lastIndexOf(".");
                String entryExt = (idx > -1) ? entryName.substring(idx+1) : "";
                
                // trying to determine the mimeType per file extension   
                String entryMime = plasmaParser.getMimeTypeByFileExt(entryExt);
                
                // getting the entry content
                plasmaParserDocument theDoc = null;
                File tempFile = null;
                try {
                    byte[] buf = new byte[(int) entry.getSize()];
                    /*int bytesRead =*/ tin.read(buf);

                    tempFile = File.createTempFile("tarParser_" + ((idx>-1)?entryName.substring(0,idx):entryName), (entryExt.length()>0)?"."+entryExt:entryExt);
                    serverFileUtils.write(buf, tempFile);           
                    
                    // check for interruption
                    checkInterruption();
                    
                    // parsing the content                    
                    theDoc = theParser.parseSource(new URL(tempFile),entryMime,null,tempFile);
                } finally {
                    if (tempFile != null) try {tempFile.delete(); } catch(Exception ex){}
                }
                if (theDoc == null) continue;
                
                // merging all documents together
                if (docKeywords.length() > 0) docKeywords.append(",");
                docKeywords.append(theDoc.getKeywords(','));
                
                if (docLongTitle.length() > 0) docLongTitle.append("\n");
                docLongTitle.append(theDoc.getMainLongTitle());
                
                if (docShortTitle.length() > 0) docShortTitle.append("\n");
                docShortTitle.append(theDoc.getMainShortTitle());                
                
                docSections.addAll(Arrays.asList(theDoc.getSectionTitles()));
                
                if (docAbstrct.length() > 0) docAbstrct.append("\n");
                docAbstrct.append(theDoc.getAbstract());                   

                if (docText.length() > 0) docText.append("\n");
                docText.append(theDoc.getText());                 
                
                docAnchors.putAll(theDoc.getAnchors());
                docImages.addAll(theDoc.getImages());
            }
            
            /* (URL location, String mimeType,
             String keywords, String shortTitle, String longTitle,
             String[] sections, String abstrct,
             byte[] text, Map anchors, Map images)
             */            
            return new plasmaParserDocument(
                    location,
                    mimeType,
                    null,
                    docKeywords.toString().split(" |,"),
                    docShortTitle.toString(), 
                    docLongTitle.toString(),
                    (String[])docSections.toArray(new String[docSections.size()]),
                    docAbstrct.toString(),
                    docText.toByteArray(),
                    docAnchors,
                    docImages);
        } catch (Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            throw new ParserException("Unable to parse the zip content. " + e.getMessage());
        }
    }
    
    public void reset() {
		// Nothing todo here at the moment
    	
    }
}

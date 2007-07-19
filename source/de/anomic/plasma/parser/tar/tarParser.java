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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

import com.ice.tar.TarEntry;
import com.ice.tar.TarInputStream;

import de.anomic.net.URL;
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
//        "tar.jar"
    };    
    
    public tarParser() {        
        super(LIBX_DEPENDENCIES);
        this.parserName = "Tape Archive File Parser"; 
    }
    
    public Hashtable getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public plasmaParserDocument parse(URL location, String mimeType, String charset, InputStream source) throws ParserException, InterruptedException {
        
        long docTextLength = 0;
        OutputStream docText = null;
        File outputFile = null;
        plasmaParserDocument subDoc = null;        
        try {           
            if ((this.contentLength == -1) || (this.contentLength > Parser.MAX_KEEP_IN_MEMORY_SIZE)) {
                outputFile = File.createTempFile("zipParser",".tmp");
                docText = new BufferedOutputStream(new FileOutputStream(outputFile));
            } else {
                docText = new serverByteBuffer();
            }            
            
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
            
            // TODO: what about bzip ....

            StringBuffer docKeywords = new StringBuffer();
            StringBuffer docLongTitle = new StringBuffer();   
            LinkedList docSections = new LinkedList();
            StringBuffer docAbstrct = new StringBuffer();

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
                
                // Get the short entry name
                String entryName = entry.getName();
                
                // getting the entry file extension
                int idx = entryName.lastIndexOf(".");
                String entryExt = (idx > -1) ? entryName.substring(idx+1) : "";
                
                // trying to determine the mimeType per file extension   
                String entryMime = plasmaParser.getMimeTypeByFileExt(entryExt);
                
                // getting the entry content
                File subDocTempFile = null;
                try {
                    // create the temp file
                    subDocTempFile = createTempFile(entryName);
                    
                    // copy the data into the file
                    serverFileUtils.copy(tin,subDocTempFile,entry.getSize());
                    
                    // check for interruption
                    checkInterruption();
                    
                    // parsing the content                    
                    subDoc = theParser.parseSource(URL.newURL(location,"#" + entryName),entryMime,null,subDocTempFile);
                } catch (ParserException e) {
                    this.theLogger.logInfo("Unable to parse tar file entry '" + entryName + "'. " + e.getMessage());
                } finally {
                    if (subDocTempFile != null) try {subDocTempFile.delete(); } catch(Exception ex){/* ignore this */}
                }
                if (subDoc == null) continue;
                
                // merging all documents together
                if (docKeywords.length() > 0) docKeywords.append(",");
                docKeywords.append(subDoc.getKeywords(','));
                
                if (docLongTitle.length() > 0) docLongTitle.append("\n");
                docLongTitle.append(subDoc.getTitle());
                
                docSections.addAll(Arrays.asList(subDoc.getSectionTitles()));
                
                if (docAbstrct.length() > 0) docAbstrct.append("\n");
                docAbstrct.append(subDoc.getAbstract());

                if (subDoc.getTextLength() > 0) {
                    if (docTextLength > 0) docText.write('\n');
                    docTextLength += serverFileUtils.copy(subDoc.getText(), docText);
                }               
                
                docAnchors.putAll(subDoc.getAnchors());
                docImages.addAll(subDoc.getImages());
                
                // release subdocument
                subDoc.close();
                subDoc = null;                
            }
            
            plasmaParserDocument result = null;
            
            if (docText instanceof serverByteBuffer) {
                result = new plasmaParserDocument(
                    location,
                    mimeType,
                    null,
                    docKeywords.toString().split(" |,"),
                    docLongTitle.toString(),
                    "", // TODO: AUTHOR
                    (String[])docSections.toArray(new String[docSections.size()]),
                    docAbstrct.toString(),
                    ((serverByteBuffer)docText).toByteArray(),
                    docAnchors,
                    docImages);
            } else {
                result = new plasmaParserDocument(
                        location,
                        mimeType,
                        null,
                        docKeywords.toString().split(" |,"),
                        docLongTitle.toString(),
                        "", // TODO: AUTHOR
                        (String[])docSections.toArray(new String[docSections.size()]),
                        docAbstrct.toString(),
                        outputFile,
                        docAnchors,
                        docImages);                
            }
            
            return result;
        } catch (Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            if (subDoc != null) subDoc.close();
            
            // close the writer
            if (docText != null) try { docText.close(); } catch (Exception ex) {/* ignore this */}
            
            // delete the file
            if (outputFile != null) try { outputFile.delete(); } catch (Exception ex)  {/* ignore this */}               
            
            throw new ParserException("Unexpected error while parsing tar resource. " + e.getMessage(),location); 
        }
    }
    
    public void reset() {
        // Nothing todo here at the moment
        super.reset();
    }
}

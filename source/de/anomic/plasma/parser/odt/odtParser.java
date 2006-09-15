//zipParser.java 
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

package de.anomic.plasma.parser.odt;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import de.anomic.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.catcode.odf.ODFMetaFileAnalyzer;
import com.catcode.odf.OpenDocumentMetadata;
import com.catcode.odf.OpenDocumentTextInputStream;

import de.anomic.http.httpc;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.serverFileUtils;
import de.anomic.server.logging.serverLog;

public class odtParser extends AbstractParser implements Parser {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Hashtable SUPPORTED_MIME_TYPES = new Hashtable();    
    static { 
        SUPPORTED_MIME_TYPES.put("application/vnd.oasis.opendocument.text","odt");
        SUPPORTED_MIME_TYPES.put("application/x-vnd.oasis.opendocument.text","odt");
    }     

    /**
     * a list of library names that are needed by this parser
     * @see Parser#getLibxDependences()
     */
    private static final String[] LIBX_DEPENDENCIES = new String[] {"odf_utils_05_11_10.jar"};        
    
    public odtParser() {        
        super(LIBX_DEPENDENCIES);
        parserName = "OASIS OpenDocument V2 Text Document Parser"; 
    }
    
    public Hashtable getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public plasmaParserDocument parse(URL location, String mimeType, String charset, File dest) throws ParserException, InterruptedException {
        
        try {          
            byte[] docContent     = null;
            String docDescription = null;
            String docKeywords    = null;
            String docShortTitle       = null;
            String docLongTitle     = null;
            
            // opening the file as zip file
            ZipFile zipFile= new ZipFile(dest);
            Enumeration zipEnum = zipFile.entries();
            
            // looping through all containing files
            while (zipEnum.hasMoreElements()) {
                // check for interruption
                checkInterruption();
                
                // getting the next zip file entry
                ZipEntry zipEntry= (ZipEntry) zipEnum.nextElement();
                String entryName = zipEntry.getName();
                
                // content.xml contains the document content in xml format
                if (entryName.equals("content.xml")) {
                    InputStream zipFileEntryStream = zipFile.getInputStream(zipEntry);
                    OpenDocumentTextInputStream odStream = new OpenDocumentTextInputStream(zipFileEntryStream);
                    docContent = serverFileUtils.read(odStream); 
                
                // meta.xml contains metadata about the document
                } else if (entryName.equals("meta.xml")) {
                    InputStream zipFileEntryStream = zipFile.getInputStream(zipEntry);
                    ODFMetaFileAnalyzer metaAnalyzer = new ODFMetaFileAnalyzer();
                    OpenDocumentMetadata metaData = metaAnalyzer.analyzeMetaData(zipFileEntryStream);
                    docDescription = metaData.getDescription();
                    docKeywords    = metaData.getKeyword();
                    docShortTitle  = metaData.getTitle();
                    docLongTitle   = metaData.getSubject();
                    
                    // if there is no title availabe we generate one
                    if (docLongTitle == null) {
                        if (docShortTitle != null) {
                            docLongTitle = docShortTitle;
                        } else if (docContent != null && docContent.length <= 80) {
                            docLongTitle = new String(docContent, "UTF-8");
                        } else {
                            byte[] title = new byte[80];
                            System.arraycopy(docContent, 0, title, 0, 80);
                            docLongTitle = new String(title, "UTF-8");
                        }
                        docLongTitle.
                        replaceAll("\r\n"," ").
                        replaceAll("\n"," ").
                        replaceAll("\r"," ").
                        replaceAll("\t"," ");
                    }
                }
            }
         
            return new plasmaParserDocument(
                    location,
                    mimeType,
                    docKeywords,
                    docShortTitle, 
                    docLongTitle,
                    null,
                    docDescription,
                    docContent,
                    null,
                    null);
        } catch (Exception e) {            
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            throw new ParserException("Unable to parse the odt content. " + e.getMessage());
        } catch (Error e) {
            throw new ParserException("Unable to parse the odt content. " + e.getMessage());
        }
    }
    
    public plasmaParserDocument parse(URL location, String mimeType, String charset, InputStream source) throws ParserException {
        File dest = null;
        try {
            // creating a tempfile
            dest = File.createTempFile("OpenDocument", ".odt");
            dest.deleteOnExit();
            
            // copying the stream into a file
            serverFileUtils.copy(source, dest);
            
            // parsing the content
            return parse(location, mimeType, charset, dest);
        } catch (Exception e) {
            throw new ParserException("Unable to parse the odt document. " + e.getMessage());
        } finally {
            if (dest != null) try { dest.delete(); } catch (Exception e){}
        }
    }
    
    public void reset() {
		// Nothing todo here at the moment
    	
    }
    
    public static void main(String[] args) {
        try {
            if (args.length != 1) return;
            
            // getting the content URL
            URL contentUrl = new URL(args[0]);
            
            // creating a new parser
            odtParser testParser = new odtParser();
            
            // setting the parser logger
            testParser.setLogger(new serverLog("PARSER.ODT"));
            
            // downloading the document content
            byte[] content = httpc.singleGET(contentUrl, contentUrl.getHost(), 10000, null, null, null);
            ByteArrayInputStream input = new ByteArrayInputStream(content);
            
            // parsing the document
            testParser.parse(contentUrl, "application/vnd.oasis.opendocument.text", null, input);            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

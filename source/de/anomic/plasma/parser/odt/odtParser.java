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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
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
import de.anomic.server.serverCharBuffer;
import de.anomic.server.serverFileUtils;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;

public class odtParser extends AbstractParser implements Parser {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Hashtable<String, String> SUPPORTED_MIME_TYPES = new Hashtable<String, String>();
    static { 
        SUPPORTED_MIME_TYPES.put("application/vnd.oasis.opendocument.text","odt");
        SUPPORTED_MIME_TYPES.put("application/x-vnd.oasis.opendocument.text","odt");
    }     

    /**
     * a list of library names that are needed by this parser
     * @see Parser#getLibxDependences()
     */
    private static final String[] LIBX_DEPENDENCIES = new String[] {"odf_utils_05_11_29.jar"};        
    
    public odtParser() {        
        super(LIBX_DEPENDENCIES);
        this.parserName = "OASIS OpenDocument V2 Text Document Parser"; 
    }
    
    public Hashtable<String, String> getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public plasmaParserDocument parse(yacyURL location, String mimeType, String charset, File dest) throws ParserException, InterruptedException {
        
        Writer writer = null;
        File writerFile = null;
        try {          
            String docDescription = null;
            String docKeywordStr  = null;
            String docShortTitle  = null;
            String docLongTitle   = null;
            String docAuthor      = null;
            
            // opening the file as zip file
            ZipFile zipFile= new ZipFile(dest);
            Enumeration<? extends ZipEntry> zipEnum = zipFile.entries();
            
            // looping through all containing files
            while (zipEnum.hasMoreElements()) {
                // check for interruption
                checkInterruption();
                
                // getting the next zip file entry
                ZipEntry zipEntry= (ZipEntry) zipEnum.nextElement();
                String entryName = zipEntry.getName();
                
                // content.xml contains the document content in xml format
                if (entryName.equals("content.xml")) {
                    long contentSize = zipEntry.getSize();
                    
                    // creating a writer for output
                    if ((contentSize == -1) || (contentSize > Parser.MAX_KEEP_IN_MEMORY_SIZE)) {
                        writerFile = File.createTempFile("odtParser",".tmp");
                        writer = new OutputStreamWriter(new FileOutputStream(writerFile),"UTF-8");
                    } else {
                        writer = new serverCharBuffer(); 
                    }                    
                    
                    // extract data
                    InputStream zipFileEntryStream = zipFile.getInputStream(zipEntry);
                    OpenDocumentTextInputStream odStream = new OpenDocumentTextInputStream(zipFileEntryStream);
                    serverFileUtils.copy(odStream, writer, "UTF-8");
                
                    // close readers and writers
                    odStream.close();
                    writer.close();
                    
                } else if (entryName.equals("meta.xml")) {
                    //  meta.xml contains metadata about the document
                    InputStream zipFileEntryStream = zipFile.getInputStream(zipEntry);
                    ODFMetaFileAnalyzer metaAnalyzer = new ODFMetaFileAnalyzer();
                    OpenDocumentMetadata metaData = metaAnalyzer.analyzeMetaData(zipFileEntryStream);
                    docDescription = metaData.getDescription();
                    docKeywordStr  = metaData.getKeyword();
                    docShortTitle  = metaData.getTitle();
                    docLongTitle   = metaData.getSubject();
                    docAuthor      = metaData.getCreator();
                }
            }
            
            // if there is no title availabe we generate one
            if (docLongTitle == null) {
                if (docShortTitle != null) {
                    docLongTitle = docShortTitle;
                } 
            }            
         
            // split the keywords
            String[] docKeywords = null;
            if (docKeywordStr != null) docKeywords = docKeywordStr.split(" |,");
            
            // create the parser document
            plasmaParserDocument theDoc = null;
            if (writer instanceof serverCharBuffer) {
                byte[] contentBytes = ((serverCharBuffer)writer).toString().getBytes("UTF-8");
                theDoc = new plasmaParserDocument(
                        location,
                        mimeType,
                        "UTF-8",
                        docKeywords,
                        docLongTitle,
                        docAuthor,
                        null,
                        docDescription,
                        contentBytes,
                        null,
                        null);
            } else {
                theDoc = new plasmaParserDocument(
                        location,
                        mimeType,
                        "UTF-8",
                        docKeywords,
                        docLongTitle,
                        docAuthor,
                        null,
                        docDescription,
                        writerFile,
                        null,
                        null);
            }
            return theDoc;
        } catch (Exception e) {            
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            // close the writer
            if (writer != null) try { writer.close(); } catch (Exception ex) {/* ignore this */}
            
            // delete the file
            if (writerFile != null) try { writerFile.delete(); } catch (Exception ex)  {/* ignore this */}            
            
            throw new ParserException("Unexpected error while parsing odt file. " + e.getMessage(),location); 
        }
    }
    
    public plasmaParserDocument parse(yacyURL location, String mimeType, String charset, InputStream source) throws ParserException, InterruptedException {
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
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            throw new ParserException("Unexpected error while parsing odt file. " + e.getMessage(),location); 
        } finally {
            if (dest != null) try { dest.delete(); } catch (Exception e){/* ignore this */}
        }
    }
    
    public void reset() {
        // Nothing todo here at the moment
        super.reset();
    }
    
    public static void main(String[] args) {
        try {
            if (args.length != 1) return;
            
            // getting the content URL
            yacyURL contentUrl = new yacyURL(args[0], null);
            
            // creating a new parser
            odtParser testParser = new odtParser();
            
            // setting the parser logger
            testParser.setLogger(new serverLog("PARSER.ODT"));
            
            // downloading the document content
            byte[] content = httpc.singleGET(contentUrl, contentUrl.getHost(), 10000, null, null, null, null);
            ByteArrayInputStream input = new ByteArrayInputStream(content);
            
            // parsing the document
            testParser.parse(contentUrl, "application/vnd.oasis.opendocument.text", null, input);            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

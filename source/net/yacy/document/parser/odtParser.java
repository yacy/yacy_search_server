//odtParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.document.parser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Idiom;
import net.yacy.document.ParserException;
import net.yacy.document.parser.xml.ODContentHandler;
import net.yacy.document.parser.xml.ODMetaHandler;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.io.CharBuffer;
import net.yacy.kelondro.util.FileUtils;



public class odtParser extends AbstractParser implements Idiom {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<String>();
    public static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>();
    static {
        SUPPORTED_EXTENSIONS.add("odt");
        SUPPORTED_EXTENSIONS.add("ods");
        SUPPORTED_EXTENSIONS.add("odp");
        SUPPORTED_EXTENSIONS.add("odg");
        SUPPORTED_EXTENSIONS.add("odc");
        SUPPORTED_EXTENSIONS.add("odf");
        SUPPORTED_EXTENSIONS.add("odb");
        SUPPORTED_EXTENSIONS.add("odi");
        SUPPORTED_EXTENSIONS.add("odm");
        SUPPORTED_EXTENSIONS.add("ott");
        SUPPORTED_EXTENSIONS.add("ots");
        SUPPORTED_EXTENSIONS.add("otp");
        SUPPORTED_EXTENSIONS.add("otg");
        SUPPORTED_EXTENSIONS.add("sxw"); // Star Office Writer file format
        SUPPORTED_EXTENSIONS.add("sxc"); // Star Office Calc file format
        SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.text");
        SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.spreadsheet");
        SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.presentation");
        SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.graphics");
        SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.chart");
        SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.formula");
        SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.database");
        SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.image");
        SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.text-master");
        SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.text-template");
        SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.spreadsheet-template");
        SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.presentation-template");
        SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.graphics-template");
        SUPPORTED_MIME_TYPES.add("application/x-vnd.oasis.opendocument.text");
        SUPPORTED_MIME_TYPES.add("application/OOo-calc");
        SUPPORTED_MIME_TYPES.add("application/OOo-writer");
    }

    public odtParser() {        
        super("OASIS OpenDocument V2 Text Document Parser"); 
    }
    
    public Set<String> supportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }
    
    @Override
    public Document parse(final DigestURI location, final String mimeType, final String charset, final File dest) throws ParserException, InterruptedException {
        
        Writer writer = null;
        File writerFile = null;
        try {          
            String docDescription = null;
            String docKeywordStr  = null;
            String docShortTitle  = null;
            String docLongTitle   = null;
            String docAuthor      = null;
            String docLanguage    = null;
            
            // opening the file as zip file
            final ZipFile zipFile= new ZipFile(dest);
            final Enumeration<? extends ZipEntry> zipEnum = zipFile.entries();
            final SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
            
            // looping through all containing files
            while (zipEnum.hasMoreElements()) {
                // check for interruption
                checkInterruption();
                
                // getting the next zip file entry
                final ZipEntry zipEntry= zipEnum.nextElement();
                final String entryName = zipEntry.getName();
                
                // content.xml contains the document content in xml format
                if (entryName.equals("content.xml")) {
                    final long contentSize = zipEntry.getSize();
                    
                    // creating a writer for output
                    if ((contentSize == -1) || (contentSize > Idiom.MAX_KEEP_IN_MEMORY_SIZE)) {
                        writerFile = File.createTempFile("odtParser",".prt");
                        writer = new OutputStreamWriter(new FileOutputStream(writerFile),"UTF-8");
                    } else {
                        writer = new CharBuffer(); 
                    }                    
                    
                    // extract data
                    final InputStream zipFileEntryStream = zipFile.getInputStream(zipEntry);
                    final SAXParser saxParser = saxParserFactory.newSAXParser();
                    saxParser.parse(zipFileEntryStream, new ODContentHandler(writer));
                
                    // close readers and writers
                    zipFileEntryStream.close();
                    writer.close();
                    
                } else if (entryName.equals("meta.xml")) {
                    //  meta.xml contains metadata about the document
                    final InputStream zipFileEntryStream = zipFile.getInputStream(zipEntry);
                    final SAXParser saxParser = saxParserFactory.newSAXParser();
                    final ODMetaHandler metaData = new ODMetaHandler();
                    saxParser.parse(zipFileEntryStream, metaData);
                    docDescription = metaData.getDescription();
                    docKeywordStr  = metaData.getKeyword();
                    docShortTitle  = metaData.getTitle();
                    docLongTitle   = metaData.getSubject();
                    docAuthor      = metaData.getCreator();
                    docLanguage    = metaData.getLanguage();
                }
            }
            
            // make the languages set
            Set<String> languages = new HashSet<String>(1);
            if (docLanguage != null) languages.add(docLanguage);
            
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
            Document theDoc = null;
            if (writer instanceof CharBuffer) {
                final byte[] contentBytes = ((CharBuffer)writer).toString().getBytes("UTF-8");
                theDoc = new Document(
                        location,
                        mimeType,
                        "UTF-8",
                        languages,
                        docKeywords,
                        docLongTitle,
                        docAuthor,
                        null,
                        docDescription,
                        contentBytes,
                        null,
                        null);
            } else {
                theDoc = new Document(
                        location,
                        mimeType,
                        "UTF-8",
                        languages,
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
        } catch (final Exception e) {            
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            // close the writer
            if (writer != null) try { writer.close(); } catch (final Exception ex) {/* ignore this */}
            
            // delete the file
            if (writerFile != null) FileUtils.deletedelete(writerFile);
            
            throw new ParserException("Unexpected error while parsing odt file. " + e.getMessage(),location); 
        }
    }
    
    public Document parse(final DigestURI location, final String mimeType, final String charset, final InputStream source) throws ParserException, InterruptedException {
        File dest = null;
        try {
            // creating a tempfile
            dest = File.createTempFile("OpenDocument", ".odt");
            dest.deleteOnExit();
            
            // copying the stream into a file
            FileUtils.copy(source, dest);
            
            // parsing the content
            return parse(location, mimeType, charset, dest);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            throw new ParserException("Unexpected error while parsing odt file. " + e.getMessage(),location); 
        } finally {
            if (dest != null) FileUtils.deletedelete(dest);
        }
    }
    
    @Override
    public void reset() {
        // Nothing todo here at the moment
        super.reset();
    }
}

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

package de.anomic.document.parser;

import java.io.ByteArrayInputStream;
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

import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.util.FileUtils;

import de.anomic.crawler.retrieval.HTTPLoader;
import de.anomic.document.AbstractParser;
import de.anomic.document.Idiom;
import de.anomic.document.ParserException;
import de.anomic.document.Document;

import de.anomic.document.parser.xml.ODContentHandler;
import de.anomic.document.parser.xml.ODMetaHandler;
import de.anomic.http.client.Client;
import de.anomic.http.metadata.HeaderFramework;
import de.anomic.http.metadata.RequestHeader;
import de.anomic.server.serverCharBuffer;

public class ooxmlParser extends AbstractParser implements Idiom {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<String>();
    public static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>();
    static {
        SUPPORTED_EXTENSIONS.add("docx");
        SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        SUPPORTED_EXTENSIONS.add("dotx");
        SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.wordprocessingml.template");
        SUPPORTED_EXTENSIONS.add("potx");
        SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.presentationml.template");
        SUPPORTED_EXTENSIONS.add("ppsx");
        SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.presentationml.slideshow");
        SUPPORTED_EXTENSIONS.add("pptx");
        SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        SUPPORTED_EXTENSIONS.add("xlsx");
        SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        SUPPORTED_EXTENSIONS.add("xltx");
        SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.spreadsheetml.template");
    }

    public ooxmlParser() {        
        super("Open Office XML Document Parser"); 
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
                if (entryName.equals("word/document.xml")
                	|| entryName.startsWith("ppt/slides/slide")
                	|| entryName.startsWith("xl/worksheets/sheet")) {
                    final long contentSize = zipEntry.getSize();
                    
                    // creating a writer for output
                    if ((contentSize == -1) || (contentSize > Idiom.MAX_KEEP_IN_MEMORY_SIZE)) {
                        writerFile = File.createTempFile("ooxmlParser",".prt");
                        writer = new OutputStreamWriter(new FileOutputStream(writerFile),"UTF-8");
                    } else {
                        writer = new serverCharBuffer(); 
                    }                    
                    
                    // extract data
                    final InputStream zipFileEntryStream = zipFile.getInputStream(zipEntry);
                    final SAXParser saxParser = saxParserFactory.newSAXParser();
                    saxParser.parse(zipFileEntryStream, new ODContentHandler(writer));
                
                    // close readers and writers
                    zipFileEntryStream.close();
                    writer.close();
                    
                } else if (entryName.equals("docProps/core.xml")) {
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
            if (docLanguage != null && docLanguage.length() == 0)
        	languages.add(docLanguage);
            
            // if there is no title availabe we generate one
            if (docLongTitle == null || docLongTitle.length() == 0) {
                if (docShortTitle != null) {
                    docLongTitle = docShortTitle;
                } 
            }            
         
            // split the keywords
            String[] docKeywords = null;
            if (docKeywordStr != null) docKeywords = docKeywordStr.split(" |,");
            
            // create the parser document
            Document theDoc = null;
            if (writer instanceof serverCharBuffer) {
                final byte[] contentBytes = ((serverCharBuffer)writer).toString().getBytes("UTF-8");
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
            e.printStackTrace();
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

    public static void main(final String[] args) {
        try {
            if (args.length != 1) return;
            
            // getting the content URL
            final DigestURI contentUrl = new DigestURI(args[0], null);
            
            // creating a new parser
            final odtParser testParser = new odtParser();
            
            // downloading the document content
            final RequestHeader reqHeader = new RequestHeader();
            reqHeader.put(HeaderFramework.USER_AGENT, HTTPLoader.crawlerUserAgent);
            final byte[] content = Client.wget(contentUrl.toString(), reqHeader, 10000);
            final ByteArrayInputStream input = new ByteArrayInputStream(content);
            
            // parsing the document
            testParser.parse(contentUrl, "application/vnd.oasis.opendocument.text", null, input);            
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
}

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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.parser.xml.ODContentHandler;
import net.yacy.document.parser.xml.ODMetaHandler;
import net.yacy.kelondro.io.CharBuffer;
import net.yacy.kelondro.util.FileUtils;

import org.xml.sax.SAXException;



public class ooxmlParser extends AbstractParser implements Parser {

    public ooxmlParser() {
        super("Open Office XML Document Parser");
        this.SUPPORTED_EXTENSIONS.add("docx");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        this.SUPPORTED_EXTENSIONS.add("dotx");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.wordprocessingml.template");
        this.SUPPORTED_EXTENSIONS.add("potx");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.presentationml.template");
        this.SUPPORTED_EXTENSIONS.add("ppsx");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.presentationml.slideshow");
        this.SUPPORTED_EXTENSIONS.add("pptx");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        this.SUPPORTED_EXTENSIONS.add("xlsx");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        this.SUPPORTED_EXTENSIONS.add("xltx");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.spreadsheetml.template");
    }

    private static final ThreadLocal<SAXParser> tlSax = new ThreadLocal<SAXParser>();
    private static SAXParser getParser() throws SAXException {
    	SAXParser parser = tlSax.get();
    	if (parser == null) {
    		try {
				parser = SAXParserFactory.newInstance().newSAXParser();
			} catch (final ParserConfigurationException e) {
				throw new SAXException(e.getMessage(), e);
			}
    		tlSax.set(parser);
    	}
    	return parser;
    }

    private Document[] parse(final AnchorURL location, final String mimeType, @SuppressWarnings("unused") final String charset, final File dest) throws Parser.Failure, InterruptedException {

        CharBuffer writer = null;
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

            // looping through all containing files
            while (zipEnum.hasMoreElements()) {

                // get next zip file entry
                final ZipEntry zipEntry= zipEnum.nextElement();
                final String entryName = zipEntry.getName();

                // content.xml contains the document content in xml format
                if (entryName.equals("word/document.xml")
                	|| entryName.startsWith("ppt/slides/slide")
                	|| entryName.startsWith("xl/worksheets/sheet")) {

                    // create a writer for output
                    writer = new CharBuffer(odtParser.MAX_DOCSIZE, (int) zipEntry.getSize());

                    // extract data
                    final InputStream zipFileEntryStream = zipFile.getInputStream(zipEntry);
                    try {
                        final SAXParser saxParser = getParser();
                        saxParser.parse(zipFileEntryStream, new ODContentHandler(writer));

                        // close readers and writers
                    } finally {
                        zipFileEntryStream.close();
                    }
   
                } else if (entryName.equals("docProps/core.xml")) {
                    //  meta.xml contains metadata about the document
                    final InputStream zipFileEntryStream = zipFile.getInputStream(zipEntry);
                    final SAXParser saxParser = getParser();
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
            final Set<String> languages = new HashSet<String>(1);
            if (docLanguage != null && docLanguage.isEmpty())
        	languages.add(docLanguage);

            // if there is no title availabe we generate one
            if ((docLongTitle == null || docLongTitle.isEmpty()) && (docShortTitle != null)) {
                    docLongTitle = docShortTitle;
            }

            // split the keywords
            String[] docKeywords = null;
            if (docKeywordStr != null) docKeywords = docKeywordStr.split(" |,");

            // create the parser document
            Document[] docs = null;
            final byte[] contentBytes = (writer == null) ? null : UTF8.getBytes(writer.toString());
            List<String> descriptions = new ArrayList<String>();
            if (docDescription != null && docDescription.length() > 0) descriptions.add(docDescription);
            docs = new Document[]{new Document(
                    location,
                    mimeType,
                    "UTF-8",
                    this,
                    languages,
                    docKeywords,
                    singleList(docLongTitle),
                    docAuthor,
                    "",
                    null,
                    descriptions,
                    0.0f, 0.0f,
                    contentBytes,
                    null,
                    null,
                    null,
                    false,
                    new Date())};
            return docs;
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof Parser.Failure) throw (Parser.Failure) e;

            // close the writer
            if (writer != null) try {
                writer.close();
            } catch (final Exception ex) {/* ignore this */}

            ConcurrentLog.logException(e);
            throw new Parser.Failure("Unexpected error while parsing odt file. " + e.getMessage(),location);
        }
    }

    @Override
    public Document[] parse(final AnchorURL location, final String mimeType, final String charset, final InputStream source) throws Parser.Failure, InterruptedException {
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
            if (e instanceof Parser.Failure) throw (Parser.Failure) e;

            throw new Parser.Failure("Unexpected error while parsing odt file. " + e.getMessage(),location);
        } finally {
            if (dest != null) FileUtils.deletedelete(dest);
        }
    }

}

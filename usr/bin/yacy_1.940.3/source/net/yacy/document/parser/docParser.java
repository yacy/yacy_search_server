//docParser.java
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.hwpf.HWPFDocumentCore;
import org.apache.poi.hwpf.OldWordFileFormatException;
import org.apache.poi.hwpf.extractor.Word6Extractor;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.CommonPattern;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;

public class docParser extends AbstractParser implements Parser {

    public docParser() {
        super("Word Document Parser");
        this.SUPPORTED_EXTENSIONS.add("doc");
        this.SUPPORTED_MIME_TYPES.add("application/msword");
        this.SUPPORTED_MIME_TYPES.add("application/doc");
        this.SUPPORTED_MIME_TYPES.add("appl/text");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.msword");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.ms-word");
        this.SUPPORTED_MIME_TYPES.add("application/winword");
        this.SUPPORTED_MIME_TYPES.add("application/word");
        this.SUPPORTED_MIME_TYPES.add("application/x-msw6");
        this.SUPPORTED_MIME_TYPES.add("application/x-msword");
    }

    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final VocabularyScraper scraper, 
            final int timezoneOffset,
            final InputStream source)
            throws Parser.Failure, InterruptedException {

        final WordExtractor extractor;
        POIFSFileSystem poifs = null;
        try {
            poifs = HWPFDocumentCore.verifyAndBuildPOIFS(source); // to be able to delegate to parseOldWordDoc w/o source.ioException
            extractor = new WordExtractor(poifs);
        } catch (final OldWordFileFormatException isOldWordDoc) {
            // if old version (Word6/Word95) delegate to old parser (as long as available in poi package)
            return parseOldWordDoc(location, mimeType, poifs);
        } catch (final Exception e) {
            throw new Parser.Failure("error in docParser, WordTextExtractorFactory: " + e.getMessage(), location);
        }

        final StringBuilder contents = new StringBuilder(80);
        try {
            contents.append(extractor.getText()); // extractor gets all text incl. headers/footers
        } catch (final Exception e) {
        	try {extractor.close();} catch (IOException e1) {}
            throw new Parser.Failure("error in docParser, getText: " + e.getMessage(), location);
        }
        String title = (contents.length() > 240) ? contents.substring(0,240) : contents.toString().trim();
        title = title.replaceAll("\r"," ").replaceAll("\n"," ").replaceAll("\t"," ").trim();
        if (title.length() > 80) title = title.substring(0, 80);
        int l = title.length();
        while (true) {
            title = title.replaceAll("  ", " ");
            if (title.length() == l) break;
            l = title.length();
        }
        // get keywords (for yacy as array)
        final String keywords = extractor.getSummaryInformation().getKeywords();
        final String[] keywlist;
        if (keywords != null && !keywords.isEmpty()) {
            keywlist = CommonPattern.COMMA.split(keywords);
        } else {
            keywlist = null;
        }

        final String subject = extractor.getSummaryInformation().getSubject();
        List<String> descriptions = new ArrayList<String>();
        if (subject != null && !subject.isEmpty()) descriptions.add(subject);

        Document[] docs;
        docs = new Document[]{new Document(
            location,
            mimeType,
            StandardCharsets.UTF_8.name(),
            this,
            null,
            keywlist,
            singleList(title),
            extractor.getSummaryInformation().getAuthor(), // constuctor can handle null
            extractor.getDocSummaryInformation().getCompany(), // publisher
            null,
            descriptions,
            0.0d, 0.0d,
            contents.toString(),
            null,
            null,
            null,
            false,
            extractor.getSummaryInformation().getLastSaveDateTime() // maybe null
            )};
        try {extractor.close();} catch (IOException e1) {}
        return docs;
    }

    /**
     * Parse old Word6/95 document
     * @param location
     * @param mimeType
     * @param poifs
     * @return an array containing one Document
     * @throws net.yacy.document.Parser.Failure
     */
    @SuppressWarnings("resource")
    public Document[] parseOldWordDoc(
            final DigestURL location,
            final String mimeType,
            final POIFSFileSystem poifs) throws Failure {
        
        final Word6Extractor extractor;

        try {
            extractor = new Word6Extractor(poifs);
        } catch (final Exception e) {
            throw new Parser.Failure("error in docParser, WordTextExtractorFactory: " + e.getMessage(), location);
        }

        final StringBuilder contents = new StringBuilder(80);
        try {
            contents.append(extractor.getText());
        } catch (final Exception e) {
        	try {extractor.close();} catch (IOException e1) {}
            throw new Parser.Failure("error in docParser, getText: " + e.getMessage(), location);
        }
        String title = (contents.length() > 240) ? contents.substring(0,240) : contents.toString().trim();
        title = title.replaceAll("\r"," ").replaceAll("\n"," ").replaceAll("\t"," ").trim();
        if (title.length() > 80) title = title.substring(0, 80);
        int l = title.length();
        while (true) {
            title = title.replaceAll("  ", " ");
            if (title.length() == l) break;
            l = title.length();
        }
        // get keywords (for yacy as array)
        final String keywords = extractor.getSummaryInformation().getKeywords();
        final String[] keywlist;
        if (keywords != null && !keywords.isEmpty()) {
            keywlist = CommonPattern.COMMA.split(keywords);
        } else {
            keywlist = null;
        }

        final String subject = extractor.getSummaryInformation().getSubject();
        List<String> descriptions = new ArrayList<String>();
        if (subject != null && !subject.isEmpty()) descriptions.add(subject);

        Document[] docs;
        docs = new Document[]{new Document(
            location,
            mimeType,
            StandardCharsets.UTF_8.name(),
            this,
            null,
            keywlist,
            singleList(title),
            extractor.getSummaryInformation().getAuthor(), // constuctor can handle null
            extractor.getDocSummaryInformation().getCompany(), // publisher
            null,
            descriptions,
            0.0d, 0.0d,
            contents.toString(),
            null,
            null,
            null,
            false,
            extractor.getSummaryInformation().getLastSaveDateTime() // maybe null
            )};
        try {extractor.close();} catch (IOException e1) {}
        return docs;
    }
}

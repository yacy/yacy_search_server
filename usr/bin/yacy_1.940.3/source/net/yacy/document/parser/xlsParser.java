//xlsParser.java
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Tim Riemann
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

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.util.CommonPattern;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;

import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.hssf.extractor.ExcelExtractor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;


public class xlsParser extends AbstractParser implements Parser {

    public xlsParser(){
        super("Microsoft Excel Parser");
        this.SUPPORTED_EXTENSIONS.add("xls");
        this.SUPPORTED_EXTENSIONS.add("xla");
        this.SUPPORTED_MIME_TYPES.add("application/msexcel");
        this.SUPPORTED_MIME_TYPES.add("application/excel");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.ms-excel");
        this.SUPPORTED_MIME_TYPES.add("application/x-excel");
        this.SUPPORTED_MIME_TYPES.add("application/x-msexcel");
        this.SUPPORTED_MIME_TYPES.add("application/x-ms-excel");
        this.SUPPORTED_MIME_TYPES.add("application/x-dos_ms_excel");
        this.SUPPORTED_MIME_TYPES.add("application/xls");
    }

    /*
     * parses the source documents and returns a plasmaParserDocument containing
     * all extracted information about the parsed document
     */
    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final VocabularyScraper scraper, 
            final int timezoneOffset,
            final InputStream source) throws Parser.Failure,
            InterruptedException {

        try {
            //create a new org.apache.poi.poifs.filesystem.Filesystem
            final POIFSFileSystem poifs = new POIFSFileSystem(source);
            ExcelExtractor exceldoc = new ExcelExtractor(poifs);
            exceldoc.setIncludeSheetNames(false); // exclude sheet names from getText() as also empty sheet names are returned

            SummaryInformation sumInfo = exceldoc.getSummaryInformation();
            String title = sumInfo.getTitle();
            if (title == null || title.isEmpty()) title = MultiProtocolURL.unescape(location.getFileName());

            final String subject = sumInfo.getSubject();
            List<String> descriptions = new ArrayList<String>();
            if (subject != null && !subject.isEmpty()) descriptions.add(subject);

            // get keywords (for yacy as array)
            final String keywords = sumInfo.getKeywords();
            final String[] keywlist;
            if (keywords != null && !keywords.isEmpty()) {
               keywlist = CommonPattern.COMMA.split(keywords);
            } else keywlist = null;

            Document[] retdocs = new Document[]{new Document(
                location,
                mimeType,
                StandardCharsets.UTF_8.name(),
                this,
                null,
                keywlist,
                singleList(title),
                sumInfo.getAuthor(),
                exceldoc.getDocSummaryInformation().getCompany(),
                null,
                descriptions,
                0.0d, 0.0d,
                exceldoc.getText(),
                null,
                null,
                null,
                false,
                sumInfo.getLastSaveDateTime())};

            exceldoc.close();
            return retdocs;

        } catch (IOException ex1) {
            throw new Parser.Failure(ex1.getMessage(), location);
        }
    }
}

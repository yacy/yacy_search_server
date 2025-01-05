//pptParser.java
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.poi.hpsf.DocumentSummaryInformation;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.sl.extractor.SlideShowExtractor;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;

public class pptParser extends AbstractParser implements Parser {

    public pptParser(){
        super("Microsoft Powerpoint Parser");
        this.SUPPORTED_EXTENSIONS.add("ppt");
        this.SUPPORTED_EXTENSIONS.add("pps");
        this.SUPPORTED_MIME_TYPES.add("application/mspowerpoint");
        this.SUPPORTED_MIME_TYPES.add("application/powerpoint");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.ms-powerpoint");
        this.SUPPORTED_MIME_TYPES.add("application/ms-powerpoint");
        this.SUPPORTED_MIME_TYPES.add("application/mspowerpnt");
        this.SUPPORTED_MIME_TYPES.add("application/vnd-mspowerpoint");
        this.SUPPORTED_MIME_TYPES.add("application/x-powerpoint");
        this.SUPPORTED_MIME_TYPES.add("application/x-m");
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
            final InputStream source) throws Parser.Failure, InterruptedException {
        try {
            final BufferedInputStream bis = new BufferedInputStream(source);
            final HSLFSlideShow slideShow = new HSLFSlideShow(bis);
            final SummaryInformation summaryInfo = slideShow.getSummaryInformation();
            final DocumentSummaryInformation docSummaryInfo = slideShow.getDocumentSummaryInformation();
            @SuppressWarnings({ "rawtypes", "unchecked" })
            final SlideShowExtractor<?,?> pptExtractor = new SlideShowExtractor(slideShow);
            final String contents = pptExtractor.getText().trim();

            String title = summaryInfo == null ? "" : summaryInfo.getTitle();
            if (title.length() == 0) {
                title = contents.replaceAll("\r"," ").replaceAll("\n"," ").replaceAll("\t"," ").trim();
                if (title.length() > 80) title = title.substring(0, 80);
                int l = title.length();
                while (true) {
                    title = title.replaceAll("  ", " ");
                    if (title.length() == l) break;
                    l = title.length();
                }
            }

            final String author = summaryInfo == null ? "" : summaryInfo.getAuthor();
            final String keywords = summaryInfo == null ? "" : summaryInfo.getKeywords();
            final String subject = summaryInfo == null ? "" : summaryInfo.getSubject();
            //final String comments = summaryInfo == null ? "" : summaryInfo.getComments();
            final Date lastSaveDate = summaryInfo == null ? null : summaryInfo.getLastSaveDateTime();
            //final String category = docSummaryInfo == null ? "" : docSummaryInfo.getCategory();
            final String company = docSummaryInfo == null ? "" : docSummaryInfo.getCompany();
            //final String manager = docSummaryInfo == null ? "" : docSummaryInfo.getManager();

            final String[] keywlist;
            if (keywords != null && !keywords.isEmpty()) {
               keywlist = CommonPattern.COMMA.split(keywords);
            } else keywlist = null;

            final List<String> descriptions = new ArrayList<>();
            if (subject != null && !subject.isEmpty()) descriptions.add(subject);

            final Document[] docs = new Document[]{new Document(
                location,
                mimeType,
                StandardCharsets.UTF_8.name(),
                this,
                null,
                keywlist,
                singleList(title),
                author, // may be null
                company,
                null,
                descriptions,
                0.0d, 0.0d,
                contents,
                null,
                null,
                null,
                false,
                lastSaveDate // may be null
                )};
            try {pptExtractor.close();} catch (final IOException e1) {}
            return docs;
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;

            /*
             * an unexpected error occurred, log it and throw a Parser.Failure
             */
            ConcurrentLog.logException(e);
            final String errorMsg = "Unable to parse the ppt document '" + location + "':" + e.getMessage();
            AbstractParser.log.severe(errorMsg);
            throw new Parser.Failure(errorMsg, location);
        }
    }

}

//pdfParser.java
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;
import net.yacy.kelondro.io.CharBuffer;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;


public class pdfParser extends AbstractParser implements Parser {

    public pdfParser() {
        super("Acrobat Portable Document Parser");
        this.SUPPORTED_EXTENSIONS.add("pdf");
        this.SUPPORTED_MIME_TYPES.add("application/pdf");
        this.SUPPORTED_MIME_TYPES.add("application/x-pdf");
        this.SUPPORTED_MIME_TYPES.add("application/acrobat");
        this.SUPPORTED_MIME_TYPES.add("applications/vnd.pdf");
        this.SUPPORTED_MIME_TYPES.add("text/pdf");
        this.SUPPORTED_MIME_TYPES.add("text/x-pdf");
    }

    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final VocabularyScraper scraper,
            final int timezoneOffset,
            final InputStream source) throws Parser.Failure, InterruptedException {

        // check memory for parser
        if (!MemoryControl.request(200 * 1024 * 1024, false))
            throw new Parser.Failure("Not enough Memory available for pdf parser: " + MemoryControl.available(), location);

        // create a pdf parser
        PDDocument pdfDoc;
        try {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY); // the pdfparser is a big pain
            final RandomAccessRead readBuffer = new RandomAccessReadBuffer(source);
            pdfDoc = Loader.loadPDF(readBuffer);
        } catch (final IOException e) {
            throw new Parser.Failure(e.getMessage(), location);
        } finally {
            Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
        }

        if (pdfDoc.isEncrypted()) {
            final AccessPermission perm = pdfDoc.getCurrentAccessPermission();
            if (perm == null || !perm.canExtractContent()) {
                try {pdfDoc.close();} catch (final IOException ee) {}
                throw new Parser.Failure("Document is encrypted and cannot be decrypted", location);
            }
        }

        // extracting some metadata
        PDDocumentInformation info = pdfDoc.getDocumentInformation();
        String docTitle = null, docSubject = null, docAuthor = null, docPublisher = null, docKeywordStr = null;
        Date docDate = new Date();
        if (info != null) {
            docTitle = info.getTitle();
            docSubject = info.getSubject();
            docAuthor = info.getAuthor();
            docPublisher = info.getProducer();
            if (docPublisher == null || docPublisher.isEmpty()) docPublisher = info.getCreator();
            docKeywordStr = info.getKeywords();
            if (info.getModificationDate() != null) docDate = info.getModificationDate().getTime();
            // unused:
            // info.getTrapped());
        }
        info = null;

        if (docTitle == null || docTitle.isEmpty()) {
            docTitle = MultiProtocolURL.unescape(location.getFileName());
        }
        if (docTitle == null) {
            docTitle = docSubject;
        }
        String[] docKeywords = null;
        if (docKeywordStr != null) {
            docKeywords = docKeywordStr.split(" |,");
        }

        Document[] result = null;
        try {
            // get the links
        	final List<Collection<AnchorURL>> pdflinks = extractPdfLinks(pdfDoc);

            // collect the whole text at once
            final CharBuffer writer = new CharBuffer(odtParser.MAX_DOCSIZE);
            byte[] contentBytes = new byte[0];
            final PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(Integer.MAX_VALUE);
            writer.append(stripper.getText(pdfDoc));
            contentBytes = writer.getBytes(); // remember text in case of interrupting thread
            writer.close(); // free writer resources

            final Collection<AnchorURL> pdflinksCombined = new HashSet<>();
            for (final Collection<AnchorURL> pdflinksx: pdflinks) if (pdflinksx != null) pdflinksCombined.addAll(pdflinksx);
            result = new Document[]{new Document(
                    location,
                    mimeType,
                    StandardCharsets.UTF_8.name(),
                    this,
                    null,
                    docKeywords,
                    singleList(docTitle),
                    docAuthor,
                    docPublisher,
                    null,
                    null,
                    0.0d, 0.0d,
                    contentBytes,
                    pdflinksCombined,
                    null,
                    null,
                    false,
                    docDate)};
        } catch (final Throwable e) {
            //throw new Parser.Failure(e.getMessage(), location);
        } finally {
            try {pdfDoc.close();} catch (final Throwable e) {}
        }

        // clear cached resources in pdfbox.
        pdfDoc = null;
        clearPdfBoxCaches();

        return result;
    }

    /**
     * extract clickable links from pdf
     * @param pdf the document to parse
     * @return all detected links
     */
    private List<Collection<AnchorURL>> extractPdfLinks(final PDDocument pdf) {
        final List<Collection<AnchorURL>> linkCollections = new ArrayList<>(pdf.getNumberOfPages());
        for (final PDPage page : pdf.getPages()) {
            final Collection<AnchorURL> pdflinks = new ArrayList<>();
            try {
                final List<PDAnnotation> annotations = page.getAnnotations();
                if (annotations != null) {
                    for (final PDAnnotation pdfannotation : annotations) {
                        if (pdfannotation instanceof PDAnnotationLink) {
                            final PDAction link = ((PDAnnotationLink)pdfannotation).getAction();
                            if (link != null && link instanceof PDActionURI) {
                                final PDActionURI pdflinkuri = (PDActionURI) link;
                                final String uristr = pdflinkuri.getURI();
                                final AnchorURL url = new AnchorURL(uristr);
                                pdflinks.add(url);
                            }
                        }
                    }
                }
            } catch (final IOException ex) {}
            linkCollections.add(pdflinks);
        }
        return linkCollections;
    }

    /**
     * Clean up cache resources allocated by PDFBox that would otherwise not be released.
     */
    @SuppressWarnings("deprecation")
    public static void clearPdfBoxCaches() {
		/*
		 * Prior to pdfbox 2.0.0 font cache occupied > 80MB RAM for a single pdf and
		 * then stayed forever (detected in YaCy with pdfbox version 1.2.1). The
		 * situation is now from far better, but one (unnecessary?) cache structure in
		 * the COSName class still needs to be explicitely cleared.
		 */

		// History of related issues :
    	// http://markmail.org/thread/quk5odee4hbsauhu
		// https://issues.apache.org/jira/browse/PDFBOX-313
		// https://issues.apache.org/jira/browse/PDFBOX-351
		// https://issues.apache.org/jira/browse/PDFBOX-441
    	// https://issues.apache.org/jira/browse/PDFBOX-2200
    	// https://issues.apache.org/jira/browse/PDFBOX-2149

        COSName.clearResources();

		/*
		 * Prior to PDFBox 2.0.0, clearResources() function had to be called on the
		 * org.apache.pdfbox.pdmodel.font.PDFont class and its children. After version
		 * 2.0.0, there is no more such a function in PDFont class as font cache is
		 * handled differently and hopefully more properly.
		 */
    }

    /**
     * test
     * @param args
     */
    public static void main(final String[] args) {
        if (args.length > 0 && args[0].length() > 0) {
            // file
            final File pdfFile = new File(args[0]);
            if(pdfFile.canRead()) {

                System.out.println(pdfFile.getAbsolutePath());
                final long startTime = System.currentTimeMillis();

                // parse
                final AbstractParser parser = new pdfParser();
                Document document = null;
                FileInputStream inStream = null;
                try {
                	inStream = new FileInputStream(pdfFile);
                    document = Document.mergeDocuments(null, "application/pdf", parser.parse(null, "application/pdf", null, new VocabularyScraper(), 0, inStream));
                } catch (final Parser.Failure e) {
                    System.err.println("Cannot parse file " + pdfFile.getAbsolutePath());
                    ConcurrentLog.logException(e);
                } catch (final InterruptedException e) {
                    System.err.println("Interrupted while parsing!");
                    ConcurrentLog.logException(e);
                } catch (final NoClassDefFoundError e) {
                    System.err.println("class not found: " + e.getMessage());
                } catch (final FileNotFoundException e) {
                    ConcurrentLog.logException(e);
                } finally {
                	if(inStream != null) {
                		try {
                			inStream.close();
                		} catch(final IOException e) {
                			System.err.println("Could not close input stream on file " + pdfFile);
                		}
                	}
                }

                // statistics
                System.out.println("\ttime elapsed: " + (System.currentTimeMillis() - startTime) + " ms");

                // output
                if (document == null) {
                    System.out.println("\t!!!Parsing without result!!!");
                } else {
                    System.out.println("\tParsed text with " + document.getTextLength() + " chars of text and " + document.getAnchors().size() + " anchors");
                    final InputStream textStream = document.getTextStream();
                    try {
                        // write file
                        FileUtils.copy(textStream, new File("parsedPdf.txt"));
                    } catch (final IOException e) {
                        System.err.println("error saving parsed document");
                        ConcurrentLog.logException(e);
                    } finally {
                    	try {
                        	if(textStream != null) {
                        		/* textStream can be a FileInputStream : we must close it to ensure releasing system resource */
                        		textStream.close();
                        	}
						} catch (final IOException e) {
							ConcurrentLog.warn("PDFPARSER", "Could not close text input stream");
						}
                    }
                }
            } else {
                System.err.println("Cannot read file "+ pdfFile.getAbsolutePath());
            }
        } else {
            System.out.println("Please give a filename as first argument.");
        }
    }

}

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

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.kelondro.io.CharBuffer;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.exceptions.CryptographyException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.BadSecurityHandlerException;
import org.apache.pdfbox.pdmodel.encryption.StandardDecryptionMaterial;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.util.PDFTextStripper;


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
    public Document[] parse(final MultiProtocolURI location, final String mimeType, final String charset, final InputStream source) throws Parser.Failure, InterruptedException {

        // check memory for parser
        if (!MemoryControl.request(200 * 1024 * 1024, true))
            throw new Parser.Failure("Not enough Memory available for pdf parser: " + MemoryControl.available(), location);

        // create a pdf parser
        final PDDocument pdfDoc;
        //final PDFParser pdfParser;
        try {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
            pdfDoc = PDDocument.load(source);
            //pdfParser = new PDFParser(source);
            //pdfParser.parse();
            //pdfDoc = pdfParser.getPDDocument();
        } catch (final IOException e) {
            throw new Parser.Failure(e.getMessage(), location);
        } finally {
            Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
        }

        if (pdfDoc.isEncrypted()) {
            try {
                pdfDoc.openProtection(new StandardDecryptionMaterial(""));
            } catch (final BadSecurityHandlerException e) {
                try {pdfDoc.close();} catch (final IOException ee) {}
                throw new Parser.Failure("Document is encrypted (1): " + e.getMessage(), location);
            } catch (final IOException e) {
                try {pdfDoc.close();} catch (final IOException ee) {}
                throw new Parser.Failure("Document is encrypted (2): " + e.getMessage(), location);
            } catch (final CryptographyException e) {
                try {pdfDoc.close();} catch (final IOException ee) {}
                throw new Parser.Failure("Document is encrypted (3): " + e.getMessage(), location);
            }
            final AccessPermission perm = pdfDoc.getCurrentAccessPermission();
            if (perm == null || !perm.canExtractContent())
                throw new Parser.Failure("Document is encrypted and cannot be decrypted", location);
        }

        // extracting some metadata
        final PDDocumentInformation info = pdfDoc.getDocumentInformation();
        String docTitle = null, docSubject = null, docAuthor = null, docPublisher = null, docKeywordStr = null;
        if (info != null) {
            docTitle = info.getTitle();
            docSubject = info.getSubject();
            docAuthor = info.getAuthor();
            docPublisher = info.getProducer();
            if (docPublisher == null || docPublisher.length() == 0) docPublisher = info.getCreator();
            docKeywordStr = info.getKeywords();
            // unused:
            // info.getTrapped());
            // info.getCreationDate());
            // info.getModificationDate();
        }

        if (docTitle == null || docTitle.length() == 0) {
            docTitle = MultiProtocolURI.unescape(location.getFileName());
        }
        final CharBuffer writer = new CharBuffer(odtParser.MAX_DOCSIZE);
        byte[] contentBytes = new byte[0];
        try {
            // create a writer for output
            final PDFTextStripper  stripper = new PDFTextStripper();

            stripper.setEndPage(3); // get first 3 pages (always)
            writer.append(stripper.getText(pdfDoc));
            contentBytes = writer.getBytes(); // remember text in case of interrupting thread

            stripper.setStartPage(4); // continue with page 4 (terminated, resulting in no text)
            stripper.setEndPage(Integer.MAX_VALUE); // set to default
            // we start the pdf parsing in a separate thread to ensure that it can be terminated
            final Thread t = new Thread() {
                @Override
                public void run() {
                    try {
                        writer.append(stripper.getText(pdfDoc));
                    } catch (final Throwable e) {}
                }
            };
            t.start();
            t.join(3000);
            if (t.isAlive()) t.interrupt();
            pdfDoc.close();
            contentBytes = writer.getBytes(); // get final text before closing writer
            writer.close();
        } catch (final IOException e) {
            // close the writer
            if (writer != null) try { writer.close(); } catch (final Exception ex) {}
            try {pdfDoc.close();} catch (final IOException ee) {}
            //throw new Parser.Failure(e.getMessage(), location);
        } catch (final NullPointerException e) {
            // this exception appeared after the insertion of the jempbox-1.5.0.jar library
            Log.logException(e);
            // close the writer
            if (writer != null) try { writer.close(); } catch (final Exception ex) {}
            try {pdfDoc.close();} catch (final IOException ee) {}
            //throw new Parser.Failure(e.getMessage(), location);
        } finally {
            try {pdfDoc.close();} catch (final IOException e) {}
        }

        String[] docKeywords = null;
        if (docKeywordStr != null) {
            docKeywords = docKeywordStr.split(" |,");
        }
        if (docTitle == null) {
            docTitle = docSubject;
        }


        // clear resources in pdfbox. they say that is resolved but it's not. see:
        // https://issues.apache.org/jira/browse/PDFBOX-313
        // https://issues.apache.org/jira/browse/PDFBOX-351
        // https://issues.apache.org/jira/browse/PDFBOX-441
        // the pdfbox still generates enormeous number of object allocations and don't delete these
        // the following Object are statically stored and never flushed:
        // COSFloat, COSArray, COSInteger, COSObjectKey, COSObject, COSDictionary,
        // COSStream, COSString, COSName, COSDocument, COSInteger[], COSNull
        // the great number of these objects can easily be seen in Java Visual VM
        // we try to get this shit out of the memory here by forced clear calls, hope the best the rubbish gets out.
        COSName.clearResources();
        PDFont.clearResources();
        return new Document[]{new Document(
                location,
                mimeType,
                "UTF-8",
                this,
                null,
                docKeywords,
                docTitle,
                docAuthor,
                docPublisher,
                null,
                null,
                0.0f, 0.0f,
                contentBytes,
                null,
                null,
                null,
                false)};
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
                try {
                    document = Document.mergeDocuments(null, "application/pdf", parser.parse(null, "application/pdf", null, new FileInputStream(pdfFile)));
                } catch (final Parser.Failure e) {
                    System.err.println("Cannot parse file " + pdfFile.getAbsolutePath());
                    Log.logException(e);
                } catch (final InterruptedException e) {
                    System.err.println("Interrupted while parsing!");
                    Log.logException(e);
                } catch (final NoClassDefFoundError e) {
                    System.err.println("class not found: " + e.getMessage());
                } catch (final FileNotFoundException e) {
                    Log.logException(e);
                }

                // statistics
                System.out.println("\ttime elapsed: " + (System.currentTimeMillis() - startTime) + " ms");

                // output
                if (document == null) {
                    System.out.println("\t!!!Parsing without result!!!");
                } else {
                    System.out.println("\tParsed text with " + document.getTextLength() + " chars of text and " + document.getAnchors().size() + " anchors");
                    try {
                        // write file
                        FileUtils.copy(document.getText(), new File("parsedPdf.txt"));
                    } catch (final IOException e) {
                        System.err.println("error saving parsed document");
                        Log.logException(e);
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

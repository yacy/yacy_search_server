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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardDecryptionMaterial;
import org.apache.pdfbox.util.PDFTextStripper;

import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Idiom;
import net.yacy.document.ParserException;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.io.CharBuffer;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;


public class pdfParser extends AbstractParser implements Idiom {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<String>();
    public static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>();
    static {
        SUPPORTED_EXTENSIONS.add("pdf");
        SUPPORTED_MIME_TYPES.add("application/pdf");
        SUPPORTED_MIME_TYPES.add("application/x-pdf");
        SUPPORTED_MIME_TYPES.add("application/acrobat");
        SUPPORTED_MIME_TYPES.add("applications/vnd.pdf");
        SUPPORTED_MIME_TYPES.add("text/pdf");
        SUPPORTED_MIME_TYPES.add("text/x-pdf");
    }
    
    public pdfParser() {        
        super("Acrobat Portable Document Parser"); 
    }
    
    public Set<String> supportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }
    
    public Document parse(final DigestURI location, final String mimeType, final String charset, final InputStream source) throws ParserException, InterruptedException {
        
        PDDocument theDocument = null;
        Writer writer = null;
        File writerFile = null;
        try {       
            // reducing thread priority
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);                        
            
            // deactivating the logging for jMimeMagic
//            Logger theLogger = Logger.getLogger("org.pdfbox");
//            theLogger.setLevel(Level.INFO);            
            
            String docTitle = null, docSubject = null, docAuthor = null, docKeywordStr = null;
            
            // check for interruption
            checkInterruption();
            
            // creating a pdf parser
            final PDFParser parser = new PDFParser(source);
            parser.parse();
                        
            // check for interruption
            checkInterruption();
            
            // creating a text stripper
            final PDFTextStripper stripper = new PDFTextStripper();
            theDocument = parser.getPDDocument();
            
            if (theDocument.isEncrypted()) {
                theDocument.openProtection(new StandardDecryptionMaterial(""));
                final AccessPermission perm = theDocument.getCurrentAccessPermission();
                if (perm == null || !perm.canExtractContent())
                    throw new ParserException("Document is encrypted", location);
            }
            
            // extracting some metadata
            final PDDocumentInformation theDocInfo = theDocument.getDocumentInformation();            
            if (theDocInfo != null) {
                docTitle = theDocInfo.getTitle();
                docSubject = theDocInfo.getSubject();
                docAuthor = theDocInfo.getAuthor();
                docKeywordStr = theDocInfo.getKeywords();
            }            
            
            // creating a writer for output
            if ((this.contentLength == -1) || (this.contentLength > Idiom.MAX_KEEP_IN_MEMORY_SIZE)) {
                writerFile = File.createTempFile("pdfParser",".prt");
                writer = new OutputStreamWriter(new FileOutputStream(writerFile),"UTF-8");
            } else {
                writer = new CharBuffer(); 
            }
            try {
                stripper.writeText(theDocument, writer ); // may throw a NPE
            } catch (Exception e) {
                Log.logWarning("pdfParser", e.getMessage());
            }
            theDocument.close(); theDocument = null;            
            writer.close();

            
            String[] docKeywords = null;
            if (docKeywordStr != null) docKeywords = docKeywordStr.split(" |,");
            
            Document theDoc = null;
            
            if (writer instanceof CharBuffer) {
                final byte[] contentBytes = ((CharBuffer)writer).toString().getBytes("UTF-8");
                theDoc = new Document(
                        location,
                        mimeType,
                        "UTF-8",
                        null,
                        docKeywords,
                        (docTitle == null) ? docSubject : docTitle,
                        docAuthor,
                        null,
                        null,
                        contentBytes,
                        null,
                        null);
            } else {
                theDoc = new Document(
                        location,
                        mimeType,
                        "UTF-8",
                        null,
                        docKeywords,
                        (docTitle == null) ? docSubject : docTitle,
                        docAuthor,
                        null,
                        null,
                        writerFile,
                        null,
                        null);                
            }
            
            return theDoc;
        }
        catch (final Exception e) {       
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            // close the writer
            if (writer != null) try { writer.close(); } catch (final Exception ex) {/* ignore this */}
            
            // delete the file
            if (writerFile != null) FileUtils.deletedelete(writerFile);
            
            e.printStackTrace();
            throw new ParserException("Unexpected error while parsing pdf file. " + e.getMessage(),location); 
        } finally {
            if (theDocument != null) try { theDocument.close(); } catch (final Exception e) {/* ignore this */}
            if (writer != null)      try { writer.close(); }      catch (final Exception e) {/* ignore this */}
            Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
        }
    }
    
    @Override
    public void reset() {
        // Nothing todo here at the moment
        super.reset();
    }
    
    /**
     * test
     * @param args
     */
    public static void main(final String[] args) {
        if(args.length > 0 && args[0].length() > 0) {
            // file
            final File pdfFile = new File(args[0]);
            if(pdfFile.canRead()) {
                
                System.out.println(pdfFile.getAbsolutePath());
                final long startTime = System.currentTimeMillis();
                
                // parse
                final AbstractParser parser = new pdfParser();
                Document document = null;
                try {
                    document = parser.parse(null, "application/pdf", null, pdfFile);
                    
                } catch (final ParserException e) {
                    System.err.println("Cannot parse file "+ pdfFile.getAbsolutePath());
                    e.printStackTrace();
                } catch (final InterruptedException e) {
                    System.err.println("Interrupted while parsing!");
                    e.printStackTrace();
                } catch (final NoClassDefFoundError e) {
                    System.err.println("class not found: " + e.getMessage());
                }
                
                // statistics
                System.out.println("\ttime elapsed: " + (System.currentTimeMillis() - startTime) + " ms");
                
                // output
                if(document == null) {
                    System.out.println("\t!!!Parsing without result!!!");
                } else {
                    System.out.println("\tParsed text with " + document.getTextLength() + " chars of text and " + document.getAnchors().size() + " anchors");
                    try {
                        // write file
                        FileUtils.copy(document.getText(), new File("parsedPdf.txt"));
                    } catch (final IOException e) {
                        System.err.println("error saving parsed document");
                        e.printStackTrace();
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

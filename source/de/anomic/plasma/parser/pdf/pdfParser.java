//pdfParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//last major change: 24.04.2005
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

package de.anomic.plasma.parser.pdf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Hashtable;

import org.pdfbox.pdfparser.PDFParser;
import org.pdfbox.pdmodel.PDDocument;
import org.pdfbox.pdmodel.PDDocumentInformation;
import org.pdfbox.pdmodel.encryption.AccessPermission;
import org.pdfbox.pdmodel.encryption.StandardDecryptionMaterial;
import org.pdfbox.util.PDFTextStripper;

import de.anomic.crawler.ErrorURL;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.serverCharBuffer;
import de.anomic.server.serverFileUtils;
import de.anomic.yacy.yacyURL;

public class pdfParser extends AbstractParser implements Parser {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Hashtable<String, String> SUPPORTED_MIME_TYPES = new Hashtable<String, String>();  
    static { SUPPORTED_MIME_TYPES.put("application/pdf","pdf"); }     
    
    /**
     * a list of library names that are needed by this parser
     * @see Parser#getLibxDependences()
     */
    private static final String[] LIBX_DEPENDENCIES = new String[] {
        "PDFBox-0.7.3.jar", "FontBox-0.1.0-dev.jar", "bcprov-jdk14-139.jar"
    };        
    
    public pdfParser() {        
        super(LIBX_DEPENDENCIES);
        this.parserName = "Acrobat Portable Document Parser"; 
    }
    
    public Hashtable<String, String> getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public plasmaParserDocument parse(final yacyURL location, final String mimeType, final String charset, final InputStream source) throws ParserException, InterruptedException {
        
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
                    throw new ParserException("Document is encrypted",location,ErrorURL.DENIED_DOCUMENT_ENCRYPTED);
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
            if ((this.contentLength == -1) || (this.contentLength > Parser.MAX_KEEP_IN_MEMORY_SIZE)) {
                writerFile = File.createTempFile("pdfParser",".tmp");
                writer = new OutputStreamWriter(new FileOutputStream(writerFile),"UTF-8");
            } else {
                writer = new serverCharBuffer(); 
            }

            stripper.writeText(theDocument, writer );
            theDocument.close(); theDocument = null;            
            writer.close();

            
            String[] docKeywords = null;
            if (docKeywordStr != null) docKeywords = docKeywordStr.split(" |,");
            
            plasmaParserDocument theDoc = null;
            
            if (writer instanceof serverCharBuffer) {
                final byte[] contentBytes = ((serverCharBuffer)writer).toString().getBytes("UTF-8");
                theDoc = new plasmaParserDocument(
                        location,
                        mimeType,
                        "UTF-8",
                        docKeywords,
                        (docTitle == null) ? docSubject : docTitle,
                        docAuthor,
                        null,
                        null,
                        contentBytes,
                        null,
                        null);
            } else {
                theDoc = new plasmaParserDocument(
                        location,
                        mimeType,
                        "UTF-8",
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
            if (writerFile != null) try { writerFile.delete(); } catch (final Exception ex)  {/* ignore this */}
            
            e.printStackTrace();
            throw new ParserException("Unexpected error while parsing pdf file. " + e.getMessage(),location); 
        } finally {
            if (theDocument != null) try { theDocument.close(); } catch (final Exception e) {/* ignore this */}
            if (writer != null)      try { writer.close(); }      catch (final Exception e) {/* ignore this */}
            Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
        }
    }
    
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
                plasmaParserDocument document = null;
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
                        serverFileUtils.copy(document.getText(), new File("parsedPdf.txt"));
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

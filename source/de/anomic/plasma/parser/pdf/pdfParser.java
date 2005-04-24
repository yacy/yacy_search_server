package de.anomic.plasma.parser.pdf;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;


import org.pdfbox.pdfparser.PDFParser;
import org.pdfbox.pdmodel.PDDocument;
import org.pdfbox.pdmodel.PDDocumentInformation;
import org.pdfbox.util.PDFTextStripper;

import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;

public class pdfParser implements Parser
{

    /**
     * a list of mime types that are supported by this parser class
     */
    public static final HashSet<String> SUPPORTED_MIME_TYPES = new HashSet<String>(Arrays.asList(new String[] {
        new String("application/pdf")
    }));    
    
    public pdfParser() {
        super();
    }
    
    public HashSet getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public plasmaParserDocument parse(URL location, String mimeType, File sourceFile) throws ParserException {
        BufferedInputStream contentInputStream = null;
        try {
            contentInputStream = new BufferedInputStream(new FileInputStream(sourceFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return this.parse(location, mimeType, contentInputStream);
    }

    public plasmaParserDocument parse(URL location, String mimeType, byte[] source) throws ParserException {
        ByteArrayInputStream contentInputStream = new ByteArrayInputStream(source);
        return this.parse(location,mimeType,contentInputStream);
    }    
    
    public plasmaParserDocument parse(URL location, String mimeType, InputStream source) throws ParserException {
        
        try {       
            String docTitle = null, docSubject = null, docAuthor = null, docKeyWords = null;
            
            PDFParser parser = new PDFParser(source);
            parser.parse();
            
            PDFTextStripper stripper = new PDFTextStripper();
            PDDocument theDocument = parser.getPDDocument();
                              
            PDDocumentInformation theDocInfo = theDocument.getDocumentInformation();
            
            if (theDocInfo != null)
            {
                docTitle = theDocInfo.getTitle();
                docSubject = theDocInfo.getSubject();
                docAuthor = theDocInfo.getAuthor();
                docKeyWords = theDocInfo.getKeywords();
            }
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter( out );            
            stripper.writeText(theDocument, writer );
            
            writer.close();
            theDocument.close();
            
            byte[] contents = out.toByteArray();
			
            /*
             *         public document(URL location, String mimeType,
                            String keywords, String shortTitle, String longTitle,
                            String[] sections, String abstrct,
                            byte[] text, Map anchors, Map images) {
             * 
             */            
            plasmaParserDocument theDoc = new plasmaParserDocument(
                    location,
                    mimeType,
                    docKeyWords,
                    docSubject,
                    docTitle,
                    null,
                    null,
                    contents,
                    null,
                    null);
            
            return theDoc;
        }
        catch (Exception e) {            
            throw new ParserException("Unable to parse the pdf content. " + e.getMessage());
        }        
    }
    
    public void reset() {
    	// TODO Auto-generated method stub
    	
    }

}

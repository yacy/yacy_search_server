package net.yacy.document.importer;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ResumptionTokenReader extends DefaultHandler {

    // class variables
    private final StringBuilder buffer;
    private boolean parsingValue;
    private ResumptionToken token;
    private SAXParser saxParser;
    private InputStream stream;
    private Attributes atts;

    public ResumptionTokenReader(final InputStream stream) throws IOException {
        this.buffer = new StringBuilder();
        this.parsingValue = false;
        this.token = null;
        this.stream = stream;
        this.atts = null;
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            this.saxParser = factory.newSAXParser();
            this.saxParser.parse(this.stream, this);
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        } finally {
            try {
                this.stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public ResumptionToken getToken() {
        return this.token;
    }
    
    /*
     <resumptionToken expirationDate="2009-10-31T22:52:14Z"
     completeListSize="226"
     cursor="0">688</resumptionToken>
     */

    public void run() {
        
    }

    public void startElement(final String uri, final String name, final String tag, final Attributes atts) throws SAXException {
        if ("resumptionToken".equals(tag)) {
            this.parsingValue = true;
            this.atts = atts;
        }
    }

    public void endElement(final String uri, final String name, final String tag) {
        if (tag == null) return;
        if ("resumptionToken".equals(tag)) {
            this.token = new ResumptionToken(
                    atts.getValue("expirationDate"),
                    Integer.parseInt(atts.getValue("completeListSize")),
                    Integer.parseInt(atts.getValue("cursor")),
                    Integer.parseInt(buffer.toString().trim()));
            this.buffer.setLength(0);
            this.parsingValue = false;
        }
    }

    public void characters(final char ch[], final int start, final int length) {
        if (parsingValue) {
            buffer.append(ch, start, length);
        }
    }

}

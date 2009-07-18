package de.anomic.document.parser.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ODMetaHandler extends DefaultHandler {
	private StringBuilder buffer = new StringBuilder();
	
	private String docCreator = null;
	private String docLanguage = null;
	private String docKeyword = null;
	private String docSubject = null;
	private String docTitle = null;
	private String docDescription = null;
	
	public ODMetaHandler() {
	}
	
	@Override
	public void characters(final char ch[], final int start, final int length) {
	    buffer.append(ch, start, length);
	}
	
	@Override
	public void startElement(final String uri, final String name, final String tag, final Attributes atts) throws SAXException {
	    buffer.delete(0, buffer.length());
	}

	@Override
	public void endElement(final String uri, final String name, final String tag) {
	    if ("dc:creator".equals(tag)) {
		this.docCreator = buffer.toString();
	    } else if ("dc:language".equals(tag)) {
		this.docLanguage  = buffer.toString();
	    } else if ("meta:keyword".equals(tag)) {
		this.docKeyword  = buffer.toString();
	    } else if ("dc:subject".equals(tag)) {
		this.docSubject  = buffer.toString();
	    } else if ("dc:title".equals(tag)) {
		this.docTitle  = buffer.toString();
	    } else if ("dc:description".equals(tag)) {
		this.docDescription  = buffer.toString();
	    }
	}

	public String getCreator() {
	    return docCreator;
	}

	public String getLanguage() {
	    return docLanguage;
	}
	public String getKeyword() {
	    return docKeyword;
	}
	public String getSubject() {
	    return docSubject;
	}
	public String getTitle() {
	    return docTitle;
	}
	public String getDescription() {
	    return docDescription;
	}
}


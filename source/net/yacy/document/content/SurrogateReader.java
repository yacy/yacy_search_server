// SurrogateReader.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 15.04.2009 on http://yacy.net
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.document.content;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;


import net.yacy.cora.util.ConcurrentLog;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;


public class SurrogateReader extends DefaultHandler implements Runnable {

    // definition of the surrogate main element
    public final static String SURROGATES_MAIN_ELEMENT_NAME =
        "surrogates";
    public final static String SURROGATES_MAIN_ELEMENT_OPEN =
        "<" + SURROGATES_MAIN_ELEMENT_NAME +
        " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"" +
        " xmlns:yacy=\"http://yacy.net/\"" +
        " xmlns:geo=\"http://www.w3.org/2003/01/geo/wgs84_pos#\">";
    public final static String SURROGATES_MAIN_ELEMENT_CLOSE =
        "</" + SURROGATES_MAIN_ELEMENT_NAME + ">";

    // class variables
    private final StringBuilder buffer;
    private boolean parsingValue;
    private DCEntry surrogate;
    private String elementName;
    private final BlockingQueue<DCEntry> surrogates;
    private SAXParser saxParser;
    private final InputSource inputSource;
    private final InputStream inputStream;

    private static final ThreadLocal<SAXParser> tlSax = new ThreadLocal<SAXParser>();
    private static SAXParser getParser() throws SAXException {
    	SAXParser parser = tlSax.get();
    	if (parser == null) {
    		try {
				parser = SAXParserFactory.newInstance().newSAXParser();
			} catch (final ParserConfigurationException e) {
				throw new SAXException(e.getMessage(), e);
			}
    		tlSax.set(parser);
    	}
    	return parser;
    }
    
    public SurrogateReader(final InputStream stream, int queueSize) throws IOException {
        this.buffer = new StringBuilder(300);
        this.parsingValue = false;
        this.surrogate = null;
        this.elementName = null;
        this.surrogates = new ArrayBlockingQueue<DCEntry>(queueSize);
        
        Reader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        this.inputSource = new InputSource(reader);
        this.inputSource.setEncoding("UTF-8");
        this.inputStream = stream;
        
        try {
            this.saxParser = getParser();
        } catch (final SAXException e) {
            ConcurrentLog.logException(e);
            throw new IOException(e.getMessage());
        }
    }
    
    @Override
    public void run() {
        try {
            this.saxParser.parse(this.inputSource, this);
        } catch (final SAXParseException e) {
            ConcurrentLog.logException(e);
        } catch (final SAXException e) {
            ConcurrentLog.logException(e);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } finally {
        	try {
				this.surrogates.put(DCEntry.poison);
			} catch (final InterruptedException e1) {
			    ConcurrentLog.logException(e1);
			}
			try {
        		this.inputStream.close();
			} catch (final IOException e) {
			    ConcurrentLog.logException(e);
			}
        }
    }
    
    @Override
    public void startElement(final String uri, final String name, String tag, final Attributes atts) throws SAXException {
        if (tag == null) return;
        tag = tag.toLowerCase();
        if ("record".equals(tag) || "document".equals(tag)) {
            this.surrogate = new DCEntry();
        } else if ("element".equals(tag)) {
            this.elementName = atts.getValue("name");
        } else if ("value".equals(tag)) {
            this.buffer.setLength(0);
            this.parsingValue = true;
        } else if (tag.startsWith("dc:") || tag.startsWith("geo:") || tag.startsWith("md:")) {
            // parse dublin core attribute
            this.elementName = tag;
            this.parsingValue = true;
        }
    }

    @Override
    public void endElement(final String uri, final String name, String tag) {
        if (tag == null) return;
        tag = tag.toLowerCase();
        if ("record".equals(tag) || "document".equals(tag)) {
            //System.out.println("A Title: " + this.surrogate.title());
            try {
                this.surrogates.put(this.surrogate);
            } catch (final InterruptedException e) {
                ConcurrentLog.logException(e);
            } finally {
                //System.out.println("B Title: " + this.surrogate.title());
                this.surrogate = null;
                this.buffer.setLength(0);
                this.parsingValue = false;
            }
        } else if ("element".equals(tag)) {
            this.buffer.setLength(0);
            this.parsingValue = false;
        } else if ("value".equals(tag)) {
            //System.out.println("BUFFER-SIZE=" + buffer.length());
            final String value = buffer.toString().trim();
            if (this.elementName != null) {
                this.surrogate.getMap().put(this.elementName, new String[]{value});
            }
            this.buffer.setLength(0);
            this.parsingValue = false;
        } else if (tag.startsWith("dc:") || tag.startsWith("geo:") || tag.startsWith("md:")) {
            final String value = buffer.toString().trim();
            if (this.elementName != null && tag.equals(this.elementName)) {
                Map<String,String[]> map = this.surrogate.getMap();
                String[] oldcontent = map.get(this.elementName);
                if (oldcontent == null || oldcontent.length == 0) {
                    map.put(this.elementName, new String[]{value});
                } else {
                    String[] newcontent = new String[oldcontent.length + 1];
                    System.arraycopy(oldcontent, 0, newcontent, 0, oldcontent.length);
                    newcontent[oldcontent.length] = value;
                    map.put(this.elementName, newcontent);
                }
            }
            this.buffer.setLength(0);
            this.parsingValue = false;
        }
    }

    @Override
    public void characters(final char ch[], final int start, final int length) {
        if (parsingValue) {
            buffer.append(ch, start, length);
        }
    }

    public DCEntry take() {
        try {
            return this.surrogates.take();
        } catch (final InterruptedException e) {
            ConcurrentLog.logException(e);
            return null;
        }
    }
    
    public static void main(String[] args) {
        File f = new File(args[0]);
        SurrogateReader sr;
        try {
            InputStream is = new BufferedInputStream(new FileInputStream(f));
            if (f.getName().endsWith(".gz")) is = new GZIPInputStream(is);
            sr = new SurrogateReader(is, 1);

            Thread t = new Thread(sr, "Surrogate-Reader " + f.getAbsolutePath());
            t.start();
            DCEntry s;
            while ((s = sr.take()) != DCEntry.poison) {
                System.out.println("Title: " + s.getTitle());
                System.out.println("Date: " + s.getDate());
                System.out.println("Creator: " + s.getCreator());
                System.out.println("Publisher: " + s.getPublisher());
                System.out.println("URL: " + s.getIdentifier(true));
                System.out.println("Language: " + s.getLanguage());
                System.out.println("Body: " + s.getDescriptions().toString());
            }
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
    }
}

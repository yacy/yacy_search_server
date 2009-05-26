// SurrogateReader.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 15.04.2009 on http://yacy.net
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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

package de.anomic.content.file;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import de.anomic.content.DCEntry;

public class SurrogateReader extends DefaultHandler implements Runnable {

	public static final DCEntry poison = new DCEntry();
	
    // class variables
    private final StringBuilder buffer;
    private boolean parsingValue;
    private DCEntry surrogate;
    private String elementName;
    private BlockingQueue<DCEntry> surrogates;
    private SAXParser saxParser;
    private InputStream stream;
    
    public SurrogateReader(final InputStream stream, int queueSize) throws IOException {
        this.buffer = new StringBuilder();
        this.parsingValue = false;
        this.surrogate = null;
        this.elementName = null;
        this.surrogates = new ArrayBlockingQueue<DCEntry>(queueSize);
        this.stream = stream;
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            this.saxParser = factory.newSAXParser();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        } catch (SAXException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        }
    }
    
    public void run() {
        try {
            this.saxParser.parse(this.stream, this);
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
        	try {
				this.surrogates.put(poison);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			try {
        		this.stream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
    }
    
    public void startElement(final String uri, final String name, final String tag, final Attributes atts) throws SAXException {
        if ("record".equals(tag) || "document".equals(tag)) {
            this.surrogate = new DCEntry();
        } else if ("element".equals(tag)) {
            this.elementName = atts.getValue("name");
        } else if ("value".equals(tag)) {
            this.buffer.setLength(0);
            this.parsingValue = true;
        } else if (tag.startsWith("dc:")) {
            // parse dublin core attribute
            this.elementName = tag;
            this.parsingValue = true;
        }
    }

    public void endElement(final String uri, final String name, final String tag) {
        if (tag == null) return;
        if ("record".equals(tag) || "document".equals(tag)) {
            //System.out.println("A Title: " + this.surrogate.title());
            try {
                this.surrogates.put(this.surrogate);
            } catch (InterruptedException e) {
                e.printStackTrace();
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
                this.surrogate.put(this.elementName, value);
            }
            this.buffer.setLength(0);
            this.parsingValue = false;
        } else if (tag.startsWith("dc:")) {
            final String value = buffer.toString().trim();
            if (this.elementName != null) {
                this.surrogate.put(this.elementName, value);
            }
            this.buffer.setLength(0);
            this.parsingValue = false;
        }
    }

    public void characters(final char ch[], final int start, final int length) {
        if (parsingValue) {
            buffer.append(ch, start, length);
        }
    }

    public DCEntry take() {
        try {
            return this.surrogates.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public static void main(String[] args) {
        File f = new File(args[0]);
        SurrogateReader sr;
        try {
            sr = new SurrogateReader(new BufferedInputStream(new FileInputStream(f)), 1);

            Thread t = new Thread(sr, "Surrogate-Reader " + f.getAbsolutePath());
            t.start();
            DCEntry s;
            System.out.println("1");
            while ((s = sr.take()) != SurrogateReader.poison) {
                System.out.println("Title: " + s.title());
                System.out.println("Date: " + s.date());
                System.out.println("URL: " + s.url());
                System.out.println("Language: " + s.language());
                System.out.println("Body: " + s.body());
                System.out.println("Categories: " + s.categories());
            }
            System.out.println("2");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;


import net.yacy.kelondro.logging.Log;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;


public class SurrogateReader extends DefaultHandler implements Runnable {

    // class variables
    private final StringBuilder buffer;
    private boolean parsingValue;
    private DCEntry surrogate;
    private String elementName;
    private final BlockingQueue<DCEntry> surrogates;
    private SAXParser saxParser;
    private final InputStream stream;
    
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
            Log.logException(e);
            throw new IOException(e.getMessage());
        } catch (SAXException e) {
            Log.logException(e);
            throw new IOException(e.getMessage());
        }
    }
    
    public void run() {
        try {
            this.saxParser.parse(this.stream, this);
        } catch (SAXParseException e) {
            Log.logException(e);
        } catch (SAXException e) {
            Log.logException(e);
        } catch (IOException e) {
            Log.logException(e);
        } finally {
        	try {
				this.surrogates.put(DCEntry.poison);
			} catch (InterruptedException e1) {
			    Log.logException(e1);
			}
			try {
        		this.stream.close();
			} catch (IOException e) {
			    Log.logException(e);
			}
        }
    }
    
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
        } else if (tag.startsWith("dc:")) {
            // parse dublin core attribute
            this.elementName = tag;
            this.parsingValue = true;
        }
    }

    public void endElement(final String uri, final String name, String tag) {
        if (tag == null) return;
        tag = tag.toLowerCase();
        if ("record".equals(tag) || "document".equals(tag)) {
            //System.out.println("A Title: " + this.surrogate.title());
            try {
                this.surrogates.put(this.surrogate);
            } catch (InterruptedException e) {
                Log.logException(e);
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
            if (this.elementName != null && tag.equals(this.elementName)) {
                value.replaceAll(";", ",");
                String oldcontent = this.surrogate.get(this.elementName);
                if (oldcontent == null) {
                    this.surrogate.put(this.elementName, value);
                } else {
                    this.surrogate.put(this.elementName, oldcontent + ";" + value);
                }
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
            Log.logException(e);
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
                System.out.println("Body: " + s.getDescription());
            }
        } catch (IOException e) {
            Log.logException(e);
        }
    }
}

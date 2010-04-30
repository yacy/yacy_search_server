/**
 *  OAIListFriendsLoader
 *  Copyright 2010 by Michael Peter Christen
 *  First released 29.04.2010 at http://yacy.net
 *  
 *  This is a part of YaCy, a peer-to-peer based web search engine
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file COPYING.LESSER.
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document.importer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.repository.LoaderDispatcher;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.retrieval.Response;

public class OAIListFriendsLoader {

    private static final long serialVersionUID = -8705115274655024604L;
    
    //private static String url10 = "http://roar.eprints.org/cgi/roar_search/advanced/export_roar_ROAR::ListFriends.xml?screen=ROAR%3A%3AFacetSearch&_action_export=1&output=ROAR%3A%3AListFriends&exp=1|1|-recordcount%2F-date|archive|-|-|eprint_status%3Aeprint_status%3AALL%3AEQ%3Aarchive|metadata_visibility%3Ametadata_visibility%3AALL%3AEX%3Ashow";
    private static String url10 = "http://roar.eprints.org/cgi/roar_search/advanced/export_roar_ROAR::ListFriends.xml?_action_export=1&output=ROAR%3A%3AListFriends";
    private static File cache10 = new File("DATA/DICTIONARIES/harvesting/export_roar_ROAR_ListFriends.xml");
    private static String url20 = "http://www.openarchives.org/Register/ListFriends";
    private static File cache20 = new File("DATA/DICTIONARIES/harvesting/ListFriends.xml");
    
    public static void init(LoaderDispatcher loader) {
        loader.loadIfNotExistBackground(url10, cache10);
        loader.loadIfNotExistBackground(url20, cache20);
    }
    
    public static Map<String, String> load(LoaderDispatcher loader) {
        Map<String, String> map10;
        try {
            map10 = load(null, null, new File("DATA/DICTIONARIES/harvesting/export_roar_ROAR_ListFriends.xml"));
        } catch (IOException e) {
            map10 = new TreeMap<String, String>();
        }
        
        Map<String, String> map20;
        try {
            map20 = load(null, null, new File("DATA/DICTIONARIES/harvesting/ListFriends.xml"));
        } catch (IOException e) {
            map20 = new TreeMap<String, String>();
        }
        
        map10.putAll(map20);
        return map10;
    }
    
    /**
     * load a OAI ListFriends file from the net or from a cache location
     * If the given file does exist, the OAI ListFriends File is loaded and parsed.
     * The resulting map is a mapping from OAI-PMH start url to a loaction description
     * @param loader a LoaderDispatcher that loads the file if targetFile does not exist
     * @param source the source URL for the OAI ListFriends file
     * @param targetFile the file where the loaded content is stored if it does not exist, the source othervise
     * @return a Map from OAI-PMH source to source description (which is usually also a URL)
     * @throws IOException
     */
    private static Map<String, String> load(LoaderDispatcher loader, DigestURI source, File targetFile) throws IOException {
        
        byte[] b;
        if (targetFile.exists()) {
            // load file
            b = FileUtils.read(targetFile);
        } else {
            // load from the net
            Response response = loader.load(source, false, true, CrawlProfile.CACHE_STRATEGY_NOCACHE);
            b = response.getContent();
            FileUtils.copy(b, targetFile);
        }
               
        return new Parser(b).map;
    }
    
    
    // get a resumption token using a SAX xml parser from am input stream
    private static class Parser extends DefaultHandler {

        // class variables
        private final StringBuilder buffer;
        private boolean parsingValue;
        private SAXParser saxParser;
        private InputStream stream;
        private Attributes atts;
        private int recordCounter;
        private TreeMap<String, String> map;

        public Parser(final byte[] b) throws IOException {
            this.map = new TreeMap<String, String>();
            this.recordCounter = 0;
            this.buffer = new StringBuilder();
            this.parsingValue = false;
            this.atts = null;
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            this.stream = new ByteArrayInputStream(b);
            try {
                this.saxParser = factory.newSAXParser();
                this.saxParser.parse(this.stream, this);
            } catch (SAXException e) {
                Log.logException(e);
                Log.logWarning("OAIListFriendsLoader.Parser", "OAIListFriends was not parsed:\n" + new String(b));
            } catch (IOException e) {
                Log.logException(e);
                Log.logWarning("OAIListFriendsLoader.Parser", "OAIListFriends was not parsed:\n" + new String(b));
            } catch (ParserConfigurationException e) {
                Log.logException(e);
                Log.logWarning("OAIListFriendsLoader.Parser", "OAIListFriends was not parsed:\n" + new String(b));
                throw new IOException(e.getMessage());
            } finally {
                try {
                    this.stream.close();
                } catch (IOException e) {
                    Log.logException(e);
                }
            }
        }
        
        /*
         <?xml version="1.0" encoding="UTF-8"?>
         <BaseURLs>
         <baseURL id="http://roar.eprints.org/id/eprint/102">http://research.nla.gov.au/oai</baseURL>
         <baseURL id="http://roar.eprints.org/id/eprint/174">http://oai.bibsys.no/repository</baseURL>
         <baseURL id="http://roar.eprints.org/id/eprint/1064">http://oai.repec.openlib.org/</baseURL>
         </BaseURLs>
         */
        
        public void startElement(final String uri, final String name, final String tag, final Attributes atts) throws SAXException {
            if ("baseURL".equals(tag)) {
                recordCounter++;
                this.parsingValue = true;
                this.atts = atts;
            }
        }

        public void endElement(final String uri, final String name, final String tag) {
            if (tag == null) return;
            if ("baseURL".equals(tag)) {
                this.map.put(buffer.toString(), this.atts.getValue("id"));
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
    
    public static void main(String[] args) {
        try {
            Map<String, String> map1 = load(null, null, new File("DATA/DICTIONARIES/harvesting/export_roar_ROAR_ListFriends.xml"));
            int count1 = map1.size();
            
            Map<String, String> map2 = load(null, null, new File("DATA/DICTIONARIES/harvesting/ListFriends.xml"));
            int count2 = map2.size();
            
            map1.putAll(map2);
            System.out.println("count1 = " + count1 + ", count2 = " + count2 + ", all = " + map1.size());
            
            for (Map.Entry<String, String> entry: map1.entrySet()) System.out.println(entry.getKey());            
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }

}

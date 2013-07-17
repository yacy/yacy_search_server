/**
 *  YaCySearchClient
 *  an interface for Adaptive Replacement Caches
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 20.09.2010 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.yacy.cora.util.CommonPattern;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * most simple rss reader application for YaCy search result retrieval
 * this is an example application that you can use to integrate YaCy search results in your own java applications
 */
public class YaCySearchClient {

    /*
     * YaCy Search Results are produced in Opensearch format which is basically RSS.
     * The YaCy Search Result API Client is therefore implemented as a simple RSS reader.
     */
    private final String host, query;
    private final int port;
    private int offset;

    public YaCySearchClient(final String host, final int port, final String query) {
        this.host = host;
        this.port = port;
        this.offset = -10;
        this.query = query;
    }

    public SearchResult next() throws IOException {
        this.offset += 10; // you may call this again and get the next results
        return new SearchResult();
    }

    public class SearchResult extends ArrayList<RSSEntry> {
        private static final long serialVersionUID = 1337L;
        public SearchResult() throws IOException {
            URL url;
            Document doc;
            String u = new StringBuilder(120).append("http://")
                    .append(YaCySearchClient.this.host)
                    .append(":")
                    .append(YaCySearchClient.this.port)
                    .append("/yacysearch.rss?verify=false&startRecord=")
                    .append(YaCySearchClient.this.offset)
                    .append("&maximumRecords=10&resource=local&query=")
                    .append(CommonPattern.SPACE.matcher(YaCySearchClient.this.query).replaceAll("+")).toString();
            try { url = new URL(u); } catch (final MalformedURLException e) { throw new IOException (e); }
            try { doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(url.openStream()); }
            catch (final ParserConfigurationException e) { throw new IOException (e); }
            catch (final SAXException e) { throw new IOException (e); }
            final NodeList nodes = doc.getElementsByTagName("item");
            for (int i = 0; i < nodes.getLength(); i++)
                this.add(new RSSEntry((Element) nodes.item(i)));
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(this.size() * 80 + 1);
            for (RSSEntry entry: this) sb.append(entry.toString());
            return sb.toString();
        }
    }

    public static class RSSEntry {
        String title, link, snippet;
        public RSSEntry(Element element) {
            this.title = val(element, "title", "");
            this.link  = val(element, "link", "");
            this.snippet = val(element, "description", "");
        }
        private static String val(Element parent, String label, String dflt) {
            Element e = (Element) parent.getElementsByTagName(label).item(0);
            Node child = e.getFirstChild();
            return (child instanceof CharacterData) ?
                    ((CharacterData) child).getData() : dflt;
        }

        @Override
        public String toString() {
            return new StringBuilder(80).append("Title      : ")
                    .append(this.title)
                    .append("\nLink       : ")
                    .append(this.link)
                    .append("\nDescription: ")
                    .append(this.snippet)
                    .append("\n").toString();
        }
    }

    /**
     * Call the main method with one argument, the query string
     * search results are then simply printed out.
     * Multiple search requests can be submitted by adding more call arguments.
     * Use this method as stub for an integration in your own programs
     */
    public static void main(String[] args) {
        for (String query: args) try {
            long t = System.currentTimeMillis();
            YaCySearchClient search = new YaCySearchClient("localhost", 8090, query);
            System.out.println("Search result for '" + query + "':");
            System.out.print(search.next().toString()); // get 10 results; you may repeat this for next 10
            System.out.println("Search Time: " + (System.currentTimeMillis() - t) + " milliseconds\n");
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}

/**
 *  Peers
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 21.09.2012 at http://yacy.net
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

package net.yacy.cora.federate.yacy.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.yacy.cora.federate.yacy.Peer;
import net.yacy.cora.federate.yacy.Peers;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.http.HTTPClient;

/**
 * discover all peers in the network when only one peer is known.
 * this works only for a limited number of peers, not more than some thousands
 */
public class Network {
    
    /**
     * get the list of peers from one peer
     * @param address
     * @return a network as list of peers
     * @throws IOException
     */
    public static Peers getNetwork(final String address) throws IOException {
        Peers peers = new Peers();
        final HTTPClient httpclient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent);
            final byte[] content = httpclient.GETbytes("http://" + address  + "/Network.xml?page=1&maxCount=1000&ip=", null, null, false);
            ByteArrayInputStream bais = new ByteArrayInputStream(content);
            Document doc = null;
            try {
                doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(bais);
            } catch (final Throwable e) {
                throw new IOException(e.getMessage());
            }
            bais.close();
            doc.getDocumentElement().normalize();
            NodeList objects = doc.getElementsByTagName("peer");
     
            for (int i = 0; i < objects.getLength(); i++) {
               Node object = objects.item(i);
               if (object.getNodeType() == Node.ELEMENT_NODE) {
                  Element element = (Element) object;
                  Peer peer = new Peer();
                  for (Peer.Schema attr: Peer.Schema.values()) {
                      peer.put(attr, getAttr(attr.name(), element));
                  }
                  peers.add(peer);
                  //log.info(peer.toString());
               }
            }
        return peers;
    }
    
    private static String getAttr(String attr, Element eElement) {
        NodeList nl0 = eElement.getElementsByTagName(attr);
        if (nl0 == null) return "";
        Node n0 = nl0.item(0);
        if (n0 == null) return "";
        NodeList nl1 = n0.getChildNodes();
        if (nl1 == null) return "";
        Node n1 = nl1.item(0);
        if (n1 == null) return "";
        return n1.getNodeValue();
    }
    
    public static void main(String[] args) {
        //getNetwork("search.yacy.net");
        try {getNetwork("sokrates.homeunix.net:9090");} catch (final IOException e1) {}
        try {HTTPClient.closeConnectionManager();} catch (final InterruptedException e) {}
    }
}

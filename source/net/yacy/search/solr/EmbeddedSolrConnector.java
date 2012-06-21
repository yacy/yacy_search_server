/**
 *  EmbeddedSolrConnector
 *  Copyright 2012 by Michael Peter Christen
 *  First released 21.06.2012 at http://yacy.net
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


package net.yacy.search.solr;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import net.yacy.cora.services.federated.solr.AbstractSolrConnector;
import net.yacy.cora.services.federated.solr.SolrConnector;

import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.CoreContainer;
import org.xml.sax.SAXException;

public class EmbeddedSolrConnector extends AbstractSolrConnector implements SolrConnector {

    private final CoreContainer core;

    public EmbeddedSolrConnector(File storagePath,  File configFile) throws IOException {
        super();
        try {
            this.core = new CoreContainer(storagePath.getAbsolutePath(), configFile);
        } catch (ParserConfigurationException e) {
            throw new IOException(e.getMessage(), e);
        } catch (SAXException e) {
            throw new IOException(e.getMessage(), e);
        }
        super.init(new EmbeddedSolrServer(this.core, "metadata"));
    }

    @Override
    public void close() {
        super.close();
        this.core.shutdown();
    }

}

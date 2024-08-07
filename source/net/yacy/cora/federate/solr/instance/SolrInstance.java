/**
 *  SolrInstance
 *  Copyright 2013 by Michael Peter Christen
 *  First released 13.02.2013 at https://yacy.net
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

package net.yacy.cora.federate.solr.instance;

import java.util.Collection;

import org.apache.solr.client.solrj.SolrClient;

public interface SolrInstance {

    public String getDefaultCoreName();
    
    public Collection<String> getCoreNames();

    public SolrClient getDefaultServer();
    
    public SolrClient getServer(String name);
    
    public void close();
}

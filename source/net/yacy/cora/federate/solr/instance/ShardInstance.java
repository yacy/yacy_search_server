/**
 *  ShardInstance
 *  Copyright 2013 by Michael Peter Christen
 *  First released 13.02.2013 at http://yacy.net
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.federate.solr.connector.ShardSelection;

import org.apache.solr.client.solrj.SolrServer;

public class ShardInstance implements SolrInstance {

    private final ArrayList<RemoteInstance> instances;
    private final ShardSelection.Method method;
    private SolrServer defaultServer;
    private Map<String, SolrServer> serverCache;
    private final boolean writeEnabled;

    public ShardInstance(final ArrayList<RemoteInstance> instances, final ShardSelection.Method method, final boolean writeEnabled) {
        this.instances = instances;
        this.method = method;
        this.writeEnabled = writeEnabled;
        this.defaultServer = null;
        this.serverCache = new ConcurrentHashMap<String, SolrServer>();
    }

    @Override
    public String getDefaultCoreName() {
        return instances.get(0).getDefaultCoreName();
    }

    @Override
    public Collection<String> getCoreNames() {
        return instances.get(0).getCoreNames();
    }

    @Override
    public SolrServer getDefaultServer() {
        if (this.defaultServer != null) return this.defaultServer;
        ArrayList<SolrServer> server = new ArrayList<SolrServer>(instances.size());
        for (int i = 0; i < instances.size(); i++) server.set(i, instances.get(i).getDefaultServer());
        this.defaultServer = new ServerShard(server, method, this.writeEnabled);
        return this.defaultServer;
    }

    @Override
    public SolrServer getServer(String name) {
        SolrServer s = serverCache.get(name);
        if (s != null) return s;
        ArrayList<SolrServer> server = new ArrayList<SolrServer>(instances.size());
        for (int i = 0; i < instances.size(); i++) server.add(i, instances.get(i).getServer(name));
        s = new ServerShard(server, method, this.writeEnabled);
        this.serverCache.put(name, s);
        return s;
    }

    @Override
    public void close() {
        for (RemoteInstance instance: instances) instance.close();
    }
    
    public ArrayList<String> getAdminInterfaces() {
        ArrayList<String> a = new ArrayList<String>();
        for (RemoteInstance i: this.instances) a.add(i.getAdminInterface());
        return a;
    }
}

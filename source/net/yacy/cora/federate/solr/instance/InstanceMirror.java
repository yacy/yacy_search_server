/**
 *  InstanceMirror
 *  Copyright 2013 by Michael Peter Christen
 *  First released 18.02.2013 at http://yacy.net
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

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.federate.solr.connector.ConcurrentUpdateSolrConnector;
import net.yacy.cora.federate.solr.connector.EmbeddedSolrConnector;
import net.yacy.cora.federate.solr.connector.MirrorSolrConnector;
import net.yacy.cora.federate.solr.connector.RemoteSolrConnector;
import net.yacy.cora.federate.solr.connector.SolrConnector;

public class InstanceMirror {

    private EmbeddedInstance embeddedSolrInstance;
    private ShardInstance remoteSolrInstance;
    private Map<String, ConcurrentUpdateSolrConnector> mirrorConnectorCache;
    private Map<String, EmbeddedSolrConnector> embeddedConnectorCache;
    private Map<String, RemoteSolrConnector> remoteConnectorCache;

    public InstanceMirror() {
        this.embeddedSolrInstance = null;
        this.remoteSolrInstance = null;
        this.mirrorConnectorCache = new ConcurrentHashMap<String, ConcurrentUpdateSolrConnector>();
        this.embeddedConnectorCache = new ConcurrentHashMap<String, EmbeddedSolrConnector>();
        this.remoteConnectorCache = new ConcurrentHashMap<String, RemoteSolrConnector>();
    }
    
    public boolean isConnectedEmbedded() {
        return this.embeddedSolrInstance != null;
    }

    public void connectEmbedded(EmbeddedInstance c) {
        disconnectEmbedded();
        this.embeddedSolrInstance = c;
    }

    public EmbeddedInstance getEmbedded() {
        return this.embeddedSolrInstance;
    }

    public void disconnectEmbedded() {
        mirrorConnectorCache.clear();
        if (this.embeddedSolrInstance == null) return;
        Set<SolrConnector> connectors = new HashSet<SolrConnector>();
        connectors.addAll(this.embeddedConnectorCache.values());
        for (SolrConnector connector: connectors) connector.close();
        this.embeddedConnectorCache.clear();
        this.embeddedSolrInstance.close();
        this.embeddedSolrInstance = null;
    }

    public boolean isConnectedRemote() {
        return this.remoteSolrInstance != null;
    }

    public void connectRemote(ShardInstance c) {
        disconnectRemote();
        this.remoteSolrInstance = c;
    }

    public ShardInstance getRemote() {
        return this.remoteSolrInstance;
    }

    public void disconnectRemote() {
        mirrorConnectorCache.clear();
        if (this.remoteSolrInstance == null) return;
        for (RemoteSolrConnector connector: this.remoteConnectorCache.values()) connector.close();
        this.remoteConnectorCache.clear();
        this.remoteSolrInstance.close();
        this.remoteSolrInstance = null;
    }

    public synchronized void close() {
        Set<SolrConnector> connectors = new HashSet<SolrConnector>();
        connectors.addAll(this.mirrorConnectorCache.values());
        for (SolrConnector connector: connectors) connector.close();
        this.mirrorConnectorCache.clear();
    }

    public String getDefaultCoreName() {
        if (this.embeddedSolrInstance != null) return this.embeddedSolrInstance.getDefaultCoreName();
        if (this.remoteSolrInstance != null) return this.remoteSolrInstance.getDefaultCoreName();
        return null;
    }

    public Collection<String> getCoreNames() {
        if (this.embeddedSolrInstance != null) return this.embeddedSolrInstance.getCoreNames();
        if (this.remoteSolrInstance != null) return this.remoteSolrInstance.getCoreNames();
        return null;
    }

    public EmbeddedSolrConnector getDefaultEmbeddedConnector() {
        if (this.embeddedSolrInstance == null) return null;
        String coreName = this.getDefaultCoreName();
        if (coreName == null) return null;
        EmbeddedSolrConnector esc = this.embeddedConnectorCache.get(coreName);
        if (esc != null) return esc;
        esc = new EmbeddedSolrConnector(this.embeddedSolrInstance);
        this.embeddedConnectorCache.put(coreName, esc);
        return esc;
    }

    public RemoteSolrConnector getDefaultRemoteConnector(boolean useBinaryResponseWriter) throws IOException {
        if (this.remoteSolrInstance == null) return null;
        String coreName = this.getDefaultCoreName();
        if (coreName == null) return null;
        RemoteSolrConnector esc = this.remoteConnectorCache.get(coreName);
        if (esc != null) return esc;
        esc = new RemoteSolrConnector(this.remoteSolrInstance, useBinaryResponseWriter);
        this.remoteConnectorCache.put(coreName, esc);
        return esc;
    }

    public EmbeddedSolrConnector getEmbeddedConnector(String corename) {
        if (this.embeddedSolrInstance == null) return null;
        EmbeddedSolrConnector esc = this.embeddedConnectorCache.get(corename);
        if (esc != null) return esc;
        esc = new EmbeddedSolrConnector(this.embeddedSolrInstance, corename);
        this.embeddedConnectorCache.put(corename, esc);
        return esc;
    }

    public RemoteSolrConnector getRemoteConnector(String corename) {
        if (this.remoteSolrInstance == null) return null;
        RemoteSolrConnector rsc = this.remoteConnectorCache.get(corename);
        if (rsc != null) return rsc;
        rsc = new RemoteSolrConnector(this.remoteSolrInstance, true, corename);
        this.remoteConnectorCache.put(corename, rsc);
        return rsc;
    }
    
    public SolrConnector getDefaultMirrorConnector() {
        String coreName = this.getDefaultCoreName();
        if (coreName == null) return null;
        return getGenericMirrorConnector(coreName);
    }

    public SolrConnector getGenericMirrorConnector(String corename) {
        ConcurrentUpdateSolrConnector msc = this.mirrorConnectorCache.get(corename);
        if (msc != null) return msc;
        EmbeddedSolrConnector esc = getEmbeddedConnector(corename);
        RemoteSolrConnector rsc = getRemoteConnector(corename);
        msc = new ConcurrentUpdateSolrConnector(new MirrorSolrConnector(esc, rsc), RemoteInstance.queueSizeByMemory(), 100000, Runtime.getRuntime().availableProcessors());
        this.mirrorConnectorCache.put(corename, msc);
        return msc;
    }

    public int bufferSize() {
        int b = 0;
        for (SolrConnector sc: this.mirrorConnectorCache.values()) b += sc.bufferSize();
        for (EmbeddedSolrConnector esc: this.embeddedConnectorCache.values()) b += esc.bufferSize();
        for (RemoteSolrConnector rsc: this.remoteConnectorCache.values()) b += rsc.bufferSize();
        return b;
    }
    
    public void clearCaches() {
        for (SolrConnector csc: this.mirrorConnectorCache.values()) csc.clearCaches();
        for (EmbeddedSolrConnector esc: this.embeddedConnectorCache.values()) esc.clearCaches();
        for (RemoteSolrConnector rsc: this.remoteConnectorCache.values()) rsc.clearCaches();
    }
    
}

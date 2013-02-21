/**
 *  EmbeddedInstance
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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.MemoryControl;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrCore;

import com.google.common.io.Files;

public class EmbeddedInstance implements SolrInstance {
    
    private final static String[] confFiles = {"solrconfig.xml", "schema.xml", "stopwords.txt", "synonyms.txt", "protwords.txt", "currency.xml", "elevate.xml", "xslt/example.xsl", "xslt/json.xsl", "lang/"};

    private CoreContainer coreContainer;
    private String defaultCoreName;
    private SolrCore defaultCore;
    private SolrServer defaultCoreServer;
    private File containerPath;
    private Map<String, SolrCore> cores;
    private Map<String, SolrServer> server;

    public EmbeddedInstance(final File solr_config, final File containerPath, String givenDefaultCoreName, String[] initializeCoreNames) throws IOException {
        super();
        // copy the solrconfig.xml to the storage path
        this.containerPath = containerPath;
        
        // ensure that default core path exists
        File defaultCorePath = new File(containerPath, givenDefaultCoreName);
        if (!defaultCorePath.exists()) defaultCorePath.mkdirs();
        
        // migrate old conf directory
        File conf = new File(defaultCorePath, "conf");
        File oldConf = new File(containerPath, "conf");
        if (oldConf.exists()) oldConf.renameTo(conf);
        
        // migrate old data directory
        File oldData = new File(containerPath, "data");
        if (oldData.exists()) oldData.renameTo(new File(defaultCorePath, "data"));

        // initialize the cores' configuration
        for (String coreName: initializeCoreNames) {
            initializeCoreConf(solr_config, containerPath, coreName);
        }
        
        // initialize the coreContainer
        String containerDir = this.containerPath.getAbsolutePath(); // the home directory of all resources.
        File configFile = new File(solr_config, "solr.xml"); //  the configuration file for all cores
        this.coreContainer = new CoreContainer(containerDir, configFile); // this may take indefinitely long if solr files are broken
        if (this.coreContainer == null) throw new IOException("cannot create core container dir = " + containerDir + ", configFile = " + configFile);

        // get the default core from the coreContainer
        this.defaultCoreName = this.coreContainer.getDefaultCoreName();
        assert(this.defaultCoreName.equals(givenDefaultCoreName));
        Log.logInfo("SolrEmbeddedInstance", "detected default solr core: " + this.defaultCoreName);
        this.defaultCore = this.coreContainer.getCore(this.defaultCoreName);
        assert givenDefaultCoreName.equals(this.defaultCore.getName()) : "givenDefaultCoreName = " + givenDefaultCoreName + ", this.defaultCore.getName() = " + this.defaultCore.getName();
        if (this.defaultCore == null) {
            throw new IOException("cannot get the default core; available = " + MemoryControl.available() + ", free = " + MemoryControl.free());
        }
        this.defaultCoreServer = new EmbeddedSolrServer(this.coreContainer, this.defaultCoreName);
        
        // initialize core cache
        this.cores = new ConcurrentHashMap<String, SolrCore>();
        this.cores.put(this.defaultCoreName, this.defaultCore);
        this.server = new ConcurrentHashMap<String, SolrServer>();
        this.server.put(this.defaultCoreName, this.defaultCoreServer);
    }

    private void initializeCoreConf(final File solr_config, final File containerPath, String coreName) {

        // ensure that default core path exists
        File corePath = new File(containerPath, coreName);
        if (!corePath.exists()) corePath.mkdirs();

        // ensure necessary subpaths exist
        File conf = new File(corePath, "conf");
        conf.mkdirs();
        File data = new File(corePath, "data");
        data.mkdirs();
        
        // (over-)write configuration into conf path
        File source, target;
        for (String cf: confFiles) {
            source = new File(solr_config, cf);
            if (source.isDirectory()) {
                target = new File(conf, cf);
                target.mkdirs();
                for (String cfl: source.list()) {
                    try {
                        Files.copy(new File(source, cfl), new File(target, cfl));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                target = new File(conf, cf);
                target.getParentFile().mkdirs();
                try {
                    Files.copy(source, target);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    public File getContainerPath() {
        return this.containerPath;
    }
    
    @Override
    public String getDefaultCoreName() {
        return this.defaultCoreName;
    }

    @Override
    public Collection<String> getCoreNames() {
        return this.coreContainer.getCoreNames();
    }

    @Override
    public SolrServer getDefaultServer() {
        return this.defaultCoreServer;
    }

    @Override
    public SolrServer getServer(String coreName) {
        SolrServer s = this.server.get(coreName);
        if (s != null) return s;
        s = new EmbeddedSolrServer(this.coreContainer, coreName);
        this.server.put(coreName, s);
        return s;
    }

    public SolrCore getDefaultCore() {
        return this.defaultCore;
    }

    public SolrCore getCore(String name) {
        SolrCore c = this.cores.get(name);
        if (c != null) return c;
        c = this.coreContainer.getCore(name);
        this.cores.put(name, c);
        return c;
    }

    @Override
    public synchronized void close() {
        try {this.coreContainer.shutdown();} catch (Throwable e) {Log.logException(e);}
    }
    
}

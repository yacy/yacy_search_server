/**
 *  SolrEmbeddedInstance
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

import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.MemoryControl;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrCore;

import com.google.common.io.Files;

public class SolrEmbeddedInstance implements SolrInstance {
    
    private final static String[] confFiles = {"solrconfig.xml", "schema.xml", "stopwords.txt", "synonyms.txt", "protwords.txt", "currency.xml", "elevate.xml", "xslt/example.xsl", "xslt/json.xsl", "lang/"};

    private CoreContainer cores;
    private String defaultCoreName;
    private SolrCore defaultCore;
    private SolrServer defaultServer;
    private File storagePath;

    public SolrEmbeddedInstance(final File corePath, final File solr_config) throws IOException {
        super();
        // copy the solrconfig.xml to the storage path
        this.storagePath = corePath;
        File conf = new File(corePath, "conf");
        conf.mkdirs();
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
        String dir = corePath.getAbsolutePath();
        File configFile = new File(solr_config, "solr.xml");
        this.cores = new CoreContainer(dir, configFile); // this may take indefinitely long if solr files are broken
        if (this.cores == null) throw new IOException("cannot create core container dir = " + dir + ", configFile = " + configFile);
        this.defaultCoreName = this.cores.getDefaultCoreName();
        Log.logInfo("SolrEmbeddedInstance", "detected default solr core: " + this.defaultCoreName);
        this.defaultCore = this.cores.getCore(this.defaultCoreName); // should be "collection1"
        if (this.defaultCore == null) {
            // try again
            Collection<SolrCore> cores = this.cores.getCores();
            if (cores.size() > 0) {
                this.defaultCore = cores.iterator().next();
                this.defaultCoreName = this.defaultCore.getName();
            }
        }
        if (this.defaultCore == null) {
            throw new IOException("cannot get the default core; available = " + MemoryControl.available() + ", free = " + MemoryControl.free());
        }
        this.defaultServer = new EmbeddedSolrServer(this.cores, this.defaultCoreName);
    }


    public File getStoragePath() {
        return this.storagePath;
    }
    
    @Override
    public String getDefaultCoreName() {
        return this.defaultCoreName;
    }

    @Override
    public Collection<String> getCoreNames() {
        return this.cores.getCoreNames();
    }

    @Override
    public SolrServer getDefaultServer() {
        return this.defaultServer;
    }

    @Override
    public SolrServer getServer(String name) {
        return new EmbeddedSolrServer(this.cores, name);
    }

    public SolrCore getDefaultCore() {
        return this.defaultCore;
    }

    public SolrCore getCore(String name) {
        return this.cores.getCore(name);
    }

    @Override
    public synchronized void close() {
        try {this.cores.shutdown();} catch (Throwable e) {Log.logException(e);}
    }
    
}

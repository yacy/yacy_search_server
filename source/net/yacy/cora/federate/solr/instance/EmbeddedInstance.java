/**
 *  EmbeddedInstance
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.solr.client.solrj.SolrClient;
//import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.NodeConfig;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrXmlConfig;

import com.google.common.io.Files;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.federate.solr.embedded.EmbeddedSolrServer;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.MemoryControl;

public class EmbeddedInstance implements SolrInstance {

    private final static String[] confFiles = {"solrconfig.xml", "schema.xml", "stopwords.txt", "synonyms.txt", "protwords.txt", "currency.xml", "elevate.xml", "xslt/example.xsl", "xslt/json.xsl", "lang/"};
    // additional a optional   solrcore.properties     (or solrcore.x86.properties for 32bit systems is copied
    private CoreContainer coreContainer;
    private final String defaultCoreName;
    private final SolrCore defaultCore;
    private final SolrClient defaultCoreServer;
    private final File containerPath;
    private final Map<String, SolrCore> cores;
    private final Map<String, SolrClient> server;

    public EmbeddedInstance(final File solr_config, final File containerPath, String givenDefaultCoreName, String[] initializeCoreNames) throws IOException {
        super();
        // copy the solrconfig.xml to the storage path
        this.containerPath = containerPath;

        // ensure that default core path exists
        final File defaultCorePath = new File(containerPath, givenDefaultCoreName);
        if (!defaultCorePath.exists()) defaultCorePath.mkdirs();

        // migrate old conf directory
        final File oldConf = new File(containerPath, "conf");
        final File confDir = new File(defaultCorePath, "conf");
        if (oldConf.exists()) oldConf.renameTo(confDir);

        // migrate old data directory
        final File oldData = new File(containerPath, "data");
        final File dataDir = new File(defaultCorePath, "data");
        if (oldData.exists()) oldData.renameTo(dataDir);

        // create index subdirectory in data if it does not exist
        final File indexDir = new File(dataDir, "index");
        if (!indexDir.exists()) indexDir.mkdirs();

        // initialize the cores' configuration
        for (final String coreName: initializeCoreNames) {
            initializeCoreConf(solr_config, containerPath, coreName);
        }

        // initialize the coreContainer
        final File configFile = new File(solr_config, "solr.xml"); //  the configuration file for all cores
        Path solrHome = containerPath.toPath();
        Path configFilePath = configFile.toPath();
        //this.coreContainer = CoreContainer.createAndLoad(containerPath.toPath(), configFile.toPath());
        // this may take indefinitely long if solr files are broken
        NodeConfig nc = SolrXmlConfig.fromFile(solrHome, configFilePath, new Properties());
        this.coreContainer = new CoreContainer(nc);
        try {
        	this.coreContainer.load();
        } catch (Exception e) {
        	this.coreContainer.shutdown();
            throw e;
        }
        
        if (this.coreContainer == null) throw new IOException("cannot create core container dir = " + containerPath + ", configFile = " + configFile);

        // get the default core from the coreContainer
        this.defaultCoreName = givenDefaultCoreName;
        ConcurrentLog.info("SolrEmbeddedInstance", "detected default solr core: " + this.defaultCoreName);
        this.defaultCore = this.coreContainer.getCore(this.defaultCoreName);
        assert givenDefaultCoreName.equals(this.defaultCore.getName()) : "givenDefaultCoreName = " + givenDefaultCoreName + ", this.defaultCore.getName() = " + this.defaultCore.getName();
        if (this.defaultCore == null) {
            throw new IOException("cannot get the default core; available = " + MemoryControl.available() + ", free = " + MemoryControl.free());
        }
        this.defaultCoreServer = new EmbeddedSolrServer(this.coreContainer, this.defaultCoreName);

        // initialize core cache
        this.cores = new ConcurrentHashMap<>();
        this.cores.put(this.defaultCoreName, this.defaultCore);
        this.server = new ConcurrentHashMap<>();
        this.server.put(this.defaultCoreName, this.defaultCoreServer);
    }

    @Override
    public int hashCode() {
        return this.containerPath.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EmbeddedInstance && this.containerPath.equals(((EmbeddedInstance) o).containerPath);
    }

    private static void initializeCoreConf(final File solr_config, final File containerPath, String coreName) {

        // ensure that default core path exists
        final File corePath = new File(containerPath, coreName);
        if (!corePath.exists()) corePath.mkdirs();

        // check if core.properties exists in each path (thats new in Solr 5.0)
        final File core_properties = new File(corePath, "core.properties");
        if (!core_properties.exists()) {
            // create the file
            try (
                    /* Automatically closed by this try-with-resources statement */
                    FileOutputStream fos = new FileOutputStream(core_properties);
                    ) {
                fos.write(ASCII.getBytes("name=" + coreName + "\n"));
                fos.write(ASCII.getBytes("shard=${shard:}\n"));
                fos.write(ASCII.getBytes("collection=${collection:" + coreName + "}\n"));
                fos.write(ASCII.getBytes("config=${solrconfig:solrconfig.xml}\n"));
                fos.write(ASCII.getBytes("schema=${schema:schema.xml}\n"));
                fos.write(ASCII.getBytes("coreNodeName=${coreNodeName:}\n"));
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }

        // ensure necessary subpaths exist
        final File conf = new File(corePath, "conf");
        conf.mkdirs();
        final File data = new File(corePath, "data");
        data.mkdirs();

        // (over-)write configuration into conf path
        File source, target;
        for (final String cf: confFiles) {
            source = new File(solr_config, cf);
            if (source.isDirectory()) {
                target = new File(conf, cf);
                target.mkdirs();
                for (final String cfl: source.list()) {
                    try {
                        Files.copy(new File(source, cfl), new File(target, cfl));
                    } catch (final IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                target = new File(conf, cf);
                target.getParentFile().mkdirs();
                try {
                    Files.copy(source, target);
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // copy the solrcore.properties
        // for 32bit systems (os.arch name not containing '64') take the solrcore.x86.properties as solrcore.properties if exists
        final String os = System.getProperty("os.arch");
        if (os.contains("64")) {
            source = new File(solr_config, "solrcore.properties");
        } else {
            source = new File(solr_config, "solrcore.x86.properties");
            if (!source.exists()) {
                source = new File(solr_config, "solrcore.properties");
            }
        }
        // solr always reads the solrcore.properties file if exists in core/conf directory
        target = new File(conf, "solrcore.properties");

        if (source.exists()) {
            try {
                Files.copy(source, target);
                ConcurrentLog.fine("initializeCoreConf", "overwrite " + target.getAbsolutePath() + " with " + source.getAbsolutePath());
            } catch (final IOException ex) {
                ex.printStackTrace();
            }
        }

    }

    public File getContainerPath() {
        return this.containerPath;
    }

    public CoreContainer getCoreContainer() {
        return this.coreContainer;
    }

    @Override
    public String getDefaultCoreName() {
        return this.defaultCoreName;
    }

    @Override
    public Collection<String> getCoreNames() {
        return this.coreContainer.getAllCoreNames();
    }

    @Override
    public SolrClient getDefaultServer() {
        return this.defaultCoreServer;
    }

    @Override
    public SolrClient getServer(String coreName) {
        SolrClient s = this.server.get(coreName);
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
        for (final SolrCore core: this.cores.values()) core.close();
        if (this.coreContainer != null) try {
            this.coreContainer.shutdown();
            this.coreContainer = null;
        } catch (final Throwable e) {ConcurrentLog.logException(e);}
    }

}

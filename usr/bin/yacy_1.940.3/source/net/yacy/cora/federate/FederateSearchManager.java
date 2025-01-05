/**
 * FederateSearchManager.java
 * Copyright 2015 by Burkhard Buelte
 * First released 19.01.2015 at https://yacy.net
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program in the file lgpl21.txt If not, see
 * <http://www.gnu.org/licenses/>.
 */
package net.yacy.cora.federate;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.opensearch.OpenSearchConnector;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.storage.Configuration;
import net.yacy.cora.storage.Configuration.Entry;
import net.yacy.cora.storage.Files;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.robots.RobotsTxtEntry;
import net.yacy.document.parser.xml.opensearchdescriptionReader;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.QueryGoal;
import net.yacy.search.query.QueryModifier;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.schema.WebgraphSchema;

/**
 * Handling of queries to configured remote OpenSearch systems.
 */
public class FederateSearchManager {

    /** Logger for this class */
    private static final ConcurrentLog LOG = new ConcurrentLog(FederateSearchManager.class.getName());

    /** Delay between connects (in ms) */
    private final int accessDelay = 15000;

    private File confFile = null; // later initialized to DATA/SETTINGS/heuristicopensearch.conf

    /** Connectors list */
    private HashSet<AbstractFederateSearchConnector> conlist;

    /** PropertiesConfiguration cfg */
    protected Configuration cfg;

    /** Switchboard instance */
    private Switchboard switchboard;

    /** Self reference for static .getManager() */
    private static FederateSearchManager manager = null;

    /**
     * @param sb switchboard instance. Must not be null.
     */
    public FederateSearchManager(Switchboard sb) {
        super();
        this.conlist = new HashSet<>();

        // from here we need Switchboard settings
        if (sb == null) {
            return;
        }
        this.switchboard = sb;
        // Data needed  active  name, url(template), desc, rule-when-to-use, specifics
        this.confFile = new File(sb.getDataPath(), "DATA/SETTINGS/heuristicopensearch.conf");
        if (!this.confFile.exists()) {
            try {
                Files.copy(new File(sb.appPath, "defaults/heuristicopensearch.conf"), this.confFile);
                final File defdir = new File(sb.dataPath, "DATA/SETTINGS/federatecfg");
                if (!defdir.exists()) {
                    Files.copy(new File(sb.appPath, "defaults/federatecfg"), defdir);
                }
            } catch (final IOException ex) {
            }
        }
        // read settings config file
        if (this.confFile.exists()) {
            try {
                this.cfg = new Configuration(this.confFile);
                final Iterator<Entry> it = this.cfg.entryIterator();
                while (it.hasNext()) {
                    final Entry cfgentry = it.next();
                    final String url = cfgentry.getValue();
                    if (cfgentry.enabled() && url != null && !url.isEmpty()) {
                        final String name = cfgentry.key();
                        if (url.startsWith("cfgfile:")) { // is cfgfile with field mappings (no opensearch url)
                            // format    prefix:connectortype:configfilename
                            // example   cfgfile:solrconnector:testsys.solr.schema
                            final String[] parts = url.split(":");
                            if (parts[1].equalsIgnoreCase("solrconnector")) {
                                final SolrFederateSearchConnector sfc = new SolrFederateSearchConnector();
                                if (sfc.init(name, sb.getDataPath()+ "/DATA/SETTINGS/federatecfg/" + parts[2])) {
                                    this.conlist.add(sfc);
                                }
                            } else {
                                LOG.config("Error in configuration of: " + url);
                            }
                        } else { // handle opensearch url template
                            final OpenSearchConnector osc = new OpenSearchConnector(url);
                            if (osc.init(name, sb.getDataPath()+ "/DATA/SETTINGS/federatecfg/" + OpenSearchConnector.htmlMappingFileName(name))) {
                                this.conlist.add(osc);
                            }
                        }
                    }
                }
            } catch (final IOException ex) {
                LOG.config("Unexpected error when reading configuration file : " + this.confFile, ex);
            }
        }
        manager = this; // reference for static access via .getManager()
    }

    /**
     * Get instance of this manager. There should be only one instance running,
     * use this to get or initialize the manager.
     *
     * @return
     */
    public static FederateSearchManager getManager() {
        if (manager == null) {
            manager = new FederateSearchManager(Switchboard.getSwitchboard());
        }
        return manager;
    }

    /**
     * Sends a query request to remote systems configured.
     * If search query domain is LOCAL procedure does nothing.
     *
     * @param theSearch
     */
    public void search(SearchEvent theSearch) {
        if (theSearch != null) {
            if (!theSearch.query.isLocal() && !MemoryControl.shortStatus()) {
                final Set<AbstractFederateSearchConnector> picklist = this.getBest();
                for (final AbstractFederateSearchConnector fsc : picklist) {
                    fsc.search(theSearch);
                }
            }
        }
    }

    /**
     * Sends a query to configured remote systems.
     *
     * @param query
     * @return list of results according to YaCy schema
     */
    public List<URIMetadataNode> query(QueryParams query) {
        if (!query.isLocal() && !MemoryControl.shortStatus()) {
            final List<URIMetadataNode> sdl = new ArrayList<>();
            final Set<AbstractFederateSearchConnector> picklist = this.getBest();
            for (final AbstractFederateSearchConnector fsc : picklist) {
                sdl.addAll(fsc.query(query));
            }
            return sdl;
        }
        return null;
    }

    /**
     * Takes a search string, converts it to queryparams and calls the
     * query(queryparams)
     *
     * @param querystr
     * @return SolrDocumentlist of remote query results according to YaCy schema
     */
    public List<URIMetadataNode> query(String querystr) {

        final QueryGoal qg = new QueryGoal(querystr);
        final Bitfield filter = new Bitfield();
        final QueryParams query = new QueryParams(
                qg,
                new QueryModifier(0),
                Integer.MAX_VALUE,
                "",
                Classification.ContentDomain.ALL,
                "", //lang
                0, //timezoneOffset
                null,
                CacheStrategy.IFFRESH,
                100, 0, //count, offset
                ".*", //urlmask
                null,
                null,
                QueryParams.Searchdom.LOCAL,
                filter,
                false,
                null,
                MultiProtocolURL.TLD_any_zone_filter,
                "",
                false,
                this.switchboard.index,
                this.switchboard.getRanking(),
                "",//userAgent
                0.0d, 0.0d, 0.0d,
                new HashSet<>());

        return this.query(query);
    }

    /**
     * Add a search target system/connector to the config file
     *
     * @param urlTemplate query template url
     * @return successful added
     */
    public boolean addOpenSearchTarget(String name, String urlTemplate, boolean active, String comment) {
        if (this.confFile == null) {
            return false;
        }

        try {
            final Configuration conf = new Configuration(this.confFile);
            if (name != null && !name.isEmpty()) {
                conf.add(name, null, active);
                final Configuration.Entry e = conf.get(name);
                e.setValue(urlTemplate);
                e.setEnable(active);
                e.setComment(comment);
                conf.put(name, e);
                try {
                    conf.commit();
                    if (active) {
                        final OpenSearchConnector osd = new OpenSearchConnector(urlTemplate);
                        final String htmlMappingFile = this.switchboard.getDataPath()+ "/DATA/SETTINGS/federatecfg/" + OpenSearchConnector.htmlMappingFileName(name);
                        if (osd.init(name, htmlMappingFile)) {
                            this.conlist.add(osd);
                        }
                    }
                } catch (final IOException ex) {
                    LOG.warn("config file write error");
                }
                return true;
            }
        } catch (final IOException e1) {
            LOG.severe("Unexpected error when writing configuration file : " + this.confFile, e1);
            return false;
        }
        return false;
    }

    /**
     * Get the number of active remote query target systems
     */
    public int getSize() {
        return this.conlist.size();
    }

    /**
     * Get best systems from configured targets
     *
     * @return list of searchtargetconnectors
     */
    protected Set<AbstractFederateSearchConnector> getBest() {
        final HashSet<AbstractFederateSearchConnector> retset = new HashSet<>();
        final long currentTime = System.currentTimeMillis();
        for (final AbstractFederateSearchConnector fsc : this.conlist) {
            // check access time
            if (fsc.lastaccesstime + this.accessDelay < currentTime && fsc.lastaccesstime < currentTime) {
                retset.add(fsc);
            }
        }
        return retset;
    }

    /**
     * Discover opensearch description links from local (embedded) Solr index
     * using meta data field 'outboundlinks_tag_txt' and add found systems to
     * the config file
     *
     * @return true if background discover job was started, false if job not
     * started
     */
    public boolean discoverFromSolrIndex(final Switchboard sb) {
        if (sb == null) {
            return false;
        }
        // check if needed Solr fields are available (selected)
        if (!sb.index.fulltext().useWebgraph()) {
            LOG.severe("Error on connecting to embedded Solr webgraph index");
            return false;
        }
        final SolrConnector connector = sb.index.fulltext().getWebgraphConnector();
        final boolean metafieldavailable = sb.index.fulltext().getWebgraphConfiguration().contains(WebgraphSchema.target_rel_s.name())
                && (sb.index.fulltext().getWebgraphConfiguration().contains(WebgraphSchema.target_protocol_s.name()) && sb.index.fulltext().getWebgraphConfiguration().contains(WebgraphSchema.target_urlstub_s.name()))
                && sb.getConfigBool(SwitchboardConstants.CORE_SERVICE_WEBGRAPH, false);
        if (!metafieldavailable) {
            LOG.warn("webgraph option and webgraph Schema fields target_rel_s, target_protocol_s and target_urlstub_s must be switched on");
            return false;
        }
        // the solr search
        final String webgraphquerystr = WebgraphSchema.target_rel_s.getSolrFieldName() + ":search";
        final String[] webgraphqueryfields = {WebgraphSchema.target_protocol_s.getSolrFieldName(), WebgraphSchema.target_urlstub_s.getSolrFieldName()};
        // alternatively target_protocol_s + "://" +target_host_s + target_path_s

        final long numfound;
        try {
            final SolrDocumentList docList = connector.getDocumentListByQuery(webgraphquerystr, null, 0, 1, webgraphqueryfields);
            numfound = docList.getNumFound();
            if (numfound == 0) {
                LOG.info("no results found, abort discover job");
                return true;
            }
            LOG.info("start checking " + Long.toString(numfound) + " found index results");
        } catch (final IOException ex) {
            LOG.severe("Error on Solr webgraph core query", ex);
            return false;
        }

        final long stoptime = System.currentTimeMillis() + 1000 * 3600; // make sure job doesn't run forever

        // job to iterate through Solr index to find links to opensearchdescriptions
        // started as background job as connect timeouts may cause it run a long time
        final Thread job = new Thread(FederateSearchManager.class.getSimpleName() + ".discoverFromSolrIndex") {
            @Override
            public void run() {
                try {
                    boolean doloop = true;
                    int loopnr = 0;
                    final Set<String> dblmem = new HashSet<>(); // temp memory for already checked url
                    while (doloop) {
                        LOG.info("start Solr query loop at " + Integer.toString(loopnr * 20) + " of " + Long.toString(numfound));
                        final SolrDocumentList docList = connector.getDocumentListByQuery(webgraphquerystr, null, loopnr * 20, 20, webgraphqueryfields); // check chunk of 20 result documents
                        loopnr++;
                        if (stoptime < System.currentTimeMillis()) {// stop after max 1h
                            doloop = false;
                            LOG.info("long running discover task aborted");
                        }
                        if (docList != null && docList.size() > 0) {
                            final Iterator<SolrDocument> docidx = docList.iterator();
                            while (docidx.hasNext()) {
                                final SolrDocument sdoc = docidx.next();

                                final String hrefurltxt = sdoc.getFieldValue(WebgraphSchema.target_protocol_s.getSolrFieldName()) + "://" + sdoc.getFieldValue(WebgraphSchema.target_urlstub_s.getSolrFieldName());
                                URL url;
                                try {
                                    url = new URI(hrefurltxt).toURL();
                                } catch (final MalformedURLException | URISyntaxException ex) {
                                    LOG.warn("OpenSearch description URL is malformed : " + hrefurltxt);
                                    continue;
                                }
                                //TODO: check Blacklist
                                if (dblmem.add(url.getAuthority())) { // use only main path to detect double entries
                                    final opensearchdescriptionReader os = new opensearchdescriptionReader(hrefurltxt);
                                    if (os.getRSSorAtomUrl() != null) {
                                         /* Check eventual robots.txt policy */
                                          RobotsTxtEntry robotsEntry = null;
                                          MultiProtocolURL templateURL;
                                           try {
                                               templateURL = new MultiProtocolURL(os.getRSSorAtomUrl());
                                           } catch (final MalformedURLException ex) {
                                               LOG.warn("OpenSearch description URL is malformed : " + hrefurltxt);
                                               continue;
                                        }
                                           if(sb.robots != null) {
                                               robotsEntry = sb.robots.getEntry(templateURL, ClientIdentification.yacyInternetCrawlerAgent);
                                           }

                                           if(robotsEntry != null && robotsEntry.isDisallowed(templateURL)) {
                                               LOG.info("OpenSearch description template URL is disallowed by robots.xt");
                                           } else {
                                               // add found system to config file
                                            FederateSearchManager.this.addOpenSearchTarget(os.getShortName(), os.getRSSorAtomUrl(), false, os.getItem("LongName"));
                                            LOG.info("added " + os.getShortName() + " " + hrefurltxt);
                                        }
                                    } else {
                                        LOG.info("osd.xml check failed (no RSS or Atom support) for " + hrefurltxt);
                                    }
                                }
                            }
                        } else {
                            doloop = false;
                        }
                    }
                    LOG.info("finisched Solr query (checked " + Integer.toString(dblmem.size()) + " unique opensearchdescription links found in " + Long.toString(numfound) + " results)");
                } catch (final IOException ex) {
                    LOG.severe("Unexpected error", ex);
                }
            }
        };
        job.start();
        return true;
    }

    /**
     * Read or reread opensearch config file and initialize connectors
     *
     * @param cfgFileName
     * @return true if successful
     */
    public boolean init(String cfgFileName) {
        this.confFile = new File(cfgFileName);
        if (this.confFile.exists()) {
            try {
                this.cfg = new Configuration(this.confFile);
                if (!this.conlist.isEmpty()) this.conlist.clear(); // prevent double entries
                final Iterator<Entry> it = this.cfg.entryIterator();
                while (it.hasNext()) {
                    final Entry cfgentry = it.next();
                    if (cfgentry.enabled()) { // hold only enabled in memory
                        final String name = cfgentry.key();
                        final String url = cfgentry.getValue();
                        if (url != null && !url.isEmpty()) {
                            if (url.startsWith("cfgfile:")) { // is cfgfile with field mappings (no opensearch url)
                                // config entry has 3 parts separated by :    1=cfgfile 2=connectortype 3=relative path to connector-cfg-file
                                // example   cfgfile:solrconnector:testsys.solr.schema
                                final String[] parts = url.split(":");
                                if (parts[1].equalsIgnoreCase("solrconnector")) {
                                    final SolrFederateSearchConnector sfc = new SolrFederateSearchConnector();
                                    if (sfc.init(name, this.confFile.getParent()+"/federatecfg/"+parts[2])) {
                                        this.conlist.add(sfc);
                                    }
                                } else {
                                    LOG.config("Init error in configuration of: " + url);
                                }
                            } else { // handle opensearch url template
                                final OpenSearchConnector osd = new OpenSearchConnector(url);
                                if (osd.init(name, this.confFile.getParent()+"/federatecfg/" + OpenSearchConnector.htmlMappingFileName(name))) {
                                    this.conlist.add(osd);
                                }
                            }
                        }
                    }
                }
            } catch (final IOException ex) {
                LOG.config("Unexpected error when reading configuration file : " + cfgFileName);
            }
        }
        return true;
    }

}

/**
 * FederateSearchManager.java
 * Copyright 2015 by Burkhard Buelte
 * First released 19.01.2015 at http://yacy.net
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

import net.yacy.cora.federate.opensearch.OpenSearchConnector;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.storage.Configuration;
import net.yacy.cora.storage.Configuration.Entry;
import net.yacy.cora.storage.Files;
import net.yacy.cora.util.ConcurrentLog;
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
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

/**
 * Handling of queries to configured remote OpenSearch systems.
 */
public class FederateSearchManager {

    private final int accessDelay = 15000; // delay between connects (in ms)

    private File confFile = null; // later initialized to DATA/SETTINGS/heuristicopensearch.conf
    private HashSet<AbstractFederateSearchConnector> conlist; // connector list
    protected Configuration cfg;//PropertiesConfiguration cfg;
    private static FederateSearchManager manager = null; // self referenc for static .getManager()

    public FederateSearchManager(Switchboard sb) {
        super();
        this.conlist = new HashSet<AbstractFederateSearchConnector>();

        // from here we need Switchboard settings
        if (sb == null) {
            return;
        }
        // Data needed  active  name, url(template), desc, rule-when-to-use, specifics
        confFile = new File(sb.getDataPath(), "DATA/SETTINGS/heuristicopensearch.conf");
        if (!confFile.exists()) {
            try {
                Files.copy(new File(sb.appPath, "defaults/heuristicopensearch.conf"), confFile);
                File defdir = new File(sb.dataPath, "DATA/SETTINGS/federatecfg");
                if (!defdir.exists()) {
                    Files.copy(new File(sb.appPath, "defaults/federatecfg"), defdir);
                }
            } catch (IOException ex) {
            }
        }
        // read settings config file
        if (confFile.exists()) {
            try {
                cfg = new Configuration(confFile);
                Iterator<Entry> it = cfg.entryIterator();
                while (it.hasNext()) {
                    Entry cfgentry = it.next();
                    String url = cfgentry.getValue();
                    if (cfgentry.enabled() && url != null && !url.isEmpty()) {
                        String name = cfgentry.key();
                        if (url.startsWith("cfgfile:")) { // is cfgfile with field mappings (no opensearch url)
                            // format    prefix:connectortype:configfilename
                            // example   cfgfile:solrconnector:testsys.solr.schema
                            String[] parts = url.split(":");
                            if (parts[1].equalsIgnoreCase("solrconnector")) {
                                SolrFederateSearchConnector sfc = new SolrFederateSearchConnector();
                                if (sfc.init(name, sb.getDataPath()+ "/DATA/SETTINGS/federatecfg/" + parts[2])) {
                                    conlist.add(sfc);
                                }
                            } else {
                                ConcurrentLog.config("FederateSearchManager", "Error in configuration of: " + url);
                            }
                        } else { // handle opensearch url template
                            OpenSearchConnector osc = new OpenSearchConnector();
                            if (osc.init(name, url)) {
                                conlist.add(osc);
                            }
                        }
                    }
                }
            } catch (IOException ex) {
                ConcurrentLog.logException(ex);
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
                Set<AbstractFederateSearchConnector> picklist = getBest(theSearch.getQuery());
                for (AbstractFederateSearchConnector fsc : picklist) {
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
            List<URIMetadataNode> sdl = new ArrayList<URIMetadataNode>();
            Set<AbstractFederateSearchConnector> picklist = getBest(query);
            for (AbstractFederateSearchConnector fsc : picklist) {
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
        final Switchboard sb = Switchboard.getSwitchboard();
        Bitfield filter = new Bitfield();
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
                sb.index,
                sb.getRanking(),
                "",//userAgent
                0.0d, 0.0d, 0.0d,
                new String[0]);

        return query(query);
    }

    /**
     * Add a search target system/connector to the config file
     *
     * @param urlTemplate query template url
     * @return successful added
     */
    public boolean addOpenSearchTarget(String name, String urlTemplate, boolean active, String comment) {
        if (confFile == null) {
            return false;
        }

        try {
            Configuration conf = new Configuration(confFile);
            if (name != null && !name.isEmpty()) {
                conf.add(name, null, active);
                Configuration.Entry e = conf.get(name);
                e.setValue(urlTemplate);
                e.setEnable(active);
                e.setComment(comment);
                conf.put(name, e);
                try {
                    conf.commit();
                    if (active) {
                        OpenSearchConnector osd = new OpenSearchConnector();
                        if (osd.init(name, urlTemplate)) {
                            conlist.add(osd);
                        }
                    }
                } catch (final IOException ex) {
                    ConcurrentLog.warn("FederateSearchManager", "config file write error");
                }
                return true;
            }
        } catch (final IOException e1) {
            ConcurrentLog.logException(e1);
            return false;
        }
        return false;
    }

    /**
     * Get the number of active remote query target systems
     */
    public int getSize() {
        return conlist.size();
    }

    /**
     * Get best systems from configured targets for this search
     *
     * @param theSearch
     * @return list of searchtargetconnectors
     */
    protected Set<AbstractFederateSearchConnector> getBest(final QueryParams query) {
        HashSet<AbstractFederateSearchConnector> retset = new HashSet<AbstractFederateSearchConnector>();
        // currently only enforces limits (min access delay, frequency)
        for (AbstractFederateSearchConnector fsc : conlist) {
            // check access time
            if (fsc.lastaccesstime + accessDelay < System.currentTimeMillis()) { // enforce 15 sec delay between searches to same system
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
    public boolean discoverFromSolrIndex(Switchboard sb) {
        if (sb == null) {
            return false;
        }
        // check if needed Solr fields are available (selected)
        if (!sb.index.fulltext().useWebgraph()) {
            ConcurrentLog.severe("FederateSearchManager", "Error on connecting to embedded Solr webgraph index");
            return false;
        }
        final SolrConnector connector = sb.index.fulltext().getWebgraphConnector();
        final boolean metafieldavailable = sb.index.fulltext().getWebgraphConfiguration().contains(WebgraphSchema.target_rel_s.name())
                && (sb.index.fulltext().getWebgraphConfiguration().contains(WebgraphSchema.target_protocol_s.name()) && sb.index.fulltext().getWebgraphConfiguration().contains(WebgraphSchema.target_urlstub_s.name()))
                && sb.getConfigBool(SwitchboardConstants.CORE_SERVICE_WEBGRAPH, false);
        if (!metafieldavailable) {
            ConcurrentLog.warn("FederateSearchManager", "webgraph option and webgraph Schema fields target_rel_s, target_protocol_s and target_urlstub_s must be switched on");
            return false;
        }
        // the solr search
        final String webgraphquerystr = WebgraphSchema.target_rel_s.getSolrFieldName() + ":search";
        final String[] webgraphqueryfields = {WebgraphSchema.target_protocol_s.getSolrFieldName(), WebgraphSchema.target_urlstub_s.getSolrFieldName()};
        // alternatively target_protocol_s + "://" +target_host_s + target_path_s

        final long numfound;
        try {
            SolrDocumentList docList = connector.getDocumentListByQuery(webgraphquerystr, null, 0, 1, webgraphqueryfields);
            numfound = docList.getNumFound();
            if (numfound == 0) {
                ConcurrentLog.info("FederateSearchManager", "no results found, abort discover job");
                return true;
            }
            ConcurrentLog.info("FederateSearchManager", "start checking " + Long.toString(numfound) + " found index results");
        } catch (final IOException ex) {
            ConcurrentLog.logException(ex);
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
                    Set<String> dblmem = new HashSet<String>(); // temp memory for already checked url
                    while (doloop) {
                        ConcurrentLog.info("FederateSearchManager", "start Solr query loop at " + Integer.toString(loopnr * 20) + " of " + Long.toString(numfound));
                        SolrDocumentList docList = connector.getDocumentListByQuery(webgraphquerystr, null, loopnr * 20, 20, webgraphqueryfields); // check chunk of 20 result documents
                        loopnr++;
                        if (stoptime < System.currentTimeMillis()) {// stop after max 1h
                            doloop = false;
                            ConcurrentLog.info("FederateSearchManager", "long running discover task aborted");
                        }
                        if (docList != null && docList.size() > 0) {
                            Iterator<SolrDocument> docidx = docList.iterator();
                            while (docidx.hasNext()) {
                                SolrDocument sdoc = docidx.next();

                                String hrefurltxt = sdoc.getFieldValue(WebgraphSchema.target_protocol_s.getSolrFieldName()) + "://" + sdoc.getFieldValue(WebgraphSchema.target_urlstub_s.getSolrFieldName());
                                try {
                                    URL url = new URL(hrefurltxt);
                                    //TODO: check Blacklist
                                    if (dblmem.add(url.getAuthority())) { // use only main path to detect double entries
                                        opensearchdescriptionReader os = new opensearchdescriptionReader(hrefurltxt);
                                        if (os.getRSSorAtomUrl() != null) {
                                            // add found system to config file
                                            addOpenSearchTarget(os.getShortName(), os.getRSSorAtomUrl(), false, os.getItem("LongName"));
                                            ConcurrentLog.info("FederateSearchManager", "added " + os.getShortName() + " " + hrefurltxt);
                                        } else {
                                            ConcurrentLog.info("FederateSearchManager", "osd.xml check failed (no RSS or Atom support) for " + hrefurltxt);
                                        }
                                    }
                                } catch (final MalformedURLException ex) {
                                }
                            }
                        } else {
                            doloop = false;
                        }
                    }
                    ConcurrentLog.info("FederateSearchManager", "finisched Solr query (checked " + Integer.toString(dblmem.size()) + " unique opensearchdescription links found in " + Long.toString(numfound) + " results)");
                } catch (final IOException ex) {
                    ConcurrentLog.logException(ex);
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
        confFile = new File(cfgFileName);
        if (confFile.exists()) {
            try {
                cfg = new Configuration(confFile);
                if (!this.conlist.isEmpty()) this.conlist.clear(); // prevent double entries
                Iterator<Entry> it = cfg.entryIterator();
                while (it.hasNext()) {
                    Entry cfgentry = it.next();
                    if (cfgentry.enabled()) { // hold only enabled in memory
                        String name = cfgentry.key();
                        String url = cfgentry.getValue();
                        if (url != null && !url.isEmpty()) {
                            if (url.startsWith("cfgfile:")) { // is cfgfile with field mappings (no opensearch url)
                                // config entry has 3 parts separated by :    1=cfgfile 2=connectortype 3=relative path to connector-cfg-file
                                // example   cfgfile:solrconnector:testsys.solr.schema
                                String[] parts = url.split(":");
                                if (parts[1].equalsIgnoreCase("solrconnector")) {
                                    SolrFederateSearchConnector sfc = new SolrFederateSearchConnector();
                                    if (sfc.init(name, confFile.getParent()+"/federatecfg/"+parts[2])) {
                                        conlist.add(sfc);
                                    }
                                } else {
                                    ConcurrentLog.config("FederateSearchManager", "Init error in configuration of: " + url);
                                }
                            } else { // handle opensearch url template
                                OpenSearchConnector osd;
                                osd = new OpenSearchConnector();
                                if (osd.init(name, url)) {
                                    conlist.add(osd);
                                }
                            }
                        }
                    }
                }
            } catch (IOException ex) {
                ConcurrentLog.logException(ex);
            }
        }
        return true;
    }

}

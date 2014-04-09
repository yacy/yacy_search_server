/**
 *  OpenSearchConnector
 *  Copyright 2012 by Michael Peter Christen
 *  First released 03.11.2012 at http://yacy.net
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
package net.yacy.cora.federate.opensearch;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.storage.Configuration;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.document.parser.xml.opensearchdescriptionReader;
import net.yacy.kelondro.blob.Tables;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.schema.WebgraphSchema;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

/**
 * Handling of queries to remote OpenSearch systems. Iterates to a list of
 * configured systems until number of needed results are available. Uses a
 * temporary work table to store search template urls for the iteration during
 * search.
 */
public class OpenSearchConnector {

    private File confFile = null; // later initialized to DATA/SETTINGS/heuristicopensearch.conf
    private int size = 0; // remember the size of active opensearch targets

    public OpenSearchConnector(Switchboard sb, boolean createworktable) {
        super();
        if (sb == null) {
            return;
        }

        confFile = new File(sb.getDataPath(), "DATA/SETTINGS/heuristicopensearch.conf");

        if (createworktable) { // read from config file and create worktable
            sb.tables.clear("opensearchsys");
            try {
                Configuration cfg = new Configuration(confFile);

                // copy active opensearch systems to a work table (opensearchsys)
                Iterator<Configuration.Entry> cfgentries = cfg.entryIterator();
                while (cfgentries.hasNext()) {
                    Configuration.Entry e = cfgentries.next();
                    if (e.enabled()) {
                        String title = e.key(); // get the title
                        String urlstr = e.getValue(); // get the search template url

                        Tables.Data row = new Tables.Data();
                        row.put("title", title);
                        row.put("url", urlstr);
                        try {
                            sb.tables.insert("opensearchsys", row);
                        } catch (final SpaceExceededException ex) {
                            ConcurrentLog.logException(ex);
                        }
                    }
                }
                size = sb.tables.size("opensearchsys");
            } catch (final IOException ex) {
                ConcurrentLog.logException(ex);
            }
        }
    }

    /**
     * Sends a search request to remote systems listed in worktable until the
     * searchevent contains less than needed results. Depending on already
     * collected search results none to all configured systems are queried to
     * complete available search results.
     * if query search domain is LOCAL procedure does nothing.
     */
    static public void query(Switchboard sb, SearchEvent theSearch) {
        if (theSearch != null && sb != null) {
            if (!theSearch.query.isLocal()) {
                try {
                    Iterator<Tables.Row> ossysworktable = sb.tables.iterator("opensearchsys");
                    //int needres = theSearch.query.neededResults(); // get number of needed results
                    while (ossysworktable.hasNext() /*&& theSearch.query.getResultCount() < needres*/) {
                        Tables.Row row = ossysworktable.next();
                        String osurl = row.get("url", "");
                        String name = row.get("title", "");
                        // to reuse existing heuristicRSS procedure replace querystring with "$"
                        // querystring is inserted/replaced inside heuristicRSS
                        sb.heuristicRSS(parseSearchTemplate(osurl, "$", 0, theSearch.query.itemsPerPage), theSearch, "opensearch:" + name);
                    }
                } catch (final IOException ex) {
                    ConcurrentLog.warn("OpenSearchConnector.query", "failed reading table opensearchsys");
                }
            }
        }
    }

    /**
     * replace Opensearchdescription search template parameter with actual values
     */
    private static String parseSearchTemplate(String searchurltemplate, String query, int start, int rows) {
        String tmps = searchurltemplate.replaceAll("\\?}", "}"); // some optional parameters may include question mark '{param?}='
        tmps = tmps.replace("{startIndex}", Integer.toString(start));
        tmps = tmps.replace("{startPage}", "");
        tmps = tmps.replace("{count}", Integer.toString(rows));
        tmps = tmps.replace("{language}", "");
        tmps = tmps.replace("{inputEncoding}", "UTF-8");
        tmps = tmps.replace("{outputEncoding}", "UTF-8");
        return tmps.replace("{searchTerms}", query);
    }

    /**
     * add a opensearch target system to the config file
     */
    public boolean add(String name, String url, boolean active, String comment) {
        if (confFile == null) {
            return false;
        }

        try {
            Configuration conf = new Configuration(confFile);
            if (name != null && !name.isEmpty()) {
                conf.add(name, null, active);
                Configuration.Entry e = conf.get(name);
                e.setValue(url);
                e.setEnable(active);
                e.setComment(comment);
                conf.put(name, e);
                try {
                    conf.commit();
                } catch (final IOException ex) {
                    ConcurrentLog.warn("OpenSearchConnector.add", "config file write error");
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
     * Get the number of active remote opensearch target systems
     */
    public int getSize() {
        return size;
    }

    /**
     * Discover opensearch description links from local (embedded) Solr index using
     * meta data field 'outboundlinks_tag_txt' and add found systems to the
     * config file
     *  
     * @return true if background discover job was started, false if job not started
     */
    public boolean discoverFromSolrIndex(final Switchboard sb) {
        if (sb == null) {
            return false;
        }
        // check if needed Solr fields are available (selected)
        if (!sb.index.fulltext().useWebgraph()) {
            ConcurrentLog.severe("OpenSearchConnector.Discover", "Error on connecting to embedded Solr webgraph index");
            return false;
        }
        final SolrConnector connector = sb.index.fulltext().getWebgraphConnector();
        final boolean metafieldavailable = sb.index.fulltext().getWebgraphConfiguration().contains(WebgraphSchema.target_rel_s.name()) 
                && ( sb.index.fulltext().getWebgraphConfiguration().contains(WebgraphSchema.target_protocol_s.name()) && sb.index.fulltext().getWebgraphConfiguration().contains(WebgraphSchema.target_urlstub_s.name()) ) 
                && sb.getConfigBool(SwitchboardConstants.CORE_SERVICE_WEBGRAPH, false);
        if (!metafieldavailable) {
            ConcurrentLog.warn("OpenSearchConnector.Discover", "webgraph option and webgraph Schema fields target_rel_s, target_protocol_s and target_urlstub_s must be switched on");
            return false;
        }
        // the solr query
        final String webgraphquerystr = WebgraphSchema.target_rel_s.getSolrFieldName() + ":search";
        final String[] webgraphqueryfields = { WebgraphSchema.target_protocol_s.getSolrFieldName() , WebgraphSchema.target_urlstub_s.getSolrFieldName()};
        // alternatively target_protocol_s + "://" +target_host_s + target_path_s

        final long numfound;
        try {
            SolrDocumentList docList = connector.getDocumentListByQuery(webgraphquerystr, null, 0, 1, webgraphqueryfields);
            numfound = docList.getNumFound();
            if (numfound == 0) {
                ConcurrentLog.info("OpenSearchConnector.Discover", "no results found, abort discover job");
                return true;
            }
            ConcurrentLog.info("OpenSearchConnector.Discover", "start checking " + Long.toString(numfound) + " found index results");
        } catch (final IOException ex) {
            ConcurrentLog.logException(ex);
            return false;
        }

        final long stoptime = System.currentTimeMillis() + 1000 * 3600; // make sure job doesn't run forever

        // job to iterate through Solr index to find links to opensearchdescriptions
        // started as background job as connect timeouts may cause it run a long time
        final Thread job = new Thread() {
            @Override
            public void run() {
                try {
                    boolean doloop = true;
                    int loopnr = 0;
                    Set<String> dblmem = new HashSet<String>(); // temp memory for already checked url
                    while (doloop) {
                        ConcurrentLog.info("OpenSearchConnector.Discover", "start Solr query loop at " + Integer.toString(loopnr * 20) + " of " + Long.toString(numfound));
                        SolrDocumentList docList = connector.getDocumentListByQuery(webgraphquerystr, null, loopnr * 20, 20,webgraphqueryfields); // check chunk of 20 result documents
                        loopnr++;
                        if (stoptime < System.currentTimeMillis()) {// stop after max 1h
                            doloop = false;
                            ConcurrentLog.info("OpenSearchConnector.Discover", "long running discover task aborted");
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
                                            add(os.getShortName(), os.getRSSorAtomUrl(), false, os.getItem("LongName"));
                                            ConcurrentLog.info("OpenSearchConnector.Discover", "added " + os.getShortName() + " " + hrefurltxt);
                                        } else {
                                            ConcurrentLog.info("OpenSearchConnector.Discover", "osd.xml check failed (no RSS or Atom support) for " + hrefurltxt);
                                        }
                                    }
                                } catch (final MalformedURLException ex) {
                                }
                            }
                        } else {
                            doloop = false;
                        }
                    }
                    ConcurrentLog.info("OpenSearchConnector.Discover", "finisched Solr query (checked " + Integer.toString(dblmem.size()) + " unique opensearchdescription links found in " + Long.toString(numfound) + " results)");
                } catch (final IOException ex) {
                    ConcurrentLog.logException(ex);
                }
            }
        };
        job.start();
        return true;
    }
}

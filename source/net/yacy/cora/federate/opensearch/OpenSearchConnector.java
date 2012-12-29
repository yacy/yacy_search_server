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

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.cora.federate.solr.connector.EmbeddedSolrConnector;
import net.yacy.cora.federate.yacy.ConfigurationSet;
import net.yacy.cora.federate.yacy.ConfigurationSet.Entry;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.document.parser.xml.opensearchdescriptionReader;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.logging.Log;
import net.yacy.search.Switchboard;
import net.yacy.search.query.SearchEvent;
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
                ConfigurationSet cfg = new ConfigurationSet(confFile);

                // copy active opensearch systems to a work table (opensearchsys)
                Iterator<Entry> cfgentries = cfg.entryIterator();
                while (cfgentries.hasNext()) {
                    Entry e = cfgentries.next();
                    if (e.enabled()) {
                        String title = e.key(); // get the title
                        String urlstr = e.getValue(); // get the search template url

                        Tables.Data row = new Tables.Data();
                        row.put("title", title);
                        row.put("url", urlstr);
                        try {
                            sb.tables.insert("opensearchsys", row);
                        } catch (SpaceExceededException ex) {
                            Log.logException(ex);
                        }
                    }
                }
                size = sb.tables.size("opensearchsys");
            } catch (IOException ex) {
                Log.logException(ex);
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
                    int needres = theSearch.query.neededResults(); // get number of needed results
                    while (ossysworktable.hasNext() && theSearch.query.getResultCount() < needres) {
                        Tables.Row row = ossysworktable.next();
                        String osurl = row.get("url", "");
                        String name = row.get("title", "");
                        // to reuse existing heuristicRSS procedure replace querystring with "$"
                        // querystring is inserted/replaced inside heuristicRSS
                        sb.heuristicRSS(parseSearchTemplate(osurl, "$", 0, theSearch.query.itemsPerPage), theSearch, "opensearch:" + name);
                    }
                } catch (IOException ex) {
                    Log.logWarning("OpenSearchConnector.query", "failed reading table opensearchsys");
                }
            }
        }
    }

    /**
     * replace Opensearchdescription search template parameter with actual values
     */
    private static String parseSearchTemplate(String searchurltemplate, String query, int start, int rows) {
        String tmps = searchurltemplate.replaceAll("\\?}=", "}="); // some optional parameters may include question mark '{param?}='
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

        ConfigurationSet conf = new ConfigurationSet(confFile);
        if (name != null && !name.isEmpty()) {
            conf.add(name, null, active);
            Entry e = conf.get(name);
            e.setValue(url);
            e.setEnable(active);
            e.setComment(comment);
            conf.put(name, e);
            try {
                conf.commit();
            } catch (IOException ex) {
                Log.logWarning("OpenSearchConnector.add", "config file write error");
            }
            return true;
        } else {
            return false;
        }
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
     */
    public boolean discoverFromSolrIndex(final Switchboard sb) {
        if (sb == null) {
            return false;
        }
        final EmbeddedSolrConnector connector = (EmbeddedSolrConnector) sb.index.fulltext().getLocalSolr();
        // check if needed Solr fields are available (selected)
        if (connector == null) {
            Log.logSevere("OpenSearchConnector.Discover", "Error on connecting to embedded Solr index");
            return false;
        }
        final boolean metafieldNOTavailable = sb.index.fulltext().getSolrScheme().containsDisabled(YaCySchema.outboundlinks_tag_txt.name());
        if (metafieldNOTavailable) {
            Log.logWarning("OpenSearchConnector.Discover", "Solr Schema field outboundlinks_tag_txt must be switched on");
            return false;
        }
        // the solr query
        final String solrquerystr = YaCySchema.outboundlinks_tag_txt.getSolrFieldName() + ":\"rel=\\\"search\\\"\" OR "
                + YaCySchema.inboundlinks_tag_txt.getSolrFieldName() + ":\"rel=\\\"search\\\"\"&fl="
                + YaCySchema.sku.getSolrFieldName() + "," + YaCySchema.outboundlinks_tag_txt.getSolrFieldName() +"," + YaCySchema.inboundlinks_tag_txt.getSolrFieldName();
        final long numfound;
        try {
            SolrDocumentList docList = connector.query(solrquerystr, 0, 1);
            numfound = docList.getNumFound();
            if (numfound == 0) {
                Log.logInfo("OpenSearchConnector.Discover", "no results found, abort discover job");
                return false;
            } else {
                Log.logInfo("OpenSearchConnector.Discover", "start checking " + Long.toString(numfound) + " found index results");
            }
        } catch (IOException ex) {
            Log.logException(ex);
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
                        Log.logInfo("OpenSearchConnector.Discover", "start Solr query loop at " + Integer.toString(loopnr * 20) + " of " + Long.toString(numfound));
                        SolrDocumentList docList = connector.query(solrquerystr, loopnr * 20, 20); // check chunk of 20 result documents
                        loopnr++;
                        if (stoptime < System.currentTimeMillis()) {// stop after max 1h
                            doloop = false;
                            Log.logInfo("OpenSearchConnector.Discover", "long running discover task aborted");
                        }
                        if (docList != null && docList.size() > 0) {
                            Iterator<SolrDocument> docidx = docList.iterator();
                            while (docidx.hasNext()) {
                                SolrDocument sdoc = docidx.next();
                                Collection<Object> tagtxtlist = sdoc.getFieldValues(YaCySchema.outboundlinks_tag_txt.getSolrFieldName());
                                if (tagtxtlist == null) {
                                    tagtxtlist = sdoc.getFieldValues(YaCySchema.inboundlinks_tag_txt.getSolrFieldName());
                                } else {
                                    tagtxtlist.addAll(sdoc.getFieldValues(YaCySchema.inboundlinks_tag_txt.getSolrFieldName()));
                                }
                                Iterator<Object> tagtxtidx = tagtxtlist.iterator();
                                while (tagtxtidx.hasNext()) {
                                    // check and extract links to opensearchdescription
                                    // example: <a href="http://url/osd.xml" rel="search" name="xyz.com"></a>
                                    String tagtxt = (String) tagtxtidx.next();
                                    if (tagtxt.contains("search")) {
                                        int hrefstartpos = tagtxt.indexOf("href=");
                                        if (hrefstartpos > 0) {
                                            String hrefendpos = tagtxt.substring(hrefstartpos + 6);
                                            hrefstartpos = hrefendpos.indexOf('"');
                                            String hrefurltxt = hrefendpos.substring(0, hrefstartpos); // hrefurltxt contains now url to opensearchdescription
                                            try {
                                                URL url = new URL(hrefurltxt);
                                                //TODO: check Blacklist
                                                if (dblmem.add(url.getAuthority())) { // use only main path to detect double entries
                                                    opensearchdescriptionReader os = new opensearchdescriptionReader(hrefurltxt);
                                                    if (os.getRSSorAtomUrl() != null) {
                                                        // add found system to config file
                                                        add(os.getShortName(), os.getRSSorAtomUrl(), false, os.getItem("LongName"));
                                                        Log.logInfo("OpenSearchConnector.Discover", "added " + os.getShortName() + " " + hrefurltxt);
                                                    } else {
                                                        Log.logInfo("OpenSearchConnector.Discover", "osd.xml check failed (no RSS or Atom support) for " + hrefurltxt);
                                                    }
                                                }
                                            } catch (MalformedURLException ex) {
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            doloop = false;
                        }
                    }
                    Log.logInfo("OpenSearchConnector.Discover", "finisched Solr query (checked " + Integer.toString(dblmem.size()) + " unique opensearchdescription links found in " + Long.toString(numfound) + " results)");
                } catch (IOException ex) {
                    Log.logException(ex);
                }
            }
        };
        job.start();
        return true;
    }
}

/**
 * AbstractFederateSearchConnector.java
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.SchemaConfiguration;
import net.yacy.cora.federate.solr.SchemaDeclaration;
import net.yacy.cora.federate.solr.SolrType;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.storage.Configuration;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.Switchboard;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.schema.CollectionSchema;
import org.apache.solr.common.SolrDocument;

/**
 * Base implementation class for Federated Search Connectors providing the basic
 * funcitonality to search none YaCy systems
 * <ul>
 * <li> init() to read config file
 * <li> toYaCySchema() to convert remote schema fields to YaCy internal schema
 * names, called by query()
 * <li> query() needs to be implemented in specific connectors
 * <li> search() call's query() in a thread and adds results to internal search request.
 * </ul>
 * Subclasses should/need to override query() and maybe toYaCySchema() if more
 * is needed as a basic field mapping
 */
abstract public class AbstractFederateSearchConnector implements FederateSearchConnector {

    public String instancename; // just a identifying name
    protected SchemaConfiguration localcfg; // the schema conversion cfg for each fieldname, yacyname = remote fieldname
    public long lastaccesstime = -1; // last time accessed, used for search delay calculation
    protected String baseurl;

    /**
     * Inits the connector with the remote field names and matches to yacy
     * schema and other specific settings from config file. Every connector
     * needs at least a query target (where to query) and some definition to
     * convert the remote serch result to the internal result presentation
     * (field mapping)
     *
     * @param instanceName internal name
     * @param cfgFileName e.g. DATA/SETTINGS/FEDERATECFG/instanceName.SCHEMA
     * @return true if success false if not
     */
    @Override
    public boolean init(String instance, String cfgFileName) {
        this.instancename = instance;
        File instanceCfgFile = new File(cfgFileName);
        if (instanceCfgFile.exists()) {
            try {
                this.localcfg = new SchemaConfiguration(instanceCfgFile);
            } catch (IOException ex) {
                ConcurrentLog.config(this.instancename, "error reading schema " + cfgFileName);
                return false;
            }
            // mandatory to contain a mapping for "sku" or alternatively "cfg_skufieldname" for a conversion to a final url
            if (this.localcfg.contains(CollectionSchema.sku) || this.localcfg.contains("_skufieldname")) {
                return true;
            }
            ConcurrentLog.config(this.instancename, "mandatory mapping for sku or _skufieldname missing in " + cfgFileName);
            return false;
        }
        this.localcfg = null;
        return false;
    }

    /**
     * queries a remote system and adds the results to the searchevent and to
     * the crawler if addResultsToLocalIndex is true
     *
     * @param theSearch receiving the results
     */
    @Override
    public void search(final SearchEvent theSearch) {

        final Thread job = new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("heuristic:" + instancename);
                theSearch.oneFeederStarted();
                List<URIMetadataNode> doclist = query(theSearch.getQuery());
                if (doclist != null) {
                    Map<String, LinkedHashSet<String>> snippets = new HashMap<String, LinkedHashSet<String>>(); // add nodes doesn't allow null
                    Map<String, ReversibleScoreMap<String>> facets = new HashMap<String, ReversibleScoreMap<String>>(); // add nodes doesn't allow null
                    theSearch.addNodes(doclist, facets, snippets, false, instancename, doclist.size());
                    
                    for (URIMetadataNode doc : doclist) {
                        theSearch.addHeuristic(doc.hash(), instancename, false);
                    }
                }
                // that's all we need to display serach result
                theSearch.oneFeederTerminated();

                // optional: add to crawler to get the full resource (later)
                if (doclist != null && !doclist.isEmpty() && theSearch.addResultsToLocalIndex) {
                    Collection<DigestURL> urls = new ArrayList<DigestURL>();
                    for (URIMetadataNode doc : doclist) {
                        urls.add(doc.url());
                    }
                    Switchboard.getSwitchboard().addToCrawler(urls, false);

                }
            }
        };
        job.start();
    }

    /**
     * Converts a remote schema result to YaCy schema using the fieldname
     * mapping provided as config file
     *
     * @param remote result (with remote fieldnames)
     * @return SolrDocument with field names according to the YaCy schema
     */
    protected URIMetadataNode toYaCySchema(final SolrDocument doc) {
        // set YaCy id
        String urlstr;
        if (localcfg.contains("sku"))  {
            urlstr = (String) doc.getFieldValue(localcfg.get("sku").getValue());
        } else {
            urlstr = (String) doc.getFieldValue(localcfg.get("_skufieldname").getValue());
            if (this.localcfg.contains("_skuprefix")) {
                String skuprefix = this.localcfg.get("_skuprefix").getValue();
                urlstr = skuprefix + urlstr;
            }
        }

        URIMetadataNode newdoc = new URIMetadataNode(urlstr);
        Iterator<Configuration.Entry> it = localcfg.entryIterator();
        while (it.hasNext()) {
            Configuration.Entry et = it.next();
            String yacyfieldname = et.key(); // config defines    yacyfieldname = remotefieldname
            String remotefieldname = et.getValue();
            if (remotefieldname != null && !remotefieldname.isEmpty()) {
                if (Switchboard.getSwitchboard().index.fulltext().getDefaultConfiguration().contains(yacyfieldname)) { // check if in local config

                    SchemaDeclaration est = CollectionSchema.valueOf(yacyfieldname);
                    if (est.isMultiValued()) {
                        if (doc.getFieldValues(remotefieldname) != null) {
                            newdoc.addField(yacyfieldname, doc.getFieldValues(remotefieldname)); //
                        }
                    } else {
                        if (doc.getFieldValue(remotefieldname) != null) {
                            Object val = doc.getFirstValue(remotefieldname);
                            // watch out for type conversion
                            try {
                                if (est.getType() == SolrType.num_integer && val instanceof String) {
                                    newdoc.setField(yacyfieldname, Integer.parseInt((String) val));
                                } else {
                                    newdoc.setField(yacyfieldname, val);
                                }
                            } catch (Exception ex) {
                                continue; // catch possible parse or type mismatch, skip the field
                            }
                        }
                    }
                }
            }
        }

        newdoc.addField(CollectionSchema.httpstatus_i.name(), HttpServletResponse.SC_OK); // yacy required
        return newdoc;
    }
}

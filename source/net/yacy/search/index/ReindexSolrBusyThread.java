package net.yacy.search.index;
/**
 *  ReindexSolrBusyThread
 *  Copyright 2013 by Michael Peter Christen
 *  First released 13.05.2013 at http://yacy.net
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

import java.io.IOException;

import net.yacy.search.Switchboard;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.workflow.AbstractBusyThread;
import net.yacy.search.schema.CollectionConfiguration;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;

   
    /**
     * Reindex selected documents of embedded Solr index.
     * As the <b>toSolrInputDocument</b> acts only on current schema fields 
     * this can be used to remove obsolete fields physically from index
     * 
     * can be deployed as BusyThread which is periodically called by system allowing easy interruption 
     * after each reindex chunk of 100 documents.
     * If queue is empty this removes itself from list of servers workerthreads list
     * Process:  - initialize with one or more select queries
     *           - deploy as BusyThread (or call job repeatedly until it returns false)
     *              - job reindexes on each call chunk of 100 documents       
     */
     public class ReindexSolrBusyThread extends AbstractBusyThread {

        SolrConnector esc;
        final CollectionConfiguration colcfg; // collection config
        int processed = 0; // total number of reindexed documents
        int docstoreindex = 0; // documents found to reindex for current query
        Semaphore sem = new Semaphore(1);
        ArrayList<String> querylist = new ArrayList<String>(); // list of select statements to reindex
        int start = 0; // startindex
        int chunksize = 100; // number of documents to reindex per cycle
        
        /**        
         * @param query = a solr query to select documents to reindex (like h5_txt:[* TO *])
         */
        public ReindexSolrBusyThread(String query) {
            super(100,1000,0,500);
            this.esc = Switchboard.getSwitchboard().index.fulltext().getDefaultConnector();
            this.colcfg = Switchboard.getSwitchboard().index.fulltext().getDefaultConfiguration();

            if (Switchboard.getSwitchboard().getThread("reindexSolr") != null) {
                this.interrupt(); // only one active reindex job should exist
            } else {
                if (query != null) {
                    this.querylist.add(query);
                }
            }   
            setName("reindexSolr");
            this.setPriority(Thread.MIN_PRIORITY);

        }

        /**
         * add a query selecting documents to reindex
         */
        public void addSelectQuery(String query) {
            if (query != null && !query.isEmpty()) {
                querylist.add(query);
            }
        }
       
        /**
         * add a fieldname to select documents to reindex all documents
         * containing the given fieldname are reindexed
         *
         * @param field a solr fieldname
         */
        public void addSelectFieldname(String field) {
            if (field != null && !field.isEmpty()) {
                querylist.add(field + AbstractSolrConnector.CATCHALL_DTERM);
            }
        }
       
        /**
         * each call reindexes a chunk of 100 documents until all selected documents are reindexed
         * @return false if no documents selected
         */
        @Override
        public boolean job() {
            boolean ret = true;
            if (esc != null && colcfg != null && querylist.size() > 0) {

                if (sem.tryAcquire()) {
                    try {
                        String query = querylist.get(0);
                        SolrDocumentList xdocs = esc.getDocumentListByQuery(query, null, start, chunksize);
                        docstoreindex = (int) xdocs.getNumFound();
                        
                        if (xdocs.size() == 0) { // no documents returned = all of current query reindexed (or eventual start to large)                                                       
                            querylist.remove(0); // consider normal case and remove current query                           
                            
                            if (start > 0) { // if previous cycle reindexed, commit to prevent reindex of same documents
                                esc.commit(true);
                                start = 0;
                            }
                            
                            if (chunksize < 100) { // try to increase chunksize (if reduced by freemem)
                                chunksize = chunksize + 10;
                            }
                        } else {
                            ConcurrentLog.info("MIGRATION-REINDEX", "reindex docs with query=" + query + " found=" + docstoreindex + " start=" + start);
                            start = start + chunksize;
                            
                            for (SolrDocument doc : xdocs) {
                                SolrInputDocument idoc = colcfg.toSolrInputDocument(doc);
                                Switchboard.getSwitchboard().index.putDocument(idoc);
                                processed++;
                            }
                        }                        
                    } catch (final IOException ex) {
                        ConcurrentLog.warn("MIGRATION-REINDEX", "remove following query from list due to error, q=" + querylist.remove(0));
                        ConcurrentLog.logException(ex);
                    } finally {
                        sem.release();
                    }
                }
            } else {
                ret = false;
            }

            if (querylist.isEmpty()) { // if all processed remove from scheduled list (and terminate thread)
                Switchboard.getSwitchboard().terminateThread("reindexSolr", false);
                ret = false;
            }
            return ret;
        }
              
      
         @Override
         public void terminate(final boolean waitFor) {
             querylist.clear();
             // if interrupted without finished commit to reflect latest changes
             if (docstoreindex > 0 && processed > 0) {
                 esc.commit(true);
             }
             super.terminate(waitFor);
         }
        
        /**         
         * @return total number of processed documents
         */
        public int getProcessed() {
            return processed;
        }
        
        /**
         * @return the currently processed Solr select query 
         */
        public String getCurrentQuery() {            
            return querylist.isEmpty() ? "" : querylist.get(0);
        }

        /**          
         * @return copy of all Solr select queries in the queue or null if empty
         */
        public String[] getQueryList() {
            if (querylist != null) {
                String[] list = new String[querylist.size()];
                list = querylist.toArray(list);
                return list;
            }
            return null;
        }
        
        /**
         * @return number of currently selected (found) documents
         */
        @Override
        public int getJobCount() {
            return docstoreindex;
        }

        @Override
        public void freemem() {
            // reduce number of docs processed in one job cycle
            if (chunksize > 2) {
                this.chunksize = this.chunksize / 2;
            }
            esc.commit(true);
            start = 0;            
        }

    }


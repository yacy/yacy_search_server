/**
 *  ResponseAccumulator
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

import java.util.Collection;
import java.util.Map;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;

public class ResponseAccumulator {

    final SimpleOrderedMap<Object> fieldsAcc;
    final SimpleOrderedMap<Object> index_countsAcc;
    final SimpleOrderedMap<Object> facet_countsAcc;
    final SimpleOrderedMap<Object> highlightingAcc;
    final SimpleOrderedMap<Object> headerAcc;
    final SolrDocumentList resultsAcc;

    public ResponseAccumulator() {
        this.fieldsAcc = new SimpleOrderedMap<Object>();
        this.index_countsAcc = new SimpleOrderedMap<Object>();
        this.facet_countsAcc = new SimpleOrderedMap<Object>();
        this.highlightingAcc = new SimpleOrderedMap<Object>();
        this.headerAcc = new SimpleOrderedMap<Object>();
        this.resultsAcc = new SolrDocumentList();
        
    }
    
    public void addResponse(NamedList<Object> response) {
        // set the header; this is mostly always the same (well this is not evaluated much)
        @SuppressWarnings("unchecked")
        SimpleOrderedMap<Object> header = (SimpleOrderedMap<Object>) response.get("responseHeader");
        //Integer status = (Integer) header.get("status");
        //Integer QTime = (Integer) header.get("QTime");
        //SimpleOrderedMap<Object> params = (SimpleOrderedMap<Object>) header.get("params");
        if (headerAcc.size() == 0) {
            for (Map.Entry<String, Object> e: header) headerAcc.add(e.getKey(), e.getValue());
        }
        
        // accumulate the results
        SolrDocumentList results = (SolrDocumentList) response.get("response");
        if (results != null) {
            long found = results.size();
            for (int i = 0; i < found; i++) resultsAcc.add(results.get(i));
            resultsAcc.setNumFound(resultsAcc.getNumFound() + results.getNumFound());
            resultsAcc.setMaxScore(Math.max(resultsAcc.getMaxScore() == null ? 0f : resultsAcc.getMaxScore().floatValue(), results.getMaxScore() == null ? 0f : results.getMaxScore().floatValue()));
        }
        
        // accumulate the highlighting
        @SuppressWarnings("unchecked")
        SimpleOrderedMap<Object> highlighting = (SimpleOrderedMap<Object>) response.get("highlighting");
        if (highlighting != null) {
            for (Map.Entry<String, Object> e: highlighting) highlightingAcc.add(e.getKey(), e.getValue());
        }
        
        // accumulate the facets (well this is not correct at this time...)
        @SuppressWarnings("unchecked")
        SimpleOrderedMap<Object> facet_counts = (SimpleOrderedMap<Object>) response.get("facet_counts");
        if (facet_counts != null) {
            for (Map.Entry<String, Object> e: facet_counts) facet_countsAcc.add(e.getKey(), e.getValue());
        }
        
        // accumulate the index
        @SuppressWarnings("unchecked")
        SimpleOrderedMap<Object> index_counts = (SimpleOrderedMap<Object>) response.get("index");
        if (index_counts != null) {
            for (Map.Entry<String, Object> e: index_counts) index_countsAcc.add(e.getKey(), e.getValue());
        }
        
        // accumulate the fields
        @SuppressWarnings("unchecked")
        SimpleOrderedMap<Object> schema = (SimpleOrderedMap<Object>) response.get("schema");
        if (schema != null) {
            @SuppressWarnings("unchecked")
            SimpleOrderedMap<Object> fields = (SimpleOrderedMap<Object>) schema.get("fields");
            if (fields != null) {
                for (Map.Entry<String, Object> e: fields) fieldsAcc.add(e.getKey(), e.getValue());
            }
        }
        @SuppressWarnings("unchecked")
        SimpleOrderedMap<Object> fields = (SimpleOrderedMap<Object>) response.get("fields");
        if (fields != null) {
            for (Map.Entry<String, Object> e: fields) fieldsAcc.add(e.getKey(), e.getValue());
        }
    }
    
    public NamedList<Object> getAccumulatedResponse() {
        // prepare combined response
        NamedList<Object> responsesAcc = new NamedList<Object>();
        responsesAcc.add("responseHeader", headerAcc);
        responsesAcc.add("response", resultsAcc);
        if (highlightingAcc != null && highlightingAcc.size() > 0) responsesAcc.add("highlighting", highlightingAcc);
        if (facet_countsAcc != null && facet_countsAcc.size() > 0) responsesAcc.add("facet_counts", facet_countsAcc);
        if (index_countsAcc != null && index_countsAcc.size() > 0) responsesAcc.add("index", index_countsAcc);
        if (fieldsAcc != null && fieldsAcc.size() > 0) responsesAcc.add("fields", fieldsAcc);
        return responsesAcc;
    }

    public static QueryResponse combineResponses(Collection<QueryResponse> qrl) {
        ResponseAccumulator acc = new ResponseAccumulator();
        for (final QueryResponse rsp: qrl) {
            NamedList<Object> response = rsp.getResponse();
            acc.addResponse(response);
        }
        
        // prepare combined response
        QueryResponse rspAcc = new QueryResponse();
        rspAcc.setResponse(acc.getAccumulatedResponse());
        return rspAcc;
    }
}

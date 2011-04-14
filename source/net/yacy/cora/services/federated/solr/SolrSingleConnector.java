package net.yacy.cora.services.federated.solr;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;

import net.yacy.document.Document;


public class SolrSingleConnector {

    private String solrurl;
    private SolrServer server;
    private SolrScheme scheme;
    
    public SolrSingleConnector(String url, SolrScheme scheme) throws IOException {
        this.solrurl = url;
        this.scheme = scheme;
        try {
            this.server = new SolrHTTPClient(this.solrurl);
        } catch (MalformedURLException e) {
            throw new IOException("bad connector url: " + this.solrurl);
        }
    }
    
    /**
     * delete everything in the solr index
     * @throws IOException
     */
    public void clear() throws IOException {
        try {
            server.deleteByQuery("*:*");
        } catch (SolrServerException e) {
            throw new IOException(e);
        }
    }
    
    public void delete(String id) throws IOException {
        try {
            server.deleteById(id);
        } catch (SolrServerException e) {
            throw new IOException(e);
        }
    }
    
    public void delete(List<String> ids) throws IOException {
        try {
            server.deleteById(ids);
        } catch (SolrServerException e) {
            throw new IOException(e);
        }
    }
    
    public void add(File file, String solrId) throws IOException {
        ContentStreamUpdateRequest up = new ContentStreamUpdateRequest("/update/extract");
        up.addFile(file);
        up.setParam("literal.id", solrId);
        up.setParam("uprefix", "attr_");
        up.setParam("fmap.content", "attr_content");
        //up.setAction(AbstractUpdateRequest.ACTION.COMMIT, true, true);
        try {
            server.request(up);
            server.commit();
        } catch (SolrServerException e) {
            throw new IOException(e);
        }
    }
    
    /*
    public void addx(File file, String solrId) throws IOException {
        ContentStreamUpdateRequest up = new ContentStreamUpdateRequest("/update/extract");
        ModifiableSolrParams params = new ModifiableSolrParams();
        List<ContentStream> contentStreams = new ArrayList<ContentStream>();
        contentStreams.add(new ContentStreamBase.FileStream(file));
        params.set("literal.id", solrId);
        params.set("uprefix", "attr_");
        params.set("fmap.content", "attr_content");
        params.set( UpdateParams.COMMIT, "true" );
        params.set( UpdateParams.WAIT_FLUSH, String.valueOf(true));
        params.set( UpdateParams.WAIT_SEARCHER, String.valueOf(true));
          
        try {
            server.
            server.request(up);
        } catch (SolrServerException e) {
            throw new IOException(e);
        }
    }
    */
    
    public void add(String id, Document doc) throws IOException {
        add(id, doc, this.scheme);
    }
    
    public void add(String id, Document doc, SolrScheme tempScheme) throws IOException {
        addSolr(tempScheme.yacy2solr(id, doc));
    }
    
    protected void addSolr(SolrInputDocument doc) throws IOException {
        Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument>();
        docs.add(doc);
        addSolr(docs);
    }
    
    protected void addSolr(Collection<SolrInputDocument> docs) throws IOException {
        try {
            server.add(docs);
            server.commit();
            /* To immediately commit after adding documents, you could use: 
                  UpdateRequest req = new UpdateRequest();
                  req.setAction( UpdateRequest.ACTION.COMMIT, false, false );
                  req.add( docs );
                  UpdateResponse rsp = req.process( server );
             */
        } catch (SolrServerException e) {
            throw new IOException(e);
        }
    }
    
    /**
     * get a query result from solr
     * to get all results set the query String to "*:*"
     * @param querystring
     * @throws IOException
     */
    public SolrDocumentList get(String querystring, int offset, int count) throws IOException {
        // construct query
        SolrQuery query = new SolrQuery();
        query.setQuery(querystring);
        query.setRows(count);
        query.setStart(offset);
        query.addSortField( "price", SolrQuery.ORDER.asc );
        
        // query the server
        //SearchResult result = new SearchResult(count);
        try {
            QueryResponse rsp = server.query( query );
            SolrDocumentList docs = rsp.getResults();
            return docs;
            // add the docs into the YaCy search result container
            /*
            for (SolrDocument doc: docs) {
                result.put(element)
            }
            */
        } catch (SolrServerException e) {
            throw new IOException(e);
        }
        
        //return result;
    }
    
    public static void main(String args[]) {
        SolrSingleConnector solr;
        try {
            solr = new SolrSingleConnector("http://127.0.0.1:8983/solr", SolrScheme.SolrCell);
            solr.clear();
            File exampleDir = new File("/Data/workspace2/yacy/test/parsertest/");
            long t, t0, a = 0;
            int c = 0;
            for (String s: exampleDir.list()) {
                if (s.startsWith(".")) continue;
                t = System.currentTimeMillis();
                solr.add(new File(exampleDir, s), s);
                t0 = (System.currentTimeMillis() - t);
                a += t0;
                c++;
                System.out.println("pushed file " + s + " to solr, " + t0 + " milliseconds");
            }
            System.out.println("pushed " + c + " files in " + a + " milliseconds, " + (a / c) + " milliseconds average; " + (60000 / a * c) + " PPM");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}

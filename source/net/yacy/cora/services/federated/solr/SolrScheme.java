package net.yacy.cora.services.federated.solr;


import net.yacy.cora.document.UTF8;
import net.yacy.document.Document;
import net.yacy.kelondro.data.meta.DigestURI;

import org.apache.solr.common.SolrInputDocument;

public enum SolrScheme {

    SolrCell,
    DublinCore;

    
    public SolrInputDocument yacy2solr(String id, Document document) {
        if (this == SolrCell) return yacy2solrSolrCell(id, document);
        return null;
    }
    
    public static SolrInputDocument yacy2solrSolrCell(String id, Document yacydoc) {
        // we user the SolrCell design as index scheme
        SolrInputDocument solrdoc = new SolrInputDocument();
        DigestURI digestURI = new DigestURI(yacydoc.dc_source());
        solrdoc.addField("id", id);
        solrdoc.addField("sku", digestURI.toNormalform(true, false), 3.0f);
        /*
         *
    private final MultiProtocolURI source;      // the source url
    private final String mimeType;              // mimeType as taken from http header
    private final String charset;               // the charset of the document
    private final List<String> keywords;        // most resources provide a keyword field
    private       StringBuilder title;          // a document title, taken from title or h1 tag; shall appear as headline of search result
    private final StringBuilder creator;        // author or copyright
    private final String publisher;             // publisher
    private final List<String>  sections;       // if present: more titles/headlines appearing in the document
    private final StringBuilder description;    // an abstract, if present: short content description
    private Object text;                        // the clear text, all that is visible
    private final Map<MultiProtocolURI, String> anchors; // all links embedded as clickeable entities (anchor tags)
    private final Map<MultiProtocolURI, String> rss; // all embedded rss feeds
    private final Map<MultiProtocolURI, ImageEntry> images; // all visible pictures in document
    // the anchors and images - Maps are URL-to-EntityDescription mappings.
    // The EntityDescription appear either as visible text in anchors or as alternative
    // text in image tags.
    private Map<MultiProtocolURI, String> hyperlinks, audiolinks, videolinks, applinks;
    private Map<String, String> emaillinks;
    private MultiProtocolURI favicon;
    private boolean resorted;
    private int inboundLinks, outboundLinks; // counters for inbound and outbound links, are counted after calling notifyWebStructure
    private Set<String> languages;
    private boolean indexingDenied;
    private float lon, lat;
         */
        solrdoc.addField("title", yacydoc.dc_title());
        solrdoc.addField("author", yacydoc.dc_creator());
        solrdoc.addField("description", yacydoc.dc_description());
        solrdoc.addField("content_type", yacydoc.dc_format());
        solrdoc.addField("subject", yacydoc.dc_subject(' '));
        solrdoc.addField("text", UTF8.String(yacydoc.getTextBytes()));
        return solrdoc;
    }
    
    
    /*
     * standard solr scheme

   <field name="id" type="string" indexed="true" stored="true" required="true" /> 
   <field name="sku" type="textTight" indexed="true" stored="true" omitNorms="true"/>
   <field name="name" type="textgen" indexed="true" stored="true"/>
   <field name="alphaNameSort" type="alphaOnlySort" indexed="true" stored="false"/>
   <field name="manu" type="textgen" indexed="true" stored="true" omitNorms="true"/>
   <field name="cat" type="string" indexed="true" stored="true" multiValued="true"/>
   <field name="features" type="text" indexed="true" stored="true" multiValued="true"/>
   <field name="includes" type="text" indexed="true" stored="true" termVectors="true" termPositions="true" termOffsets="true" />

   <field name="weight" type="float" indexed="true" stored="true"/>
   <field name="price"  type="float" indexed="true" stored="true"/>
   <field name="popularity" type="int" indexed="true" stored="true" />
   <field name="inStock" type="boolean" indexed="true" stored="true" />

   <!-- Common metadata fields, named specifically to match up with
     SolrCell metadata when parsing rich documents such as Word, PDF.
     Some fields are multiValued only because Tika currently may return
     multiple values for them.
   -->
   <field name="title" type="text" indexed="true" stored="true" multiValued="true"/>
   <field name="subject" type="text" indexed="true" stored="true"/>
   <field name="description" type="text" indexed="true" stored="true"/>
   <field name="comments" type="text" indexed="true" stored="true"/>
   <field name="author" type="textgen" indexed="true" stored="true"/>
   <field name="keywords" type="textgen" indexed="true" stored="true"/>
   <field name="category" type="textgen" indexed="true" stored="true"/>
   <field name="content_type" type="string" indexed="true" stored="true" multiValued="true"/>
   <field name="last_modified" type="date" indexed="true" stored="true"/>
   <field name="links" type="string" indexed="true" stored="true" multiValued="true"/>

   <!-- catchall field, containing all other searchable text fields (implemented
        via copyField further on in this schema  -->
   <field name="text" type="text" indexed="true" stored="false" multiValued="true"/>

   <!-- catchall text field that indexes tokens both normally and in reverse for efficient
        leading wildcard queries. -->
   <field name="text_rev" type="text_rev" indexed="true" stored="false" multiValued="true"/>

     */
}

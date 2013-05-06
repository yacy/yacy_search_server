/**
 *  SolrType
 *  Copyright 2011 by Michael Peter Christen
 *  First released 14.04.2011 at http://yacy.net
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


package net.yacy.cora.federate.solr;

public enum SolrType {
    string("s", "sxt"),                  // The type is not analyzed, but indexed/stored verbatim
    text_general("t", "txt"),            // tokenizes with StandardTokenizer, removes stop words from case-insensitive "stopwords.txt", down cases, applies synonyms.
    text_en_splitting_tight(null, null), // can insert dashes in the wrong place and still match
    location("p", null),                 // lat,lon - format: specialized field for geospatial search.
    date("dt", null),                    // date format as in http://www.w3.org/TR/xmlschema-2/#dateTime with trailing 'Z'
    bool("b", "bs", "boolean"),
    num_integer("i", "val", "int"),
    num_long("l", "ls", "long"), 
    num_float("f", "fs", "float"), 
    num_double("d", "ds", "double"),
    coordinate("coordinate", "coordinatex", "tdouble");
    
    private String printName, singlevalExt, multivalExt;
    private SolrType(final String singlevalExt, final String multivalExt) {
        this.printName = this.name();
        this.singlevalExt = singlevalExt;
        this.multivalExt = multivalExt;
    }
    private SolrType(final String singlevalExt, final String multivalExt, final String printName) {
        this.printName = printName;
        this.singlevalExt = singlevalExt;
        this.multivalExt = multivalExt;
    }
    public String printName() {
        return this.printName;
    }
    public boolean appropriateName(final SchemaDeclaration collectionSchema) {
        String field = collectionSchema.name();
        int p = field.indexOf('_');
        if (p < 0 || field.length() - p > 4) return true; // special names may have no type extension
        String ext = field.substring(p + 1);
        boolean ok = collectionSchema.isMultiValued() ? this.multivalExt.equals(ext) : this.singlevalExt.equals(ext);
        assert ok : "SolrType = " + this.name() + ", field = " + field + ", ext = " + ext + ", multivalue = " + new Boolean(collectionSchema.isMultiValued()).toString() + ", singlevalExt = " + this.singlevalExt + ", multivalExt = " + this.multivalExt;
        if (!ok) return ok;
        ok = !"s".equals(this.singlevalExt) || collectionSchema.isMultiValued() || field.endsWith("s");
        assert ok : "SolrType = " + this.name() + ", field = " + field + ", ext = " + ext + ", multivalue = " + new Boolean(collectionSchema.isMultiValued()).toString() + ", singlevalExt = " + this.singlevalExt + ", multivalExt = " + this.multivalExt;
        if (!ok) return ok;
        ok = !"sxt".equals(this.singlevalExt) || !collectionSchema.isMultiValued()  || field.endsWith("sxt");
        assert ok : "SolrType = " + this.name() + ", field = " + field + ", ext = " + ext + ", multivalue = " + new Boolean(collectionSchema.isMultiValued()).toString() + ", singlevalExt = " + this.singlevalExt + ", multivalExt = " + this.multivalExt;
        if (!ok) return ok;
        ok = !"t".equals(this.singlevalExt) || collectionSchema.isMultiValued() || field.endsWith("t");
        assert ok : "SolrType = " + this.name() + ", field = " + field + ", ext = " + ext + ", multivalue = " + new Boolean(collectionSchema.isMultiValued()).toString() + ", singlevalExt = " + this.singlevalExt + ", multivalExt = " + this.multivalExt;
        if (!ok) return ok;
        return ok;
    }
}
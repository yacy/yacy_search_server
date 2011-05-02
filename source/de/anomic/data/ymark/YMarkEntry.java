package de.anomic.data.ymark;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import net.yacy.document.content.DCEntry;
import net.yacy.kelondro.blob.Tables;

public class YMarkEntry extends TreeMap<String, String> {

    private static final long serialVersionUID = 2179622977348536148L;
    
    public static final YMarkEntry POISON = new YMarkEntry();
    public static final String BOOKMARKS_ID = "id";
    public static final String BOOKMARKS_REF = "ref";
    public static final String FOLDERS_IMPORTED = "/imported";
    public static final String FOLDERS_UNSORTED = "/unsorted";

    public static enum BOOKMARK {
    	//             key                 dc_attrb            dflt            html_attrb          xbel_attrb      json_attrb      type
    	URL            ("url",             "dc:identifier",    "",             "href",             "href",         "uri",          "link"),
    	TITLE          ("title",           "dc:title",         "",             "",                 "",             "title",        "meta"),
    	DESC           ("desc",            "dc:description",   "",             "",                 "",             "",             "comment"),
    	DATE_ADDED     ("date_added",      "",                 "",             "add_date",         "added",        "dateAdded",    "date"),
    	DATE_MODIFIED  ("date_modified",   "",                 "",             "last_modified",    "modified",     "lastModified", "date"),
    	DATE_VISITED   ("date_visited",    "",                 "",             "last_visited",     "visited",      "",             "date"),
    	PUBLIC         ("public",          "",                 "flase",        "",                 "yacy:public",  "",             "lock"),
    	TAGS           ("tags",            "dc:subject",       "unsorted",     "shortcuturl",      "yacy:tags",    "keyword",      "tag"),
    	VISITS         ("visits",          "",                 "0",            "",                 "yacy:visits",  "",             "stat"),
    	FOLDERS        ("folders",         "",                 "/unsorted",    "",                 "",             "",             "folder");
    	    	
    	private String key;
    	private String dc_attrb;
    	private String dflt;
    	private String html_attrb;
    	private String xbel_attrb;
    	private String json_attrb;
    	private String type;
    
        private static final Map<String,BOOKMARK> lookup = new HashMap<String,BOOKMARK>();
        static {
        	for(BOOKMARK b : EnumSet.allOf(BOOKMARK.class))
        		lookup.put(b.key(), b);
        }
    	
        private static StringBuilder buffer = new StringBuilder(25);
        
    	private BOOKMARK(final String k, final String d, final String s, final String a, final String x, final String j, final String t) {
    		this.key = k;
    		this.dc_attrb = d;
    		this.dflt = s;
    		this.html_attrb = a;
    		this.xbel_attrb = x;
    		this.json_attrb = j;
    		this.type = t;
    	}
    	public static BOOKMARK get(String key) { 
            return lookup.get(key); 
    	}
    	public static boolean contains(String key) {
    		return lookup.containsKey(key);
    	}
    	public String key() {
    		return this.key;
    	}    	
    	public String deflt() {
    		return  this.dflt;
    	}
    	public String html_attrb() {
    		return this.html_attrb;
    	}
    	public String xbel_attrb() {
    		return this.xbel_attrb;
    	}
        public String json_attrb() {
            return this.json_attrb;
        }
        public String dc_attrb() {
            return this.dc_attrb;
        }
    	public String xbel() {
    		buffer.setLength(0);
    		buffer.append('"');
    		buffer.append('\n');
    		buffer.append(' ');
    		buffer.append(this.xbel_attrb);
    		buffer.append('=');
    		buffer.append('"');
    		return buffer.toString();
    	}
    	public String type() {
    		return this.type;
    	}
    }

    public YMarkEntry() {
    	this(true);
    }
    
    public YMarkEntry(final boolean setDefaults) {
        super();
        if(setDefaults) {
            setCurrentTimeMillis(BOOKMARK.DATE_ADDED);
            setCurrentTimeMillis(BOOKMARK.DATE_MODIFIED);
            setDefaults();
        }
    }    
    
    public YMarkEntry(final DCEntry dc) {
        for (BOOKMARK b : BOOKMARK.values()) {
            if(dc.containsKey(b.dc_attrb)) {
                this.put(b.key(), dc.get(b.dc_attrb));
            }
        }
        setCurrentTimeMillis(BOOKMARK.DATE_ADDED);
        setCurrentTimeMillis(BOOKMARK.DATE_MODIFIED);
        setDefaults();
    }
    
    public YMarkEntry(final Tables.Row bmk_row) {
        for (BOOKMARK b : BOOKMARK.values()) {
            if(bmk_row.containsKey(b.key())) {
                this.put(b.key(), bmk_row.get(b.key(), b.deflt()));
            }
        }
    }
    
    private void setCurrentTimeMillis(BOOKMARK b) {
        switch(b) {
        	case DATE_ADDED:
        	case DATE_MODIFIED:
        	case DATE_VISITED:
        		this.put(b.key(), String.valueOf(System.currentTimeMillis()));
    		    break;
            default:
    			break;
        }        
    }
    
    private void setDefaults() {
        for (BOOKMARK b : BOOKMARK.values()) {
            if(!b.deflt().isEmpty() && !this.containsKey(b.key())) {
                this.put(b.key(), b.deflt());
            }
        }
    }
    
    public DCEntry getDCEntry() {
        final DCEntry dc = new DCEntry();
        for (BOOKMARK b : BOOKMARK.values()) {
            if(!b.dc_attrb.isEmpty() && this.containsKey(b.key())) {
                dc.put(b.dc_attrb, this.get(b.key()));
            }
        }
        return dc;
    }
    
    public Tables.Data getData() {
        final Tables.Data data = new Tables.Data();
        for (BOOKMARK b : BOOKMARK.values()) {
            if(this.containsKey(b.key()) && this.get(b.key()) != null) {
                data.put(b.key(), this.get(b.key()));
            } else {
                data.put(b.key(), b.deflt());
            }
        }
        return data;
    }
}

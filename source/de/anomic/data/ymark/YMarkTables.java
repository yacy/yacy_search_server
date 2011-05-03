// YMarkTables.java
// (C) 2011 by Stefan Förster, sof@gmx.de, Norderstedt, Germany
// first published 2010 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.data.ymark;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.blob.Tables.Row;
import net.yacy.kelondro.index.RowSpaceExceededException;
import de.anomic.data.WorkTables;

public class YMarkTables {
    
    public static enum TABLES {
		BOOKMARKS ("_bookmarks"),
		TAGS ("_tags"),
		FOLDERS ("_folders");
		
		private String basename;
		
		private TABLES(String b) {
			this.basename = b;
		}
		public String basename() {
			return this.basename;
		}
		public String tablename(String bmk_user) {
			return bmk_user+this.basename;
		}
	}
	
	public static enum PROTOCOLS {
    	HTTP ("http://"),
    	HTTPS ("https://");
    	
    	private String protocol;
    	
    	private PROTOCOLS(String s) {
    		this.protocol = s;
    	}
    	public String protocol() {
    		return this.protocol;
    	}
    	public String protocol(String s) {
    		return this.protocol+s;
    	}
    }
		
    public final static String FOLDERS_ROOT = "/";   
    public final static String BOOKMARKS_LOG = "BOOKMARKS";
    public final static String USER_ADMIN = "admin";
	public final static String USER_AUTHENTICATE = "AUTHENTICATE";
	public final static String USER_AUTHENTICATE_MSG = "Authentication required!";
	
    public final static String p1 = "(?:^|.*,)";
    public final static String p2 = "\\Q";
    public final static String p3 = "\\E";
    public final static String p4 = "(?:,.*|$)";
    public final static String p5 = "((?:";
    public final static String p6 = "),.*){";
    public final static String p7 = "/.*)";
    public final static String p8 = "(?:,|$)";
	
    public final static int BUFFER_LENGTH = 256;
    
    private final WorkTables worktables;
    
    public YMarkTables(final Tables wt) {
    	this.worktables = (WorkTables)wt;
    }
   
    public void deleteBookmark(final String bmk_user, final byte[] urlHash) throws IOException, RowSpaceExceededException {
        final String bmk_table = TABLES.BOOKMARKS.tablename(bmk_user);
    	Tables.Row bmk_row = null;
        bmk_row = this.worktables.select(bmk_table, urlHash);
        if(bmk_row != null) {
    		this.worktables.delete(bmk_table,urlHash);
        }
    }
    
    public void deleteBookmark(final String bmk_user, final String url) throws IOException, RowSpaceExceededException {
    	this.deleteBookmark(bmk_user, YMarkUtil.getBookmarkId(url));
    }
    
    public TreeMap<String, YMarkTag> getTags(final Iterator<Row> rowIterator) {
    	final TreeMap<String,YMarkTag> tags = new TreeMap<String,YMarkTag>();
    	Tables.Row bmk_row = null;
    	Iterator<String> tit = null;
    	String tag;
    	while(rowIterator.hasNext()) {
    		bmk_row = rowIterator.next();
    		if(bmk_row.containsKey(YMarkEntry.BOOKMARK.TAGS.key())) {
    			tit = YMarkUtil.keysStringToSet(bmk_row.get(YMarkEntry.BOOKMARK.TAGS.key(), YMarkEntry.BOOKMARK.TAGS.deflt())).iterator();
    			while(tit.hasNext()) {
    				tag = tit.next();
    				if(tags.containsKey(tag)) {
    					tags.get(tag).inc();
    				} else {
    					tags.put(tag, new YMarkTag(tag));
    				}
    			}
    		}
    	}
    	return tags;
    }
    
    public TreeMap<String, YMarkTag> getTags(final String bmk_user) throws IOException {
    	final String bmk_table = TABLES.BOOKMARKS.tablename(bmk_user);
    	final TreeMap<String,YMarkTag> tags = getTags(this.worktables.iterator(bmk_table));
    	return tags;
    }
    
    
    public TreeSet<String> getFolders(final String bmk_user, final String root) throws IOException {
    	final String bmk_table = TABLES.BOOKMARKS.tablename(bmk_user);
    	final TreeSet<String> folders = new TreeSet<String>();
    	final StringBuilder path = new StringBuilder(200);
    	final StringBuffer patternBuilder = new StringBuffer(BUFFER_LENGTH);
    	patternBuilder.setLength(0);
    	patternBuilder.append(p1);
    	patternBuilder.append('(');
    	patternBuilder.append(root);
    	patternBuilder.append(p7);
    	patternBuilder.append(p8);
    	final Pattern r = Pattern.compile(patternBuilder.toString());
    	final Iterator<Tables.Row> bit = this.worktables.iterator(bmk_table, YMarkEntry.BOOKMARK.FOLDERS.key(), r);
    	Tables.Row bmk_row = null;
    	
    	while(bit.hasNext()) {
    		bmk_row = bit.next();
    		if(bmk_row.containsKey(YMarkEntry.BOOKMARK.FOLDERS.key())) {    	    	
    			final String[] folderArray = (new String(bmk_row.get(YMarkEntry.BOOKMARK.FOLDERS.key()),"UTF8")).split(YMarkUtil.TAGS_SEPARATOR);                    
    	        for (final String folder : folderArray) {
    	            if(folder.length() > root.length() && folder.substring(0, root.length()+1).equals(root+'/')) {
    	                if(!folders.contains(folder)) {
        	        		path.setLength(0);
        	                path.append(folder);
        	                //TODO: get rid of .toString.equals()
        	                while(path.length() > 0 && !path.toString().equals(root)){
        	                	folders.add(path.toString());                  
        	                	path.setLength(path.lastIndexOf(YMarkUtil.FOLDERS_SEPARATOR));
        	                }	
    	        		}
    	        	}    	        	
    	        }
    		}
    	}
        if (!root.equals(YMarkTables.FOLDERS_ROOT)) { folders.add(root); }    	
        return folders;
    }
    
    public Iterator<Tables.Row> getBookmarksByFolder(final String bmk_user, final String folder) throws IOException {    	
    	final String bmk_table = TABLES.BOOKMARKS.tablename(bmk_user);
        final StringBuffer patternBuilder = new StringBuffer(BUFFER_LENGTH);
    	patternBuilder.setLength(0);
    	patternBuilder.append(p1);
    	patternBuilder.append('(');
    	patternBuilder.append(p2);
		patternBuilder.append(folder);
		patternBuilder.append(p3);
		patternBuilder.append(')');
		patternBuilder.append(p4);
    	final Pattern p = Pattern.compile(patternBuilder.toString());
    	return this.worktables.iterator(bmk_table, YMarkEntry.BOOKMARK.FOLDERS.key(), p);
    }
    
    public Iterator<Tables.Row> getBookmarksByTag(final String bmk_user, final String[] tagArray) throws IOException {    	
    	final String bmk_table = TABLES.BOOKMARKS.tablename(bmk_user);
        final StringBuffer patternBuilder = new StringBuffer(BUFFER_LENGTH);
    	patternBuilder.setLength(0);
    	patternBuilder.append(p1);
    	patternBuilder.append(p5);
    	for (final String tag : tagArray) {
        	patternBuilder.append(p2);
    		patternBuilder.append(tag);
    		patternBuilder.append(p3);
        	patternBuilder.append('|');
		}
    	patternBuilder.deleteCharAt(patternBuilder.length()-1);
    	patternBuilder.append(p6);
    	patternBuilder.append(tagArray.length);
    	patternBuilder.append('}');
    	final Pattern p = Pattern.compile(patternBuilder.toString());
    	return this.worktables.iterator(bmk_table, YMarkEntry.BOOKMARK.TAGS.key(), p);
    }
    
    public SortedSet<Row> orderBookmarksBy(final Iterator<Row> rowIterator, final String sortname, final String sortorder) {
        TreeSet<Row> sortTree = new TreeSet<Tables.Row>(new TablesRowComparator(sortname));
        Row row;
        while (rowIterator.hasNext()) {
            row = rowIterator.next();
            if(row != null)
                sortTree.add(row);
        }
        if(sortorder.equals("desc"))
            return sortTree.descendingSet();
        return sortTree;
    }
    
    public void addTags(final String bmk_user, final String url, final String tagString, final boolean merge) throws IOException, RowSpaceExceededException {
    	if(!tagString.isEmpty()) {
        	// do not set defaults as we only want to update tags
    		final YMarkEntry bmk = new YMarkEntry(false);
        	bmk.put(YMarkEntry.BOOKMARK.URL.key(), url);    	
        	bmk.put(YMarkEntry.BOOKMARK.TAGS.key(), YMarkUtil.cleanTagsString(tagString));
        	this.addBookmark(bmk_user, bmk, merge, true);  	
    	}  	
    }
    
    public void addFolder(final String bmk_user, final String url, final String folder) throws IOException, RowSpaceExceededException {
    	if(!folder.isEmpty()) {
        	// do not set defaults as we only want to add a folder
    		final YMarkEntry bmk = new YMarkEntry(false);
        	bmk.put(YMarkEntry.BOOKMARK.URL.key(), url);    	
        	bmk.put(YMarkEntry.BOOKMARK.FOLDERS.key(), folder);
        	this.addBookmark(bmk_user, bmk, true, true);  	
    	}  	
    }
    
    public void visited(final String bmk_user, final String url) throws IOException, RowSpaceExceededException {
    	// do not set defaults
		final YMarkEntry bmk = new YMarkEntry(false);
    	bmk.put(YMarkEntry.BOOKMARK.URL.key(), url);    	
    	bmk.put(YMarkEntry.BOOKMARK.DATE_VISITED.key(), (new YMarkDate()).toString());
    	this.addBookmark(bmk_user, bmk, true, true);
    }
    
    
	public void addBookmark(final String bmk_user, final YMarkEntry bmk, final boolean mergeTags, final boolean mergeFolders) throws IOException, RowSpaceExceededException {
		final String bmk_table = TABLES.BOOKMARKS.tablename(bmk_user);
        final String date = String.valueOf(System.currentTimeMillis());
		final byte[] urlHash = YMarkUtil.getBookmarkId(bmk.get(YMarkEntry.BOOKMARK.URL.key()));
		Tables.Row bmk_row = null;

		if (urlHash != null) {
			bmk_row = this.worktables.select(bmk_table, urlHash);
	        if (bmk_row == null) {
	        	// create and insert new entry
            	 this.worktables.insert(bmk_table, urlHash, bmk.getData());
	        } else {	
	        	// modify and update existing entry
                HashSet<String> oldSet;
                HashSet<String> newSet;
	        	
                for (YMarkEntry.BOOKMARK b : YMarkEntry.BOOKMARK.values()) {
	            	switch(b) {
	    				case DATE_ADDED:
	    					if(!bmk_row.containsKey(b.key()))
	    						bmk_row.put(b.key(), date); 
	    					break;
	    				case DATE_MODIFIED:
	    					bmk_row.put(b.key(), date); 
	    					break;
	    				case TAGS:
	    	            	oldSet = YMarkUtil.keysStringToSet(bmk_row.get(b.key(),b.deflt()));
	    	            	if(bmk.containsKey(b.key())) {
	    	            		newSet = YMarkUtil.keysStringToSet(bmk.get(b.key()));
	    	            		if(mergeTags) {
		    	            		newSet.addAll(oldSet);
		    	            		if(newSet.size() > 1 && newSet.contains(YMarkEntry.BOOKMARK.TAGS.deflt()))
		    	            			newSet.remove(YMarkEntry.BOOKMARK.TAGS.deflt());
		    	            		bmk_row.put(b.key(), YMarkUtil.keySetToString(newSet));
	    	            		} else {
	    	            			bmk_row.put(b.key(), bmk.get(b.key()));
	    	            		}
	    	            	} else {
	    	            		bmk_row.put(b.key(), bmk_row.get(b.key(), b.deflt()));
	    	            	}				
	    	            	break;
	    				case FOLDERS:
	    					oldSet = YMarkUtil.keysStringToSet(bmk_row.get(b.key(),b.deflt()));
	    					if(bmk.containsKey(b.key())) {
	    	            		newSet = YMarkUtil.keysStringToSet(bmk.get(b.key()));
	    	            		if(mergeFolders) {
		    	            		newSet.addAll(oldSet);
		    	            		if(newSet.size() > 1 && newSet.contains(YMarkEntry.BOOKMARK.FOLDERS.deflt()))
		    	            			newSet.remove(YMarkEntry.BOOKMARK.FOLDERS.deflt());
		    	            		bmk_row.put(b.key(), YMarkUtil.keySetToString(newSet));
	    	            		} else {
	    	            			bmk_row.put(b.key(), bmk.get(b.key()));
	    	            		}
	    	            	} else {
	    	            		bmk_row.put(b.key(), bmk_row.get(b.key(), b.deflt()));
	    	            	}
	    					break;	
	    				default:
	    					if(bmk.containsKey(b.key())) {
	    						bmk_row.put(b.key(), bmk.get(b.key()));
	    					} else {
	    						bmk_row.put(b.key(), bmk_row.get(b.key(), b.deflt()));
	    					}
	            	 }
	             }
                // update bmk_table
                this.worktables.update(bmk_table, bmk_row); 
            }
		}
	}
}


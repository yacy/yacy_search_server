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

package net.yacy.data.ymark;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.data.WorkTables;
import net.yacy.document.Document;
import net.yacy.document.Parser.Failure;
import net.yacy.kelondro.blob.TableColumnIndexException;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.blob.Tables.Row;
import net.yacy.kelondro.blob.TablesColumnIndex;
import net.yacy.repository.LoaderDispatcher;

public class YMarkTables {

    public static enum TABLES {
		BOOKMARKS ("_bookmarks"),
		TAGS ("_tags"),
		FOLDERS ("_folders");

		private String basename;

		private TABLES(final String b) {
			this.basename = b;
		}
		public String basename() {
			return this.basename;
		}
		public String tablename(final String bmk_user) {
			return bmk_user+this.basename;
		}
	}

	public static enum PROTOCOLS {
    	HTTP ("http://"),
    	HTTPS ("https://");

    	private String protocol;

    	private PROTOCOLS(final String s) {
    		this.protocol = s;
    	}
    	public String protocol() {
    		return this.protocol;
    	}
    	public String protocol(final String s) {
    		return this.protocol+s;
    	}
    }

    public final static String FOLDERS_ROOT = "/";
    public final static String BOOKMARKS_LOG = "BOOKMARKS";
    public final static String USER_ADMIN = "admin";
	public final static String USER_AUTHENTICATE_MSG = "Bookmark user authentication required!";

    public final static int BUFFER_LENGTH = 256;

    private final WorkTables worktables;
    private final Map<String, ChangeListener> progressListeners;

    public boolean dirty = false;

    public YMarkTables(final Tables wt) {
    	this.worktables = (WorkTables)wt;
    	this.progressListeners = new ConcurrentHashMap<String, ChangeListener>();
    	this.buildIndex();
    }

    public ChangeListener getProgressListener(String thread) {
    	final ChangeListener l = new ProgressListener();
    	this.progressListeners.put(thread, l);
    	return l;
    }

    public void removeProgressListener(String thread) {
    	this.progressListeners.remove(thread);
    }

    public class ProgressListener implements ChangeListener {
    	// the progress in %
    	private int progress = 0;
    	@Override
        public void stateChanged(ChangeEvent e) {
    		final MonitoredReader mreader = (MonitoredReader)e.getSource();
    		this.progress = (int)((mreader.getProgress() / mreader.maxProgress())*100);
    	}
    	public int progress() {
    		return this.progress;
    	}
    }

    public void buildIndex() {
    	final Iterator<String> iter = this.worktables.iterator();
    	while(iter.hasNext()) {
    		final String bmk_table = iter.next();
        	if(bmk_table.endsWith(TABLES.BOOKMARKS.basename())) {
        		try {
    				final long time = System.currentTimeMillis();
    				final TablesColumnIndex index = this.worktables.getIndex(bmk_table);
					if(index.getType() == TablesColumnIndex.INDEXTYPE.RAM || index.size() == 0) {
						ConcurrentLog.info(YMarkTables.BOOKMARKS_LOG, "buildIndex() "+YMarkEntry.BOOKMARK.indexColumns().keySet().toString());
						index.buildIndex(YMarkEntry.BOOKMARK.indexColumns(), this.worktables.iterator(bmk_table));
						ConcurrentLog.info(YMarkTables.BOOKMARKS_LOG, "build "+index.getType().name()+" index for columns "+YMarkEntry.BOOKMARK.indexColumns().keySet().toString()
								+" of table "+bmk_table+" containing "+this.worktables.size(bmk_table)+ " bookmarks"
								+" ("+(System.currentTimeMillis()-time)+"ms)");
					}
    			} catch (final IOException e) {
					ConcurrentLog.logException(e);
				} catch (final TableColumnIndexException e) {
					// currently nothing to do...
				}
    		}
    	}
    }

    public void deleteBookmark(final String bmk_user, final byte[] urlHash) throws IOException, SpaceExceededException {
        final String bmk_table = TABLES.BOOKMARKS.tablename(bmk_user);
    	Tables.Row bmk_row = null;
        bmk_row = this.worktables.select(bmk_table, urlHash);
        if(bmk_row != null) {
    		this.worktables.delete(bmk_table,urlHash);
        }
    	if(this.worktables.hasIndex(bmk_table, YMarkEntry.BOOKMARK.FOLDERS.key())) {
			try {
				this.worktables.getIndex(bmk_table).delete(urlHash);
			} catch (final TableColumnIndexException e) {
				// currently nothing to do...
			}
    	}
    }

    public void deleteBookmark(final String bmk_user, final String url) throws IOException, SpaceExceededException {
    	final byte[] urlHash = YMarkUtil.getBookmarkId(url);
    	this.deleteBookmark(bmk_user, urlHash);
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
    	final TreeMap<String,YMarkTag> tags = new TreeMap<String,YMarkTag>();
    	if(this.worktables.hasIndex(bmk_table, YMarkEntry.BOOKMARK.TAGS.key())) {
    		try {
				final TablesColumnIndex index = this.worktables.getIndex(bmk_table);
				final Iterator<String> iter = index.keySet(YMarkEntry.BOOKMARK.TAGS.key()).iterator();
				while(iter.hasNext()) {
    				final String tag = iter.next();
    				tags.put(tag, new YMarkTag(tag, index.get(YMarkEntry.BOOKMARK.TAGS.key(), tag).size()));
				}
				return tags;
			} catch (final Exception e) {
				// nothing to do
			}
    	}
    	return getTags(this.worktables.iterator(bmk_table));
    }

    public TreeSet<String> getFolders(final String bmk_user, String root) throws IOException {
    	final TreeSet<String> folders = new TreeSet<String>();
    	final StringBuilder path = new StringBuilder(BUFFER_LENGTH);
    	final String r = root + YMarkUtil.FOLDERS_SEPARATOR;
    	final String bmk_table = TABLES.BOOKMARKS.tablename(bmk_user);

    	// if exists, try the index first
    	if(this.worktables.hasIndex(bmk_table, YMarkEntry.BOOKMARK.FOLDERS.key())) {
    		TablesColumnIndex index;
			try {
				index = this.worktables.getIndex(bmk_table);
	    		final Iterator<String> fiter = index.keySet(YMarkEntry.BOOKMARK.FOLDERS.key()).iterator();
	    		while(fiter.hasNext()) {
	    			final String folder = fiter.next();
	    			if(folder.startsWith(r)) {
						path.setLength(0);
		                path.append(folder);
		                while(path.length() > 0 && !path.toString().equals(root)){
		                	final String p = path.toString();
		                	if(folders.isEmpty() || !p.equals(folders.floor(p))) {
		                		folders.add(p);
		                	}
		                	path.setLength(path.lastIndexOf(YMarkUtil.FOLDERS_SEPARATOR));
		                }
		        	}
	    		}
	        	if (!root.equals(YMarkTables.FOLDERS_ROOT)) { folders.add(root); }
	        	return folders;
			} catch (final Exception e) {
				ConcurrentLog.logException(e);
			}
    	}

    	// by default iterate all bookmarks and extract folder information
    	final Iterator<Tables.Row> bit = this.worktables.iterator(bmk_table);
    	Tables.Row bmk_row = null;
    	while(bit.hasNext()) {
    		bmk_row = bit.next();
    		if(bmk_row.containsKey(YMarkEntry.BOOKMARK.FOLDERS.key())) {
    			final String[] folderArray = YMarkUtil.TAGS_SEPARATOR_PATTERN.split(new String(bmk_row.get(YMarkEntry.BOOKMARK.FOLDERS.key()),"UTF8"));
    	        for (final String folder : folderArray) {
    	        	if(folder.length() > root.length() && folder.substring(0, root.length()+1).equals(r)) {
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

    public int getSize(final String bmk_user) throws IOException {
    	final String bmk_table = TABLES.BOOKMARKS.tablename(bmk_user);
    	return this.worktables.size(bmk_table);
    }

    public Iterator<Tables.Row> getBookmarksByFolder(final String bmk_user, final String foldersString) {
        final String bmk_table = TABLES.BOOKMARKS.tablename(bmk_user);
        return this.worktables.getByIndex(bmk_table, YMarkEntry.BOOKMARK.FOLDERS.key(), YMarkEntry.BOOKMARK.FOLDERS.seperator(), foldersString);
    }

    public Iterator<Tables.Row> getBookmarksByTag(final String bmk_user, final String tagsString) {
        final String bmk_table = TABLES.BOOKMARKS.tablename(bmk_user);
        return this.worktables.getByIndex(bmk_table, YMarkEntry.BOOKMARK.TAGS.key(), YMarkEntry.BOOKMARK.TAGS.seperator(), tagsString);
    }

    public List<Row> orderBookmarksBy(final Iterator<Row> rowIterator, final String sortname, final String sortorder) {
        final List<Row> sortList = new ArrayList<Row>();
        Row row;
        while (rowIterator.hasNext()) {
            row = rowIterator.next();
            if(row != null)
                sortList.add(row);
        }
        Collections.sort(sortList, new TablesRowComparator(sortname, sortorder));
        return sortList;
    }

    public void addTags(final String bmk_user, final String url, final String tagString, final boolean merge) throws IOException {
    	if(!tagString.isEmpty()) {
        	// do not set defaults as we only want to update tags
    		final YMarkEntry bmk = new YMarkEntry(false);
        	bmk.put(YMarkEntry.BOOKMARK.URL.key(), url);
        	bmk.put(YMarkEntry.BOOKMARK.TAGS.key(), YMarkUtil.cleanTagsString(tagString));
        	addBookmark(bmk_user, bmk, merge, true);
    	}
    	this.dirty = true;
    }

    public void replaceTags(final Iterator<Row> rowIterator, final String bmk_user, final String tagString, final String replaceString) throws IOException {
    	final String bmk_table = TABLES.BOOKMARKS.tablename(bmk_user);
    	final HashSet<String> remove = YMarkUtil.keysStringToSet(YMarkUtil.cleanTagsString(tagString.toLowerCase()));
        final StringBuilder t = new StringBuilder(200);
    	HashSet<String> tags;
        Row row;
        while (rowIterator.hasNext()) {
            row = rowIterator.next();
            if(row != null) {
            	tags = YMarkUtil.keysStringToSet(row.get(YMarkEntry.BOOKMARK.TAGS.key(), YMarkEntry.BOOKMARK.TAGS.deflt()).toLowerCase());
            	tags.removeAll(remove);
            	t.append(YMarkUtil.keySetToString(tags));
            }
            t.append(YMarkUtil.TAGS_SEPARATOR);
            t.append(replaceString);
            row.put(YMarkEntry.BOOKMARK.TAGS.key(), YMarkUtil.cleanTagsString(t.toString()));
            this.worktables.update(bmk_table, row);
            if(this.worktables.hasIndex(bmk_table)) {
            	try {
					this.worktables.getIndex(bmk_table).update(YMarkEntry.BOOKMARK.TAGS.key(), YMarkEntry.BOOKMARK.TAGS.seperator(), row);
				} catch (final Exception e) {
					// nothing to do
				}
            }
        }
        this.dirty = true;
    }

    public void addFolder(final String bmk_user, final String url, final String folder) throws IOException {
    	if(!folder.isEmpty()) {
        	// do not set defaults as we only want to add a folder
    		final YMarkEntry bmk = new YMarkEntry(false);
        	bmk.put(YMarkEntry.BOOKMARK.URL.key(), url);
        	bmk.put(YMarkEntry.BOOKMARK.FOLDERS.key(), folder);
        	addBookmark(bmk_user, bmk, true, true);
    	}
    }

    public void visited(final String bmk_user, final String url) throws IOException {
    	// do not set defaults
		final YMarkEntry bmk = new YMarkEntry(false);
    	bmk.put(YMarkEntry.BOOKMARK.URL.key(), url);
    	bmk.put(YMarkEntry.BOOKMARK.DATE_VISITED.key(), (new YMarkDate()).toString());
    	addBookmark(bmk_user, bmk, true, true);
    }

    public void createBookmark(final LoaderDispatcher loader, final String url, final ClientIdentification.Agent agent, final String bmk_user, final boolean autotag, final String tagsString, final String foldersString) throws IOException, Failure {
    	createBookmark(loader, new DigestURL(url), agent, bmk_user, autotag, tagsString, foldersString);
    }

    public void createBookmark(final LoaderDispatcher loader, final DigestURL url, final ClientIdentification.Agent agent, final String bmk_user, final boolean autotag, final String tagsString, final String foldersString) throws IOException, Failure {

    	final YMarkEntry bmk_entry = new YMarkEntry(false);
        final YMarkMetadata meta = new YMarkMetadata(url);
		final Document document = meta.loadDocument(loader, agent);
		final EnumMap<YMarkMetadata.METADATA, String> metadata = meta.loadMetadata();
		final String urls = url.toNormalform(true);
		bmk_entry.put(YMarkEntry.BOOKMARK.URL.key(), urls);
		if(!this.worktables.has(YMarkTables.TABLES.BOOKMARKS.tablename(bmk_user), YMarkUtil.getBookmarkId(urls))) {
			bmk_entry.put(YMarkEntry.BOOKMARK.PUBLIC.key(), "false");
			bmk_entry.put(YMarkEntry.BOOKMARK.TITLE.key(), metadata.get(YMarkMetadata.METADATA.TITLE));
			bmk_entry.put(YMarkEntry.BOOKMARK.DESC.key(), metadata.get(YMarkMetadata.METADATA.DESCRIPTION));
		}
		final String fs = YMarkUtil.cleanFoldersString(foldersString);
		if(fs.isEmpty())
			bmk_entry.put(YMarkEntry.BOOKMARK.FOLDERS.key(), YMarkEntry.BOOKMARK.FOLDERS.deflt());
		else
			bmk_entry.put(YMarkEntry.BOOKMARK.FOLDERS.key(), fs);
		final StringBuilder strb = new StringBuilder();
		if(autotag) {
			final String autotags = YMarkAutoTagger.autoTag(document, 3, this.worktables.bookmarks.getTags(bmk_user));
			strb.append(autotags);
		}
		if(!tagsString.isEmpty()) {
			strb.append(YMarkUtil.TAGS_SEPARATOR);
			strb.append(tagsString);
		}
		bmk_entry.put(YMarkEntry.BOOKMARK.TAGS.key(),YMarkUtil.cleanTagsString(strb.toString()));
		this.worktables.bookmarks.addBookmark(bmk_user, bmk_entry, true, true);
    }

    public boolean hasBookmark(final String bmk_user, final String urlhash) {
        final String bmk_table = TABLES.BOOKMARKS.tablename(bmk_user);
        try {
            return this.worktables.has(bmk_table, ASCII.getBytes(urlhash));
        } catch (final IOException e) {
            return false;
        }
    }

	public void addBookmark(final String bmk_user, final YMarkEntry bmk, final boolean mergeTags, final boolean mergeFolders) throws IOException {
		final String bmk_table = TABLES.BOOKMARKS.tablename(bmk_user);
        final String date = String.valueOf(System.currentTimeMillis());
		byte[] urlHash = null;
        try {
			urlHash = YMarkUtil.getBookmarkId(bmk.get(YMarkEntry.BOOKMARK.URL.key()));
        } catch (final MalformedURLException e) {
        	ConcurrentLog.info("BOOKMARKIMPORT", "invalid url: "+bmk.get(YMarkEntry.BOOKMARK.URL.key()));
        }
		Tables.Row bmk_row = null;

		if (urlHash != null) {
			try {
				bmk_row = this.worktables.select(bmk_table, urlHash);
			} catch (final Exception e) {

			}
	        if (bmk_row == null) {
	        	// create and insert new entry
				if(!bmk.containsKey(YMarkEntry.BOOKMARK.DATE_ADDED.key())) {
					bmk.put(YMarkEntry.BOOKMARK.DATE_ADDED.key(), date);
					bmk.put(YMarkEntry.BOOKMARK.DATE_MODIFIED.key(), date);
				}
	        	this.worktables.insert(bmk_table, urlHash, bmk.getData());
	        	try {
	        		if(this.worktables.hasIndex(bmk_table))
	        			this.worktables.getIndex(bmk_table).add(YMarkEntry.BOOKMARK.indexColumns(), bmk, urlHash);
				} catch (final Exception e) {
					// nothing to do
				}
	        } else {
	        	// modify and update existing entry
                HashSet<String> oldSet;
                HashSet<String> newSet;

                for (final YMarkEntry.BOOKMARK b : YMarkEntry.BOOKMARK.values()) {
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
	        	try {
					if(this.worktables.hasIndex(bmk_table))
						this.worktables.getIndex(bmk_table).update(YMarkEntry.BOOKMARK.indexColumns(), bmk_row);
				} catch (final Exception e) {
					// nothing to do
				}
            }

	        this.dirty = true;
		}
	}
}


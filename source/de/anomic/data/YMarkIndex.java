package de.anomic.data;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.regex.Pattern;

import net.yacy.cora.storage.ConcurrentARC;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.blob.Tables.Data;
import net.yacy.kelondro.blob.Tables.Row;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;

public class YMarkIndex {

    public static enum INDEX {
    	ID 		("id", ""),
    	NAME 	("name", ""),
    	DESC 	("desc", ""),
    	URLS 	("urls", "");
    	
    	private String key;
    	private String dflt;
    	
    	private INDEX(String k, String s) {
    		this.key = k;
    		this.dflt = s;
    	}
    	public String key() {
    		return this.key;
    	}
    	public String deflt() {
    		return  this.dflt;
    	}
    }
    
    public static enum INDEX_ACTION {
    	ADD,
    	REMOVE
    }

    public final static String PATTERN_PREFIX = "^\\Q";
    public final static String PATTERN_POSTFIX = YMarkTables.FOLDERS_SEPARATOR+"\\E.*$";
    
    private final WorkTables worktables;
    private final String table_basename;
    private final ConcurrentARC<String, byte[]> cache;
        
    public YMarkIndex(final Tables wt, final String tb) {
    	this.worktables = (WorkTables)wt;
    	this.table_basename = tb;
    	this.cache = new ConcurrentARC<String, byte[]>(50,1);
    }
    
    public String getKeyname(final String user, final byte[] key) throws IOException, RowSpaceExceededException {
    	final String index_table = user + this.table_basename;
    	Tables.Row row = this.worktables.select(index_table, key);
   		return new String(row.get(INDEX.NAME.key(), INDEX.NAME.deflt()));
    }
    
    public static int getFolderDepth(String folder) {
    	final int depth = folder.split(YMarkTables.FOLDERS_SEPARATOR).length -1;
    	if (depth < 0) 
    		return 0;
    	else
    		return depth;
    }
    
    public Iterator<String> getFolders(final String user, final String root) throws IOException {
    	final String index_table = user + this.table_basename;
    	final TreeSet<String> folders = new TreeSet<String>();
    	final Pattern r = Pattern.compile(PATTERN_PREFIX + root + PATTERN_POSTFIX);
    	final Iterator<Row> it = this.worktables.iterator(index_table, INDEX.NAME.key(), r);
    	final StringBuilder path = new StringBuilder(100);
        Row folder;
        
        while (it.hasNext()) {
            folder = it.next();
            path.setLength(0);
            path.append(new String(folder.get(INDEX.NAME.key(), INDEX.NAME.deflt())));
            //TODO: get rid of .toString.equals()
            while(path.length() > 0 && !path.toString().equals(root)){
                folders.add(path.toString());                  
                path.setLength(path.lastIndexOf(YMarkTables.FOLDERS_SEPARATOR));
            }
        }
        if (!root.equals(YMarkTables.FOLDERS_ROOT)) { folders.add(root); }
        return folders.iterator(); 
    }
    
    protected void clearCache() {
    	this.cache.clear();
    }
    
    protected void createIndexEntry(final String user, final  String keyname, final HashSet<String> urlSet) throws IOException {
        final byte[] key = YMarkTables.getKeyId(keyname);
        final String index_table = user + this.table_basename;
        final String cacheKey = index_table+":"+keyname;
    	final byte[] BurlSet = YMarkTables.keySetToBytes(urlSet);
        Data tagEntry = new Data();        
		this.cache.insert(cacheKey, BurlSet);    	
    	tagEntry.put(INDEX.NAME.key, keyname);
        tagEntry.put(INDEX.URLS.key, BurlSet);
        this.worktables.insert(index_table, key, tagEntry);
    }
    
    protected void removeIndexEntry(final String user, String keysString, final byte[] urlHash) {
		final String[] keyArray = keysString.split(YMarkTables.TAGS_SEPARATOR);                    
        for (final String tag : keyArray) {
        	this.updateIndexTable(user, tag, urlHash, INDEX_ACTION.REMOVE);
        }
	}
    
    protected void insertIndexEntry(final String user, String keysString, final byte[] urlHash) {
    	final String[] keyArray = keysString.split(YMarkTables.TAGS_SEPARATOR);                    
		for (final String key : keyArray) {
			this.updateIndexTable(user, key, urlHash, INDEX_ACTION.ADD);
		}
    }
    
	protected void updateIndexEntry(final String user, final byte[] urlHash, final HashSet<String> oldSet, final HashSet<String> newSet) {
		Iterator <String> tagIter;        
        HashSet<String> urlSet = new HashSet<String>(newSet);
        newSet.removeAll(oldSet);
        tagIter = newSet.iterator();
        while(tagIter.hasNext()) {
        	this.updateIndexTable(user, tagIter.next(), urlHash, INDEX_ACTION.ADD);
        }
        oldSet.removeAll(urlSet);
        tagIter=oldSet.iterator();
        while(tagIter.hasNext()) {
        	this.updateIndexTable(user, tagIter.next(), urlHash, INDEX_ACTION.REMOVE);
        }  
	}
    
	public HashSet<String> getBookmarks(final String user, final String keyname) throws IOException, RowSpaceExceededException {
		final String index_table = user + this.table_basename;
		final String cacheKey = index_table+":"+keyname;
		if (this.cache.containsKey(cacheKey)) {
			return YMarkTables.keysStringToSet(new String(this.cache.get(cacheKey)));
		} else {
			final Tables.Row idx_row = this.worktables.select(index_table, YMarkTables.getKeyId(keyname));
			if (idx_row != null) {						
				final byte[] keys = idx_row.get(INDEX.URLS.key);
				this.cache.put(cacheKey, keys);
				return YMarkTables.keysStringToSet(new String(keys));
			}
		}
		return new HashSet<String>();
	}
	
	public HashSet<String> getBookmarks(final String user, final String[] keyArray) throws IOException, RowSpaceExceededException {
		final HashSet<String> urlSet = new HashSet<String>();
		urlSet.addAll(getBookmarks(user, keyArray[0]));
		if (urlSet.isEmpty())
			return urlSet;
		if (keyArray.length > 1) {
			for (final String keyname : keyArray) {
				urlSet.retainAll(getBookmarks(user, keyname));
				if (urlSet.isEmpty())
					return urlSet;
			}
		}
		return urlSet;		
	}
    
	/**
	 * YMark function that updates the tag/folder index
	 * @param user 
	 * @param keyname
	 * @param url is the url has as returned by DigestURI.hash()
	 * @param action is either add (1) or remove (2)
	 */
    protected void updateIndexTable(final String user, final String keyname, final byte[] url, final INDEX_ACTION action) {
    	final String index_table = user + this.table_basename;
        final String cacheKey = index_table+":"+keyname;
        final byte[] key = YMarkTables.getKeyId(keyname);
        final String urlHash = new String(url);  
        Tables.Row row = null;
        
        // try to load urlSet from cache
        HashSet<String>urlSet = this.cache.containsKey(cacheKey) ? YMarkTables.keysStringToSet(new String(this.cache.get(cacheKey))) : new HashSet<String>();
        
    	try {
    		row = this.worktables.select(index_table, key);    		
    		
    		// key has no index_table entry
    		if(row == null) {
    			switch (action) {
					case ADD:		        		
						urlSet.add(urlHash);        			
	        			createIndexEntry(user, keyname, urlSet);
			            break;
					case REMOVE:
						// key has no index_table entry but a cache entry
						// TODO: this shouldn't happen
						if(!urlSet.isEmpty()) {
							urlSet.remove(urlHash);	        			
		        			createIndexEntry(user, keyname, urlSet);
						}
						break;
					default:
						break;       					
				}    			
    		} 
    		// key has an existing index_table entry
    		else {
    	        byte[] BurlSet = null;
    			// key has no cache entry
    			if (urlSet.isEmpty()) {
	    			// load urlSet from index_table
    				urlSet = YMarkTables.keysStringToSet(new String(row.get(INDEX.URLS.key)));	   
    			}    			
    			switch (action) {
					case ADD:
		        		urlSet.add(urlHash);
	        			break;
					case REMOVE:
						urlSet.remove(urlHash);
						break;					
					default:
						break;
    			}
    			if (urlSet.isEmpty()) {
    				this.cache.remove(cacheKey);
    				this.worktables.delete(index_table, key);
    			} else {
    	        	BurlSet = YMarkTables.keySetToBytes(urlSet);
        			this.cache.insert(cacheKey, BurlSet);
        			row.put(INDEX.URLS.key, BurlSet);
        			this.worktables.update(index_table, row);
    			}
    		}
    	}  catch (IOException e) {
            Log.logException(e);
		} catch (RowSpaceExceededException e) {
            Log.logException(e);
		}
	}
}

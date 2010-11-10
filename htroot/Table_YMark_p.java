import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import de.anomic.data.YMarkTables;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class Table_YMark_p {
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        prop.put("showtable", 0);
        prop.put("showedit", 0);
        prop.put("showselection", 0);
        
        String table = (post == null) ? null : post.get("table", null);
        if (table != null && !sb.tables.hasHeap(table)) table = null;
        
        // get the user name for the selected table
        String bmk_user = null;
        if (table != null)
        	bmk_user = table.substring(0,table.indexOf('_'));
        
        // show table selection
        int count = 0;
        Iterator<String> ti = sb.tables.tables();
        String tablename;
        prop.put("showselection", 1);
        while (ti.hasNext()) {
            tablename = ti.next();
            if(tablename.endsWith(YMarkTables.TABLES.BOOKMARKS.basename())) {
                prop.put("showselection_tables_" + count + "_name", tablename);
                prop.put("showselection_tables_" + count + "_selected", (table != null && table.equals(tablename)) ? 1 : 0);
                count++;
            }
        }
        prop.put("showselection_tables", count);
        prop.put("showselection_pattern", "");

        if (post == null) return prop; // return rewrite properties
        
        // get available tags and folders
        count = 0;
        byte[] key;
        try {
			Iterator<byte[]> iter = sb.tables.keys(YMarkTables.TABLES.TAGS.tablename(bmk_user));
			while(iter.hasNext()) {
				key = iter.next();
				prop.put("showselection_tags_" + count + "_tagHash", new String(key));
				prop.put("showselection_tags_" + count + "_tagName", sb.tables.bookmarks.tags.getKeyname(bmk_user, key));
				count++;
			}
			prop.put("showselection_tags", count);
			count = 0;
			iter = sb.tables.keys(YMarkTables.TABLES.FOLDERS.tablename(bmk_user));
			while(iter.hasNext()) {
				key = iter.next();
				prop.put("showselection_folders_" + count + "_folderHash", new String(key));
				prop.put("showselection_folders_" + count + "_folderName", sb.tables.bookmarks.folders.getKeyname(bmk_user, key));
				count++;
			}
			prop.put("showselection_folders", count);
		} catch (IOException e) {
            Log.logException(e);
		} catch (RowSpaceExceededException e) {
            Log.logException(e);
		}
		
        String counts = post.get("count", null);
        int maxcount = (counts == null || counts.equals("all")) ? Integer.MAX_VALUE : Integer.parseInt(counts);
        String pattern = post.get("search", "");
        Pattern matcher = (pattern.length() == 0 || pattern.equals(".*")) ? null : Pattern.compile(".*" + pattern + ".*");
        prop.put("pattern", pattern);
        
        List<String> columns = null;
        if (table != null) try {
            columns = sb.tables.columns(table);
        } catch (IOException e) {
            Log.logException(e);
            columns = new ArrayList<String>();
        }
        
        final Iterator<String> cit = columns.iterator();
        count = 0;
        while(cit.hasNext()) {
			prop.put("showselection_columns_" + count + "_col", cit.next());
			count++;	
        }
        prop.put("showselection_columns", count);
        
        // apply deletion requests
        if (post.get("deletetable", "").length() > 0) try {            
        	sb.tables.clear(table);
        	sb.tables.clear(YMarkTables.TABLES.FOLDERS.tablename(bmk_user));
        	sb.tables.clear(YMarkTables.TABLES.TAGS.tablename(bmk_user));
        } catch (IOException e) {
            Log.logException(e);
        }
        
        if (post.get("deleterows", "").length() > 0) {
            for (Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getValue().startsWith("mark_")) try {
                	sb.tables.bookmarks.deleteBookmark(bmk_user, entry.getValue().substring(5).getBytes());
                } catch (IOException e) {
                    Log.logException(e);
                } catch (RowSpaceExceededException e) {
                    Log.logException(e);
				}
            }
        }
        
        if (post.get("commitrow", "").length() > 0) {
        	final HashMap<String, String> bmk = new HashMap<String, String>();
            for (Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getKey().startsWith("col_")) {
                    bmk.put(entry.getKey().substring(4), entry.getValue());
                }
            }
            try {
				sb.tables.bookmarks.addBookmark(bmk_user, bmk, false);
			} catch (IOException e) {
                Log.logException(e);
			} catch (RowSpaceExceededException e) {
                Log.logException(e);
			}
        }
        
        // generate table
        prop.put("showtable", 0);
        prop.put("showedit", 0);
        
        if (table != null) {
            
            if (post.containsKey("editrow")) {
                // check if we can find a key
                String pk = null;
                for (Map.Entry<String, String> entry: post.entrySet()) {
                    if (entry.getValue().startsWith("mark_")) {
                        pk = entry.getValue().substring(5);
                        break;
                    }
                }
                try {
                    if (pk != null && sb.tables.has(table, pk.getBytes())) {
                        setEdit(sb, prop, table, pk, columns);
                    }
                } catch (IOException e) {
                    Log.logException(e);
                } catch (RowSpaceExceededException e) {
                    Log.logException(e);
                }
            } else if (post.containsKey("addrow")) try {
                // get a new key
                String pk = new String(sb.tables.createRow(table));
                setEdit(sb, prop, table, pk, columns);
            } catch (IOException e) {
                Log.logException(e);
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
            } else {
                prop.put("showtable", 1);
                prop.put("showtable_table", table);
                
                // insert the columns
                
                for (int i = 0; i < columns.size(); i++) {
                    prop.putHTML("showtable_columns_" + i + "_header", columns.get(i));
                }
                prop.put("showtable_columns", columns.size());
                
                // insert all rows
                try {
                    maxcount = Math.min(maxcount, sb.tables.size(table));
                } catch (IOException e) {
                    Log.logException(e);
                    maxcount = 0;
                }
                count = 0;
                try {
                	Iterator<Tables.Row> mapIterator;
                	                    
                	if(post.containsKey("folders") && !post.get("folders").isEmpty()) {
                    	mapIterator = sb.tables.bookmarks.folders.getBookmarks(bmk_user, post.get("folders"));
                    } else if(post.containsKey("tags") && !post.get("tags").isEmpty()) {
                    	mapIterator = sb.tables.bookmarks.tags.getBookmarks(bmk_user, post.get("tags"));
                    } else {
                    	final Iterator<Tables.Row> plainIterator = sb.tables.iterator(table, matcher);
                    	mapIterator = sb.tables.orderByPK(plainIterator, maxcount).iterator();
                    }
                    
                    Tables.Row row;
                    boolean dark = true;
                    byte[] cell;
                    while (mapIterator.hasNext() && count < maxcount) {
                        row = mapIterator.next();
                        if (row == null) continue;
                        
                        // write table content
                        prop.put("showtable_list_" + count + "_dark", ((dark) ? 1 : 0) ); dark=!dark;
                        prop.put("showtable_list_" + count + "_pk", new String(row.getPK()));
                        prop.put("showtable_list_" + count + "_count", count);
                        for (int i = 0; i < columns.size(); i++) {
                            cell = row.get(columns.get(i));
                            prop.putHTML("showtable_list_" + count + "_columns_" + i + "_cell", cell == null ? "" : new String(cell));
                        }
                        prop.put("showtable_list_" + count + "_columns", columns.size());
                        count++;
                    }
                } catch (IOException e) {
                    Log.logException(e);
                } catch (RowSpaceExceededException e) {
                    Log.logException(e);
				}
                prop.put("showtable_list", count);
                prop.put("showtable_num", count);
            }
            
        }
        
        // adding the peer address
        prop.put("address", sb.peers.mySeed().getPublicAddress());
        
        // return rewrite properties
        return prop;
    }
    
    private static void setEdit(final Switchboard sb, final serverObjects prop, final String table, final String pk, List<String> columns) throws IOException, RowSpaceExceededException {
        prop.put("showedit", 1);
        prop.put("showedit_table", table);
        prop.put("showedit_pk", pk);
        Tables.Row row = sb.tables.select(table, pk.getBytes());
        if (row == null) return;
        int count = 0;
        byte[] cell;
        for (String col: columns) {
            cell = row.get(col);
            prop.put("showedit_list_" + count + "_key", col);
            prop.put("showedit_list_" + count + "_value", cell == null ? "" : new String(cell));
            count++;
        }
        prop.put("showedit_list", count);
    }
}

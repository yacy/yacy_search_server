import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.data.ymark.YMarkEntry;
import net.yacy.data.ymark.YMarkTables;
import net.yacy.data.ymark.YMarkUtil;
import net.yacy.kelondro.blob.Tables;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;


public class Table_YMark_p {
    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        prop.put("showtable", 0);
        prop.put("showedit", 0);
        prop.put("showselection", 0);

        String table = (post == null) ? "admin_bookmarks" : post.get("table", "admin_bookmarks");
        if (table != null && !sb.tables.hasHeap(table)) table = null;

        // get the user name for the selected table
        String bmk_user = null;
        if (table != null)
        	bmk_user = table.substring(0,table.indexOf('_',0));

        // currently selected table
        prop.put("showselection_table", table);

        // show table selection
        int count = 0;
        final Iterator<String> ti = sb.tables.iterator();
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
        /*
        byte[] key;
        String name;
        try {
			Iterator<byte[]> iter = sb.tables.keys(YMarkTables.TABLES.TAGS.tablename(bmk_user));
			while(iter.hasNext()) {
				key = iter.next();
				name = sb.tables.bookmarks.tags.getKeyname(bmk_user, key);
				prop.put("showselection_tags_" + count + "_tagHash", UTF8.String(key));
				prop.put("showselection_tags_" + count + "_tagName", name);
				prop.put("showselection_tags_" + count + "_tagCount", sb.tables.bookmarks.tags.getBookmarkIds(bmk_user, name).size());
				count++;
			}
			prop.put("showselection_tags", count);
			count = 0;
			iter = sb.tables.keys(YMarkTables.TABLES.FOLDERS.tablename(bmk_user));
			while(iter.hasNext()) {
				key = iter.next();
				name = sb.tables.bookmarks.folders.getKeyname(bmk_user, key);
				prop.put("showselection_folders_" + count + "_folderHash", UTF8.String(key));
				prop.put("showselection_folders_" + count + "_folderName", name);
				prop.put("showselection_folders_" + count + "_folderCount", sb.tables.bookmarks.folders.getBookmarkIds(bmk_user, name).size());
				count++;
			}
			prop.put("showselection_folders", count);
		} catch (final IOException e) {
            Log.logException(e);
		} catch (final RowSpaceExceededException e) {
            Log.logException(e);
		}
		*/

        final String counts = post.get("count", null);
        int maxcount = (counts == null || counts.equals("all")) ? Integer.MAX_VALUE : post.getInt("count", 10);
        final String pattern = post.get("search", "");
        final Pattern matcher = (pattern.isEmpty() || pattern.equals(".*")) ? null : Pattern.compile(".*" + pattern + ".*");
        prop.put("pattern", pattern);

        List<String> columns = new ArrayList<String>();
        for (final Map.Entry<String, String> entry: post.entrySet()) {
            if (entry.getKey().startsWith("col_")) {
            	columns.add(entry.getKey().substring(4));
            }
        }
        if (columns.isEmpty() && table != null) try {
            columns = sb.tables.columns(table);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }

        count = 0;
        if (table != null) {
            Iterator<String> cit;
            String col;
            try {
                cit = sb.tables.columns(table).iterator();
    	        while(cit.hasNext()) {
                    col = cit.next();
                    prop.put("showselection_columns_" + count + "_col", col);
                    prop.put("showselection_columns_" + count + "_checked", columns.contains(col) ? 1 : 0);
                    count++;
    	        }
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }
        prop.put("showselection_columns", count);

        // apply deletion requests
        if (!post.get("deletetable", "").isEmpty()) {
            sb.tables.clear(table);
            sb.tables.clear(YMarkTables.TABLES.FOLDERS.tablename(bmk_user));
            sb.tables.clear(YMarkTables.TABLES.TAGS.tablename(bmk_user));
        }


        // apply rebuildIndex request
        /*
        if (!post.get("rebuildindex", "").isEmpty()) try {
            sb.tables.bookmarks.folders.rebuildIndex(bmk_user);
            sb.tables.bookmarks.tags.rebuildIndex(bmk_user);
        }  catch (final IOException e) {
            Log.logException(e);
        }
        */

        if (!post.get("deleterows", "").isEmpty()) {
            for (final Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getValue().startsWith("mark_")) try {
                    sb.tables.bookmarks.deleteBookmark(bmk_user, entry.getValue().substring(5).getBytes());
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                } catch (final SpaceExceededException e) {
                    ConcurrentLog.logException(e);
                }
            }
        }

        if (!post.get("commitrow", "").isEmpty()) {
            final YMarkEntry bmk = new YMarkEntry();
            for (final Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getKey().startsWith("col_")) {
                    bmk.put(entry.getKey().substring(4), entry.getValue());
                }
            }
            try {
                sb.tables.bookmarks.addBookmark(bmk_user, bmk, false, false);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }

        // generate table
        prop.put("showtable", 0);
        prop.put("showedit", 0);

        if (table != null) {

            if (post.containsKey("editrow")) {
                // check if we can find a key
                String pk = null;
                for (final Map.Entry<String, String> entry: post.entrySet()) {
                    if (entry.getValue().startsWith("mark_")) {
                        pk = entry.getValue().substring(5);
                        break;
                    }
                }
                try {
                    if (pk != null && sb.tables.has(table, pk.getBytes())) {
                        setEdit(sb, prop, table, pk, columns);
                    }
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                } catch (final SpaceExceededException e) {
                    ConcurrentLog.logException(e);
                }
            } else if (post.containsKey("addrow")) try {
                // get a new key
                final String pk = UTF8.String(sb.tables.createRow(table));
                setEdit(sb, prop, table, pk, columns);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            } catch (final SpaceExceededException e) {
                ConcurrentLog.logException(e);
            } else {
                prop.put("showtable", 1);
                prop.put("showtable_table", table);


                try {
                    prop.put("showtable_bmksize", sb.tables.size(table));
                    prop.put("showtable_tagsize", sb.tables.size(YMarkTables.TABLES.TAGS.tablename(bmk_user)));
                    prop.put("showtable_foldersize", sb.tables.size(YMarkTables.TABLES.FOLDERS.tablename(bmk_user)));
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                    prop.put("showtable_bmksize", 0);
                    prop.put("showtable_tagsize", 0);
                    prop.put("showtable_foldersize", 0);
                }

                // insert the columns

                for (int i = 0; i < columns.size(); i++) {
                    prop.putHTML("showtable_columns_" + i + "_header", columns.get(i));
                }
                prop.put("showtable_columns", columns.size());

                // insert all rows
                try {
                    maxcount = Math.min(maxcount, sb.tables.size(table));
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                    maxcount = 0;
                }
                count = 0;
                try {
                    Iterator<Tables.Row> mapIterator;
                    if (post.containsKey("folders") && !post.get("folders").isEmpty()) {
                        // mapIterator = sb.tables.orderByPK(sb.tables.bookmarks.folders.getBookmarks(bmk_user, post.get("folders")), maxcount).iterator();
                    	mapIterator = sb.tables.bookmarks.getBookmarksByFolder(bmk_user, post.get("folders"));
                    } else if(post.containsKey("tags") && !post.get("tags").isEmpty()) {
                    	// mapIterator = sb.tables.orderByPK(sb.tables.bookmarks.tags.getBookmarks(bmk_user, post.get("tags")), maxcount).iterator();
                    	final String tagsString = YMarkUtil.cleanTagsString(post.get(YMarkEntry.BOOKMARK.TAGS.key()));
                    	mapIterator = sb.tables.bookmarks.getBookmarksByTag(bmk_user, tagsString);
                    } else {
                    	mapIterator = sb.tables.orderByPK(sb.tables.iterator(table, matcher), maxcount).iterator();
                    }

                    Tables.Row row;
                    boolean dark = true;
                    byte[] cell;
                    while (mapIterator.hasNext() && count < maxcount) {
                        row = mapIterator.next();
                        if (row == null) continue;

                        // write table content
                        prop.put("showtable_list_" + count + "_dark", ((dark) ? 1 : 0) ); dark=!dark;
                        prop.put("showtable_list_" + count + "_pk", UTF8.String(row.getPK()));
                        prop.put("showtable_list_" + count + "_count", count);
                        for (int i = 0; i < columns.size(); i++) {
                            cell = row.get(columns.get(i));
                            prop.putHTML("showtable_list_" + count + "_columns_" + i + "_cell", cell == null ? "" : UTF8.String(cell));
                        }
                        prop.put("showtable_list_" + count + "_columns", columns.size());
                        count++;
                    }
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
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

    private static void setEdit(final Switchboard sb, final serverObjects prop, final String table, final String pk, final List<String> columns) throws IOException, SpaceExceededException {
        prop.put("showedit", 1);
        prop.put("showedit_table", table);
        prop.put("showedit_pk", pk);
        final Tables.Row row = sb.tables.select(table, pk.getBytes());
        if (row == null) return;
        int count = 0;
        byte[] cell;
        for (final String col: columns) {
            cell = row.get(col);
            prop.put("showedit_list_" + count + "_key", col);
            prop.put("showedit_list_" + count + "_value", cell == null ? "" : UTF8.String(cell));
            count++;
        }
        prop.put("showedit_list", count);
    }
}

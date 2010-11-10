import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import de.anomic.data.YMarkTables;
import de.anomic.data.YMarksXBELImporter;
import de.anomic.data.userDB;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class get_xbel {
	public static final String ROOT = "root";
	public static final String SOURCE = "source";

	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
		final Switchboard sb = (Switchboard) env;
		final serverObjects prop = new serverObjects();        
		final HashSet<String> alias = new HashSet<String>();
		final StringBuilder buffer = new StringBuilder(250);
		final userDB.Entry user = sb.userDB.getUser(header); 
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(userDB.Entry.BOOKMARK_RIGHT);
		final String bmk_user;
        
        if(isAdmin || isAuthUser) {
        	bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);
        	
        	String root = YMarkTables.FOLDERS_ROOT;  	
        	String[] foldername = null;
        	
        	// TODO: better handling of query
        	if (post != null){
        		if (post.containsKey(ROOT)) {
            		if (post.get(ROOT).equals(SOURCE) || post.get(ROOT).equals(YMarkTables.FOLDERS_ROOT)) {
            			root = "";
            		} else if (post.get(ROOT).startsWith(YMarkTables.FOLDERS_ROOT)) {
            			root = post.get(ROOT);
            		} else {
            			root = "";            			
            		}
        		}
        	} else {
        		root = "";
        	}
        	
        	final int root_depth = root.split(YMarkTables.FOLDERS_SEPARATOR).length;
    		Iterator<String> fit = null;
        	Iterator<String> bit = null;
        	int count = 0;    		
        	int n = root_depth;
        	
        	try {
				fit = sb.tables.bookmarks.folders.getFolders(bmk_user, root);
			} catch (IOException e) {
				Log.logException(e);
			}

			Log.logInfo(YMarkTables.BOOKMARKS_LOG, "root: "+root+" root_deph: "+root_depth);
			
			while (fit.hasNext()) {    		   		
        		String folder = fit.next();
        		foldername = folder.split(YMarkTables.FOLDERS_SEPARATOR); 
        		if (n != root_depth && foldername.length <= n) {
					prop.put("xbel_"+count+"_elements", "</folder>");
            		count++;
        		}
        		if (foldername.length >= n) {
        			n = foldername.length;
        			if(n != root_depth) {
                		prop.put("xbel_"+count+"_elements", "<folder id=\"f:"+new String(YMarkTables.getKeyId(foldername[n-1]))+"\">");
                		count++;
                		prop.put("xbel_"+count+"_elements", "<title>" + CharacterCoding.unicode2xml(foldername[n-1], true) + "</title>");   		
                		count++;	
        			}
            		try {
            			bit = sb.tables.bookmarks.folders.getBookmarkIds(bmk_user, folder).iterator();
            	    	Tables.Row bmk_row = null;
            	    	String urlHash;
            			while(bit.hasNext()){ 
            				urlHash = new String(bit.next());
            	    		if(alias.contains(urlHash)) {
            	    			buffer.setLength(0);
            	    			buffer.append(YMarksXBELImporter.XBEL.ALIAS.startTag(true));
            	    			buffer.append(" ref=\"b:");
            	    			buffer.append(urlHash);
            	    			buffer.append("\"/>");            	    			
            	    			prop.put("xbel_"+count+"_elements", buffer.toString()); 		
            		    		count++;  	
            	    		} else {
            					alias.add(urlHash);
            	    			bmk_row = sb.tables.select(YMarkTables.TABLES.BOOKMARKS.tablename(bmk_user), urlHash.getBytes());
            		        	if(bmk_row != null) {
            		        		buffer.setLength(0);
            		        		
            		        		buffer.append(YMarksXBELImporter.XBEL.BOOKMARK.startTag(true));
            		        		buffer.append(" id=\"b:");
            		        		buffer.append(urlHash);
            		        		
            		        		buffer.append(YMarkTables.BOOKMARK.URL.xbel());
            		        		buffer.append(CharacterCoding.unicode2xml(bmk_row.get(YMarkTables.BOOKMARK.URL.key(), YMarkTables.BOOKMARK.URL.deflt()), true));
            		        		
            		        		buffer.append(YMarkTables.BOOKMARK.DATE_ADDED.xbel());
            		        		buffer.append(CharacterCoding.unicode2xml(YMarkTables.getISO8601(bmk_row.get(YMarkTables.BOOKMARK.DATE_ADDED.key())), true));
            		        		
            		        		buffer.append(YMarkTables.BOOKMARK.DATE_MODIFIED.xbel());
            		        		buffer.append(CharacterCoding.unicode2xml(YMarkTables.getISO8601(bmk_row.get(YMarkTables.BOOKMARK.DATE_MODIFIED.key())), true));
            		        		
            		        		buffer.append(YMarkTables.BOOKMARK.DATE_VISITED.xbel());
            		        		buffer.append(CharacterCoding.unicode2xml(YMarkTables.getISO8601(bmk_row.get(YMarkTables.BOOKMARK.DATE_VISITED.key())), true));
            		        		
            		        		buffer.append(YMarkTables.BOOKMARK.TAGS.xbel());
            		        		buffer.append(bmk_row.get(YMarkTables.BOOKMARK.TAGS.key(), YMarkTables.BOOKMARK.TAGS.deflt()));
            		        		
            		        		buffer.append(YMarkTables.BOOKMARK.PUBLIC.xbel());
            		        		buffer.append(bmk_row.get(YMarkTables.BOOKMARK.PUBLIC.key(), YMarkTables.BOOKMARK.PUBLIC.deflt()));
            		        		
            		        		buffer.append(YMarkTables.BOOKMARK.VISITS.xbel());
            		        		buffer.append(bmk_row.get(YMarkTables.BOOKMARK.VISITS.key(), YMarkTables.BOOKMARK.VISITS.deflt()));
            		        		
            		        		buffer.append("\"\n>");
            		        		prop.put("xbel_"+count+"_elements", buffer.toString());
            			    		count++; 
            			    		
            		        		buffer.setLength(0);
            		        		buffer.append(YMarksXBELImporter.XBEL.TITLE.startTag(false));
            			    		buffer.append(CharacterCoding.unicode2xml(bmk_row.get(YMarkTables.BOOKMARK.TITLE.key(), YMarkTables.BOOKMARK.TITLE.deflt()), true));
            			    		buffer.append(YMarksXBELImporter.XBEL.TITLE.endTag(false));
            			    		prop.put("xbel_"+count+"_elements", buffer.toString());
            			    		count++;

            			    		buffer.setLength(0);
            		        		buffer.append(YMarksXBELImporter.XBEL.DESC.startTag(false));
            			    		buffer.append(CharacterCoding.unicode2xml(bmk_row.get(YMarkTables.BOOKMARK.DESC.key(), YMarkTables.BOOKMARK.DESC.deflt()), true));
            			    		buffer.append(YMarksXBELImporter.XBEL.DESC.endTag(false));
            			    		prop.put("xbel_"+count+"_elements", buffer.toString());
            			    		count++;
            			    		
            			    		prop.put("xbel_"+count+"_elements", YMarksXBELImporter.XBEL.BOOKMARK.endTag(false));   		
            			    		count++;    
            		        	}
            				}
            			}
					} catch (IOException e) {
						Log.logException(e);
						continue;
					} catch (RowSpaceExceededException e) {
						Log.logException(e);
						continue;
					}
        		}
        	}
			while(n > root_depth) {
				prop.put("xbel_"+count+"_elements", YMarksXBELImporter.XBEL.FOLDER.endTag(false));
	    		count++;
	    		n--;
			}
    		prop.put("user", bmk_user.substring(0,1).toUpperCase() + bmk_user.substring(1));
    		prop.put("xbel", count);
    		
        }  else {
        	prop.put(YMarkTables.USER_AUTHENTICATE,YMarkTables.USER_AUTHENTICATE_MSG);
        }  
        // return rewrite properties
        return prop;
	}
}

	

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import javax.swing.text.html.parser.ParserDelegator;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.logging.Log;
import de.anomic.data.YMarksHTMLImporter;
import de.anomic.data.YMarkTables;
import de.anomic.data.userDB;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class import_html {

	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        final userDB.Entry user = sb.userDB.getUser(header); 
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(userDB.Entry.BOOKMARK_RIGHT);
        
        if(isAdmin || isAuthUser) {
        	final String bmk_table = (isAuthUser ? user.getUserName() : YMarkTables.TABLE_BOOKMARKS_USER_ADMIN)+YMarkTables.TABLE_BOOKMARKS_BASENAME;
        	if(post.containsKey("htmlfile")){
				try {
					final ByteArrayInputStream byteIn = new ByteArrayInputStream(post.get("htmlfile$file").getBytes("UTF-8"));
					if(byteIn !=null) {
						final InputStreamReader reader =  new InputStreamReader(byteIn,"UTF-8");
						final ParserDelegator delegator =  new ParserDelegator();
						final YMarksHTMLImporter htmlHandler = new YMarksHTMLImporter(sb.tables, bmk_table);
						delegator.parse(reader, htmlHandler, true);
					}
				} catch (UnsupportedEncodingException e) {
	                Log.logException(e);
				} catch (IOException e) {
	                Log.logException(e);
				}				 
				prop.put("result", "1");
        	}
        }
        // return rewrite properties
        return prop;
	}
}










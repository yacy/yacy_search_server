import java.util.Iterator;

import net.yacy.yacy;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.interaction.Interaction;
import net.yacy.search.Switchboard;
import de.anomic.data.BookmarkHelper;
import de.anomic.data.UserDB;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public final class DemoServlet {

	public static serverObjects respond(final RequestHeader header,
			final serverObjects post, final serverSwitch env) {
		
		// return variable that accumulates replacements
		final serverObjects prop = new serverObjects();
		
		final Switchboard sb = Switchboard.getSwitchboard();
		
		prop.put("temperature", "-10°C");
		
		// Display currently logged on user
		prop.put("username", Interaction.GetLoggedOnUser(header));
		
		//Generate Userlist
		int numUsers = 0;
		for (String user : Interaction.GetUsers()) {
            prop.putHTML("users_"+numUsers+"_user", user);
            numUsers++;
        }
        prop.put("users", numUsers);
        
        
		
		if (post != null) {
		
		if (post.containsKey("submit")) {
			
			prop.put("temperature", post.get("textthing"));
			
			String filename= post.get("textthing");
					
			int counter = 0;
			
			while (counter < 10) {
				
				prop.put("localimg_"+counter+"_path","/"+filename);
				
				prop.put("localimg_"+counter+"_checked", "2");
				counter++;
			}
			
			prop.put("localimg", counter);
			
			
			
			prop.put("temperature", yacy.homedir+"/DATA/HTDOCS/"+filename);
		}
		
		}
			
		// return rewrite properties
		return prop;
	}

}

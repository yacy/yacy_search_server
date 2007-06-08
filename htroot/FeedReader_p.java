//FeedReader_p.java
//------------
// part of YACY
//
// (C) 2007 Alexander Schier
//
// last change: $LastChangedDate:  $ by $LastChangedBy: $
// $LastChangedRevision: $
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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Iterator;

import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;
import de.nava.informa.core.ChannelIF;
import de.nava.informa.core.ParseException;
import de.nava.informa.impl.basic.ChannelBuilder;
import de.nava.informa.impl.basic.Item;
import de.nava.informa.parsers.FeedParser;

public class FeedReader_p {
    
    
    public static servletProperties respond(httpHeader header, serverObjects post, serverSwitch env) {
        servletProperties prop = new servletProperties();
        prop.put("SUPERTEMPLATE", "/env/page.html");
        
        URL url;
		try {
			if(post!=null){
				url = new URL((String) post.get("url"));
				int maxitems=Integer.parseInt(post.get("max", "0"));
				int offset=Integer.parseInt(post.get("offset", "0")); //offset to the first displayed item
				ChannelBuilder builder=new ChannelBuilder();
				ChannelIF channel=FeedParser.parse(builder, url);
				prop.put("page_title", channel.getTitle());
				if(channel.getCreator()!=null){
					prop.put("page_hasAuthor", 1);
					prop.put("page_hasAuthor_author", channel.getCreator());
				}else
					prop.put("page_hasAuthor", 0);
				prop.put("page_description", channel.getDescription());

				Collection feedItems=channel.getItems();
				if(!feedItems.isEmpty()){
					Iterator it=feedItems.iterator();
					int count=0;
					while(it.hasNext() && (maxitems==0 || count<maxitems)){
						Item item=(Item)it.next();
						prop.put("page_items_"+count+"_author", item.getCreator());
						prop.put("page_items_"+count+"_title", item.getTitle());
						prop.put("page_items_"+count+"_link", item.getLink().toString());
						prop.putASIS("page_items_"+count+"_description", item.getDescription());
						prop.put("page_items_"+count+"_date", item.getDate());
						if(! (offset-- >0)) //first check, if there still is an offset. if yes, skip count++, so the next item will overwrite this item. then decrement offset.
							count++;
					}
					prop.put("page_items", count);
				}
				prop.put("page", 1);
			}
		} catch (MalformedURLException e) {
			prop.put("page", 2);
		} catch (IOException e) {
			prop.put("page", 2);
		} catch (ParseException e) {
			prop.put("page", 2);
		}
        
        // return rewrite properties
        return prop;
    }
}

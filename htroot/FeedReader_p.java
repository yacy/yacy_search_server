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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Iterator;

import de.anomic.data.rssReader;
import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;

public class FeedReader_p {
    
	Class rssReaderClass;
	String url;
	Object reader;
	public FeedReader_p(String url) throws ClassNotFoundException, NoClassDefFoundError{
		this.url=url;
		//reflection
		Class[] paramClasses=new Class[1];
		Object[] params=new Object[1];
		paramClasses[0]=Class.forName("java.lang.String");
        rssReaderClass=Class.forName("de.anomic.data.rssReader");
        params[0]=url.toString();
		Constructor constructor;
		try {
			constructor = rssReaderClass.getConstructor(paramClasses);
			reader=constructor.newInstance(params);
		}
		catch (SecurityException e) {} 
		catch (NoSuchMethodException e) {}
		catch (IllegalArgumentException e) {} 
		catch (InstantiationException e) {} 
		catch (IllegalAccessException e) {} 
		catch (InvocationTargetException e) {}
		
	}
	//this could be in the servlet class(?)
	public Object ReflectionWrapper(Class theclass, Object theinstance, String method, Class[] class_array, Object[] object_array){
		try {
			return theclass.getMethod(method, class_array).invoke(theinstance, object_array);
		} 
		catch (IllegalArgumentException e) {} 
		catch (SecurityException e) {} 
		catch (IllegalAccessException e) {} 
		catch (InvocationTargetException e) {} 
		catch (NoSuchMethodException e) {}
		return null;
	}
	public String getTitle(){
		return (String) ReflectionWrapper(rssReaderClass, reader, "getTitle", null, null);
	}
	public String getCreator(){
		return (String) ReflectionWrapper(rssReaderClass, reader, "getCreator", new Class[0], new Object[0]);
	}
	public String getDescription(){
		return (String) ReflectionWrapper(rssReaderClass, reader, "getDescription", new Class[0], new Object[0]);
	}
	public Collection getFeedItems(){
		return (Collection) ReflectionWrapper(rssReaderClass, reader, "getFeedItems", new Class[0], new Object[0]);
	}
    public static servletProperties respond(httpHeader header, serverObjects post, serverSwitch env) {
        servletProperties prop = new servletProperties();
        
        URL url;
        prop.put("page", 0);
		try {
			if(post!=null){
				url = new URL((String) post.get("url"));
				int maxitems=Integer.parseInt(post.get("max", "0"));
				int offset=Integer.parseInt(post.get("offset", "0")); //offset to the first displayed item
				FeedReader_p self;
				try {
					self = new FeedReader_p(url.toString());
				} catch (ClassNotFoundException e) {
					prop.put("page", 2);
					prop.put("page_error", 0);
					return prop;
				} catch (NoClassDefFoundError e) {
					prop.put("page", 2);
					prop.put("page_error", 0);
					return prop;
				}
				
				//rssReader reader=new rssReader(url.toString());
				prop.put("page_title", self.getTitle());
				if(self.getCreator()!=null){
					prop.put("page_hasAuthor", 1);
					prop.put("page_hasAuthor_author", self.getCreator());
				}else
					prop.put("page_hasAuthor", 0);
				prop.put("page_description", self.getDescription());

				Collection feedItems=self.getFeedItems();
				if(feedItems!=null && !feedItems.isEmpty()){
					Iterator it=feedItems.iterator();
					int count=0;
					while(it.hasNext() && (maxitems==0 || count<maxitems)){
						rssReader.Item item=(rssReader.Item)it.next();
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
			prop.put("page_error", 1);
		}
        
        // return rewrite properties
        return prop;
    }
}

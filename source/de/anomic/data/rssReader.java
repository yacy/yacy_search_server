//rssReader.java
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

package de.anomic.data;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.TreeSet;

import de.nava.informa.core.ChannelIF;
import de.nava.informa.core.ParseException;
import de.nava.informa.impl.basic.ChannelBuilder;
import de.nava.informa.parsers.FeedParser;

import de.anomic.yacy.yacyCore;

public class rssReader {
	URL url;
	ChannelIF channel;
	TreeSet feedItems;
	public rssReader(String url) throws MalformedURLException{
		this.url=new URL(url);
		String yAddress=yacyCore.seedDB.resolveYacyAddress(this.url.getHost());
		if(yAddress != null){
			this.url=new URL(this.url.getProtocol()+"://"+yAddress+"/"+this.url.getPath());
		}
		ChannelBuilder builder=new ChannelBuilder();
		try {
			channel=FeedParser.parse(builder, this.url);
			Collection oldfeedItems=channel.getItems();
			feedItems=new TreeSet(new ItemComparator());
			Iterator it=oldfeedItems.iterator();
			int count=0;
			while(it.hasNext()){
				de.nava.informa.impl.basic.Item item=(de.nava.informa.impl.basic.Item) it.next();
				Item newItem=new Item(count++, item.getLink(), item.getTitle(), item.getDescription(), item.getDate(), item.getCreator());
				feedItems.add(newItem);
			}
		}
		catch (IOException e) {} 
		catch (ParseException e) {}
	}
	public String getCreator(){
		return (channel!=null)? channel.getCreator(): null;
	}
	public String getTitle(){
		return (channel!=null)? channel.getTitle(): null;
	}
	public String getDescription(){
		return (channel!=null)? channel.getDescription(): null;
	}
	public Collection getFeedItems(){
		return feedItems;
	}
    
    public class Item{
        String creator, title, description;
        Date date;
        URL link;
        int num;
        public Item(int num, URL link, String title, String description, Date date, String creator){
            this.link=link;
            this.title=title;
            this.description=description;
            this.date=date;
            this.creator=creator;
            this.num=num;
        }
        public URL getLink(){
            return link;
        }
        public String getTitle(){
            return (title!=null)? title: "";
        }
        public String getDescription(){
            return (description!=null)? description: "";
        }
        public Date getDate(){
            return (date!=null)? date: new Date();
        }
        public String getCreator(){
            return (creator!=null)? creator: "";
        }
        public int getNum(){
            return num;
        }
        
    }
    
    public class ItemComparator implements Comparator {
        public int compare(Object o1, Object o2){
            int num1=((Item)o1).getNum();
            int num2=((Item)o2).getNum();
            return num2-num1;
        }
        public boolean equals(Object o1, Object o2){
            return compare(o1, o2)==0;
        }
    }
	
}

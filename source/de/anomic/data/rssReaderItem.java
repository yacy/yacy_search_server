//rssReaderItem.java
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

import java.net.URL;
import java.util.Date;


public class rssReaderItem{
	String creator, title, description;
	Date date;
	URL link;
	int num;
	public rssReaderItem(int num, URL link, String title, String description, Date date, String creator){
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
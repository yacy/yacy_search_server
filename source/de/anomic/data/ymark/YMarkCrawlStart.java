// YMarkCrawlStart.java
// (C) 2011 by Stefan Förster, sof@gmx.de, Norderstedt, Germany
// first published 2010 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2011-03-09 13:50:39 +0100 (Mi, 09 Mrz 2011) $
// $LastChangedRevision: 7574 $
// $LastChangedBy: apfelmaennchen $
//
// LICENSE
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

package de.anomic.data.ymark;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;

import net.yacy.kelondro.blob.Tables;

import de.anomic.data.WorkTables;

public class YMarkCrawlStart extends HashMap<String,String>{

	private static final long serialVersionUID = 1L;
	private WorkTables worktables;
	
	public YMarkCrawlStart(final WorkTables worktables) {
		this.worktables = worktables;
	}
	
	public YMarkCrawlStart(final WorkTables worktables, final String url) {
		this.worktables = worktables;
		this.clear();
		this.load(url);
	}
	
	public void load(String url) {	
		try {
			final StringBuffer buffer = new StringBuffer(500);
			buffer.append("^.*crawlingURL=\\Q");
			buffer.append(url);
			buffer.append("\\E?.*");
			final Pattern pattern = Pattern.compile(buffer.toString());			
			final Iterator<Tables.Row> APIcalls = this.worktables.iterator(WorkTables.TABLE_API_NAME, WorkTables.TABLE_API_COL_URL, pattern);
			Tables.Row row = null;			
			while(APIcalls.hasNext()) {
				row = APIcalls.next();
				if(row.get(WorkTables.TABLE_API_COL_TYPE, "").equals("crawler")) {
					buffer.setLength(0);
					buffer.append(row.get(WorkTables.TABLE_API_COL_URL, ""));
					buffer.delete(0, buffer.indexOf("?")+1);					
					int start = 0;
					int end = 0;
					String key;
					String value;
					while(start < buffer.length()) {
						end = buffer.indexOf("=", start);
						key = buffer.substring(start, end);
						start = end+1;
						end = buffer.indexOf("&", start);
						if(end < 0 || end > buffer.length())
							end = buffer.length()-1;
						value = buffer.substring(start, end);
						start = end+1;
						this.put(key, value);
					}
					break;
				}				
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
		}
	}
}

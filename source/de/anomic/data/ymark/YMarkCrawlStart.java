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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;

import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.blob.Tables;
import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.CrawlSwitchboard;
import de.anomic.data.WorkTables;

public class YMarkCrawlStart extends HashMap<String,String>{

	private static final long serialVersionUID = 1L;
	private final WorkTables worktables;
	private Date date_last_exec;
	private Date date_next_exec;
	private Date date_recording;
	private String apicall_pk;
	private String url;

	public YMarkCrawlStart(final WorkTables worktables) {
		super();
		this.date_recording = new Date(0);
		this.worktables = worktables;
	}

	public YMarkCrawlStart(final WorkTables worktables, final String url) {
		super();
		this.worktables = worktables;
		this.url = url;
		this.date_recording = new Date(0);
		this.clear();
		this.load();
	}

	public String getPK() {
		if(this.isEmpty())
			return "";
		return this.apicall_pk;
	}

	public Date date_last_exec() {
		if(this.isEmpty())
			return new Date(0);
		return this.date_last_exec;
	}

	public Date date_next_exec() {
		if(this.isEmpty())
			return new Date(0);
		return this.date_next_exec;
	}

	public boolean hasSchedule() {
		return !this.isEmpty() && this.date_next_exec.after(new Date());
	}

	public boolean isRunning(final CrawlSwitchboard crawler) {
		final Iterator<byte[]> iter = crawler.getActive().iterator();
		while(iter.hasNext()) {
			final byte[] key = iter.next();
			final CrawlProfile crawl = crawler.getActive(key);
			if (crawl.startURL().equals(this.url)) {
				return true;
			}
		}
		return false;
	}

	public Date date_recording() {
		return this.date_recording;
	}

	public void set_url(final String url) {
		if(!this.url.equals(url)) {
			this.url = url;
			this.clear();
			this.load();
		}
	}

	public int exec(final String host, final int port, final String realm) {
		return this.worktables.execAPICall(this.apicall_pk, host, port, realm);
	}

	private void load() {
		try {
			final StringBuilder buffer = new StringBuilder(500);
			buffer.append("^crawl start for ");
			buffer.append(Pattern.quote(this.url));
			buffer.append("?.*");
			final Pattern pattern = Pattern.compile(buffer.toString());
			//final Iterator<Tables.Row> APIcalls = this.worktables.iterator(WorkTables.TABLE_API_NAME, WorkTables.TABLE_API_COL_URL, pattern);
			final Iterator<Tables.Row> APIcalls = this.worktables.iterator(WorkTables.TABLE_API_NAME, WorkTables.TABLE_API_COL_COMMENT, pattern);
			Tables.Row row = null;
			while(APIcalls.hasNext()) {
				row = APIcalls.next();
				if(row.get(WorkTables.TABLE_API_COL_TYPE, "").equals("crawler")) {
					Date date = row.get(WorkTables.TABLE_API_COL_DATE_RECORDING, row.get(WorkTables.TABLE_API_COL_DATE, new Date()));
					if(date.after(this.date_recording)) {
						this.clear();
						this.apicall_pk = UTF8.String(row.getPK());
						this.date_recording = date;
						this.date_next_exec = row.get(WorkTables.TABLE_API_COL_DATE_NEXT_EXEC, new Date(0));
						this.date_last_exec = row.get(WorkTables.TABLE_API_COL_DATE_LAST_EXEC, new Date(0));
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
							put(key, value);
						}
					}
				}
			}
		} catch (final IOException e) {
			// TODO Auto-generated catch block
		}
	}
}

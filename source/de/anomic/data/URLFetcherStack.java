// URLFetcherStack.java 
// -------------------------------------
// part of YACY
//
// (C) 2007 by Franz Brausze
//
// last change: $LastChangedDate: $ by $LastChangedBy: $
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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Iterator;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroStack;
import de.anomic.kelondro.kelondroRow.EntryIndex;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;

public class URLFetcherStack {
    
    public static final String DBFILE = "urlRemote2.stack";
    
    private static final kelondroRow rowdef = new kelondroRow(
            "String urlstring-256",
            kelondroBase64Order.enhancedCoder,
            0
    );
    private final kelondroStack db;
    private final serverLog log;
    
    private int popped = 0;
    private int pushed = 0;
    
    public URLFetcherStack(final File path) throws IOException {
        this.db = new kelondroStack(new File(path, DBFILE), rowdef);
        this.log = new serverLog("URLFETCHERSTACK");
    }
    
    public int getPopped() { return this.popped; }
    public int getPushed() { return this.pushed; }
    public void clearStat() { this.popped = 0; this.pushed = 0; }
    
    public void finalize() throws Throwable {
        this.db.close();
    }
    
    public boolean push(final yacyURL url) {
        try {
            this.db.push(this.db.row().newEntry(
                    new byte[][] { url.toNormalform(true, true).getBytes() }
            ));
            this.pushed++;
            return true;
        } catch (final IOException e) {
            this.log.logSevere("error storing entry", e);
            return false;
        }
    }
    
    public yacyURL pop() {
        try {
            final kelondroRow.Entry r = this.db.pop();
            if (r == null) return null;
            final String url = r.getColString(0, null);
            try {
                this.popped++;
                return new yacyURL(url, null);
            } catch (final MalformedURLException e) {
                this.log.logSevere("found invalid URL-entry: " + url);
                return null;
            }
        } catch (final IOException e) {
            this.log.logSevere("error retrieving entry", e);
            return null;
        }
    }
    
    public String[] top(final int count) {
        try {
            final ArrayList<String> ar = new ArrayList<String>();
            final Iterator<EntryIndex> it = db.contentRows(500);
            kelondroRow.EntryIndex ei;
            for (int i=0; i<count && it.hasNext(); i++) {
                ei = it.next();
                if (ei == null) continue;
                ar.add(ei.getColString(0, null));
            }
            return ar.toArray(new String[ar.size()]);
        } catch (final kelondroException e) {
            this.log.logSevere("error retrieving entry", e);
            return null;
        }
    }
    
    public int size() {
        return this.db.size();
    }
}

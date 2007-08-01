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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

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
import de.anomic.net.URL;
import de.anomic.server.logging.serverLog;

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
    
    public URLFetcherStack(String path) throws IOException {
        this.db = new kelondroStack(
                new File(path + File.separator + DBFILE),
                rowdef);
        this.log = new serverLog("URLFETCHERSTACK");
    }
    
    public int getPopped() { return this.popped; }
    public int getPushed() { return this.pushed; }
    public void clearStat() { this.popped = 0; this.pushed = 0; }
    
    public void finalize() throws Throwable {
        this.db.close();
    }
    
    public boolean push(URL url) {
        try {
            this.db.push(this.db.row().newEntry(
                    new byte[][] { url.toNormalform(true, true).getBytes() }
            ));
            this.pushed++;
            return true;
        } catch (IOException e) {
            this.log.logSevere("error storing entry", e);
            return false;
        }
    }
    
    public URL pop() {
        try {
            kelondroRow.Entry r = this.db.pop();
            if (r == null) return null;
            final String url = r.getColString(0, null);
            try {
                this.popped++;
                return new URL(url);
            } catch (MalformedURLException e) {
                this.log.logSevere("found invalid URL-entry: " + url);
                return null;
            }
        } catch (IOException e) {
            this.log.logSevere("error retrieving entry", e);
            return null;
        }
    }
    
    public String[] top(int count) {
        try {
            final ArrayList ar = new ArrayList();
            Iterator it = db.contentRows(500);
            kelondroRow.EntryIndex ei;
            for (int i=0; i<count && it.hasNext(); i++) {
                ei = (kelondroRow.EntryIndex)it.next();
                if (ei == null) continue;
                ar.add(ei.getColString(0, null));
            }
            return (String[])ar.toArray(new String[ar.size()]);
        } catch (kelondroException e) {
            this.log.logSevere("error retrieving entry", e);
            return null;
        }
    }
    
    public int size() {
        return this.db.size();
    }
}

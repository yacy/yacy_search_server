// loaderThreads.java 
// ---------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package de.anomic.tools;

import java.util.Hashtable;

import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.http.ProxySettings;
import net.yacy.kelondro.data.meta.DigestURI;

public class loaderThreads {
    
    // global values for loader threads
    protected int timeout;
    protected String user;
    protected String password;
    protected ProxySettings remoteProxyConfig;

    // management objects for collection of threads
    Hashtable<String, Thread> threads;
    int completed, failed;
    
    public loaderThreads() {
       this(10000, null, null);
    }
    
    public loaderThreads(
            final int timeout, 
            final String user, 
            final String password
    ) {
        this.timeout = timeout;
        this.user = user;
        this.password = password;
        this.threads = new Hashtable<String, Thread>();
        this.completed = 0;
        this.failed = 0;
    }
    
    public void newThread(final String name, final DigestURI url, final loaderProcess process) {
        final Thread t = new loaderThread(url, process);
        threads.put(name, t);
        t.start();
    }
    
    public void terminateThread(final String name) {
        final loaderThread t = (loaderThread) threads.get(name);
        if (t == null) throw new RuntimeException("no such thread: " + name);
        t.terminate();
    }
    
    public int threadCompleted(final String name) {
        final loaderThread t = (loaderThread) threads.get(name);
        if (t == null) throw new RuntimeException("no such thread: " + name);
        return t.completed();
    }
    
    public int threadStatus(final String name) {
        final loaderThread t = (loaderThread) threads.get(name);
        if (t == null) throw new RuntimeException("no such thread: " + name);
        return t.status();
    }
    
    public int completed() {
        return completed;
    }
    
    public int failed() {
        return failed;
    }
    
    public int count() {
        return threads.size();
    }
    
    public Exception threadError(final String name) {
        final loaderThread t = (loaderThread) threads.get(name);
        if (t == null) throw new RuntimeException("no such thread: " + name);
        return t.error();
    }

    protected class loaderThread extends Thread {
        private final DigestURI url;
        private Exception error;
        private final loaderProcess process;
        private byte[] page;
        private boolean loaded;
        
        public loaderThread(final DigestURI url, final loaderProcess process) {
            this.url = url;
            this.process = process;
            this.error = null;
            this.page = null;
            this.loaded = false;
        }

        public void run() {
            try {
                page = url.get(ClientIdentification.getUserAgent(), timeout);
                loaded = true;
                process.feed(page);
                if (process.status() == loaderCore.STATUS_FAILED) {
                    error = process.error();
                }
                if ((process.status() == loaderCore.STATUS_COMPLETED) ||
                    (process.status() == loaderCore.STATUS_FINALIZED)) completed++;
                if ((process.status() == loaderCore.STATUS_ABORTED) ||
                    (process.status() == loaderCore.STATUS_FAILED)) failed++;
            } catch (final Exception e) {
                error = e;
                failed++;
            }
        }
            
	public void terminate() {
            process.terminate();
        }
        
        public boolean loaded() {
            return loaded;
        }
        
        public int completed() {
            if (process.status() == loaderCore.STATUS_READY) return 1;
            if (process.status() == loaderCore.STATUS_RUNNING) return 9 + ((process.completed() * 9) / 10);
            if (process.status() == loaderCore.STATUS_COMPLETED) return 100;
            return 0;
        }

        public int status() {
            return process.status(); // see constants in loaderCore
        }
    
        public Exception error() {
            return error;
        }
        
    }

}

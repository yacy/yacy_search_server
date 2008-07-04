// loaderThreads.java 
// ---------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 28.09.2004
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

package de.anomic.tools;

import java.util.ArrayList;
import java.util.Hashtable;

import de.anomic.crawler.HTTPLoader;
import de.anomic.http.HttpClient;
import de.anomic.http.httpHeader;
import de.anomic.http.httpRemoteProxyConfig;
import de.anomic.yacy.yacyURL;

public class loaderThreads {
    
    // global values for loader threads
    protected int timeout;
    protected String user;
    protected String password;
    protected httpRemoteProxyConfig remoteProxyConfig;

    // management objects for collection of threads
    Hashtable<String, Thread> threads;
    int completed, failed;
    
    public loaderThreads() {
       this(10000, null, null);
    }
    
    public loaderThreads(
            int timeout, 
            String user, 
            String password
    ) {
        this.timeout = timeout;
        this.user = user;
        this.password = password;
        this.threads = new Hashtable<String, Thread>();
        this.completed = 0;
        this.failed = 0;
    }
    
    public void newPropLoaderThread(String name, yacyURL url) {
        newThread(name, url, new propLoader());
    }
    
    public void newThread(String name, yacyURL url, loaderProcess process) {
        Thread t = new loaderThread(url, process);
        threads.put(name, t);
        t.start();
    }
    
    public void terminateThread(String name) {
        loaderThread t = (loaderThread) threads.get(name);
        if (t == null) throw new RuntimeException("no such thread: " + name);
        else t.terminate();
    }
    
    public int threadCompleted(String name) {
        loaderThread t = (loaderThread) threads.get(name);
        if (t == null) throw new RuntimeException("no such thread: " + name);
        else return t.completed();
    }
    
    public int threadStatus(String name) {
        loaderThread t = (loaderThread) threads.get(name);
        if (t == null) throw new RuntimeException("no such thread: " + name);
        else return t.status();
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
    
    public Exception threadError(String name) {
        loaderThread t = (loaderThread) threads.get(name);
        if (t == null) throw new RuntimeException("no such thread: " + name);
        else return t.error();
    }

    protected class loaderThread extends Thread {
        private yacyURL url;
        private Exception error;
        private loaderProcess process;
        private byte[] page;
        private boolean loaded;
        
        public loaderThread(yacyURL url, loaderProcess process) {
            this.url = url;
            this.process = process;
            this.error = null;
            this.page = null;
            this.loaded = false;
        }

        public void run() {
            try {
                httpHeader reqHeader = new httpHeader();
                reqHeader.put(httpHeader.USER_AGENT, HTTPLoader.crawlerUserAgent);
                page = HttpClient.wget(url.toString(), reqHeader, timeout);
                loaded = true;
                process.feed(page);
                if (process.status() == loaderCore.STATUS_FAILED) {
                    error = process.error();
                }
                if ((process.status() == loaderCore.STATUS_COMPLETED) ||
                    (process.status() == loaderCore.STATUS_FINALIZED)) completed++;
                if ((process.status() == loaderCore.STATUS_ABORTED) ||
                    (process.status() == loaderCore.STATUS_FAILED)) failed++;
            } catch (Exception e) {
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
    
    public class propLoader extends loaderCore implements loaderProcess {
        
        public propLoader() {
            this.status = STATUS_READY;
        }
        
        public synchronized void feed(byte[] v) {
            this.status = STATUS_RUNNING;
            this.completion = 1;
            int line = 0;
            String s, key, value;
            int p;
            ArrayList<String> lines = nxTools.strings(v);
            try {
                while ((this.run) && (line < lines.size())) {
                    // parse line and construct a property
                    s = lines.get(line);
                    if ((s != null) && ((p = s.indexOf('=')) > 0)) {
                        key = s.substring(0, p).trim();
                        value = s.substring(p + 1).trim();
                        if (key.length() > 0) result.put(key, value);
                    }
                    // update thread information
                    line++;
                    this.completion = 100 * line / lines.size();
                }
                if (line == lines.size()) {
                    this.status = STATUS_COMPLETED;
                    return;
                } else {
                    this.status = STATUS_ABORTED;
                    return;
                }
            } catch (Exception e) {
                this.status = STATUS_FAILED;
                this.error = e;
                return;
            }
        }
    }
    
    /*
    public static void main(String[] args) {
        httpdProxyHandler.setRemoteProxyConfig(httpRemoteProxyConfig.init("192.168.1.122", 3128));
        loaderThreads loader = new loaderThreads();
        try {
            loader.newPropLoaderThread("load1", new yacyURL("http://www.anomic.de/superseed.txt", null));
        } catch (MalformedURLException e) {
            
        }
    }
    */
}

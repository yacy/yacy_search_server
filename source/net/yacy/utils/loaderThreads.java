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

package net.yacy.utils;

import java.util.HashMap;
import java.util.Map;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;

public class loaderThreads {

    // global values for loader threads
    protected int timeout;
    protected String user;
    protected String password;

    // management objects for collection of threads
    private final Map<String, Thread> threads;
    private int completed, failed;

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
        this.threads = new HashMap<String, Thread>();
        this.completed = 0;
        this.failed = 0;
    }

    public void newThread(final String name, final DigestURL url, final loaderProcess process, final ClientIdentification.Agent agent) {
        final Thread t = new loaderThread(url, process, agent);
        this.threads.put(name, t);
        t.start();
    }

    public void terminateThread(final String name) {
        final loaderThread t = (loaderThread) this.threads.get(name);
        if (t == null) throw new RuntimeException("no such thread: " + name);
        t.terminate();
    }

    public int threadCompleted(final String name) {
        final loaderThread t = (loaderThread) this.threads.get(name);
        if (t == null) throw new RuntimeException("no such thread: " + name);
        return t.completed();
    }

    public int threadStatus(final String name) {
        final loaderThread t = (loaderThread) this.threads.get(name);
        if (t == null) throw new RuntimeException("no such thread: " + name);
        return t.status();
    }

    public int completed() {
        return this.completed;
    }

    public int failed() {
        return this.failed;
    }

    public int count() {
        return this.threads.size();
    }

    public Exception threadError(final String name) {
        final loaderThread t = (loaderThread) this.threads.get(name);
        if (t == null) throw new RuntimeException("no such thread: " + name);
        return t.error();
    }

    protected class loaderThread extends Thread {
        private final DigestURL url;
        private Exception error;
        private final loaderProcess process;
        private byte[] page;
        private boolean loaded;
        final ClientIdentification.Agent agent;

        public loaderThread(final DigestURL url, final loaderProcess process, final ClientIdentification.Agent agent) {
            this.url = url;
            this.process = process;
            this.error = null;
            this.page = null;
            this.loaded = false;
            this.agent = agent;
        }

        @Override
        public void run() {
            try {
                this.page = this.url.get(this.agent, null, null);
                this.loaded = true;
                this.process.feed(this.page);
                if (this.process.status() == loaderCore.STATUS_FAILED) {
                    this.error = this.process.error();
                }
                if ((this.process.status() == loaderCore.STATUS_COMPLETED) ||
                    (this.process.status() == loaderCore.STATUS_FINALIZED)) loaderThreads.this.completed++;
                if ((this.process.status() == loaderCore.STATUS_ABORTED) ||
                    (this.process.status() == loaderCore.STATUS_FAILED)) loaderThreads.this.failed++;
            } catch (final Exception e) {
                this.error = e;
                loaderThreads.this.failed++;
            }
        }

	public void terminate() {
            this.process.terminate();
        }

        public boolean loaded() {
            return this.loaded;
        }

        public int completed() {
            if (this.process.status() == loaderCore.STATUS_READY) return 1;
            if (this.process.status() == loaderCore.STATUS_RUNNING) return 9 + ((this.process.completed() * 9) / 10);
            if (this.process.status() == loaderCore.STATUS_COMPLETED) return 100;
            return 0;
        }

        public int status() {
            return this.process.status(); // see constants in loaderCore
        }

        public Exception error() {
            return this.error;
        }

    }

}

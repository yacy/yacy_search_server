/**
 *  TimeoutRequest
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 08.10.2007 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.protocol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.yacy.cora.util.ConcurrentLog;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/**
 * TimeoutRequest is a class that can apply a timeout on method calls that may block
 * for undefined time. Some network operations can only be accessed without a given
 * time-out value. Using this class all network operations may be timed out.
 * This class provides also some static methods that give already solutions for typical
 * network operations that should be timed-out, like dns resolving and reverse domain name resolving.
 */
public class TimeoutRequest<E> {

    private final Callable<E> call;

    /**
     * initialize the TimeoutRequest with a callable method
     */
    public TimeoutRequest(final Callable<E> call) {
        this.call = call;
    }

    /**
     * call the method using a time-out
     * @param timeout
     * @return
     * @throws ExecutionException
     */
    public E call(final long timeout) throws ExecutionException {
        final ExecutorService service = Executors.newSingleThreadExecutor();
        try {
            final Future<E> taskFuture = service.submit(this.call);
            final Runnable t = new Runnable() {
                @Override
                public void run() { taskFuture.cancel(true); }
            };
            service.execute(t);
            service.shutdown();
            try {
                return taskFuture.get(timeout, TimeUnit.MILLISECONDS);
            } catch (final CancellationException e) {
                // callable was interrupted
                throw new ExecutionException(e);
            } catch (final InterruptedException e) {
                // service was shutdown
                throw new ExecutionException(e);
            } catch (final ExecutionException e) {
                // callable failed unexpectedly
                throw e;
            } catch (final TimeoutException e) {
                // time-out
                throw new ExecutionException(e);
            }
        } catch (final OutOfMemoryError e) {
            ConcurrentLog.warn(TimeoutRequest.class.getName(), "OutOfMemoryError / retry follows", e);
            // in case that no memory is there to create a new native thread
            try {
                return this.call.call();
            } catch (final Exception e1) {
                throw new ExecutionException(e1);
            }
        }
    }

    /**
     * ping a remote server using a given uri and a time-out
     * @param uri
     * @param timeout
     * @return true if the server exists and replies within the given time-out
     * @throws ExecutionException
     */
    public static boolean ping(final String host, final int port, final int timeout) throws ExecutionException {
        return new TimeoutRequest<Boolean>(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                //long time = System.currentTimeMillis();
                try {
                    final Socket socket = new Socket();
                    //System.out.println("PING socket create = " + (System.currentTimeMillis() - time) + " ms (" + host + ":" + port + ")"); time = System.currentTimeMillis();
                    socket.connect(new InetSocketAddress(host, port), timeout);
                    //System.out.println("PING socket connect = " + (System.currentTimeMillis() - time) + " ms (" + host + ":" + port + ")"); time = System.currentTimeMillis();
                    if (socket.isConnected()) {
                        socket.close();
                        return Boolean.TRUE;
                    }
                    //System.out.println("PING socket close = " + (System.currentTimeMillis() - time) + " ms (" + host + ":" + port + ")"); time = System.currentTimeMillis();
                    return Boolean.FALSE;
                } catch (final UnknownHostException e) {
                    //System.out.println("PING socket UnknownHostException = " + (System.currentTimeMillis() - time) + " ms (" + host + ":" + port + ")"); time = System.currentTimeMillis();
                    return Boolean.FALSE;
                } catch (final IOException e) {
                    //System.out.println("PING socket IOException = " + (System.currentTimeMillis() - time) + " ms (" + host + ":" + port + ")"); time = System.currentTimeMillis();
                    return Boolean.FALSE;
                }
            }
        }).call(timeout).booleanValue();
    }

    /**
     * perform a reverse domain name lookup for a given InetAddress within a given timeout
     * @param i
     * @param timeout
     * @return the host name of a given InetAddress
     * @throws ExecutionException
     */
    public static String getHostName(final InetAddress i, final long timeout) throws ExecutionException {
        return new TimeoutRequest<String>(new Callable<String>() {
            @Override
            public String call() { return i.getHostName(); }
        }).call(timeout);
    }

    /**
     * check if a smb file exists
     * @param file
     * @param timeout
     * @return
     * @throws IOException
     */
    public static boolean exists(final SmbFile file, final long timeout) throws IOException {
        try {
            return new TimeoutRequest<Boolean>(new Callable<Boolean>() {
                @Override
                public Boolean call() { try {
                    return file.exists();
                } catch (final SmbException e) {
                    return Boolean.FALSE;
                } }
            }).call(timeout).booleanValue();
        } catch (final ExecutionException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * check if a smb file can be read
     * @param file
     * @param timeout
     * @return
     * @throws IOException
     */
    public static boolean canRead(final SmbFile file, final long timeout) throws IOException {
        try {
            return new TimeoutRequest<Boolean>(new Callable<Boolean>() {
                @Override
                public Boolean call() { try {
                    return file.canRead();
                } catch (final SmbException e) {
                    return Boolean.FALSE;
                } }
            }).call(timeout).booleanValue();
        } catch (final ExecutionException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * check if a smb file ran be written
     * @param file
     * @param timeout
     * @return
     * @throws IOException
     */
    public static boolean canWrite(final SmbFile file, final long timeout) throws IOException {
        try {
            return new TimeoutRequest<Boolean>(new Callable<Boolean>() {
                @Override
                public Boolean call() { try {
                    return file.canWrite();
                } catch (final SmbException e) {
                    return Boolean.FALSE;
                } }
            }).call(timeout).booleanValue();
        } catch (final ExecutionException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * check if a smb file is hidden
     * @param file
     * @param timeout
     * @return
     * @throws IOException
     */
    public static boolean isHidden(final SmbFile file, final long timeout) throws IOException {
        try {
            return new TimeoutRequest<Boolean>(new Callable<Boolean>() {
                @Override
                public Boolean call() { try {
                    return file.isHidden();
                } catch (final SmbException e) {
                    return Boolean.FALSE;
                } }
            }).call(timeout).booleanValue();
        } catch (final ExecutionException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * check if a smb file is a directory
     * @param file
     * @param timeout
     * @return
     * @throws IOException
     */
    public static boolean isDirectory(final SmbFile file, final long timeout) throws IOException {
        try {
            return new TimeoutRequest<Boolean>(new Callable<Boolean>() {
                @Override
                public Boolean call() { try {
                    return file.isDirectory();
                } catch (final SmbException e) {
                    return Boolean.FALSE;
                } }
            }).call(timeout).booleanValue();
        } catch (final ExecutionException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * get the size of a smb file
     * @param file
     * @param timeout
     * @return
     * @throws IOException
     */
    public static long length(final SmbFile file, final long timeout) throws IOException {
        try {
            return new TimeoutRequest<Long>(new Callable<Long>() {
                @Override
                public Long call() { try {
                    return file.length();
                } catch (final SmbException e) {
                    return Long.valueOf(0);
                } }
            }).call(timeout).longValue();
        } catch (final ExecutionException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * get last-modified time of a smb file
     * @param file
     * @param timeout
     * @return
     * @throws IOException
     */
    public static long lastModified(final SmbFile file, final long timeout) throws IOException {
        try {
            return new TimeoutRequest<Long>(new Callable<Long>() {
                @Override
                public Long call() { try {
                    return file.lastModified();
                } catch (final SmbException e) {
                    return Long.valueOf(0);
                } }
            }).call(timeout).longValue();
        } catch (final ExecutionException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * get list of a smb directory
     * @param file
     * @param timeout
     * @return
     * @throws IOException
     */
    public static String[] list(final SmbFile file, final long timeout) throws IOException {
        try {
            return new TimeoutRequest<String[]>(new Callable<String[]>() {
                @Override
                public String[] call() { try {
                    return file.list();
                } catch (final SmbException e) {
                    //Log.logWarning("TimeoutRequest:list", file.toString() + " - no list", e);
                    return null;
                } }
            }).call(timeout);
        } catch (final ExecutionException e) {
            throw new IOException(e.getMessage());
        }
    }

}

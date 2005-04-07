// serverCore.java 
// -------------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2002-2004
// last major change: 09.03.2004
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

package de.anomic.server;

// standard server
import java.io.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.lang.reflect.*;

// needed for ssl
import javax.net.*;
import javax.net.ssl.*;
import java.security.KeyStore;
import javax.security.cert.X509Certificate;

public class serverCore extends serverAbstractThread implements serverThread {

    // generic input/output static methods
    public static final byte cr = 13;
    public static final byte lf = 10;
    public static final byte[] crlf = {cr, lf};
    public static final String crlfString = new String(crlf);

    // static variables
    public static final Boolean TERMINATE_CONNECTION = Boolean.FALSE;
    public static final Boolean RESUME_CONNECTION = Boolean.TRUE;

    // class variables
    private int port;                      // the listening port
    private ServerSocket socket;           // listener
    private int maxSessions = 0;           // max. number of sessions; 0=unlimited
    private serverLog log;                 // log object
    //private serverSwitch switchboard;      // external values
    private int timeout;                   // connection time-out of the socket
    private Hashtable activeThreads;       // contains the active threads
    private Hashtable sleepingThreads;     // contains the threads that are alive since the sleepthreashold
    private boolean termSleepingThreads;   // if true then threads over sleepthreashold are killed
    private int thresholdActive = 5000;    // after that time a thread should have got a command line
    private int thresholdSleep = 30000;     // after that time a thread is considered as beeing sleeping (30 seconds)
    private int thresholdDead = 3600000;     // after that time a thread is considered as beeing dead-locked (1 hour)
    private serverHandler handlerPrototype;// the command class (a serverHandler) 
    private Class[] initHandlerClasses;    // the init's methods arguments
    private Class[] initSessionClasses;    // the init's methods arguments
    private serverSwitch switchboard;      // the command class switchboard
    private Hashtable denyHost;
    private int commandMaxLength;

    private static ServerSocketFactory getServerSocketFactory(boolean dflt, File keyfile, String passphrase) {
        // see doc's at
        // http://java.sun.com/developer/technicalArticles/Security/secureinternet/
        if (dflt) {
            return ServerSocketFactory.getDefault();
        } else {
	    SSLServerSocketFactory ssf = null;
	    try {
		// set up key manager to do server authentication
		SSLContext ctx;
		KeyManagerFactory kmf;
		KeyStore ks;
		char[] pp = passphrase.toCharArray();

                // open keystore
		ks = KeyStore.getInstance("JKS");
		ks.load(new FileInputStream(keyfile), pp);
                
                // get a KeyManager Factory
                String algorithm = KeyManagerFactory.getDefaultAlgorithm(); // should be "SunX509"
                kmf = KeyManagerFactory.getInstance(algorithm);
                kmf.init(ks, pp);
                
                // create a ssl context with the keyManager Factory
                //ctx = SSLContext.getInstance("TLS");
                ctx = SSLContext.getInstance("SSLv3");
                
		ctx.init(kmf.getKeyManagers(), null, null);

		ssf = ctx.getServerSocketFactory();
		return ssf;
	    } catch (Exception e) {
		e.printStackTrace();
                return null;
	    }
	}
    }

    // class initializer
    public serverCore(int port, int maxSessions, int timeout,
                      boolean termSleepingThreads, boolean blockAttack,
                      serverHandler handlerPrototype, serverSwitch switchboard,
                      int commandMaxLength, int logl) throws IOException {
	this.port = port;
        this.commandMaxLength = commandMaxLength;
	this.denyHost = (blockAttack) ? new Hashtable() : null;

        /*
        try {
            ServerSocketFactory ssf = getServerSocketFactory(false, new File("D:\\dev\\proxy\\addon\\testkeys"), "passphrase");
            this.socket = ssf.createServerSocket(port);
            //((SSLServerSocket) this.socket ).setNeedClientAuth(true);
        } catch (java.net.BindException e) {
            System.out.println("FATAL ERROR: " + e.getMessage() + " - probably root access rights needed. check port number"); System.exit(0);
        }
        */
        
	try {
            this.socket = new ServerSocket(port);
	} catch (java.net.BindException e) {
 	    System.out.println("FATAL ERROR: " + e.getMessage() + " - probably root access rights needed. check port number"); System.exit(0);
	}
        
        try {
	    this.handlerPrototype = handlerPrototype;
            this.switchboard = switchboard;
	    this.initHandlerClasses = new Class[] {Class.forName("de.anomic.server.serverSwitch")};
	    this.initSessionClasses = new Class[] {Class.forName("de.anomic.server.serverCore$Session")};
	    this.maxSessions = maxSessions;
	    this.socket.setSoTimeout(0); // unlimited
	    this.timeout = timeout;
	    this.termSleepingThreads = termSleepingThreads;
            this.log = new serverLog("SERVER", logl);
	    activeThreads = new Hashtable();
	    sleepingThreads = new Hashtable();
	} catch (java.lang.ClassNotFoundException e) {
 	    System.out.println("FATAL ERROR: " + e.getMessage() + " - Class Not Found"); System.exit(0);
	}
    }
    
    public static boolean isNotLocal(URL url) {
        return isNotLocal(url.getHost());
    }
    
    private static boolean isNotLocal(String ip) {
	if ((ip.equals("localhost")) ||
	    (ip.startsWith("127")) ||
	    (ip.startsWith("192.168")) ||
	    (ip.startsWith("10."))
	    ) return false;
	return true;
    }
    
    public static InetAddress publicIP() {
	try {
	    // list all addresses
	    //InetAddress[] ia = InetAddress.getAllByName("localhost");
	    InetAddress[] ia = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
	    //for (int i = 0; i < ia.length; i++) System.out.println("IP: " + ia[i].getHostAddress()); // DEBUG
	    if (ia.length == 0) {
		try {
		    return InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
		    try {
			return InetAddress.getByName("127.0.0.1");
		    } catch (UnknownHostException ee) {
			return null;
		    }
		}
	    }
	    if (ia.length == 1) {
		// only one network connection available
		return ia[0];
	    }
	    // we have more addresses, find an address that is not local
	    int b0, b1;
	    for (int i = 0; i < ia.length; i++) {
		b0 = 0Xff & ia[i].getAddress()[0];
		b1 = 0Xff & ia[i].getAddress()[1];
		if ((b0 != 10) && // class A reserved
		    (b0 != 127) && // loopback
		    ((b0 != 172) || (b1 < 16) || (b1 > 31)) && // class B reserved
		    ((b0 != 192) || (b0 != 168)) && // class C reserved
		    (ia[i].getHostAddress().indexOf(":") < 0)
		    ) return ia[i];
	    }
	    // there is only a local address, we filter out the possibly returned loopback address 127.0.0.1
	    for (int i = 0; i < ia.length; i++) {
		if (((0Xff & ia[i].getAddress()[0]) != 127) &&
		    (ia[i].getHostAddress().indexOf(":") < 0)) return ia[i];
	    }
	    // if all fails, give back whatever we have
	    for (int i = 0; i < ia.length; i++) {
		if (ia[i].getHostAddress().indexOf(":") < 0) return ia[i];
	    }
	    return ia[0];
	} catch (java.net.UnknownHostException e) {
	    System.err.println("ERROR: (internal) " + e.getMessage());
	    return null;
	}
    }

    public void open() {
        log.logSystem("* server started on " + publicIP() + ":" + port);
    }
    
    // class body
    public boolean job() throws Exception {
        // prepare for new connection
        idleThreadCheck();
        switchboard.handleBusyState(activeThreads.size());

        log.logDebug(
        "* waiting for connections, " + activeThreads.size() + " sessions running, " +
        sleepingThreads.size() + " sleeping");
        
        // list all connection (debug)
        /*
        if (activeThreads.size() > 0) {
            Enumeration threadEnum = activeThreads.keys();
            Session se;
            long time;
            while (threadEnum.hasMoreElements()) {
                se = (Session) threadEnum.nextElement();
                time = System.currentTimeMillis() - ((Long) activeThreads.get(se)).longValue();
                log.logDebug("* ACTIVE SESSION (" + ((se.isAlive()) ? "alive" : "dead") + ", " + time + "): " + se.request);
            }
        }
        */
        
        // wait for new connection
        announceThreadBlockApply();
        Socket controlSocket = socket.accept();
        announceThreadBlockRelease();
        if ((denyHost == null) || (denyHost.get((""+controlSocket.getInetAddress().getHostAddress())) == null)) {
            //log.logDebug("* catched request from " + controlSocket.getInetAddress().getHostAddress());
            controlSocket.setSoTimeout(timeout);
            
            Session connection = new Session(controlSocket);
            // start the thread
            connection.start();
            //try {Thread.currentThread().sleep(1000);} catch (InterruptedException e) {} // wait for debug
            activeThreads.put(connection, new Long(System.currentTimeMillis()));
            //log.logDebug("* NEW SESSION: " + connection.request);
            
        } else {
            System.out.println("ACCESS FROM " + controlSocket.getInetAddress().getHostAddress() + " DENIED");
        }
        // idle until number of maximal threads is (again) reached
        //synchronized(this) {
        while ((maxSessions > 0) && (activeThreads.size() >= maxSessions)) try {
            log.logDebug("* Waiting for activeThreads=" + activeThreads.size() + " < maxSessions=" + maxSessions);
            Thread.currentThread().sleep(2000);
            idleThreadCheck();
        } catch (InterruptedException e) {}
        return true;
    }

    public void close() {
        log.logSystem("* terminated");
    }
    
    public int getJobCount() {
        return activeThreads.size();
    }
    
    // idle sensor: the thread is idle if there are no sessions running
    public boolean idle() {
        idleThreadCheck();
        return (activeThreads.size() == 0);
    }
    
    public void idleThreadCheck() {
	// a 'garbage collector' for session threads
	Enumeration threadEnum;
	Session session;
        
	// look for sleeping threads
	threadEnum = activeThreads.keys();
        long time;
	while (threadEnum.hasMoreElements()) {
	    session = (Session) (threadEnum.nextElement());
            //if (session.request == null) session.interrupt();
	    if (session.isAlive()) {
                // check if socket still exists
                time = System.currentTimeMillis() - ((Long) activeThreads.get(session)).longValue();
                if (/*(session.controlSocket.isClosed()) || */
                    (!(session.controlSocket.isBound())) ||
                    (!(session.controlSocket.isConnected())) ||
                    ((session.request == null) && (time > 1000))) {
                    // kick it
                    try {
                        session.out.close();
                        session.in.close();
                        session.controlSocket.close();
                    } catch (IOException e) {}
                    session.interrupt(); // hopefully this wakes him up.
                    activeThreads.remove(session);
                    String reason = "";
                    if (session.controlSocket.isClosed()) reason = "control socked closed";
                    if (!(session.controlSocket.isBound())) reason = "control socked unbound";
                    if (!(session.controlSocket.isConnected())) reason = "control socked not connected";
                    if (session.request == null) reason = "no request placed";
                    log.logDebug("* canceled disconnected connection (" + reason + ") '" + session.request + "'");
                } else if (time > thresholdSleep) {
		    // move thread from the active threads to the sleeping
		    sleepingThreads.put(session, activeThreads.remove(session));
                    log.logDebug("* sleeping connection '" + session.request + "'");
		} else if ((time > thresholdActive) && (session.request == null)) {
		    // thread is not in use (or too late). kickk it.
		    try {
                        session.out.close();
                        session.in.close();
                        session.controlSocket.close();
                    } catch (IOException e) {}
                    session.interrupt(); // hopefully this wakes him up.
                    activeThreads.remove(session);
                    log.logDebug("* canceled inactivated connection");
		}
	    } else {
		// the thread is dead, remove it
                log.logDebug("* normal close of connection to '" + session.request + "', time=" + session.getTime());
		activeThreads.remove(session);
	    }
	}

	// look for dead threads
	threadEnum = sleepingThreads.keys();
	while (threadEnum.hasMoreElements()) {
	    session = (Session) (threadEnum.nextElement());
	    if (session.isAlive()) {
		// check the age of the thread
		if (System.currentTimeMillis() - ((Long) sleepingThreads.get(session)).longValue() > thresholdDead) {
		    // kill the thread
		    if (termSleepingThreads) {
                        try {
                            session.out.close();
                            session.in.close();
                            session.controlSocket.close();
                        } catch (IOException e) {}
                        session.interrupt(); // hopefully this wakes him up.
                    }
		    sleepingThreads.remove(session);
                    log.logDebug("* out-timed connection '" + session.request + "'");
		}
	    } else {
		// the thread is dead, remove it
		sleepingThreads.remove(session);
                log.logDebug("* dead connection '" + session.request + "'");
	    }
	}

    }

    public class Session extends Thread {
	
        private long start;                // startup time
        private serverHandler commandObj;
	private String request;            // current command line
	private int commandCounter;        // for logging: number of commands in this session
	private String identity;           // a string that identifies the client (i.e. ftp: account name)
	//private boolean promiscuous;       // if true, no lines are read and streams are only passed
	public  Socket controlSocket;      // dialog socket
	public  InetAddress userAddress;   // the address of the client
	public  PushbackInputStream in;    // on control input stream
	public  OutputStream out;          // on control output stream, autoflush
	
	public Session(Socket controlSocket) throws IOException {
	    //this.promiscuous = false;
            this.start = System.currentTimeMillis();
            //log.logDebug("* session " + handle + " allocated");
	    this.identity = "-";
	    this.userAddress = controlSocket.getInetAddress();
	    String ipname = userAddress.getHostAddress();
	    // check if we want to allow this socket to connect us
	    this.controlSocket = controlSocket;
	    this.in = new PushbackInputStream(controlSocket.getInputStream());
	    this.out = controlSocket.getOutputStream();
	    commandCounter = 0;
	    // initiate the command class
	    // we pass the input and output stream to the commands,
	    // so that they can take over communication, if needed
	    try {
		// use the handler prototype to create a new command object class
                commandObj = (serverHandler) handlerPrototype.clone();
                commandObj.initSession(this);
	    } catch (Exception e) {
                e.printStackTrace();
            }
            //log.logDebug("* session " + handle + " initialized. time = " + (System.currentTimeMillis() - handle));
	}
	
        public long getTime() {
            return System.currentTimeMillis() - start;
        }
        
	public void setIdentity(String id) {
	    this.identity = id;
	}

	/*
	public void setPromiscuous() {
	    this.promiscuous = true;
	}
	*/

	public void log(boolean outgoing, String request) {
	    log.logInfo(userAddress.getHostAddress() + "/" + this.identity + " " +
		     "[" + activeThreads.size() + ", " + commandCounter +
		     ((outgoing) ? "] > " : "] < ") +
		     request);
	}

	public void writeLine(String messg) throws IOException {
	    send(out, messg);
	    log(true, messg);
	}

	public byte[] readLine() {
	    return receive(in, timeout, commandMaxLength, false);
	}

	public final void run() {
            //log.logDebug("* session " + handle + " started. time = " + (System.currentTimeMillis() - handle));
	    try {
		listen();
	    } finally {
		try {
                    out.flush();
                    // close everything
                    out.close();
                    in.close();
                    controlSocket.close();
		} catch (IOException e) {
		    System.err.println("ERROR: (internal) " + e);
		}
		synchronized (this) {this.notify();}
	    }
            //log.logDebug("* session " + handle + " completed. time = " + (System.currentTimeMillis() - handle));
            announceMoreExecTime(System.currentTimeMillis() - start);
	}
	
	private void listen() {
	    try {
		// set up some reflection
		Class[] stringType    = {"".getClass()};
		Class[] exceptionType = {Class.forName("java.lang.Throwable")};
		
                // send greeting
		Object result = commandObj.greeting();
		if (result != null) {
		    if ((result instanceof String) && (((String) result).length() > 0)) writeLine((String) result);
		}

		// start dialog
		byte[] requestBytes = null;
		boolean terminate = false;
		int pos;
		String cmd;
		String tmp;
		Object[] stringParameter = new String[1];
		while ((in != null) && ((requestBytes = readLine()) != null)) {
		    commandCounter++;
		    request = new String(requestBytes);
                    //log.logDebug("* session " + handle + " received command '" + request + "'. time = " + (System.currentTimeMillis() - handle));
		    log(false, request);
		    try {
			pos = request.indexOf(' ');
			if (pos < 0) {
			    cmd = request.trim().toUpperCase();
			    stringParameter[0] = "";
			} else {
			    cmd = request.substring(0, pos).trim().toUpperCase();
			    stringParameter[0] = request.substring(pos).trim();
			}

			// exec command and return value
			result = commandObj.getClass().getMethod(cmd, stringType).invoke(commandObj, stringParameter);
                        //log.logDebug("* session " + handle + " completed command '" + request + "'. time = " + (System.currentTimeMillis() - handle));
                        this.out.flush();
			if (result == null) {
			    /*
			    log(2, true, "(NULL RETURNED/STREAM PASSED)");
			    */
			} else if (result instanceof Boolean) {
			    if (((Boolean) result) == TERMINATE_CONNECTION) break;
			} else if (result instanceof String) {
			    if (((String) result).startsWith("!")) {
				result = ((String) result).substring(1);
				terminate = true;
			    }
			    writeLine((String) result);
			} else if (result instanceof InputStream) {
			    tmp = send(out, (InputStream) result);
			    if ((tmp.length() > 4) && (tmp.toUpperCase().startsWith("PASS"))) {
				log(true, "PASS ********");
			    } else {
				log(true, tmp);
			    }
			    tmp = null;
			}
			if (terminate) break;

                    } catch (InvocationTargetException ite) {
			System.out.println("ERROR A " + userAddress.getHostAddress());
			// we extract a target exception and let the thread survive
			writeLine((String) commandObj.error(ite.getTargetException()));
		    } catch (NoSuchMethodException nsme) {
			System.out.println("ERROR B " + userAddress.getHostAddress());
			if (isNotLocal(userAddress.getHostAddress().toString())) {
                            if (denyHost != null)
                                denyHost.put((""+userAddress.getHostAddress()), "deny"); // block client: hacker attempt
			}
			break;
			// the client requested a command that does not exist
			//Object[] errorParameter = { nsme };
			//writeLine((String) error.invoke(this.cmdObject, errorParameter));
		    } catch (IllegalAccessException iae) {
			System.out.println("ERROR C " + userAddress.getHostAddress());
			// wrong parameters: this an only be an internal problem
			writeLine((String) commandObj.error(iae));
		    } catch (java.lang.ClassCastException e) {
			System.out.println("ERROR D " + userAddress.getHostAddress());
			// ??
			writeLine((String) commandObj.error(e));
		    } catch (Exception e) {
			System.out.println("ERROR E " + userAddress.getHostAddress());
			// whatever happens: the thread has to survive!
			writeLine("UNKNOWN REASON:" + (String) commandObj.error(e));
		    }
                }
	    } catch (java.lang.ClassNotFoundException e) {
		System.out.println("Internal Error: wrapper class not found: " + e.getMessage());
		System.exit(0);
	    } catch (java.io.IOException e) {
		// connection interruption: more or less normal
	    }
	}
	
    }

    public static byte[] receive(PushbackInputStream pbis, long timeout, int maxSize, boolean logerr) {
        // this is essentially a readln on a PushbackInputStream
        int bufferSize = 0;
        bufferSize = 10;

	try {
	    long t = timeout;
	    while (((bufferSize = pbis.available()) == 0) && (t > 0)) try {
		Thread.currentThread().sleep(100);
		t -= 100;
	    } catch (InterruptedException e) {}
	    if (t <= 0) {
                if (logerr) serverLog.logError("SERVER", "receive interrupted - timeout");
                return null;
            }
	    if (bufferSize == 0) {
                if (logerr) serverLog.logError("SERVER", "receive interrupted - buffer empty");
                return null;
            }
        } catch (IOException e) {
            if (logerr) serverLog.logError("SERVER", "receive interrupted - exception 1 = " + e.getMessage());
	    return null;
	}
        
	byte[] buffer = new byte[bufferSize];
	byte[] bufferBkp;
	bufferSize = 0;
	int b = 0;
	
	try {
	    while ((b = pbis.read()) > 31) {
		// we have a valid byte in b, add it to the buffer
		if (buffer.length == bufferSize) {
		    // the buffer is full, double its size
		    bufferBkp = buffer;
		    buffer = new byte[bufferSize * 2];
		    java.lang.System.arraycopy(bufferBkp, 0, buffer, 0, bufferSize);
		    bufferBkp = null;
		}
                //if (bufferSize > 10000) {System.out.println("***ERRORDEBUG***:" + new String(buffer));} // debug
		buffer[bufferSize++] = (byte) b; // error hier: ArrayIndexOutOfBoundsException: -2007395416 oder 0
                if (bufferSize > maxSize) break;
            }
	    // we have catched a possible line end
	    if (b == cr) {
		// maybe a lf follows, read it:
		if ((b = pbis.read()) != lf) if (b >= 0) pbis.unread(b); // we push back the byte
	    }
	    
	    // finally shrink buffer
	    bufferBkp = buffer;
	    buffer = new byte[bufferSize];
	    java.lang.System.arraycopy(bufferBkp, 0, buffer, 0, bufferSize);
	    bufferBkp = null;
	    
	    // return only the byte[]
	    return buffer;
	} catch (IOException e) {
            if (logerr) serverLog.logError("SERVER", "receive interrupted - exception 2 = " + e.getMessage());
	    return null;
	}
    }

    public static void send(OutputStream os, String buf) throws IOException {
	os.write(buf.getBytes());
	os.write(crlf);
	os.flush();
    }
    
    public static void send(OutputStream os, byte[] buf) throws IOException {
	os.write(buf);
	os.write(crlf);
	os.flush();
    }
        
    public static String send(OutputStream os, InputStream is) throws IOException {
	int bufferSize = is.available();
	byte[] buffer = new byte[((bufferSize < 1) || (bufferSize > 4096)) ? 4096 : bufferSize];
	int l;
	while ((l = is.read(buffer)) > 0) {os.write(buffer, 0, l);}
	os.write(crlf);
	os.flush();
	if (bufferSize > 80) return "<LONG STREAM>"; else return new String(buffer);
    }

}

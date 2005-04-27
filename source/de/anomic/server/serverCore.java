// serverCore.java 
// -------------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2002-2004
// last major change: 09.03.2004
//
// ThreadPool
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Hashtable;

// needed for ssl
import javax.net.*;
import javax.net.ssl.*;
import java.security.KeyStore;

import org.apache.commons.pool.impl.GenericObjectPool;

public final class serverCore extends serverAbstractThread implements serverThread {

    // generic input/output static methods
    public static final byte cr = 13;
    public static final byte lf = 10;
    public static final byte[] crlf = {cr, lf};
    public static final String crlfString = new String(crlf);

    // static variables
    public static final Boolean TERMINATE_CONNECTION = Boolean.FALSE;
    public static final Boolean RESUME_CONNECTION = Boolean.TRUE;
    public static Hashtable bfHost = new Hashtable(); // for brute-force prevention

    // class variables
    private int port;                      // the listening port
    private ServerSocket socket;           // listener
    private int maxSessions = 0;           // max. number of sessions; 0=unlimited
    serverLog log;                         // log object
    private int timeout;                   // connection time-out of the socket
    
    private boolean termSleepingThreads;   // if true then threads over sleepthreashold are killed
    private int thresholdActive = 5000;    // after that time a thread should have got a command line
    private int thresholdSleep = 30000;    // after that time a thread is considered as beeing sleeping (30 seconds)
    private int thresholdDead = 3600000;   // after that time a thread is considered as beeing dead-locked (1 hour)
    serverHandler handlerPrototype;        // the command class (a serverHandler) 
    private Class[] initHandlerClasses;    // the init's methods arguments
    private Class[] initSessionClasses;    // the init's methods arguments
    private serverSwitch switchboard;      // the command class switchboard
    private Hashtable denyHost;
    private int commandMaxLength;
    
    /**
     * The session-object pool
     */
    final SessionPool theSessionPool;
    final ThreadGroup theSessionThreadGroup = new ThreadGroup("sessionThreadGroup");

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
    	    this.timeout = timeout;
    	    this.termSleepingThreads = termSleepingThreads;
            this.log = new serverLog("SERVER", logl);
//    	    activeThreads = new Hashtable();
//    	    sleepingThreads = new Hashtable();
    	} catch (java.lang.ClassNotFoundException e) {
     	    System.out.println("FATAL ERROR: " + e.getMessage() + " - Class Not Found"); System.exit(0);
    	}
        
        // implementation of session thread pool
        GenericObjectPool.Config config = new GenericObjectPool.Config();
        
        // The maximum number of active connections that can be allocated from pool at the same time,
        // 0 for no limit
        config.maxActive = this.maxSessions;
        
        // The maximum number of idle connections connections in the pool
        // 0 = no limit.        
        config.maxIdle = this.maxSessions / 2;
        config.minIdle = this.maxSessions / 4;    
        
        // block undefinitely 
        config.maxWait = timeout; 
        
        // Action to take in case of an exhausted DBCP statement pool
        // 0 = fail, 1 = block, 2= grow        
        config.whenExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_BLOCK; 
        config.minEvictableIdleTimeMillis = this.thresholdSleep; 
        config.testOnReturn = true;
        
        this.theSessionPool = new SessionPool(new SessionFactory(this.theSessionThreadGroup),config);  
    
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
        // idleThreadCheck();
        this.switchboard.handleBusyState(this.theSessionPool.getNumActive() /*activeThreads.size() */);

        log.logDebug(
        "* waiting for connections, " + this.theSessionPool.getNumActive() + " sessions running, " +
        this.theSessionPool.getNumIdle() + " sleeping");
        
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
        Socket controlSocket = this.socket.accept();
        announceThreadBlockRelease();
	String clientIP = ""+controlSocket.getInetAddress().getHostAddress();
        if (bfHost.get(clientIP) != null) {
            log.logInfo("SLOWING DOWN ACCESS FOR BRUTE-FORCE PREVENTION FROM " + clientIP);
	    // add a delay to make brute-force harder
	    try {Thread.currentThread().sleep(1000);} catch (InterruptedException e) {}
	}
        if ((this.denyHost == null) || (this.denyHost.get(clientIP) == null)) {
            controlSocket.setSoTimeout(this.timeout);
            Session connection = (Session) this.theSessionPool.borrowObject();
            connection.execute(controlSocket);
            //log.logDebug("* NEW SESSION: " + connection.request + " from " + clientIP);
        } else {
            System.out.println("ACCESS FROM " + clientIP + " DENIED");
        }
        // idle until number of maximal threads is (again) reached
        //synchronized(this) {
//        while ((maxSessions > 0) && (activeThreads.size() >= maxSessions)) try {
//            log.logDebug("* Waiting for activeThreads=" + activeThreads.size() + " < maxSessions=" + maxSessions);
//            Thread.currentThread().sleep(2000);
//            idleThreadCheck();
//        } catch (InterruptedException e) {}
        return true;
    }

    public void close() {
        try {
            // consuming the isInterrupted Flag. Otherwise we could not properly close the session pool
            Thread.interrupted();
            
            // close the session pool
            this.theSessionPool.close();
        }
        catch (Exception e) {
            this.log.logSystem("Unable to close session pool: " + e.getMessage());
        }
        log.logSystem("* terminated");
    }
    
    public int getJobCount() {
        return this.theSessionPool.getNumActive();
    }
    
    // idle sensor: the thread is idle if there are no sessions running
    public boolean idle() {
        // idleThreadCheck();
        return (this.theSessionPool.getNumActive() == 0);
    }
    
    public final class SessionPool extends GenericObjectPool 
    {
        public boolean isClosed = false;
        
        /**
         * First constructor.
         * @param objFactory
         */        
        public SessionPool(SessionFactory objFactory) {
            super(objFactory);
            this.setMaxIdle(75); // Maximum idle threads.
            this.setMaxActive(150); // Maximum active threads.
            this.setMinEvictableIdleTimeMillis(30000); //Evictor runs every 30 secs.
            //this.setMaxWait(1000); // Wait 1 second till a thread is available
        }
        
        public SessionPool(SessionFactory objFactory,
                           GenericObjectPool.Config config) {
            super(objFactory, config);
        }
        
        /**
         * @see org.apache.commons.pool.impl.GenericObjectPool#borrowObject()
         */
        public Object borrowObject() throws Exception  {
           return super.borrowObject();
        }

        /**
         * @see org.apache.commons.pool.impl.GenericObjectPool#returnObject(java.lang.Object)
         */
        public void returnObject(Object obj) throws Exception  {
            super.returnObject(obj);
        }        
        
        public synchronized void close() throws Exception {

            /*
             * shutdown all still running session threads ...
             */
            // interrupting all still running or pooled threads ...
            serverCore.this.theSessionThreadGroup.interrupt();
            
            /* waiting for all threads to finish */
            int threadCount  = serverCore.this.theSessionThreadGroup.activeCount();    
            Thread[] threadList = new Thread[threadCount];     
            threadCount = serverCore.this.theSessionThreadGroup.enumerate(threadList);
            
            try {
                for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                    ((Session)threadList[currentThreadIdx]).setStopped(true);
                }                
                
                for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                    // we need to use a timeout here because of missing interruptable session threads ...
                    if (threadList[currentThreadIdx].isAlive()) threadList[currentThreadIdx].join(500);
                }
            }
            catch (InterruptedException e) {
                serverCore.this.log.logWarning("Interruption while trying to shutdown all session threads.");  
            }
            
            this.isClosed = true;
            super.close();  
        }
        
    }
    
    public final class SessionFactory implements org.apache.commons.pool.PoolableObjectFactory {

        final ThreadGroup sessionThreadGroup;
        public SessionFactory(ThreadGroup theSessionThreadGroup) {
            super();  
            
            if (theSessionThreadGroup == null)
                throw new IllegalArgumentException("The threadgroup object must not be null.");
            
            this.sessionThreadGroup = theSessionThreadGroup;
        }
        
        /**
         * @see org.apache.commons.pool.PoolableObjectFactory#makeObject()
         */
        public Object makeObject() {
            return new Session(this.sessionThreadGroup);
        }
        
         /**
         * @see org.apache.commons.pool.PoolableObjectFactory#destroyObject(java.lang.Object)
         */
        public void destroyObject(Object obj) {
            if (obj instanceof Session) {
                Session theSession = (Session) obj;
                theSession.setStopped(true);
            }
        }
        
        /**
         * @see org.apache.commons.pool.PoolableObjectFactory#validateObject(java.lang.Object)
         */
        public boolean validateObject(Object obj) {
            if (obj instanceof Session) 
            {
                Session theSession = (Session) obj;
                if (!theSession.isAlive() || theSession.isInterrupted()) return false;
                if (theSession.isRunning()) return true;
                return false;
            }
            return true;
        }
        
        /**
         * @param obj 
         * 
         */
        public void activateObject(Object obj)  {
            //log.debug(" activateObject...");
        }

        /**
         * @param obj 
         * 
         */
        public void passivateObject(Object obj) { 
            //log.debug(" passivateObject..." + obj);
            if (obj instanceof Session)  {
                Session theSession = (Session) obj;
                
                // Clean up the result of the execution
                theSession.setResult(null);                 
            }
        }        
    }
    
    public final class Session extends Thread {
	
        // used as replacement for activeThreads, sleepingThreads
        // static ThreadGroup sessionThreadGroup = new ThreadGroup("sessionThreadGroup");
        
        // synchronization object needed for the threadpool implementation
        private Object syncObject;        
        
        private Object processingResult = null;
        
        private boolean running = false;
        private boolean stopped = false;
        private boolean done = false;        
        
        
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

        private final serverByteBuffer readLineBuffer = new serverByteBuffer(256);
    	
    	public Session(ThreadGroup theThreadGroup) {
            super(theThreadGroup,"Session");
    	}
        
        public void setStopped(boolean stopped) {
            this.stopped = stopped;            
        }

        public void execute(Socket controlSocket) {
            this.execute(controlSocket, null);
        }
        
        public synchronized void execute(Socket controlSocket, Object synObj) {           
            this.controlSocket = controlSocket;
            this.syncObject = synObj;
            this.done = false;
            
            if (!this.running)  {
               // this.setDaemon(true);
               this.start();
            }  else { 
               this.notifyAll();
            }          
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
    	    serverCore.this.log.logInfo(userAddress.getHostAddress() + "/" + this.identity + " " +
    		     "[" + serverCore.this.theSessionPool.getNumActive() + ", " + this.commandCounter +
    		     ((outgoing) ? "] > " : "] < ") +
    		     request);
    	}
    
    	public void writeLine(String messg) throws IOException {
    	    send(this.out, messg);
    	    log(true, messg);
    	}
    
    	public byte[] readLine() {
    	    return receive(in, this.readLineBuffer, timeout, commandMaxLength, false);
    	}
    
    
        /**
         * @return
         */
        public boolean isRunning() {
            return this.running;
        }
    
        /**
         * @param object
         */
        public void setResult(Object object) {
            this.processingResult  = object;
        }    
        
        /**
         * 
         */
        public void reset()  {
            this.done = true;
            this.syncObject = null;
            this.readLineBuffer.reset();
        }    
        
        /**
         * 
         * 
         * @see java.lang.Thread#run()
         */
        public void run()  {
            this.running = true;
            
            // The thread keeps running.
            while (!this.stopped && !Thread.interrupted()) { 
                 if (this.done)  { 
                     // We are waiting for a task now.
                    synchronized (this)  {
                       try  {
                          this.wait(); //Wait until we get a request to process.
                       } 
                       catch (InterruptedException e) {
                           this.stopped = true;
                           // log.error("", e);
                       }
                    }
                 } 
                 else 
                 { 
                    //There is a task....let us execute it.
                    try  {
                       execute();
                       if (this.syncObject != null) {
                          synchronized (this.syncObject) {
                              //Notify the completion.
                              this.syncObject.notifyAll();
                          }
                       }
                    }  catch (Exception e) {
                        // log.error("", e);
                    } 
                    finally  {
                        reset();
                        
                        if (!this.stopped && !this.isInterrupted()) {
                            try {
                                this.setName("Session_inPool");
                                serverCore.this.theSessionPool.returnObject(this);
                            }
                            catch (Exception e1) {
                                // e1.printStackTrace();
                                this.stopped = true;
                            }
                        }
                    }
                 }
              }
        }    
        
        private void execute() throws InterruptedException {
                   
            try {
                // setting the session startup time
                this.start = System.currentTimeMillis();  
                
                // settin the session identity
                this.identity = "-";
                
                // getting some client information
                this.userAddress = this.controlSocket.getInetAddress();
                this.setName("Session_" + this.userAddress.getHostAddress() + ":" + this.controlSocket.getPort());
                
                // TODO: check if we want to allow this socket to connect us
                
                // getting input and output stream for communication with client
                this.in = new PushbackInputStream(this.controlSocket.getInputStream());
                this.out = this.controlSocket.getOutputStream();
                
                
                // initiate the command class
                this.commandCounter = 0;
                if ((this.commandObj != null) && 
                    (this.commandObj.getClass().getName().equals(serverCore.this.handlerPrototype.getClass().getName()))) {
                    this.commandObj.reset();
                }
                else {
                    this.commandObj = (serverHandler) serverCore.this.handlerPrototype.clone();                    
                }
                this.commandObj.initSession(this);
    
    		    listen();
            } catch (Exception e) {
                if (e instanceof InterruptedException) throw (InterruptedException) e;
                System.err.println("ERROR: (internal) " + e);        
    	    } finally {
        		try {
                    this.out.flush();
                    // close everything
                    this.out.close();
                    this.in.close();
                    this.controlSocket.close();
        		} catch (IOException e) {
        		    System.err.println("ERROR: (internal) " + e);
        		}
    	    }
            
            //log.logDebug("* session " + handle + " completed. time = " + (System.currentTimeMillis() - handle));
            announceMoreExecTime(System.currentTimeMillis() - this.start);
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
        		while ((this.in != null) && ((requestBytes = readLine()) != null)) {
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
            			result = this.commandObj.getClass().getMethod(cmd, stringType).invoke(this.commandObj, stringParameter);
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
                } // end of while
    	    } catch (java.lang.ClassNotFoundException e) {
        		System.out.println("Internal Error: wrapper class not found: " + e.getMessage());
        		System.exit(0);
    	    } catch (java.io.IOException e) {
                // connection interruption: more or less normal
    	    }
    	}
	
    }

    public static byte[] receive(PushbackInputStream pbis, serverByteBuffer readLineBuffer, long timeout, int maxSize, boolean logerr) {

        // this is essentially a readln on a PushbackInputStream
        int bufferSize = 0;
        bufferSize = 10;

        // reuse an existing linebuffer or create a new one ...
        if (readLineBuffer == null) {
            readLineBuffer = new serverByteBuffer(256);
        } else {
            readLineBuffer.reset();
        }
        
      
      // TODO: we should remove this statements because calling the available function is very time consuming
      // we better should use nio sockets instead because they are interruptable ...
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
            
    	// byte[] buffer = new byte[bufferSize];
    	// byte[] bufferBkp;
    	bufferSize = 0;
    	int b = 0;
    	
    	try {
    	    while ((b = pbis.read()) > 31) {
//        		// we have a valid byte in b, add it to the buffer
//        		if (buffer.length == bufferSize) {
//        		    // the buffer is full, double its size
//        		    bufferBkp = buffer;
//        		    buffer = new byte[bufferSize * 2];
//        		    java.lang.System.arraycopy(bufferBkp, 0, buffer, 0, bufferSize);
//        		    bufferBkp = null;
//        		}
//                        //if (bufferSize > 10000) {System.out.println("***ERRORDEBUG***:" + new String(buffer));} // debug
//        		buffer[bufferSize++] = (byte) b; // error hier: ArrayIndexOutOfBoundsException: -2007395416 oder 0
                
                readLineBuffer.write(b);
                if (bufferSize++ > maxSize) break;                
            }
            
    	    // we have catched a possible line end
    	    if (b == cr) {
        		// maybe a lf follows, read it:
        		if ((b = pbis.read()) != lf) if (b >= 0) pbis.unread(b); // we push back the byte
    	    }
    	    
    	    // finally shrink buffer
//    	    bufferBkp = buffer;
//    	    buffer = new byte[bufferSize];
//    	    java.lang.System.arraycopy(bufferBkp, 0, buffer, 0, bufferSize);
//    	    bufferBkp = null;
    	    
    	    // return only the byte[]
    	    // return buffer;
            return readLineBuffer.toByteArray();
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
    
    protected void finalize() throws Throwable {
        if (!this.theSessionPool.isClosed) this.theSessionPool.close();
        super.finalize();
    }

}

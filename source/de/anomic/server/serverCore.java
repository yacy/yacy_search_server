// serverCore.java 
// -------------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2002-2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.channels.ClosedByInterruptException;
import java.util.Enumeration;
import java.util.Hashtable;

/*
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
*/

import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool.Config;

import de.anomic.http.httpc;
import de.anomic.icap.icapd;
import de.anomic.server.logging.serverLog;
import de.anomic.urlRedirector.urlRedirectord;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.plasma.plasmaSwitchboard;

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
    private String port;                      // the listening port
    public boolean forceRestart = false;     // specifies if the server should try to do a restart
    
    public static boolean portForwardingEnabled = false;
    public static serverPortForwarding portForwarding = null;
    
    private ServerSocket socket;           // listener
    serverLog log;                         // log object
    private int timeout;                   // connection time-out of the socket
    
    private int thresholdSleep = 30000;    // after that time a thread is considered as beeing sleeping (30 seconds)
    serverHandler handlerPrototype;        // the command class (a serverHandler) 

    private serverSwitch switchboard;      // the command class switchboard
    Hashtable denyHost;
    int commandMaxLength;
    
    /**
     * The session-object pool
     */
    SessionPool theSessionPool;
    final ThreadGroup theSessionThreadGroup = new ThreadGroup("sessionThreadGroup");
    private Config cralwerPoolConfig = null;

    public ThreadGroup getSessionThreadGroup() {
        return this.theSessionThreadGroup;
    }
    
    /*
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
    */
    
    public static String clientAddress(Socket s) {
        InetAddress uAddr = s.getInetAddress();
        if (uAddr.isAnyLocalAddress()) return "localhost";
        String cIP = uAddr.getHostAddress();
        if (cIP.equals("0:0:0:0:0:0:0:1")) cIP = "localhost";
        if (cIP.equals("127.0.0.1")) cIP = "localhost";
        return cIP;
    }

    // class initializer
    public serverCore( 
            int timeout,
            boolean blockAttack,
            serverHandler handlerPrototype, 
            serverSwitch switchboard,
            int commandMaxLength
    ) {
        this.timeout = timeout;
        
        this.commandMaxLength = commandMaxLength;
        this.denyHost = (blockAttack) ? new Hashtable() : null;
        this.handlerPrototype = handlerPrototype;
        this.switchboard = switchboard;
        
        // initialize logger
        this.log = new serverLog("SERVER");

        // init servercore
        init();
    }
    
    public synchronized void init() {
        this.log.logInfo("Initializing serverCore ...");
        
        // read some config values
        this.port = this.switchboard.getConfig("port", "8080").trim();
        
        // Open a new server-socket channel
        try {
            this.initPort(this.port);
        } catch (Exception e) {
            String errorMsg = "FATAL ERROR: " + e.getMessage() + " - probably root access rights needed. check port number";
            this.log.logSevere(errorMsg);
            System.out.println(errorMsg);             
            System.exit(0);
        }

        // init port forwarding            
        try {
            this.initPortForwarding();
        } catch (Exception e) {
            this.log.logSevere("Unable to initialize server port forwarding.",e);
            this.switchboard.setConfig("portForwardingEnabled","false");
        } catch (Error e) {
            this.log.logSevere("Unable to initialize server port forwarding.",e);
            this.switchboard.setConfig("portForwardingEnabled","false");
        }
        
        // init session pool
        initSessionPool();        
    }
    
    public void initSessionPool() {
        this.log.logInfo("Initializing session pool ...");
        
        // implementation of session thread pool
        this.cralwerPoolConfig = new GenericObjectPool.Config();
        
        // The maximum number of active connections that can be allocated from pool at the same time,
        // 0 for no limit
        this.cralwerPoolConfig.maxActive = Integer.valueOf(switchboard.getConfig("httpdMaxActiveSessions","150")).intValue();
        
        // The maximum number of idle connections connections in the pool
        // 0 = no limit.        
        this.cralwerPoolConfig.maxIdle = Integer.valueOf(switchboard.getConfig("httpdMaxIdleSessions","75")).intValue();
        this.cralwerPoolConfig.minIdle = Integer.valueOf(switchboard.getConfig("httpdMinIdleSessions","5")).intValue();    
        
        // block undefinitely 
        this.cralwerPoolConfig.maxWait = timeout; 
        
        // Action to take in case of an exhausted DBCP statement pool
        // 0 = fail, 1 = block, 2= grow        
        this.cralwerPoolConfig.whenExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_BLOCK; 
        this.cralwerPoolConfig.minEvictableIdleTimeMillis = this.thresholdSleep; 
        this.cralwerPoolConfig.testOnReturn = true;
        
        this.theSessionPool = new SessionPool(new SessionFactory(this.theSessionThreadGroup),this.cralwerPoolConfig);        
    }
    
    public void initPort(String thePort) throws IOException {
        this.log.logInfo("Trying to bind server to port " + thePort);
        
        // Binds the ServerSocket to a specific address 
        InetSocketAddress bindAddress = null;
        this.socket = new ServerSocket();
        this.socket.bind(bindAddress = generateSocketAddress(thePort));
        
        // updating the port information
        yacyCore.seedDB.mySeed.put(yacySeed.PORT,Integer.toString(bindAddress.getPort()));    
    }
    
    public InetSocketAddress generateSocketAddress(String thePort) throws SocketException {
        
        // parsing the port configuration
        String bindIP = null;
        int bindPort;
        
        int pos = -1;
        if ((pos = thePort.indexOf(":"))!= -1) {
            bindIP = thePort.substring(0,pos).trim();
            thePort = thePort.substring(pos+1); 
            
            if (bindIP.startsWith("#")) {
                String interfaceName = bindIP.substring(1);
                String hostName = null;
                this.log.logFine("Trying to determine IP address of interface '" + interfaceName + "'.");                    

                Enumeration interfaces = NetworkInterface.getNetworkInterfaces();
                if (interfaces != null) {
                    while (interfaces.hasMoreElements()) {
                        NetworkInterface interf = (NetworkInterface) interfaces.nextElement();
                        if (interf.getName().equalsIgnoreCase(interfaceName)) {
                            Enumeration addresses = interf.getInetAddresses();
                            if (addresses != null) {
                                while (addresses.hasMoreElements()) {
                                    InetAddress address = (InetAddress)addresses.nextElement();
                                    if (address instanceof Inet4Address) {
                                        hostName = address.getHostAddress();
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                if (hostName == null) {
                    this.log.logWarning("Unable to find interface with name '" + interfaceName + "'. Binding server to all interfaces");
                    bindIP = null;
                } else {
                    this.log.logInfo("Binding server to interface '" + interfaceName + "' with IP '" + hostName + "'.");
                    bindIP = hostName;
                }
            } 
        }
        bindPort = Integer.parseInt(thePort);    
        
        return (bindIP == null) 
        ? new InetSocketAddress(bindPort)
        : new InetSocketAddress(bindIP, bindPort);
    }
    
    public void initPortForwarding() throws Exception {
        // doing the port forwarding stuff
        if (this.switchboard.getConfig("portForwardingEnabled","false").equalsIgnoreCase("true")) {
            this.log.logInfo("Initializing port forwarding ...");
            try {
                String localHost = this.socket.getInetAddress().getHostName();
                Integer localPort = new Integer(this.socket.getLocalPort());
                
                this.log.logInfo("Trying to load port forwarding class");
                
                // loading port forwarding class
                Class forwarderClass = Class.forName("de.anomic.server.serverPortForwardingSch");
                serverCore.portForwarding = (serverPortForwarding) forwarderClass.newInstance();                
                
                // initializing port forwarding
                serverCore.portForwarding.init(
                        this.switchboard,
                        localHost,
                        localPort.intValue());
                
                // connection to port forwarding host
                serverCore.portForwarding.connect();
                
                serverCore.portForwardingEnabled = true;
                yacyCore.seedDB.mySeed.put(yacySeed.IP,publicIP());
                yacyCore.seedDB.mySeed.put(yacySeed.PORT,Integer.toString(serverCore.portForwarding.getPort()));                               
            } catch (Exception e) {
                serverCore.portForwardingEnabled = false;
                this.switchboard.setConfig("portForwardingEnabled", "false");
                throw e;
            } catch (Error e) {
                serverCore.portForwardingEnabled = false;
                this.switchboard.setConfig("portForwardingEnabled", "false");
                throw e;                
            }
        }
    }

    public GenericObjectPool.Config getPoolConfig() {
        return this.cralwerPoolConfig ;
    }
    
    public void setPoolConfig(GenericObjectPool.Config newConfig) {
        this.theSessionPool.setConfig(newConfig);
    }    
    
    public static boolean isNotLocal(URL url) {
        return isNotLocal(url.getHost());
    }
    
    static boolean isNotLocal(String ip) {
	if ((ip.equals("localhost")) ||
	    (ip.startsWith("127")) ||
	    (ip.startsWith("192.168")) ||
	    (ip.startsWith("10."))
	    ) return false;
	return true;
    }
    
    public static String publicIP() {
        try {
            
            // if a static IP was configured, we have to return it here ...
            plasmaSwitchboard sb = plasmaSwitchboard.getSwitchboard();
            if(sb != null){
                String staticIP=sb.getConfig("staticIP", "");
                if( (!staticIP.equals("")) && sb.getConfig("yacyDebugMode", "false").equals("true") ){
                    return staticIP;
                }
            }
            
            // If port forwarding was enabled we need to return the remote IP Address
            if ((serverCore.portForwardingEnabled)&&(serverCore.portForwarding != null)) {
                //does not return serverCore.portForwarding.getHost(), because hostnames are not valid, except in DebugMode
                return InetAddress.getByName(serverCore.portForwarding.getHost()).getHostAddress();
            }
            
            // otherwise we return the real IP address of this host
            InetAddress pLIP = publicLocalIP();
            if (pLIP != null) return pLIP.getHostAddress();
            return null;
        } catch (java.net.UnknownHostException e) {
            System.err.println("ERROR: (internal) " + e.getMessage());
            return null;
        }
    }
    
    public static InetAddress publicLocalIP() {            
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
        this.log.logConfig("* server started on " + publicLocalIP() + ":" + this.port);
    }
    
    // class body
    public boolean job() throws Exception {
        try {
            // prepare for new connection
            // idleThreadCheck();
            this.switchboard.handleBusyState(this.theSessionPool.getNumActive() /*activeThreads.size() */);
            
            this.log.logFinest(
                    "* waiting for connections, " + this.theSessionPool.getNumActive() + " sessions running, " +
                    this.theSessionPool.getNumIdle() + " sleeping");
            
            // wait for new connection
            announceThreadBlockApply();
            Socket controlSocket = this.socket.accept();
            announceThreadBlockRelease();
            
            String cIP = clientAddress(controlSocket);
            //System.out.println("server bfHosts=" + bfHost.toString());
            if (bfHost.get(cIP) != null) {
                Integer attempts = (Integer) bfHost.get(cIP);
                if (attempts == null) attempts = new Integer(1); else attempts = new Integer(attempts.intValue() + 1);
                bfHost.put(cIP, attempts);
                this.log.logWarning("SLOWING DOWN ACCESS FOR BRUTE-FORCE PREVENTION FROM " + cIP + ", ATTEMPT " + attempts.intValue());
                // add a delay to make brute-force harder
                announceThreadBlockApply();
                try {Thread.sleep(attempts.intValue() * 2000);} catch (InterruptedException e) {}
                announceThreadBlockRelease();
                if ((attempts.intValue() >= 10) && (this.denyHost != null)) {
                    this.denyHost.put(cIP, "deny");
                }
            }
            
            if ((this.denyHost == null) || (this.denyHost.get(cIP) == null)) {
                // setting the timeout properly
                controlSocket.setSoTimeout(this.timeout);
                
                // getting a free session thread from the pool
                Session connection = (Session) this.theSessionPool.borrowObject();
                
                // processing the new request
                connection.execute(controlSocket,this.timeout);
            } else {
                this.log.logWarning("ACCESS FROM " + cIP + " DENIED");
            }
            
            return true;
        } catch (SocketException e) {
            if (this.forceRestart) {
                // reinitialize serverCore
                init(); 
                this.forceRestart = false;
                return true;
            }
            throw e;
        }
    }

    public synchronized void close() {
        // consuming the isInterrupted Flag. Otherwise we could not properly close the session pool
        Thread.interrupted();
        
        // closing the port forwarding channel
        if ((portForwardingEnabled) && (portForwarding != null) ) {
            try {
                this.log.logInfo("Shutdown port forwarding ...");
                portForwarding.disconnect();
                portForwardingEnabled = false;
                portForwarding = null;
            } catch (Exception e) {
                this.log.logWarning("Unable to shutdown the port forwarding channel.");
            }
        }
        
        // closing the serverchannel and socket
        try {
            this.log.logInfo("Closing server socket ...");
            this.socket.close();
        } catch (Exception e) {
            this.log.logWarning("Unable to close the server socket."); 
        }

        // closing the session pool
        try {
            this.log.logInfo("Closing server session pool ...");
            this.theSessionPool.close();
        } catch (Exception e) {
            this.log.logWarning("Unable to close the session pool.");
        }        
        
        this.log.logConfig("* terminated");
    }
    
    public int getJobCount() {
        return this.theSessionPool.getNumActive();
    }
    
    public int getActiveSessionCount() {
        return this.theSessionPool.getNumActive();
    }    
    
    public int getIdleSessionCount() {
        return this.theSessionPool.getNumIdle();
    }
    
    public int getMaxSessionCount() {
        return this.theSessionPool.getMaxActive();
    }
    
    // idle sensor: the thread is idle if there are no sessions running
    public boolean idle() {
        // idleThreadCheck();
        return (this.theSessionPool.getNumActive() == 0);
    }
    
    public final class SessionPool extends GenericObjectPool {
        public boolean isClosed = false;
        
        /**
         * First constructor.
         * @param objFactory
         */        
        public SessionPool(SessionFactory objFactory) {
            super(objFactory);
            this.setMaxIdle(50); // Maximum idle threads.
            this.setMaxActive(100); // Maximum active threads.
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
            if (obj instanceof Session) {
                super.returnObject(obj);
            } else {
                serverLog.logSevere("SESSION-POOL","Object of wront type '" + obj.getClass().getName() +
                                    "'returned to pool.");
            }
        }        
        
        public synchronized void close() throws Exception {

            /*
             * shutdown all still running session threads ...
             */
            this.isClosed = true;
            
            /* waiting for all threads to finish */
            int threadCount  = serverCore.this.theSessionThreadGroup.activeCount();    
            Thread[] threadList = new Thread[threadCount];     
            threadCount = serverCore.this.theSessionThreadGroup.enumerate(threadList);
            
            try {
                // trying to gracefull stop all still running sessions ...
                serverCore.this.log.logInfo("Signaling shutdown to " + threadCount + " remaining session threads ...");
                for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                    Thread currentThread = threadList[currentThreadIdx];
                    if (currentThread.isAlive()) {
                        if (currentThread instanceof Session) {
                            ((Session)currentThread).setStopped(true);
                        }
                    }
                }          

                // waiting a frew ms for the session objects to continue processing
                try { Thread.sleep(500); } catch (InterruptedException ex) {}                
                
                // interrupting all still running or pooled threads ...
                serverCore.this.log.logInfo("Sending interruption signal to " + serverCore.this.theSessionThreadGroup.activeCount() + " remaining session threads ...");
                serverCore.this.theSessionThreadGroup.interrupt();                
                
                // if there are some sessions that are blocking in IO, we simply close the socket
                serverCore.this.log.logFine("Trying to abort " + serverCore.this.theSessionThreadGroup.activeCount() + " remaining session threads ...");
                for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                    Thread currentThread = threadList[currentThreadIdx];
                    if (currentThread.isAlive()) {
                        if (currentThread instanceof Session) {
                            serverCore.this.log.logInfo("Trying to shutdown session thread '" + currentThread.getName() + "' [" + currentThreadIdx + "].");
                            ((Session)currentThread).close();
                        }
                    }
                }                
                
                // we need to use a timeout here because of missing interruptable session threads ...
                serverCore.this.log.logFine("Waiting for " + serverCore.this.theSessionThreadGroup.activeCount() + " remaining session threads to finish shutdown ...");
                for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                    Thread currentThread = threadList[currentThreadIdx];
                    if (currentThread.isAlive()) {
                        if (currentThread instanceof Session) {
                            serverCore.this.log.logFine("Waiting for session thread '" + currentThread.getName() + "' [" + currentThreadIdx + "] to finish shutdown.");
                            try { currentThread.join(500); } catch (InterruptedException ex) {}
                        }
                    }
                }
                
                serverCore.this.log.logInfo("Shutdown of remaining session threads finish.");
            } catch (Exception e) {
                serverCore.this.log.logSevere("Unexpected error while trying to shutdown all remaining session threads.",e);
            }
            
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
            Session newSession = new Session(this.sessionThreadGroup);
            newSession.setPriority(Thread.MAX_PRIORITY);
            return newSession;
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
            if (obj instanceof Session) {
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
//            if (obj instanceof Session)  {
//                Session theSession = (Session) obj;              
//            }
        }        
    }
    
    public final class Session extends Thread {
	
        // used as replacement for activeThreads, sleepingThreads
        // static ThreadGroup sessionThreadGroup = new ThreadGroup("sessionThreadGroup");
        
        // synchronization object needed for the threadpool implementation
        private Object syncObject;        
        
        private boolean running = false;
        private boolean stopped = false;
        private boolean done = false;        
        
        
        private long start;                // startup time
        private serverHandler commandObj;
        private Hashtable commandObjMethodCache = new Hashtable(5);
        
    	private String request;            // current command line
    	private int commandCounter;        // for logging: number of commands in this session
    	private String identity;           // a string that identifies the client (i.e. ftp: account name)
    	//private boolean promiscuous;       // if true, no lines are read and streams are only passed
        
    	public  Socket controlSocket;      // dialog socket
    	public  InetAddress userAddress;   // the address of the client
        public  int userPort;              // the ip port used by the client 
    	public  PushbackInputStream in;    // on control input stream
    	public  OutputStream out;          // on control output stream, autoflush
        private int socketTimeout;

        private final serverByteBuffer readLineBuffer = new serverByteBuffer(256);
        
    	
    	public Session(ThreadGroup theThreadGroup) {
            super(theThreadGroup,"Session");
    	}
        
        public int getCommandCount() {
            return this.commandCounter;
        }
        
        public String getCommandLine() {
            return this.request;
        }
        
        public serverHandler getCommandObj() {
            return this.commandObj;
        }
        
        public InetAddress getUserAddress() {
            return this.userAddress;
        }
        
        public int getUserPort() {
            return this.userPort;
        }
        
        public void setStopped(boolean stopped) {
            this.stopped = stopped;            
        }
        
        public boolean isStopped() {
            return this.stopped;
        }
        
        public void close() {
            if (this.isAlive()) {
                try {
                    // trying to close all still open httpc-Sockets first                    
                    int closedSockets = httpc.closeOpenSockets(this);
                    if (closedSockets > 0) {
                        serverCore.this.log.logInfo(closedSockets + " HTTP-client sockets of thread '" + this.getName() + "' closed.");
                    }                    
                    
                    // closing the socket to the client
                    if ((this.controlSocket != null)&&(this.controlSocket.isConnected())) {
                        this.controlSocket.close();
                        serverCore.this.log.logInfo("Closing main socket of thread '" + this.getName() + "'");
                    }
                } catch (Exception e) {}
            }            
        }

        public void execute(Socket controlSocket, int socketTimeout) {
            this.execute(controlSocket, socketTimeout, null);
        }
        
        public synchronized void execute(Socket controlSocket, int socketTimeout, Object synObj) {
            this.socketTimeout = socketTimeout;
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
            return System.currentTimeMillis() - this.start;
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
    	    serverCore.this.log.logFine(this.userAddress.getHostAddress() + "/" + this.identity + " " +
    		     "[" + ((serverCore.this.theSessionPool.isClosed)? -1 : serverCore.this.theSessionPool.getNumActive()) + ", " + this.commandCounter +
    		     ((outgoing) ? "] > " : "] < ") +
    		     request);
    	}
    
    	public void writeLine(String messg) throws IOException {
    	    send(this.out, messg);
    	    log(true, messg);
    	}
    
    	public byte[] readLine() {
    	    return receive(this.in, this.readLineBuffer, serverCore.this.commandMaxLength, false);
    	}
    
        /**
         * reads a line from the input socket
         * this function is provided by the server through a passed method on initialization
         * @return the next requestline as string
         */
        public String readLineAsString() {
            byte[] l = readLine();
            return (l == null) ? null: new String(l);
        }
    
        /**
         * @return
         */
        public boolean isRunning() {
            return this.running;
        }
    
        /**
         * 
         */
        public void reset()  {
            this.done = true;
            this.syncObject = null;
            this.readLineBuffer.reset();
            if (this.commandObj !=null) this.commandObj.reset();
            this.userAddress = null;
            this.userPort = 0;
            this.controlSocket = null;
            this.request = null;
        }    
        
        private void shortReset() {
            this.request = null;
        }
        
        /**
         * 
         * 
         * @see java.lang.Thread#run()
         */
        public void run()  {
            this.running = true;
            
            // The thread keeps running.
            while (!this.stopped && !this.isInterrupted()) {
                if (this.done)  {
                    // We are waiting for a task now.
                    synchronized (this)  {
                        try  {
                            this.wait(); //Wait until we get a request to process.
                        } catch (InterruptedException e) {
                            this.stopped = true;
                            // log.error("", e);
                        }
                    }
                } else {
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
                    } finally  {
                        reset();
                        
                        if (!this.stopped && !this.isInterrupted() && !serverCore.this.theSessionPool.isClosed) {
                            try {
                                this.setName("Session_inPool");
                                serverCore.this.theSessionPool.returnObject(this);
                            } catch (Exception e1) {
                                // e1.printStackTrace();
                                this.stopped = true;
                            }
                        } else if (!serverCore.this.theSessionPool.isClosed) {
                            try {
                                serverCore.this.theSessionPool.invalidateObject(this);
                            } catch (Exception e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
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
                this.userPort = this.controlSocket.getPort();
                this.setName("Session_" + this.userAddress.getHostAddress() + ":" + this.controlSocket.getPort());
                
                // TODO: check if we want to allow this socket to connect us
                
                // getting input and output stream for communication with client
                this.in = new PushbackInputStream(this.controlSocket.getInputStream());
                this.out = this.controlSocket.getOutputStream();

                // reseting the command counter
                this.commandCounter = 0;
                
                // listen for commands
    		    listen();
            } catch (Exception e) {
                if (e instanceof InterruptedException) throw (InterruptedException) e;
                System.err.println("ERROR: (internal) " + e);        
    	    } finally {
        		try {
                    this.out.flush();               
                    
                    this.controlSocket.shutdownInput();
                    this.controlSocket.shutdownOutput();                    
                    
                    this.in.close();                    
                    this.out.close();                     
                    
                    // close everything                    
                    this.controlSocket.close();   this.controlSocket = null;
                                      
        		} catch (IOException e) {}
    	    }
            
            //log.logDebug("* session " + handle + " completed. time = " + (System.currentTimeMillis() - handle));
    	}
    	
        private void listen() {
            try {
                // set up some reflection
                Class[] stringType    = {"".getClass()};
                //Class[] exceptionType = {Class.forName("java.lang.Throwable")};
                
                Object result;
//                // send greeting
//                Object result = commandObj.greeting();
//                if (result != null) {
//                    if ((result instanceof String) && (((String) result).length() > 0)) writeLine((String) result);
//                }
                
                // start dialog
                byte[] requestBytes = null;
                boolean terminate = false;
                String reqCmd;
                String reqProtocol = "HTTP";
                Object[] stringParameter = new String[1];
                while ((this.in != null) && ((requestBytes = readLine()) != null)) {                    
                    this.setName("Session_" + this.userAddress.getHostAddress() + 
                                 ":" + this.controlSocket.getPort() + 
                                 "#" + this.commandCounter);
                    
                    this.request = new String(requestBytes);
                    //log.logDebug("* session " + handle + " received command '" + request + "'. time = " + (System.currentTimeMillis() - handle));
                    log(false, this.request);
                    try {                        
                        // if we can not determine the proper command string we try to call function emptyRequest
                        // of the commandObject
                        if (this.request.trim().length() == 0) this.request = "EMPTY";
                        
                        // getting the rest of the request parameters
                        int pos = this.request.indexOf(' ');
                        if (pos < 0) {
                            reqCmd = this.request.trim().toUpperCase();
                            stringParameter[0] = "";
                        } else {
                            reqCmd = this.request.substring(0, pos).trim().toUpperCase();
                            stringParameter[0] = this.request.substring(pos).trim();
                        }
                        
                        // now we need to initialize the session
                        if (this.commandCounter == 0) {
                            // first we need to determine the proper protocol handler
                            if (this.request.indexOf("ICAP") >= 0)          reqProtocol = "ICAP";
                            else if (this.request.startsWith("REDIRECTOR")) reqProtocol = "REDIRECTOR";
                            else                                            reqProtocol = "HTTP";                            
                            
                            // next we need to get the proper protocol handler
                            if (reqProtocol.equals("ICAP")) {
                                this.commandObj = new icapd();
                            } else if (reqProtocol.equals("REDIRECTOR")) {
                                this.commandObj = new urlRedirectord();
                            } else {
//                                if ((this.commandObj != null) && 
//                                        (this.commandObj.getClass().getName().equals(serverCore.this.handlerPrototype.getClass().getName()))) {
//                                        this.commandObj.reset();
//                                    } else {
//                                        this.commandObj = (serverHandler) serverCore.this.handlerPrototype.clone();
//                                    }
                                
                                this.commandObj = (serverHandler) serverCore.this.handlerPrototype.clone();
                            }
                            
                            // initializing the session
                            this.commandObj.initSession(this); 
                        }
                        
                        // count the amount of requests that were processed by this session until yet
                        this.commandCounter++;
                        
                        // setting the socket timeout for reading of the request content
                        this.controlSocket.setSoTimeout(this.socketTimeout);
                        
                        // exec command and return value
                        Object commandMethod = this.commandObjMethodCache.get(reqProtocol + "_" + reqCmd);
                        if (commandMethod == null) {
                            try {
                                commandMethod = this.commandObj.getClass().getMethod(reqCmd, stringType);
                                this.commandObjMethodCache.put(reqProtocol + "_" + reqCmd,commandMethod);
                            } catch (NoSuchMethodException noMethod) {
                                commandMethod = this.commandObj.getClass().getMethod("UNKNOWN", stringType);
                                stringParameter[0] = this.request.trim();
                            }
                        }
                        //long commandStart = System.currentTimeMillis();
                        result = ((Method)commandMethod).invoke(this.commandObj, stringParameter);
                        //announceMoreExecTime(commandStart - System.currentTimeMillis()); // shall be negative!
                        //log.logDebug("* session " + handle + " completed command '" + request + "'. time = " + (System.currentTimeMillis() - handle));
                        this.out.flush();
                        if (result == null) {
                                    /*
                                    log(2, true, "(NULL RETURNED/STREAM PASSED)");
                                     */
                        } else if (result instanceof Boolean) {
                            if (((Boolean) result).equals(TERMINATE_CONNECTION)) break;
                            
                            /* 
                             * setting timeout to a very high level. 
                             * this is needed because of persistent connection
                             * support.
                             */
                            if (!this.controlSocket.isClosed()) this.controlSocket.setSoTimeout(30*60*1000);
                        } else if (result instanceof String) {
                            if (((String) result).startsWith("!")) {
                                result = ((String) result).substring(1);
                                terminate = true;
                            }
                            writeLine((String) result);
                        } else if (result instanceof InputStream) {
                            String tmp = send(this.out, (InputStream) result);
                            if ((tmp.length() > 4) && (tmp.toUpperCase().startsWith("PASS"))) {
                                log(true, "PASS ********");
                            } else {
                                log(true, tmp);
                            }
                            tmp = null;
                        }
                        if (terminate) break;
                        
                    } catch (InvocationTargetException ite) {                        
                        System.out.println("ERROR A " + this.userAddress.getHostAddress());
                        // we extract a target exception and let the thread survive
                        writeLine(this.commandObj.error(ite.getTargetException()));
                    } catch (NoSuchMethodException nsme) {
                        System.out.println("ERROR B " + this.userAddress.getHostAddress());
                        if (isNotLocal(this.userAddress.getHostAddress().toString())) {
                            if (serverCore.this.denyHost != null) {
                                serverCore.this.denyHost.put((""+this.userAddress.getHostAddress()), "deny"); // block client: hacker attempt
                            }
                        }
                        break;
                        // the client requested a command that does not exist
                        //Object[] errorParameter = { nsme };
                        //writeLine((String) error.invoke(this.cmdObject, errorParameter));
                    } catch (IllegalAccessException iae) {
                        System.out.println("ERROR C " + this.userAddress.getHostAddress());
                        // wrong parameters: this an only be an internal problem
                        writeLine(this.commandObj.error(iae));
                    } catch (java.lang.ClassCastException e) {
                        System.out.println("ERROR D " + this.userAddress.getHostAddress());
                        // ??
                        writeLine(this.commandObj.error(e));
                    } catch (Exception e) {
                        System.out.println("ERROR E " + this.userAddress.getHostAddress());
                        // whatever happens: the thread has to survive!
                        writeLine("UNKNOWN REASON:" + this.commandObj.error(e));
                    }      
                    
                    shortReset();
                    
                } // end of while
            } /* catch (java.lang.ClassNotFoundException e) {
                System.out.println("Internal error: Wrapper class not found: " + e.getMessage());
                System.exit(0);
            } */ catch (java.io.IOException e) {
                // connection interruption: more or less normal
            }
            //announceMoreExecTime(System.currentTimeMillis() - this.start);
        }
        
    }

    public static byte[] receive(PushbackInputStream pbis, serverByteBuffer readLineBuffer, int maxSize, boolean logerr) {

        // reuse an existing linebuffer
        readLineBuffer.reset();
        
        int bufferSize = 0, b = 0;    	
    	try {
    	    while ((b = pbis.read()) > 31) {
                readLineBuffer.write(b);
                if (bufferSize++ > maxSize) break;                
            }
            
    	    // we have catched a possible line end
    	    if (b == cr) {
        		// maybe a lf follows, read it:
        		if ((b = pbis.read()) != lf) if (b >= 0) pbis.unread(b); // we push back the byte
    	    }
    	    
            if ((readLineBuffer.length()==0)&&(b == -1)) return null;
            return readLineBuffer.toByteArray();
        } catch (ClosedByInterruptException e) {
            if (logerr) serverLog.logSevere("SERVER", "receive interrupted - timeout");
            return null;            
        } catch (IOException e) {
            if (logerr) serverLog.logSevere("SERVER", "receive interrupted - exception 2 = " + e.getMessage());
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
    
    public static final void checkInterruption() throws InterruptedException {
        Thread currentThread = Thread.currentThread();
        if (currentThread.isInterrupted()) throw new InterruptedException();  
        if ((currentThread instanceof serverCore.Session) && ((serverCore.Session)currentThread).isStopped()) throw new InterruptedException();
    }
    
    public void reconnect() {
        Thread restart = new Restarter();
        restart.start();
    }
    
    // restarting the serverCore
    public class Restarter extends Thread { 
        public serverCore theServerCore = null;
        public void run() {
            // waiting for a while
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            // signaling restart
            serverCore.this.forceRestart = true;
            
            // closing socket to notify the thread
            serverCore.this.close();
        }
    }
}

// serverCore.java 
// -------------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.server;

// standard server
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import java.nio.channels.ClosedByInterruptException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.Domains;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.ByteBuffer;
import net.yacy.kelondro.workflow.AbstractBusyThread;
import net.yacy.kelondro.workflow.BusyThread;

import de.anomic.tools.PKCS12Tool;

public final class serverCore extends AbstractBusyThread implements BusyThread {

    // special ASCII codes used for protocol handling
    /**
     * Horizontal Tab
     */
    public static final byte HT = 9;
    /**
     * Line Feed
     */
    public static final byte LF = 10;
    /**
     * Carriage Return
     */
    public static final byte CR = 13;
    /**
     * Space
     */
    public static final byte SP = 32;
    /**
     * Line End of HTTP/ICAP headers
     */
    public  static final byte[] CRLF = {CR, LF};
    public  static final String CRLF_STRING = UTF8.String(CRLF);
    public  static final String LF_STRING = UTF8.String(new byte[]{LF});
    public  static final Class<?>[] sessionCallType = {String.class, Session.class}; //  set up some reflection
    public  static final long startupTime = System.currentTimeMillis();
    private static final ThreadGroup sessionThreadGroup = new ThreadGroup("sessionThreadGroup");
    private static final Map<String, Method> commandObjMethodCache = new ConcurrentHashMap<String, Method>(5);
    
    /**
     * will be increased with each session and is used to return a hash code
     */
    static int sessionCounter = 0;
    
    // static variables
    private static final long    keepAliveTimeout     = 60000; // time that a connection is kept alive if requested with a keepAlive statement
    public  static final Boolean TERMINATE_CONNECTION = Boolean.FALSE;
    public  static final Boolean RESUME_CONNECTION    = Boolean.TRUE;
    
    /**
     * for brute-force prevention
     */
    public static final Map<String, Integer> bfHost = new ConcurrentHashMap<String, Integer>();
    
    // class variables
    /**
     * the port, which is visible from outside (in most cases bind-port)
     */
    private String extendedPort;
    /**
     * if set, yacy will bind to this port, but set extendedPort in the seed
     */
    private String bindPort;
    /**
     * specifies if the server should try to do a restart
     */
    boolean forceRestart = false;
    
    public static boolean useStaticIP = false;
    protected Log log;
    private SSLSocketFactory sslSocketFactory = null;
    private ServerSocket socket;           // listener
    private final int timeout;             // connection time-out of the socket
    serverHandler handlerPrototype;        // the command class (a serverHandler) 

    private final serverSwitch switchboard;   // the command class switchboard
    private Map<String, String> denyHost;
    int commandMaxLength;
    private int maxBusySessions;
    private long lastAutoTermination;
    
    public final void terminateOldSessions(long minage) {
        if (System.currentTimeMillis() - lastAutoTermination < 3000) return;
        this.lastAutoTermination = System.currentTimeMillis();
        //if (serverCore.getJobCount() < maxBusySessions - 10) return; // don't panic
        
        for (Session s: getJobList()) {
            if (!s.isAlive()) continue;
            if (s.getTime() < minage) continue;
            
            // stop thread
            this.log.logInfo("check for " + s.getName() + ": " + s.getTime() + " ms alive, stopping thread");
            
            // trying to stop session
            s.setStopped(true);
            try { Thread.sleep(10); } catch (final InterruptedException ex) {}
            
            // trying to interrupt session
            synchronized (s) {
                s.notify();
            }
            s.interrupt();
            
            // trying to close socket
            if (s.isAlive()) {
                s.close();
            }
        }
    }
    
    public static String clientAddress(final Socket s) {
        final InetAddress uAddr = s.getInetAddress();
        if (uAddr.isAnyLocalAddress()) return "localhost";
        String cIP = uAddr.getHostAddress();
        if (Domains.isLocal(cIP)) cIP = "localhost";
        return cIP;
    }

    // class initializer
    public serverCore( 
            final int timeout,
            final boolean blockAttack,
            final serverHandler handlerPrototype, 
            final serverSwitch switchboard,
            final int commandMaxLength
    ) {
        super(Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE);
        this.timeout = timeout;
        
        this.commandMaxLength = commandMaxLength;
        this.denyHost = (blockAttack) ? new ConcurrentHashMap<String, String>() : null;
        this.handlerPrototype = handlerPrototype;
        this.switchboard = switchboard;
        
        // initialize logger
        this.log = new Log("SERVER");

        // init the ssl socket factory
        this.sslSocketFactory = initSSLFactory();

        // init session parameter
        this.maxBusySessions = Math.max(1, Integer.parseInt(switchboard.getConfig("httpdMaxBusySessions","100")));
        
        this.lastAutoTermination = System.currentTimeMillis();
        
        // init servercore
        init();
    }
    
    public boolean withSSL() {
        return this.sslSocketFactory != null;
    }
    
    public synchronized void init() {
        this.log.logInfo("Initializing serverCore ...");
        
        // read some config values
        this.extendedPort = this.switchboard.getConfig("port", "8090").trim();
        this.bindPort = this.switchboard.getConfig("bindPort", "").trim();
        
        // Open a new server-socket channel
        try {
            // bind the ServerSocket to a specific address 
            // InetSocketAddress bindAddress = null;
            this.socket = new ServerSocket();
            if (bindPort == null || bindPort.equals("")) {
                this.log.logInfo("Trying to bind server to port " + extendedPort);
                this.socket.bind(/*bindAddress = */generateSocketAddress(extendedPort));
            } else { //bindPort set, use another port to bind than the port reachable from outside
                this.log.logInfo("Trying to bind server to port " + bindPort+ " with "+ extendedPort + "as seedPort.");
                this.socket.bind(/*bindAddress = */generateSocketAddress(bindPort));
            }
            // updating the port information
            //yacyCore.seedDB.mySeed.put(yacySeed.PORT,Integer.toString(bindAddress.getPort()));    
            //yacyCore.seedDB.mySeed().put(yacySeed.PORT, extendedPort);
        } catch (final Exception e) {
            final String errorMsg = "FATAL ERROR: " + e.getMessage() + " - probably root access rights needed. check port number";
            this.log.logSevere(errorMsg);
            System.out.println(errorMsg);             
            System.exit(0);
        }
    }
    
    public static int getPortNr(String extendedPortString) {
        int pos = -1;
        if ((pos = extendedPortString.indexOf(':'))!= -1) {
            extendedPortString = extendedPortString.substring(pos+1);
        }
        return Integer.parseInt(extendedPortString);         
    }
    
    public InetSocketAddress generateSocketAddress(String extendedPortString) throws SocketException {
        
        // parsing the port configuration
        String bindIP = null;
        int bindPort;
        
        int pos = -1;
        if ((pos = extendedPortString.indexOf(':'))!= -1) {
            bindIP = extendedPortString.substring(0,pos).trim();
            extendedPortString = extendedPortString.substring(pos+1); 
            
            if (bindIP.length() > 0 && bindIP.charAt(0) == '#') {
                final String interfaceName = bindIP.substring(1);
                String hostName = null;
                if (this.log.isFine()) this.log.logFine("Trying to determine IP address of interface '" + interfaceName + "'.");                    

                final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                if (interfaces != null) {
                    while (interfaces.hasMoreElements()) {
                        final NetworkInterface interf = interfaces.nextElement();
                        if (interf.getName().equalsIgnoreCase(interfaceName)) {
                            final Enumeration<InetAddress> addresses = interf.getInetAddresses();
                            if (addresses != null) {
                                while (addresses.hasMoreElements()) {
                                    final InetAddress address = addresses.nextElement();
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
        bindPort = Integer.parseInt(extendedPortString);    
        
        return (bindIP == null) 
        ? new InetSocketAddress(bindPort)
        : new InetSocketAddress(bindIP, bindPort);
    }
    
    public void open() {
        this.log.logConfig("* server started on " + Domains.myPublicLocalIP() + ":" + this.extendedPort);
    }
    
    public void freemem() {
        // FIXME: can we something here to flush memory? Idea: Reduce the size of some of our various caches.
    }
    
    // class body
    public boolean job() throws Exception {
        try {
            // prepare for new connection
            // idleThreadCheck();
            this.switchboard.handleBusyState(getJobCount());
            if (this.log.isFinest()) this.log.logFinest("* waiting for connections, " + getJobCount() + " sessions running");
            
            announceThreadBlockApply();
            
            // wait for new connection
            Socket controlSocket = this.socket.accept();
            
            announceThreadBlockRelease();
            
            int pp/*, trycount = 0*/;
            if ((pp = getJobCount()) >= this.maxBusySessions) {
                terminateOldSessions(3000);
                this.log.logInfo("termination of old sessions: before = " + pp + ", after = " + getJobCount());
                //if (getJobCount() < this.maxBusySessions) break;
                //if (trycount++ > 5) break;
                //Thread.sleep(1000); // lets try again after a short break
            }
            /*
            if (getJobCount() >= this.maxBusySessions) {
                // immediately close connection if too much sessions are still running
                this.log.logWarning("* connections (" + getJobCount() + ") exceeding limit (" + this.maxBusySessions + ")" + ", closing new incoming connection from "+ controlSocket.getRemoteSocketAddress());
                
                controlSocket.close();
                return false;
                
            }
            */
            
            final String cIP = clientAddress(controlSocket);
            //System.out.println("server bfHosts=" + bfHost.toString());
            /*
            if (bfHost.get(cIP) != null) {
                Integer attempts = bfHost.get(cIP);
                if (attempts == null) attempts = Integer.valueOf(1); else attempts = Integer.valueOf(attempts.intValue() + 1);
                bfHost.put(cIP, attempts);
                this.log.logWarning("SLOWING DOWN ACCESS FOR BRUTE-FORCE PREVENTION FROM " + cIP + ", ATTEMPT " + attempts.intValue());
                // add a delay to make brute-force harder
                announceThreadBlockApply();
                try {Thread.sleep(attempts.intValue());} catch (final InterruptedException e) {}
                announceThreadBlockRelease();
                if ((attempts.intValue() >= 10) && (this.denyHost != null)) {
                    this.denyHost.put(cIP, "deny");
                }
            }
            */
            
            if ((this.denyHost == null) || (this.denyHost.get(cIP) == null)) {
                // setting the timeout properly
                assert this.timeout >= 1000;
                controlSocket.setSoTimeout(this.timeout);
                
                // wrap this socket
                if (this.sslSocketFactory != null) {
                    controlSocket = new serverCoreSocket(controlSocket);

                    // if the current connection is SSL we need to do a handshake
                    if (((serverCoreSocket)controlSocket).isSSL()) {                
                        controlSocket = negotiateSSL(controlSocket);    
                    }            
                }
                // keep-alive: if set to true, the server frequently sends keep-alive packets to the client which the client must respond to
                // we set this to false to prevent that a missing ack from the client forces the server to close the connection
                controlSocket.setKeepAlive(false); 
                
                // disable Nagle's algorithm (waiting for more data until packet is full)
                // controlSocket.setTcpNoDelay(true);
                
                // set a non-zero linger, that means that a socket.close() blocks until all data is written
                controlSocket.setSoLinger(false, this.timeout);
                
                // ensure that MTU-48 is not exceeded to prevent that routers cannot handle large data packets
                // read http://www.cisco.com/warp/public/105/38.shtml for explanation
                //controlSocket.setSendBufferSize(1440);
                //controlSocket.setReceiveBufferSize(1440);
                
                // create session
                final Session connection = new Session(sessionThreadGroup, controlSocket, this.timeout);
                //terminateOldSessions(60000);
                connection.start();
            } else {
                this.log.logWarning("ACCESS FROM " + cIP + " DENIED");
            }
            
            return true;
        } catch (final SocketException e) {
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
        
        // shut down all busySessions
        for (final Session session: getJobList()) {
            if (session == null) continue;
            try {
                session.interrupt();
            } catch (final SecurityException e ) {
                Log.logException(e);
            } catch (final ConcurrentModificationException e) {
                Log.logException(e);
            }
        }
        
        // close the serverchannel and socket
        try {
            this.log.logInfo("Closing server socket ...");
            this.socket.close();
        } catch (final Exception e) {
            this.log.logWarning("Unable to close the server socket."); 
        }

        // close all sessions
        this.log.logInfo("Closing server sessions ...");
        for (final Session session: getJobList()) {
            session.interrupt();
            //session.close();
        }
        
        this.log.logConfig("* terminated");
    }
    
    public List<Session> getJobList() {
        final Thread[] threadList = new Thread[sessionThreadGroup.activeCount()];     
        serverCore.sessionThreadGroup.enumerate(threadList, false);
        ArrayList<Session> l = new ArrayList<Session>();
        for (Thread t: threadList) {
            if (t == null) continue;
            if (!(t instanceof Session)) {
                //log.logSevere("serverCore.getJobList - thread is not Session: " + t.getClass().getName());
                continue;
            }
            l.add((Session) t);
        }
        return l;
    }
    
    public int getJobCount() {
        return getJobList().size();
    }
    
    public int getMaxSessionCount() {
        return this.maxBusySessions;
    }
    
    public void setMaxSessionCount(final int count) {
        this.maxBusySessions = count;
    }
    
    // idle sensor: the thread is idle if there are no sessions running
    public boolean idle() {
        // idleThreadCheck();
        return (getJobCount() == 0);
    }

    public final class Session extends Thread {

        //boolean destroyed = false;
        private boolean runningsession = false;
        private boolean stopped = false;
        
        private long start;                // startup time
        private serverHandler commandObj;
        
    	private String request;            // current command line
    	private int commandCounter;        // for logging: number of commands in this session
    	private String identity;           // a string that identifies the client (i.e. ftp: account name)
    	//private boolean promiscuous;       // if true, no lines are read and streams are only passed
        
    	public  Socket controlSocket;      // dialog socket
    	public  InetAddress userAddress;   // the address of the client
        public  int userPort;              // the ip port used by the client 
    	public  PushbackInputStream in;    // on control input stream
    	public  OutputStream out;          // on control output stream, auto-flush
        public  int socketTimeout;
        public  int hashIndex;

    	public Session(final ThreadGroup theThreadGroup, final Socket controlSocket, final int socketTimeout) {
            super(theThreadGroup, controlSocket.getInetAddress().toString() + "@" + Long.toString(System.currentTimeMillis()));
            this.setPriority(Thread.MAX_PRIORITY);
            this.socketTimeout = socketTimeout;
            this.controlSocket = controlSocket;
            this.hashIndex = sessionCounter;
            sessionCounter++;
        }

        public int hashCode() {
            // return a hash code so it is possible to store objects of httpc objects in a HashSet
            return this.hashIndex;
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
        
        public void setStopped(final boolean stopped) {
            this.stopped = stopped;            
        }
        
        public boolean isStopped() {
            return this.stopped;
        }
        
        public void close() {
            // closing the socket to the client
            if (this.controlSocket != null) try {
                this.controlSocket.close();
                log.logInfo("Closing main socket of thread '" + this.getName() + "'");
                this.controlSocket = null;
            } catch (final Exception e) {}
        }
        
        public long getRequestStartTime() {
            return this.start;
        }
        
        public long getTime() {
            return System.currentTimeMillis() - this.start;
        }
            
    	public void setIdentity(final String id) {
    	    this.identity = id;
    	}
    
    	public void log(final boolean outgoing, final String request) {
    	    if (log.isFine()) log.logFine(this.userAddress.getHostAddress() + "/" + this.identity + " " +
    		     "[" + getJobCount() + ", " + this.commandCounter +
    		     ((outgoing) ? "] > " : "] < ") +
    		     request);
    	}
    
    	public void writeLine(final String messg) throws IOException {
    	    send(this.out, messg);
    	    log(true, messg);
    	}
    
    	public byte[] readLine() {
    	    return receive(this.in, commandMaxLength, true);
    	}
    
        /**
         * reads a line from the input socket
         * this function is provided by the server through a passed method on initialization
         * @return the next requestline as string
         */
        public String readLineAsString() {
            final byte[] l = readLine();
            return (l == null) ? null: UTF8.String(l);
        }
    
        /**
         * @return whether the {@link Thread} is currently running
         */
        public boolean isRunning() {
            return this.runningsession;
        }
        
        /**
         * 
         * 
         * @see java.lang.Thread#run()
         */
        public void run()  {
            this.runningsession = true;
            
            try {
                // setting the session startup time
                this.start = System.currentTimeMillis();                 
                
                // set the session identity
                this.identity = "-";
                
                // getting some client information
                this.userAddress = this.controlSocket.getInetAddress();
                this.userPort = this.controlSocket.getPort();
                this.setName("Session_" + this.userAddress.getHostAddress() + ":" + this.controlSocket.getPort());
                
                // TODO: check if we want to allow this socket to connect us
                
                // getting input and output stream for communication with client
                if (this.controlSocket.getInputStream() instanceof PushbackInputStream) {
                    this.in = (PushbackInputStream) this.controlSocket.getInputStream();
                } else {
                    this.in = new PushbackInputStream(this.controlSocket.getInputStream());
                }
                this.out = this.controlSocket.getOutputStream();

                // reseting the command counter
                this.commandCounter = 0;
                
                // listen for commands
                listen();
            } catch (final IOException e) {
                Log.logException(e);
            } catch (final Exception e) {
                Log.logException(e);
            } finally {
                try {
                    if ((this.controlSocket != null) && (! this.controlSocket.isClosed())) {
                        // flush data
                        this.out.flush();
                    
                        // maybe this doesn't work for all SSL socket implementations
                        if (!(this.controlSocket instanceof SSLSocket)) {
                            this.controlSocket.shutdownInput();
                            this.controlSocket.shutdownOutput();
                        }
                    
                        // close streams
                        this.in.close();                    
                        this.out.close();                   
                    
                        // close everything                    
                        this.controlSocket.close();
                    }
                } catch (final IOException e) {
                    Log.logException(e);
                } finally {
                    this.controlSocket = null;
                }
            }
            
        }
        
        private void listen() {
            try {
                // start dialog
                byte[] requestBytes = null;
                boolean terminate = false;
                String reqCmd;
                String reqProtocol = "HTTP";
                long situationDependentKeepAliveTimeout = keepAliveTimeout;
                while (this.in != null &&
                       this.controlSocket != null &&
                       this.controlSocket.isConnected() &&
                       (this.commandCounter == 0 || System.currentTimeMillis() - this.start < situationDependentKeepAliveTimeout) &&
                       (requestBytes = readLine()) != null) {
                    this.setName("Session_" + this.userAddress.getHostAddress() + 
                            ":" + this.controlSocket.getPort() + 
                            "#" + this.commandCounter);
                    
                    this.request = UTF8.String(requestBytes);
                    //this.log.logDebug("* session " + handle + " received command '" + request + "'. time = " + (System.currentTimeMillis() - handle));
                    log(false, this.request);
                    try {                        
                        // if we can not determine the proper command string we try to call function emptyRequest
                        // of the commandObject
                        if (this.request.trim().length() == 0) this.request = "EMPTY";
                        
                        final Object[] parameter = new Object[2];
                        
                        // get the rest of the request parameters
                        final int pos = this.request.indexOf(' ');
                        if (pos < 0) {
                            reqCmd = this.request.trim().toUpperCase();
                            parameter[0] = "";
                        } else {
                            reqCmd = this.request.substring(0, pos).trim().toUpperCase();
                            parameter[0] = this.request.substring(pos).trim();
                        }
                        parameter[1] = this;
                        
                        // now we need to initialize the session
                        if (this.commandCounter == 0) {
                            // first we need to determine the proper protocol handler
                            if (this.request.indexOf("HTTP") >= 0)          reqProtocol = "HTTP";
                            else                                            reqProtocol = null;                            
                            
                            if (this.request == null) break;
                            if (reqProtocol != null && reqProtocol.equals("HTTP")) {
                                this.commandObj = handlerPrototype.clone();
                            }
                        }
                        
                        // count the amount of requests that were processed by this session until yet
                        this.commandCounter++;
                        
                        // setting the socket timeout for reading of the request content
                        this.controlSocket.setSoTimeout(this.socketTimeout);
                        
                        // exec command and return value
                        Method commandMethod = commandObjMethodCache.get(reqProtocol + "_" + reqCmd);
                        if (commandMethod == null) {
                            try {
                                commandMethod = this.commandObj.getClass().getMethod(reqCmd, sessionCallType);
                                commandObjMethodCache.put(reqProtocol + "_" + reqCmd, commandMethod);
                            } catch (final NoSuchMethodException noMethod) {
                                commandMethod = this.commandObj.getClass().getMethod("UNKNOWN", sessionCallType);
                                parameter[0] = this.request.trim();
                            }
                        }
                        
                        Object result = null;
                        try {
                            result = commandMethod.invoke(this.commandObj, parameter);
                        } catch (OutOfMemoryError e) {
                            log.logWarning("commandMethod.invoke: OutOfMemoryError / 1 (retry1 follows)");
                            // try again
                            terminateOldSessions(2000);
                            try {
                                result = commandMethod.invoke(this.commandObj, parameter);
                            } catch (OutOfMemoryError e2) {
                                log.logWarning("commandMethod.invoke: OutOfMemoryError / 2 (retry2 follows)");
                                // try again
                                Thread.sleep(1000);
                                result = commandMethod.invoke(this.commandObj, parameter);
                            }
                        }
                        
                        //announceMoreExecTime(commandStart - System.currentTimeMillis()); // shall be negative!
                        //this.log.logDebug("* session " + handle + " completed command '" + request + "'. time = " + (System.currentTimeMillis() - handle));
                        this.out.flush();
                        if (result != null) {
                            if (result instanceof Boolean) {
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
                        }
                        if (terminate) break;
                        
                        
                    } catch (final InvocationTargetException e) {
                        log.logSevere("command execution, target exception " + e.getMessage() + " for client " + this.userAddress.getHostAddress(), e);
                        // we extract a target exception
                        writeLine(this.commandObj.error(e.getTargetException()));
                        break;
                    } catch (final NoSuchMethodException e) {
                        log.logSevere("command execution, method exception " + e.getMessage() + " for client " + this.userAddress.getHostAddress(), e);
                        if (!this.userAddress.isSiteLocalAddress()) {
                            if (denyHost != null) {
                                denyHost.put(this.userAddress.getHostAddress(), "deny"); // block client: hacker attempt
                            }
                        }
                        break;
                    } catch (final IllegalAccessException e) {
                        log.logSevere("command execution, illegal access exception " + e.getMessage() + " for client " + this.userAddress.getHostAddress(), e);
                        // wrong parameters: this can only be an internal problem
                        writeLine(this.commandObj.error(e));
                        break;
                    } catch (final ClassCastException e) {
                        log.logSevere("command execution, cast exception " + e.getMessage() + " for client " + this.userAddress.getHostAddress(), e);
                        // ??
                        writeLine(this.commandObj.error(e));
                        break;
                    } catch (final Exception e) {
                        log.logSevere("command execution, generic exception " + e.getMessage() + " for client " + this.userAddress.getHostAddress(), e);
                        // whatever happens: the thread has to survive!
                        writeLine("UNKNOWN REASON:" + ((this.commandObj == null) ? "no command object" : this.commandObj.error(e)));
                        break;
                    }
                    // check if we should still keep this alive:
                    break; // no more keep-alive, not needed for speed and causes only trouble
                    /*
                    if (getJobCount() > maxBusySessions / 2) break;
                    // the more connections are alive, the shorter the keep alive timeout
                    situationDependentKeepAliveTimeout = keepAliveTimeout / Math.max(1, getJobCount() - 20);
                    */
                } // end of while
            } catch (final IOException e) {
                log.logSevere("command execution, IO exception " + e.getMessage() + " for client " + this.userAddress.getHostAddress(), e);
            }
            //announceMoreExecTime(System.currentTimeMillis() - this.start);
        }

        public boolean isSSL() {
            return this.controlSocket != null && this.controlSocket instanceof SSLSocket;
        }
        
    }

    /**
     * Read a line from a protocol stream (HTTP/ICAP) and do some
     * pre-processing (check validity, strip line endings).
     * <br>
     * Illegal control characters will be stripped from the result.
     * Besides the valid line ending CRLF a single LF is treated as a
     * line ending as well to avoid errors with buggy server.
     * 
     * @param pbis    The stream to read from.
     * @param maxSize maximum number of bytes to read in one run.
     * @param logerr  log error messages if true, be silent otherwise.
     * 
     * @return A byte array representing one line of the input or 
     *         <code>null</null> if EOS reached.
     */
    public static byte[] receive(final PushbackInputStream pbis, final int maxSize, final boolean logerr) {

        // reuse an existing linebuffer
        final ByteBuffer readLineBuffer = new ByteBuffer(80);

        int bufferSize = 0, b = 0;
        try {
            // catch bytes until line end or illegal character reached or buffer full
            // resulting readLineBuffer doesn't include CRLF or illegal control chars
            while (bufferSize < maxSize) {
                b = pbis.read();
            
                if ((b > 31 && b != 127) || b == HT) {
                    // add legal chars to the result
                    readLineBuffer.append(b);
                    bufferSize++;
                } else if (b == CR) {
                    // possible beginning of CRLF, check following byte
                    b = pbis.read();
                    if (b == LF) {
                        // line end caught: break the loop
                        break;
                    } else if (b >= 0) {
                        // no line end: push back the byte, ignore the CR
                        pbis.unread(b);
                    }
                } else if (b == LF || b < 0) {
                    // LF without precedent CR: treat as line end of broken servers
                    // b < 0: EOS
                    break;
                }
            }

            // EOS
            if (bufferSize == 0 && b == -1) return null;
            return readLineBuffer.getBytes();
        } catch (final ClosedByInterruptException e) {
            if (logerr) Log.logWarning("SERVER", "receive interrupted");
            return null;            
        } catch (final IOException e) {
            String message = e.getMessage();
            if (logerr && !message.equals("Socket closed") && !message.equals("Connection reset") && !message.equals("Read timed out")) Log.logWarning("SERVER", "receive closed by IOException: " + e.getMessage());
            return null;
        } finally {
        	try {
				readLineBuffer.close();
			} catch (IOException e) {
			    Log.logException(e);
			}
        }
    }

    public static void send(final OutputStream os, final String buf) throws IOException {
    	os.write(UTF8.getBytes(buf));
    	os.write(CRLF);
    	os.flush();
    }
    
    public static void send(final OutputStream os, final byte[] buf) throws IOException {
    	os.write(buf);
    	os.write(CRLF);
    	os.flush();
    }
        
    public static String send(final OutputStream os, final InputStream is) throws IOException {
    	final int bufferSize = is.available();
    	final byte[] buffer = new byte[((bufferSize < 1) || (bufferSize > 4096)) ? 4096 : bufferSize];
    	int l;
    	while ((l = is.read(buffer)) > 0) {os.write(buffer, 0, l);}
    	os.write(CRLF);
    	os.flush();
    	if (bufferSize > 80) return "<LONG STREAM>";
    	return UTF8.String(buffer);
    }
    
    public static final void checkInterruption() throws InterruptedException {
        final Thread currentThread = Thread.currentThread();
        if (currentThread.isInterrupted()) throw new InterruptedException();  
        if ((currentThread instanceof serverCore.Session) && ((serverCore.Session)currentThread).isStopped()) throw new InterruptedException();
    }
    
    public void reconnect(final int delay) {
        final Thread restart = new Restarter(delay);
        restart.start();
    }
    
    // restarting the serverCore
    public class Restarter extends Thread { 
        public serverCore theServerCore = null;
        public int delay = 5000;
        public Restarter(final int delay) {
            this.delay = delay;
        }
        public void run() {
            // waiting for a while
            try {
                Thread.sleep(delay);
            } catch (final InterruptedException e) {
                Log.logException(e);
            } catch (final Exception e) {
                Log.logException(e);
            }
            
            // signaling restart
            forceRestart = true;
            
            // closing socket to notify the thread
            close();
        }
    }
    
    private SSLSocketFactory initSSLFactory() {
        
        // getting the keystore file name
        String keyStoreFileName = this.switchboard.getConfig("keyStore", "").trim();        
        
        // getting the keystore pwd
        final String keyStorePwd = this.switchboard.getConfig("keyStorePassword", "").trim();
        
        // take a look if we have something to import
        final String pkcs12ImportFile = this.switchboard.getConfig("pkcs12ImportFile", "").trim();
        if (pkcs12ImportFile.length() > 0) {
            this.log.logInfo("Import certificates from import file '" + pkcs12ImportFile + "'.");
            
            try {
                // getting the password
                final String pkcs12ImportPwd = this.switchboard.getConfig("pkcs12ImportPwd", "").trim();

                // creating tool to import cert
                final PKCS12Tool pkcsTool = new PKCS12Tool(pkcs12ImportFile,pkcs12ImportPwd);

                // creating a new keystore file
                if (keyStoreFileName.length() == 0) {
                    // using the default keystore name
                    keyStoreFileName = "DATA/SETTINGS/myPeerKeystore";
                    
                    // creating an empty java keystore
                    final KeyStore ks = KeyStore.getInstance("JKS");
                    ks.load(null,keyStorePwd.toCharArray());
                    final FileOutputStream ksOut = new FileOutputStream(keyStoreFileName);
                    ks.store(ksOut, keyStorePwd.toCharArray());
                    ksOut.close();
                    
                    // storing path to keystore into config file
                    this.switchboard.setConfig("keyStore", keyStoreFileName);
                }

                // importing certificate
                pkcsTool.importToJKS(keyStoreFileName, keyStorePwd);
                
                // removing entries from config file
                this.switchboard.setConfig("pkcs12ImportFile", "");
                this.switchboard.setConfig("keyStorePassword", "");
                
                // deleting original import file
                // TODO: should we do this
                
            } catch (final Exception e) {
                this.log.logSevere("Unable to import certificate from import file '" + pkcs12ImportFile + "'.",e);
            }
        } else if (keyStoreFileName.length() == 0) return null;
        
        
        // get the ssl context
        try {
            this.log.logInfo("Initializing SSL support ...");
            
            // creating a new keystore instance of type (java key store)
            if (this.log.isFine()) this.log.logFine("Initializing keystore ...");
            final KeyStore ks = KeyStore.getInstance("JKS");
            
            // loading keystore data from file
            if (this.log.isFine()) this.log.logFine("Loading keystore file " + keyStoreFileName);
            final FileInputStream stream = new FileInputStream(keyStoreFileName);            
            ks.load(stream, keyStorePwd.toCharArray());
            stream.close();
            
            // creating a keystore factory
            if (this.log.isFine()) this.log.logFine("Initializing key manager factory ...");
            final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks,keyStorePwd.toCharArray());
            
            // initializing the ssl context
            if (this.log.isFine()) this.log.logFine("Initializing SSL context ...");
            final SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(kmf.getKeyManagers(), null, null);
            
            final SSLSocketFactory factory = sslcontext.getSocketFactory(); 
            this.log.logInfo("SSL support initialized successfully");
            return factory;
        } catch (final Exception e) {
            final String errorMsg = "FATAL ERROR: Unable to initialize the SSL Socket factory. " + e.getMessage();
            this.log.logSevere(errorMsg);
            System.out.println(errorMsg);             
            System.exit(0); 
            return null;
        }
    }
    
    public Socket negotiateSSL(final Socket sock) throws Exception {

        SSLSocket sslsock;
        
        try {
            sslsock=(SSLSocket)this.sslSocketFactory.createSocket(
                    sock,
                    sock.getInetAddress().getHostAddress(),
                    sock.getPort(),
                    true);

            sslsock.addHandshakeCompletedListener(
                    new HandshakeCompletedListener() {
                       public void handshakeCompleted(
                          final HandshakeCompletedEvent event) {
                          System.out.println("Handshake finished!");
                          System.out.println(
                          "\t CipherSuite:" + event.getCipherSuite());
                          System.out.println(
                          "\t SessionId " + event.getSession());
                          System.out.println(
                          "\t PeerHost " + event.getSession().getPeerHost());
                       }
                    }
                 );             
            
            sslsock.setUseClientMode(false);
            final String[] suites = sslsock.getSupportedCipherSuites();
            sslsock.setEnabledCipherSuites(suites);
//            start handshake
            sslsock.startHandshake();
            
            //String cipherSuite = sslsock.getSession().getCipherSuite();
            
            return sslsock;
        } catch (final Exception e) {
            throw e;
        }
    }    
}

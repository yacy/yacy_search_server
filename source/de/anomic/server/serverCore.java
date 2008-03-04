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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import de.anomic.icap.icapd;
import de.anomic.server.logging.serverLog;
import de.anomic.server.portForwarding.serverPortForwarding;
import de.anomic.tools.PKCS12Tool;
import de.anomic.urlRedirector.urlRedirectord;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public final class serverCore extends serverAbstractThread implements serverThread {

    // special ASCII codes used for protocol handling
    public static final byte HT = 9;  // Horizontal Tab
    public static final byte LF = 10; // Line Feed
    public static final byte CR = 13; // Carriage Return
    public static final byte SP = 32; // Space
    public static final byte[] CRLF = {CR, LF}; // Line End of HTTP/ICAP headers
    public static final String CRLF_STRING = new String(CRLF);
    public static final String LF_STRING = new String(new byte[]{LF});
    public static final Class<?>[] stringType = {"".getClass()}; //  set up some reflection
    public static final long startupTime = System.currentTimeMillis();
    public static final ThreadGroup sessionThreadGroup = new ThreadGroup("sessionThreadGroup");
    static int sessionCounter = 0; // will be increased with each session and is used to return a hash code
    
    // static variables
    public static final Boolean TERMINATE_CONNECTION = Boolean.FALSE;
    public static final Boolean RESUME_CONNECTION = Boolean.TRUE;
    public static HashMap<String, Integer> bfHost = new HashMap<String, Integer>(); // for brute-force prevention
    
    // class variables
    private String extendedPort;           // the port, which is visible from outside (in most cases bind-port)
	private String bindPort;               // if set, yacy will bind to this port, but set extendedPort in the seed
    public boolean forceRestart = false;   // specifies if the server should try to do a restart
    
    public static boolean portForwardingEnabled = false;
    public static boolean useStaticIP = false;
    public static serverPortForwarding portForwarding = null;
    
    private SSLSocketFactory sslSocketFactory = null;
    private ServerSocket socket;           // listener
    serverLog log;                         // log object
    private int timeout;                   // connection time-out of the socket
    serverHandler handlerPrototype;        // the command class (a serverHandler) 

    private serverSwitch switchboard;      // the command class switchboard
    HashMap<String, String> denyHost;
    int commandMaxLength;
    private int maxBusySessions;
    HashSet<Session> busySessions;
    
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
        this.denyHost = (blockAttack) ? new HashMap<String, String>() : null;
        this.handlerPrototype = handlerPrototype;
        this.switchboard = switchboard;
        
        // initialize logger
        this.log = new serverLog("SERVER");

        // init the ssl socket factory
        this.sslSocketFactory = initSSLFactory();

        // init session parameter
        maxBusySessions = Integer.valueOf(switchboard.getConfig("httpdMaxBusySessions","100")).intValue();
        busySessions = new HashSet<Session>();
        
        // init servercore
        init();
    }
    
    public boolean withSSL() {
        return this.sslSocketFactory != null;
    }
    
    public synchronized void init() {
        this.log.logInfo("Initializing serverCore ...");
        
        // read some config values
        this.extendedPort = this.switchboard.getConfig("port", "8080").trim();
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
            yacyCore.seedDB.mySeed().put(yacySeed.PORT, extendedPort);
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

    }
    
    public static int getPortNr(String extendedPortString) {
        int pos = -1;
        if ((pos = extendedPortString.indexOf(":"))!= -1) {
            extendedPortString = extendedPortString.substring(pos+1);
        }
        return Integer.parseInt(extendedPortString);         
    }
    
    public InetSocketAddress generateSocketAddress(String extendedPortString) throws SocketException {
        
        // parsing the port configuration
        String bindIP = null;
        int bindPort;
        
        int pos = -1;
        if ((pos = extendedPortString.indexOf(":"))!= -1) {
            bindIP = extendedPortString.substring(0,pos).trim();
            extendedPortString = extendedPortString.substring(pos+1); 
            
            if (bindIP.startsWith("#")) {
                String interfaceName = bindIP.substring(1);
                String hostName = null;
                this.log.logFine("Trying to determine IP address of interface '" + interfaceName + "'.");                    

                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                if (interfaces != null) {
                    while (interfaces.hasMoreElements()) {
                        NetworkInterface interf = interfaces.nextElement();
                        if (interf.getName().equalsIgnoreCase(interfaceName)) {
                            Enumeration<InetAddress> addresses = interf.getInetAddresses();
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
        bindPort = Integer.parseInt(extendedPortString);    
        
        return (bindIP == null) 
        ? new InetSocketAddress(bindPort)
        : new InetSocketAddress(bindIP, bindPort);
    }
    
    public void initPortForwarding() throws Exception {
        // doing the port forwarding stuff
        if (this.switchboard.getConfigBool("portForwarding.Enabled",false)) {
            this.log.logInfo("Initializing port forwarding ...");
            try {
                // getting the port forwarding type to use
                String forwardingType = this.switchboard.getConfig("portForwarding.Type","none");                               
                
                // loading port forwarding class
                this.log.logInfo("Trying to load port forwarding class for forwarding type '" + forwardingType + "'.");
                String forwardingClass = this.switchboard.getConfig("portForwarding." + forwardingType ,"");
                
                Class<?> forwarderClass = Class.forName(forwardingClass);
                serverCore.portForwarding = (serverPortForwarding) forwarderClass.newInstance();                
                
                // initializing port forwarding
                String localHost = this.socket.getInetAddress().getHostName();
                Integer localPort = new Integer(this.socket.getLocalPort());                
                
                serverCore.portForwarding.init(
                        this.switchboard,
                        localHost,
                        localPort.intValue());
                
                // connection to port forwarding host
                serverCore.portForwarding.connect();
                
                serverCore.portForwardingEnabled = true;
                yacyCore.seedDB.mySeed().put(yacySeed.IP, serverDomains.myPublicIP());
                yacyCore.seedDB.mySeed().put(yacySeed.PORT,Integer.toString(serverCore.portForwarding.getPort()));                               
            } catch (Exception e) {
                serverCore.portForwardingEnabled = false;
                this.switchboard.setConfig("portForwarding.Enabled", "false");
                throw e;
            } catch (Error e) {
                serverCore.portForwardingEnabled = false;
                this.switchboard.setConfig("portForwarding.Enabled", "false");
                throw e;                
            }

        } else {
            serverCore.portForwardingEnabled = false;
            serverCore.portForwarding = null;
            yacyCore.seedDB.mySeed().put(yacySeed.IP, serverDomains.myPublicIP());
            yacyCore.seedDB.mySeed().put(yacySeed.PORT,Integer.toString(serverCore.getPortNr(this.switchboard.getConfig("port", "8080"))));             
        }
        if(! this.switchboard.getConfig("staticIP", "").equals(""))
            serverCore.useStaticIP=true;

    }
    
    public void open() {
        this.log.logConfig("* server started on " + serverDomains.myPublicLocalIP() + ":" + this.extendedPort);
    }
    
    public void freemem() {
        // FIXME: can we something here to flush memory? Idea: Reduce the size of some of our various caches.
        serverMemory.gc(2000, "serverCore.freemem()"); // thq
    }
    
    // class body
    public boolean job() throws Exception {
        try {
            // prepare for new connection
            // idleThreadCheck();
            this.switchboard.handleBusyState(this.busySessions.size());
            this.log.logFinest("* waiting for connections, " + this.busySessions.size() + " sessions running");
                        
            announceThreadBlockApply();
            
            // wait for new connection
            Socket controlSocket = this.socket.accept();
            
            // wrap this socket
            if (this.sslSocketFactory != null) {
                controlSocket = new serverCoreSocket(controlSocket);

                // if the current connection is SSL we need to do a handshake
                if (((serverCoreSocket)controlSocket).isSSL()) {                
                    controlSocket = negotiateSSL(controlSocket);    
                }            
            }
            
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
                
                // create session
                Session connection = new Session(sessionThreadGroup, controlSocket, this.timeout);
                this.busySessions.add(connection);
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
        
        // close the serverchannel and socket
        try {
            this.log.logInfo("Closing server socket ...");
            this.socket.close();
        } catch (Exception e) {
            this.log.logWarning("Unable to close the server socket."); 
        }

        // close all sessions
        this.log.logInfo("Closing server sessions ...");
        Iterator<Session> i = this.busySessions.iterator();
        Session s;
        while (i.hasNext()) {
            s = i.next();
            s.interrupt();
            s.close();
        }
        this.busySessions = null;
        
        this.log.logConfig("* terminated");
    }
    
    public int getJobCount() {
        return this.busySessions.size();
    }
    
    public int getMaxSessionCount() {
        return this.maxBusySessions;
    }
    
    public void setMaxSessionCount(int count) {
        this.maxBusySessions = count;
    }
    
    // idle sensor: the thread is idle if there are no sessions running
    public boolean idle() {
        // idleThreadCheck();
        return (this.busySessions.size() == 0);
    }

    public final class Session extends Thread {

        boolean destroyed = false;
        private boolean running = false;
        private boolean stopped = false;
        
        private long start;                // startup time
        private serverHandler commandObj;
        private HashMap<String, Object> commandObjMethodCache = new HashMap<String, Object>(5);
        
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

    	public Session(ThreadGroup theThreadGroup, Socket controlSocket, int socketTimeout) {
            super(theThreadGroup, controlSocket.getInetAddress().toString() + "@" + Long.toString(System.currentTimeMillis()));
            this.socketTimeout = socketTimeout;
            this.controlSocket = controlSocket;
            this.hashIndex = sessionCounter;
            sessionCounter++;
            
            if (!this.running)  {
               // this.setDaemon(true);
               this.start();
            }  else { 
               this.notifyAll();
            }          
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
        
        public void setStopped(boolean stopped) {
            this.stopped = stopped;            
        }
        
        public boolean isStopped() {
            return this.stopped;
        }
        
        public void close() {
            if (this.isAlive()) {
                try {
                    // closing the socket to the client
                    if ((this.controlSocket != null)&&(this.controlSocket.isConnected())) {
                        this.controlSocket.close();
                        serverCore.this.log.logInfo("Closing main socket of thread '" + this.getName() + "'");
                    }
                } catch (Exception e) {}
            }            
        }
        
        public long getRequestStartTime() {
            return this.start;
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
    		     "[" + ((busySessions == null)? -1 : busySessions.size()) + ", " + this.commandCounter +
    		     ((outgoing) ? "] > " : "] < ") +
    		     request);
    	}
    
    	public void writeLine(String messg) throws IOException {
    	    send(this.out, messg + CRLF_STRING);
    	    log(true, messg);
    	}
    
    	public byte[] readLine() {
    	    return receive(this.in, serverCore.this.commandMaxLength, true);
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
         * @return whether the {@link Thread} is currently running
         */
        public boolean isRunning() {
            return this.running;
        }
        
        /**
         * 
         * 
         * @see java.lang.Thread#run()
         */
        public void run()  {
            this.running = true;
            
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
            } catch (Exception e) {
                System.err.println("ERROR: (internal) " + e);        
            } finally {
                try {
                    if (this.controlSocket.isClosed()) return;
                    
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
                    
                    // sleep for a while
                    try {Thread.sleep(1000);} catch (InterruptedException e) {}                                                           
                    
                    // close everything                    
                    this.controlSocket.close();
                    this.controlSocket = null;
                                      
                } catch (IOException e) {
                    e.printStackTrace();
                }
                busySessions.remove(this);
            }
            
        }
        
        private void listen() {
            try {
                
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
                                this.commandObjMethodCache.put(reqProtocol + "_" + reqCmd, commandMethod);
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
                        if (!this.userAddress.isSiteLocalAddress()) {
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
                } // end of while
            } /* catch (java.lang.ClassNotFoundException e) {
                System.out.println("Internal error: Wrapper class not found: " + e.getMessage());
                System.exit(0);
            } */ catch (java.io.IOException e) {
                // connection interruption: more or less normal
            }
            //announceMoreExecTime(System.currentTimeMillis() - this.start);
        }

        public boolean isSSL() {
            return this.controlSocket instanceof SSLSocket;
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
    public static byte[] receive(PushbackInputStream pbis, int maxSize, boolean logerr) {

        // reuse an existing linebuffer
        serverByteBuffer readLineBuffer = new serverByteBuffer(80);

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
                        // line end catched: break the loop
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
    	// TODO make sure there was no reason to add this additional newline
    	//os.write(CRLF);
    	os.flush();
    }
    
    public static void send(OutputStream os, byte[] buf) throws IOException {
    	os.write(buf);
    	os.write(CRLF);
    	os.flush();
    }
        
    public static String send(OutputStream os, InputStream is) throws IOException {
    	int bufferSize = is.available();
    	byte[] buffer = new byte[((bufferSize < 1) || (bufferSize > 4096)) ? 4096 : bufferSize];
    	int l;
    	while ((l = is.read(buffer)) > 0) {os.write(buffer, 0, l);}
    	os.write(CRLF);
    	os.flush();
    	if (bufferSize > 80) return "<LONG STREAM>"; else return new String(buffer);
    }
    
    protected void finalize() throws Throwable {
        super.finalize();
    }
    
    public static final void checkInterruption() throws InterruptedException {
        Thread currentThread = Thread.currentThread();
        if (currentThread.isInterrupted()) throw new InterruptedException();  
        if ((currentThread instanceof serverCore.Session) && ((serverCore.Session)currentThread).isStopped()) throw new InterruptedException();
    }
    public void reconnect() {
        this.reconnect(5000);
    }
    public void reconnect(int delay) {
        Thread restart = new Restarter();
        restart.start();
    }
    
    // restarting the serverCore
    public class Restarter extends Thread { 
        public serverCore theServerCore = null;
        public int delay = 5000;
        public void run() {
            // waiting for a while
            try {
                Thread.sleep(delay);
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
    
    private SSLSocketFactory initSSLFactory() {
        
        // getting the keystore file name
        String keyStoreFileName = this.switchboard.getConfig("keyStore", "").trim();        
        
        // getting the keystore pwd
        String keyStorePwd = this.switchboard.getConfig("keyStorePassword", "").trim();
        
        // take a look if we have something to import
        String pkcs12ImportFile = this.switchboard.getConfig("pkcs12ImportFile", "").trim();
        if (pkcs12ImportFile.length() > 0) {
            this.log.logInfo("Import certificates from import file '" + pkcs12ImportFile + "'.");
            
            try {
                // getting the password
                String pkcs12ImportPwd = this.switchboard.getConfig("pkcs12ImportPwd", "").trim();

                // creating tool to import cert
                PKCS12Tool pkcsTool = new PKCS12Tool(pkcs12ImportFile,pkcs12ImportPwd);

                // creating a new keystore file
                if (keyStoreFileName.length() == 0) {
                    // using the default keystore name
                    keyStoreFileName = "DATA/SETTINGS/myPeerKeystore";
                    
                    // creating an empty java keystore
                    KeyStore ks = KeyStore.getInstance("JKS");
                    ks.load(null,keyStorePwd.toCharArray());
                    FileOutputStream ksOut = new FileOutputStream(keyStoreFileName);
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
                
            } catch (Exception e) {
                this.log.logSevere("Unable to import certificate from import file '" + pkcs12ImportFile + "'.",e);
            }
        } else if (keyStoreFileName.length() == 0) return null;
        
        
        // get the ssl context
        try {
            this.log.logInfo("Initializing SSL support ...");
            
            // creating a new keystore instance of type (java key store)
            this.log.logFine("Initializing keystore ...");
            KeyStore ks = KeyStore.getInstance("JKS");
            
            // loading keystore data from file
            this.log.logFine("Loading keystore file " + keyStoreFileName);
            FileInputStream stream = new FileInputStream(keyStoreFileName);            
            ks.load(stream, keyStorePwd.toCharArray());
            
            // creating a keystore factory
            this.log.logFine("Initializing key manager factory ...");
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks,keyStorePwd.toCharArray());
            
            // initializing the ssl context
            this.log.logFine("Initializing SSL context ...");
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(kmf.getKeyManagers(), null, null);
            
            SSLSocketFactory factory = sslcontext.getSocketFactory(); 
            this.log.logInfo("SSL support initialized successfully");
            return factory;
        } catch (Exception e) {
            String errorMsg = "FATAL ERROR: Unable to initialize the SSL Socket factory. " + e.getMessage();
            this.log.logSevere(errorMsg);
            System.out.println(errorMsg);             
            System.exit(0); 
            return null;
        }
    }
    
    public Socket negotiateSSL(Socket sock) throws Exception {

        SSLSocket sslsock;
        
        try {
            sslsock=(SSLSocket)this.sslSocketFactory.createSocket(
                    sock,
                    sock.getInetAddress().getHostName(),
                    sock.getPort(),
                    true);

            sslsock.addHandshakeCompletedListener(
                    new HandshakeCompletedListener() {
                       public void handshakeCompleted(
                          HandshakeCompletedEvent event) {
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
            String[] suites = sslsock.getSupportedCipherSuites();
            sslsock.setEnabledCipherSuites(suites);
//            start handshake
            sslsock.startHandshake();
            
            //String cipherSuite = sslsock.getSession().getCipherSuite();
            
            return sslsock;
        } catch (Exception e) {
            throw e;
        }
    }    
}

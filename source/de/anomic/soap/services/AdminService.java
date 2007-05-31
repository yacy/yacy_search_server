//AdminService.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file was contributed by Martin Thelian
//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.


package de.anomic.soap.services;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.XMLFormatter;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.axis.AxisFault;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.anomic.http.httpRemoteProxyConfig;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverThread;
import de.anomic.server.logging.GuiHandler;
import de.anomic.soap.AbstractService;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.yacy.yacySeed;

public class AdminService extends AbstractService {
	
	/* =====================================================================
	 * Used Plasmaswitchboard config properties
	 * ===================================================================== */
	private static final String _10_HTTPD = "10_httpd";	
	private static final String RESTART = "restart";
	
	// peer properties
	private static final String PORT = "port";
	private static final String PEER_NAME = "peerName";	
	
	// remote proxy properties
	private static final String REMOTE_PROXY_USE = "remoteProxyUse";	
	private static final String REMOTE_PROXY_USE4SSL = "remoteProxyUse4SSL";
	private static final String REMOTE_PROXY_USE4YACY = "remoteProxyUse4Yacy";
	private static final String REMOTE_PROXY_NO_PROXY = "remoteProxyNoProxy";
	private static final String REMOTE_PROXY_PWD = "remoteProxyPwd";
	private static final String REMOTE_PROXY_USER = "remoteProxyUser";
	private static final String REMOTE_PROXY_PORT = "remoteProxyPort";
	private static final String REMOTE_PROXY_HOST = "remoteProxyHost";
	
	// remote triggered crawl properties
	private static final String CRAWL_RESPONSE = "crawlResponse";
	private static final String _62_REMOTETRIGGEREDCRAWL_BUSYSLEEP = plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL_BUSYSLEEP;
	private static final String _62_REMOTETRIGGEREDCRAWL = plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL;
	
	// index transfer properties
	private static final String INDEX_RECEIVE_BLOCK_BLACKLIST = "indexReceiveBlockBlacklist";
	private static final String ALLOW_RECEIVE_INDEX = "allowReceiveIndex";
	private static final String ALLOW_DISTRIBUTE_INDEX_WHILE_CRAWLING = "allowDistributeIndexWhileCrawling";
	private static final String ALLOW_DISTRIBUTE_INDEX = "allowDistributeIndex";
	
	// message forwarding properties
	private static final String MSG_FORWARDING_TO = "msgForwardingTo";
	private static final String MSG_FORWARDING_CMD = "msgForwardingCmd";
	private static final String MSG_FORWARDING_ENABLED = "msgForwardingEnabled";
	private static final String MSG_FORWARDING = "msgForwarding";
	
	// peer profile
	private static final String PEERPROFILE_COMMENT = "comment";
	private static final String PEERPROFILE_MSN = "msn";
	private static final String PEERPROFILE_YAHOO = "yahoo";
	private static final String PEERPROFILE_JABBER = "jabber";
	private static final String PEERPROFILE_ICQ = "icq";
	private static final String PEERPROFILE_EMAIL = "email";
	private static final String PEERPROFILE_HOMEPAGE = "homepage";
	private static final String PEERPROFILE_NICKNAME = "nickname";
	private static final String PEERPROFILE_NAME = "name";
	private static final String PEER_PROFILE_FETCH_SUCCESS = "success";
	private static final String PEER_HASH = "hash";	
	
	/* =====================================================================
	 * Used XML Templates
	 * ===================================================================== */
    private static final String TEMPLATE_CONFIG_XML = "xml/config_p.xml";   
    private static final String TEMPLATE_VERSION_XML = "xml/version.xml";
    private static final String TEMPLATE_PROFILE_XML = "ViewProfile.xml";    
    
    /**
     * This function can be used to set a configuration option
     * @param key the name of the option
     * @param value the value of the option as String
     * @throws AxisFault if authentication failed
     */
    public void setConfigProperty(String key, String value) throws AxisFault {
    	// Check for errors
    	if ((key == null)||(key.length() == 0)) throw new IllegalArgumentException("Key must not be null or empty.");
    	
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);  
    	
    	// add key to switchboard
        if (value == null) value = "";
        this.switchboard.setConfig(key,value);
    }
    
    /**
     * This function can be used to set multiple configuration option
     * @param keys an array containing the names of all options
     * @param values an array containing the values of all options
     * @throws AxisFault if authentication failed
     * @throws IllegalArgumentException if key.length != value.length
     */
    public void setProperties(String[] keys, String values[]) throws AxisFault{
    	// Check for errors
    	if ((keys == null)||(keys.length == 0)) throw new IllegalArgumentException("Key array must not be null or empty.");
    	if ((values == null)||(values.length == 0)) throw new IllegalArgumentException("Values array must not be null or empty.");
    	if (keys.length != values.length) throw new IllegalArgumentException("Invalid input. " + keys.length + " keys but " + values.length + " values received.");
    	
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);
        
        for (int i=0; i < keys.length; i++) {
        	// get the key
        	String nextKey = keys[i];
        	if ((nextKey == null)||(nextKey.length() == 0)) throw new IllegalArgumentException("Key at position " + i + " was null or empty.");
        	
        	// get the value
        	String nextValue = values[i];
        	if (nextValue == null) nextValue = "";
        	
        	// save the value
        	this.switchboard.setConfig(nextKey,nextValue);      
        }        
    }
    
    /**
     * This function can be used to geht the value of a single configuration option
     * @param key the name of the option
     * @return the value of the option as string
     * @throws AxisFault if authentication failed
     */
    public String getConfigProperty(String key) throws AxisFault {
    	// Check for errors
    	if ((key == null)||(key.length() == 0)) throw new IllegalArgumentException("Key must not be null or empty.");
    	
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);  
        
        // get the config property
    	return this.switchboard.getConfig(key,null);    	
    }
    
    /**
     * This function can be used to query the value of multiple configuration options
     * @param keys an array containing the names of the configuration options to query
     * @return an array containing the values of the configuration options as string
     * @throws AxisFault if authentication failed
     */
    public String[] getConfigProperties(String[] keys) throws AxisFault {
    	// Check for errors
    	if ((keys == null)||(keys.length== 0)) throw new IllegalArgumentException("Key array must not be null or empty.");
    	
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);      	
    	
    	// get the properties
        ArrayList returnValues = new ArrayList(keys.length);
        for (int i=0; i < keys.length; i++) {
        	String nextKey = keys[i];
        	if ((nextKey == null)||(nextKey.length() == 0)) throw new IllegalArgumentException("Key at position " + i + " was null or empty.");
        	
        	returnValues.add(this.switchboard.getConfig(nextKey,null));      
        }

    	// return the result
    	return (String[]) returnValues.toArray(new String[keys.length]);        
    }
    
    
    /**
     * Returns the current configuration of this peer as XML Document
     * @return a XML document of the following format
     * <pre>
     * &lt;?xml version="1.0"?&gt;
     * &lt;settings&gt;
     *   &lt;option&gt;
	 *     &lt;key&gt;option-name&lt;/key&gt;
	 *     &lt;value&gt;option-value&lt;/value&gt;
	 *   &lt;/option&gt;
	 * &lt;/settings&gt;
     * </pre>
     * 
     * @throws AxisFault if authentication failed
     * @throws Exception
     */
    public Document getConfigPropertyList() throws Exception {
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);          	
        
        // generating the template containing the network status information
        byte[] result = this.serverContext.writeTemplate(TEMPLATE_CONFIG_XML, new serverObjects(),this.requestHeader);
        
        // sending back the result to the client
        return this.convertContentToXML(result);        
    }
    
    /**
     * Returns detailed version information about this peer
     * @return a XML document of the following format
     * <pre>
     * &lt;?xml version="1.0"?&gt;
     * &lt;version&gt;
     *	  &lt;number&gt;0.48202791&lt;/number&gt;
     *	  &lt;svnRevision&gt;2791&lt;/svnRevision&gt;
     *	  &lt;buildDate&gt;20061017&lt;/buildDate&gt;
	 * &lt;/version&gt;
     * </pre>
     * @throws AxisFault if authentication failed
     * @throws Exception
     */
    public Document getVersion() throws Exception {
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);          	
        
        // generating the template containing the network status information
        byte[] result = this.serverContext.writeTemplate(TEMPLATE_VERSION_XML, new serverObjects(), this.requestHeader);
        
        // sending back the result to the client
        return this.convertContentToXML(result);        
    }        
    
    /**
     * This function can be used to configure the peer name
     * @param newName the new name of the peer
     * @throws AxisFault if authentication failed or peer name was not accepted 
     */
    public void setPeerName(String newName) throws AxisFault {
    	// Check for errors
    	if ((newName == null)||(newName.length() == 0)) throw new IllegalArgumentException("The peer name must not be null or empty.");    	
    	
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);                    
        
        // get the previous name
        String prevName = this.switchboard.getConfig(PEER_NAME, "");
        if (prevName.equals("newName")) return;
        
        // take a look if there is already an other peer with this name
        yacySeed oldSeed = yacyCore.seedDB.lookupByName(newName);
        if (oldSeed != null) throw new AxisFault("Other peer '" + oldSeed.getName() + "/" + oldSeed.getHexHash() + "' with this name found");
        
        // name must not be too short
        if (newName.length() < 3) throw new AxisFault("Name is too short");
        
        // name must not be too long
        if (newName.length() > 80) throw new AxisFault("Name is too long.");

        // check for invalid chars
        for (int i = 0; i < newName.length(); i++) {
        	if ("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_".indexOf(newName.charAt(i)) < 0)
        	throw new AxisFault("Invalid char at position " + i);
        }
        
        // use the new name
        this.switchboard.setConfig(PEER_NAME, newName);
    }
    
    /**
     * Changes the port the server socket is bound to. 
     * 
     * Please not that after the request was accepted the server waits
     * a timeout of 5 seconds before the server port binding is changed
     *   
     * @param newPort the new server port
     * @throws AxisFault if authentication failed 
     */
    public void setPeerPort(int newPort) throws AxisFault {
    	if (newPort <= 0) throw new IllegalArgumentException("Invalid port number");
    	
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);        
        
        // get the old value
        int oldPort = (int) this.switchboard.getConfigLong(PORT, 8080);
        if (oldPort == newPort) return;
        
        // getting the server thread
        serverCore theServerCore = (serverCore) this.switchboard.getThread(_10_HTTPD);
        
        // store the new value
        this.switchboard.setConfig(PORT, newPort);
        
        // restart the port listener
        // TODO: check if the port is free
        theServerCore.reconnect(5000);        
    }
    
    /**
     * This function can be enabled the usage of an already configured remote proxy
     * @param enableProxy <code>true</code> to enable and <code>false</code> to disable remote proxy usage 
     * @throws AxisFault if authentication failed or remote proxy configuration is missing  
     */
    public void enableRemoteProxy(boolean enableProxy) throws AxisFault {
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);   
        
        // check for errors
        String proxyHost = this.switchboard.getConfig(REMOTE_PROXY_HOST, "");
        if (proxyHost.length() == 0) throw new AxisFault("Remote proxy hostname is not configured");
        
        String proxyPort = this.switchboard.getConfig(REMOTE_PROXY_PORT, "");
        if (proxyPort.length() == 0) throw new AxisFault("Remote proxy port is not configured");
        
        // store the new state
        plasmaSwitchboard sb = (plasmaSwitchboard) this.switchboard;
        sb.setConfig(REMOTE_PROXY_USE,Boolean.toString(enableProxy));
        sb.remoteProxyConfig = httpRemoteProxyConfig.init(sb);         
    }
    
    /**
     * This function can be used to configured another remote proxy that should be used by 
     * yacy as parent proxy. 
     * If a parameter value is <code>null</code> then the current configuration value is not
     * changed.
     *  
     * @param enableRemoteProxy to enable or disable remote proxy usage
     * @param proxyHost the remote proxy host name
     * @param proxyPort the remote proxy user name
     * @param proxyUserName login name for the remote proxy
     * @param proxyPwd password to login to the remote proxy
     * @param noProxyList a list of addresses that should not be accessed via the remote proxy 
     * @param useProxy4YaCy specifies if the remote proxy should be used for the yacy core protocol
     * @param useProxy4SSL specifies if the remote proxy should be used for ssl
     * 
     * @throws AxisFault if authentication failed
     */
    public void setRemoteProxy(
    		Boolean enableRemoteProxy,
    		String proxyHost,
    		Integer proxyPort,
    		String proxyUserName,
    		String proxyPwd,
    		String noProxyList,
    		Boolean useProxy4YaCy,
    		Boolean useProxy4SSL    		
    ) throws AxisFault {
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);       	
        
        if (proxyHost != null)
        	this.switchboard.setConfig(REMOTE_PROXY_HOST, proxyHost);
        
        if (proxyPort != null)
        	this.switchboard.setConfig(REMOTE_PROXY_PORT, proxyPort.toString());
        
        if (proxyUserName != null)
        	this.switchboard.setConfig(REMOTE_PROXY_USER, proxyUserName);        
        
        if (proxyPwd != null)
        	this.switchboard.setConfig(REMOTE_PROXY_PWD, proxyPwd);         
        
        if (noProxyList != null)
        	this.switchboard.setConfig(REMOTE_PROXY_NO_PROXY, noProxyList);         
        
        if (useProxy4YaCy != null)
        	this.switchboard.setConfig(REMOTE_PROXY_USE4YACY, useProxy4YaCy.toString());         
        
        if (useProxy4SSL != null)
        	this.switchboard.setConfig(REMOTE_PROXY_USE4SSL, useProxy4SSL.toString());           
        
        // enable remote proxy usage
        if (enableRemoteProxy != null) this.enableRemoteProxy(enableRemoteProxy.booleanValue());
    }
    
    /**
     * Shutdown this peer
     * @throws AxisFault if authentication failed
     */
    public void shutdownPeer() throws AxisFault {
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);        
        
        this.switchboard.setConfig(RESTART, "false");
        
        // Terminate the peer in 3 seconds (this gives us enough time to finish the request
        ((plasmaSwitchboard)this.switchboard).terminate(3000);        
    }
    
    /**
     * This function can be used to configure Remote Triggered Crawling for this peer.
     * 
     * @param enableRemoteTriggeredCrawls to enable remote triggered crawling
     * @param maximumAllowedPPM to configure the maximum allowed pages per minute that should be crawled. 
     *                          Set this to <code>0</code> for unlimited crawling.
     * 
     * @throws AxisFault
     */
    public void setDistributedCrawling(
    		Boolean enableRemoteTriggeredCrawls,
    		Integer maximumAllowedPPM
    ) throws AxisFault {
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);     
        
        // if the ppm was set, change it
        if (maximumAllowedPPM != null) {
        	long newBusySleep;
        	
        	// calculate the new sleep time for the remote triggered crawl thread
        	if (maximumAllowedPPM.intValue() < 1) {
        		// unlimited crawling
        		newBusySleep = 100;
        	} else {
        		// limited crawling
                newBusySleep = 60000 / maximumAllowedPPM.intValue();
                if (newBusySleep < 100) newBusySleep = 100;        		
        	}
        	
        	// get the server thread
            serverThread rct = this.switchboard.getThread(_62_REMOTETRIGGEREDCRAWL);
            
            // set the new sleep time
            if (rct != null) rct.setBusySleep(newBusySleep);
            
            // store it
            this.switchboard.setConfig(_62_REMOTETRIGGEREDCRAWL_BUSYSLEEP, Long.toString(newBusySleep));        	        	
        }
        
        // if set enable/disable remote triggered crawls
        if (enableRemoteTriggeredCrawls != null) {
        	this.switchboard.setConfig(CRAWL_RESPONSE, enableRemoteTriggeredCrawls.toString());
        }
    }
    
    public void setTransferProperties(
    		Boolean indexDistribution,
    		Boolean indexDistributeWhileCrawling,
    		Boolean indexReceive,
    		Boolean indexReceiveBlockBlacklist
    ) throws AxisFault {
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);       
        
        // index Distribution on/off
        if (indexDistribution != null) {
        	this.switchboard.setConfig(ALLOW_DISTRIBUTE_INDEX, indexDistribution.toString());
        }
        
        // Index Distribution while crawling
        if (indexDistributeWhileCrawling != null) {
        	this.switchboard.setConfig(ALLOW_DISTRIBUTE_INDEX_WHILE_CRAWLING, indexDistributeWhileCrawling.toString());
        }     
        
        // Index Receive
        if (indexReceive != null) {
        	this.switchboard.setConfig(ALLOW_RECEIVE_INDEX, indexReceive.toString());
        }          
        
        // block URLs received by DHT by blocklist
        if (indexReceiveBlockBlacklist != null) {
        	this.switchboard.setConfig(INDEX_RECEIVE_BLOCK_BLACKLIST, indexReceiveBlockBlacklist.toString());
        }             
    }
    
    public Document getTransferProperties() throws AxisFault, ParserConfigurationException {    	
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);         	
    	
        // creating XML document
        Element xmlElement = null;
    	Document xmlDoc = createNewXMLDocument("transferProperties");
    	Element xmlRoot = xmlDoc.getDocumentElement();
    	    	
    	xmlElement = xmlDoc.createElement(ALLOW_DISTRIBUTE_INDEX);
    	xmlElement.appendChild(xmlDoc.createTextNode(Boolean.toString(this.switchboard.getConfigBool(ALLOW_DISTRIBUTE_INDEX,true))));
    	xmlRoot.appendChild(xmlElement);
    	
    	xmlElement = xmlDoc.createElement(ALLOW_DISTRIBUTE_INDEX_WHILE_CRAWLING);
    	xmlElement.appendChild(xmlDoc.createTextNode(Boolean.toString(this.switchboard.getConfigBool(ALLOW_DISTRIBUTE_INDEX_WHILE_CRAWLING,true))));
    	xmlRoot.appendChild(xmlElement);    	
    	
    	xmlElement = xmlDoc.createElement(ALLOW_RECEIVE_INDEX);
    	xmlElement.appendChild(xmlDoc.createTextNode(Boolean.toString(this.switchboard.getConfigBool(ALLOW_RECEIVE_INDEX,true))));
    	xmlRoot.appendChild(xmlElement);    	
    	
    	xmlElement = xmlDoc.createElement(INDEX_RECEIVE_BLOCK_BLACKLIST);
    	xmlElement.appendChild(xmlDoc.createTextNode(Boolean.toString(this.switchboard.getConfigBool(INDEX_RECEIVE_BLOCK_BLACKLIST,true))));
    	xmlRoot.appendChild(xmlElement);       	
    	
    	return xmlDoc;
    }
    
    /**
     * Function to configure the message forwarding settings of a peer.
     * @see <a href="http://localhost:8080/Settings_p.html?page=messageForwarding">Peer Configuration - Message Forwarding</a>
     * 
     * @param enableForwarding specifies if forwarding should be enabled
     * @param forwardingCommand the forwarding command to use. e.g. <code>/usr/sbin/sendmail</code>
     * @param forwardingTo the delivery destination. e.g. <code>root@localhost</code>
     * 
     * @throws AxisFault if authentication failed
     */
    public void setMessageForwarding(
    		Boolean enableForwarding,
    		String forwardingCommand,
    		String forwardingTo
    )  throws AxisFault {
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);       
        
        // index Distribution on/off
        if (enableForwarding != null) {
        	this.switchboard.setConfig(MSG_FORWARDING_ENABLED, enableForwarding.toString());
        }    
        
        if (forwardingCommand != null) {
        	this.switchboard.setConfig(MSG_FORWARDING_CMD, forwardingCommand);
        }    
        
        if (forwardingTo != null) {
        	this.switchboard.setConfig(MSG_FORWARDING_TO, forwardingTo);
        }           
    }
    
    /**
     * Function to query the current message forwarding configuration of a peer.
     * @see <a href="http://localhost:8080/Settings_p.html?page=messageForwarding">Peer Configuration - Message Forwarding</a>
     * 
     * @return a XML document of the following format
     * <pre>
     * &lt;?xml version="1.0" encoding="UTF-8"?&gt;
     * &lt;msgForwarding&gt;
     *   &lt;msgForwardingEnabled&gt;false&lt;/msgForwardingEnabled&gt;
     *   &lt;msgForwardingCmd&gt;/usr/sbin/sendmail&lt;/msgForwardingCmd&gt;
     *   &lt;msgForwardingTo&gt;root@localhost&lt;/msgForwardingTo&gt;
     * &lt;/msgForwarding&gt;
     * </pre>
     * 
     * @throws AxisFault if authentication failed
     * @throws ParserConfigurationException on XML parser errors
     */
    public Document getMessageForwarding() throws AxisFault, ParserConfigurationException {
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);     
        
        // creating XML document
        Element xmlElement = null;
    	Document xmlDoc = createNewXMLDocument(MSG_FORWARDING);
    	Element xmlRoot = xmlDoc.getDocumentElement();        
    	
    	xmlElement = xmlDoc.createElement(MSG_FORWARDING_ENABLED);
    	xmlElement.appendChild(xmlDoc.createTextNode(Boolean.toString(this.switchboard.getConfigBool(MSG_FORWARDING_ENABLED,false))));
    	xmlRoot.appendChild(xmlElement);    	
    	
    	xmlElement = xmlDoc.createElement(MSG_FORWARDING_CMD);
    	xmlElement.appendChild(xmlDoc.createTextNode(this.switchboard.getConfig(MSG_FORWARDING_CMD,"")));
    	xmlRoot.appendChild(xmlElement);       	
    	
    	xmlElement = xmlDoc.createElement(MSG_FORWARDING_TO);
    	xmlElement.appendChild(xmlDoc.createTextNode(this.switchboard.getConfig(MSG_FORWARDING_TO,"")));
    	xmlRoot.appendChild(xmlElement);       	
    	
    	return xmlDoc;
    }
    
    /**
     * Function to query the last peer logging records. Please note that the maximum amount of records
     * depends on the peer GuiHandler logging configuration.<br> 
     * Per default a maximum of 400 entries are kept in memory.
     * 
     * See: DATA/LOG/yacy.logging:
     * <pre>de.anomic.server.logging.GuiHandler.size = 400</pre>
     * 
     * @param sequenceNumber all logging records with a squence number greater than this parameter are fetched. 
     * 
     * @return a XML document of the following format
     * <pre>&lt;?xml version="1.0" encoding="UTF-8"?&gt;
     * &lt;log&gt;
     * &lt;record&gt;
     *   &lt;date&gt;2006-11-03T15:35:09&lt;/date&gt;
     *   &lt;millis&gt;1162564509850&lt;/millis&gt;
     *   &lt;sequence&gt;15&lt;/sequence&gt;
     *   &lt;logger&gt;KELONDRO&lt;/logger&gt;
     *   &lt;level&gt;FINE&lt;/level&gt;
     *   &lt;thread&gt;10&lt;/thread&gt;
     *   &lt;message&gt;KELONDRO DEBUG /home/yacy/DATA/PLASMADB/ACLUSTER/indexAssortment009.db: preloaded 1 records into cache&lt;/message&gt;
     * &lt;/record&gt;
     * [...]
     * &lt;/log&gt;
     * </pre>
     * This is the default format of the java logging {@link XMLFormatter} class. 
     * See: <a href="http://java.sun.com/j2se/1.4.2/docs/guide/util/logging/overview.html#2.4">Sample XML Output</a>
     * 
     * @throws AxisFault if authentication failed
     * @throws ParserConfigurationException on XML parser errors
    **/    
    public Document getServerLog(Long sequenceNumber) throws Exception {
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);     
        
        Handler logHandler = null;
        LogRecord[] log = null;
        
        // getting the root handler
        Logger logger = Logger.getLogger("");
        
        // take a look for the GuiHandler
        Handler[] handlers = logger.getHandlers();
        for (int i=0; i<handlers.length; i++) {
            if (handlers[i] instanceof GuiHandler) {
            	// getting the log records
            	logHandler = handlers[i];
                log = ((GuiHandler)logHandler).getLogArray(sequenceNumber);
                break;
            }
        }
        
        // if the logging handler was not found report the error
        if (logHandler == null) throw new AxisFault("GuiHandler not found");
    	
        StringBuffer buffer = new StringBuffer();
        
        // format them into xml
    	XMLFormatter formatter = new XMLFormatter();
    	
    	// adding header and removing DTD definition
    	buffer.append(formatter.getHead(logHandler).replaceAll("<!DOCTYPE.*>", ""));
    	
    	// format the logging entries
    	for (int i=0; i < log.length; i++) {
    		buffer.append(formatter.format(log[i]));
    	}    	
    	
    	// adding tailer
    	buffer.append(formatter.getTail(logHandler));
    	
    	// convert into dom
    	return convertContentToXML(buffer.toString());
    }
    
    /**
     * Function to configure the profile of this peer.
     * If a input parameters is <code>null</code> the old value will not be overwritten.
     *  
     * @param name the name of the peer owner
     * @param nickname peer owner nick name
     * @param homepage 
     * @param email
     * @param icq
     * @param jabber
     * @param yahoo
     * @param msn
     * @param comments
     * 
     * @throws AxisFault if authentication failed
     */
    public void setLocalPeerProfile(
    		String name,
    		String nickname,
    		String homepage,
    		String email,
    		String icq,
    		String jabber,
    		String yahoo,
    		String msn,
    		String comments
    ) throws AxisFault {
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);     
        
        // load peer properties
        final Properties profile = new Properties();
        FileInputStream fileIn = null;
        try {
            fileIn = new FileInputStream(new File("DATA/SETTINGS/profile.txt"));
            profile.load(fileIn);
        } catch(IOException e) {
        	throw new AxisFault("Unable to load the peer profile");
        } finally {
            if (fileIn != null) try { fileIn.close(); } catch (Exception e) {/* */}
        }
        
        // set all properties
        if (name != null) profile.setProperty(PEERPROFILE_NAME,name);
        if (nickname != null) profile.setProperty(PEERPROFILE_NICKNAME,nickname);
        if (homepage != null) profile.setProperty(PEERPROFILE_HOMEPAGE,homepage);
        if (email != null) profile.setProperty(PEERPROFILE_EMAIL,email);
        if (icq != null) profile.setProperty(PEERPROFILE_ICQ,icq);
        if (jabber != null) profile.setProperty(PEERPROFILE_JABBER,jabber);
        if (yahoo != null) profile.setProperty(PEERPROFILE_YAHOO,yahoo);
        if (msn != null) profile.setProperty(PEERPROFILE_MSN,msn);
        if (comments != null) profile.setProperty(PEERPROFILE_COMMENT,comments);
        
        // store it
        FileOutputStream fileOut = null;
        try {
            fileOut = new FileOutputStream(new File("DATA/SETTINGS/profile.txt"));
            profile.store(fileOut , null );

            // generate a news message
            Properties news = profile;
            news.remove(PEERPROFILE_COMMENT);
            yacyCore.newsPool.publishMyNews(new yacyNewsRecord(yacyNewsPool.CATEGORY_PROFILE_UPDATE, news));
        } catch(IOException e) {
        	throw new AxisFault("Unable to write profile data to file");
        } finally {
            if (fileOut != null) try { fileOut.close(); } catch (Exception e) {/* */}
        }        
    }
    
    /**
     * Returns the peer profile of this peer
     * @return a xml document in the same format as returned by function {@link #getPeerProfile(String)}
     * @throws Exception 
     */
    public Document getLocalPeerProfile() throws Exception {
    	return this.getPeerProfile("localhash");
    }    
    
    /**
     * Function to query the profile of a remote peer
     * @param peerhash the peer hash
     * @return a xml document in the following format
     * <pre>
     * &lt;?xml version="1.0" encoding="UTF-8"?&gt;
     * &lt;profile&gt;
     * 	&lt;status code="3"&gt;Peer profile successfully fetched&lt;/status&gt;
     * 	&lt;name&gt;&lt;![CDATA[myName]]&gt;&lt;/name&gt;
     * 	&lt;nickname&gt;&lt;![CDATA[myNickName]]&gt;&lt;/nickname&gt;
     * 	&lt;homepage&gt;&lt;![CDATA[http://myhompage.de]]&gt;&lt;/homepage&gt;
     * 	&lt;email/&gt;
     * 	&lt;icq/&gt;
     * 	&lt;jabber/&gt;
     * 	&lt;yahoo/&gt;
     * 	&lt;msn/&gt;
     * 	&lt;comment&gt;&lt;![CDATA[Comments]]&gt;&lt;/comment&gt;
     * &lt;/profile&gt;
     * </pre>
     * @throws Exception if authentication failed
     */
    public Document getPeerProfile(String peerhash) throws Exception {
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);          	
        
        // generating the template containing the network status information
        serverObjects args = new serverObjects();
        args.put(PEER_HASH,peerhash);
        
    	// invoke servlet
    	serverObjects tp = this.serverContext.invokeServlet(TEMPLATE_PROFILE_XML,args, this.requestHeader);
        
    	// query status
    	if (tp.containsKey(PEER_PROFILE_FETCH_SUCCESS)) {
    		String success = tp.get(PEER_PROFILE_FETCH_SUCCESS,"3");
    		if (success.equals("0")) throw new AxisFault("Invalid parameters passed to servlet.");
    		else if (success.equals("1")) throw new AxisFault("The requested peer is unknown or can not be accessed.");
    		else if (success.equals("2")) throw new AxisFault("The requested peer is offline");
    	} else {
    		throw new AxisFault("Unkown error. Unable to determine profile fetching status.");
    	}
    	
    	
    	// generate output
    	byte[] result = this.serverContext.buildServletOutput(TEMPLATE_PROFILE_XML, tp);        
        
        // sending back the result to the client
        return this.convertContentToXML(result);     
    }
    
    public void doGarbageCollection() throws Exception {
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);  
        
        // execute garbage collection
        System.gc();
    } 
}

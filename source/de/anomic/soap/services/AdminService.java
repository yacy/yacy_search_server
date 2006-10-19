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

import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.axis.AxisFault;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.anomic.http.httpRemoteProxyConfig;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.soap.AbstractService;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class AdminService extends AbstractService {

    private static final String TEMPLATE_CONFIG_XML = "xml/config_p.xml";   
    private static final String TEMPLATE_VERSION_XML = "xml/version.xml";
    
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
        extractMessageContext(true);  
    	
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
        extractMessageContext(true);
        
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
        extractMessageContext(true);  
        
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
        extractMessageContext(true);      	
    	
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
        extractMessageContext(true);          	
        
        // generating the template containing the network status information
        byte[] result = writeTemplate(TEMPLATE_CONFIG_XML, new serverObjects());
        
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
        extractMessageContext(true);          	
        
        // generating the template containing the network status information
        byte[] result = writeTemplate(TEMPLATE_VERSION_XML, new serverObjects());
        
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
        extractMessageContext(true);                    
        
        // get the previous name
        String prevName = this.switchboard.getConfig("peerName", "");
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
        this.switchboard.setConfig("peerName", newName);
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
        extractMessageContext(true);        
        
        // get the old value
        int oldPort = (int) this.switchboard.getConfigLong("port", 8080);
        if (oldPort == newPort) return;
        
        // getting the server thread
        serverCore theServerCore = (serverCore) this.switchboard.getThread("10_httpd");
        
        // store the new value
        this.switchboard.setConfig("port", newPort);
        
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
        extractMessageContext(true);   
        
        // check for errors
        String proxyHost = this.switchboard.getConfig("remoteProxyHost", "");
        if (proxyHost.length() == 0) throw new AxisFault("Remote proxy hostname is not configured");
        
        String proxyPort = this.switchboard.getConfig("remoteProxyPort", "");
        if (proxyPort.length() == 0) throw new AxisFault("Remote proxy port is not configured");
        
        // store the new state
        plasmaSwitchboard sb = (plasmaSwitchboard) this.switchboard;
        sb.setConfig("remoteProxyUse",Boolean.toString(enableProxy));
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
        extractMessageContext(true);       	
        
        if (proxyHost != null)
        	this.switchboard.setConfig("remoteProxyHost", proxyHost);
        
        if (proxyPort != null)
        	this.switchboard.setConfig("remoteProxyPort", proxyPort.toString());
        
        if (proxyUserName != null)
        	this.switchboard.setConfig("remoteProxyUser", proxyUserName);        
        
        if (proxyPwd != null)
        	this.switchboard.setConfig("remoteProxyPwd", proxyPwd);         
        
        if (noProxyList != null)
        	this.switchboard.setConfig("remoteProxyNoProxy", noProxyList);         
        
        if (useProxy4YaCy != null)
        	this.switchboard.setConfig("remoteProxyUse4Yacy", useProxy4YaCy.toString());         
        
        if (useProxy4SSL != null)
        	this.switchboard.setConfig("remoteProxyUse4SSL", useProxy4SSL.toString());           
        
        // enable remote proxy usage
        if (enableRemoteProxy != null) this.enableRemoteProxy(enableRemoteProxy.booleanValue());
    }
    
    /**
     * Shutdown this peer
     * @throws AxisFault if authentication failed
     */
    public void shutdownPeer() throws AxisFault {
        // extracting the message context
        extractMessageContext(true);        
        
        this.switchboard.setConfig("restart", "false");
        
        // Terminate the peer in 3 seconds (this gives us enough time to finish the request
        ((plasmaSwitchboard)this.switchboard).terminate(3000);        
    }
    
    public void setTransferProperties(
    		Boolean indexDistribution,
    		Boolean indexDistributeWhileCrawling,
    		Boolean indexReceive,
    		Boolean indexReceiveBlockBlacklist
    ) throws AxisFault {
        // extracting the message context
        extractMessageContext(true);       
        
        // index Distribution on/off
        if (indexDistribution != null) {
        	this.switchboard.setConfig("allowDistributeIndex", indexDistribution.toString());
        }
        
        // Index Distribution while crawling
        if (indexDistributeWhileCrawling != null) {
        	this.switchboard.setConfig("allowDistributeIndexWhileCrawling", indexDistributeWhileCrawling.toString());
        }     
        
        // Index Receive
        if (indexReceive != null) {
        	this.switchboard.setConfig("allowReceiveIndex", indexReceive.toString());
        }          
        
        // block URLs received by DHT by blocklist
        if (indexReceiveBlockBlacklist != null) {
        	this.switchboard.setConfig("indexReceiveBlockBlacklist", indexReceiveBlockBlacklist.toString());
        }             
    }
    
    public Document getTransferProperties() throws AxisFault, ParserConfigurationException {    	
        // extracting the message context
        extractMessageContext(true);         	
    	
        // creating XML document
        Element xmlElement = null;
    	Document xmlDoc = createNewXMLDocument("transferProperties");
    	Element xmlRoot = xmlDoc.getDocumentElement();
    	    	
    	xmlElement = xmlDoc.createElement("allowDistributeIndex");
    	xmlElement.appendChild(xmlDoc.createTextNode(Boolean.toString(this.switchboard.getConfigBool("allowDistributeIndex",true))));
    	xmlRoot.appendChild(xmlElement);
    	
    	xmlElement = xmlDoc.createElement("allowDistributeIndexWhileCrawling");
    	xmlElement.appendChild(xmlDoc.createTextNode(Boolean.toString(this.switchboard.getConfigBool("allowDistributeIndexWhileCrawling",true))));
    	xmlRoot.appendChild(xmlElement);    	
    	
    	xmlElement = xmlDoc.createElement("allowReceiveIndex");
    	xmlElement.appendChild(xmlDoc.createTextNode(Boolean.toString(this.switchboard.getConfigBool("allowReceiveIndex",true))));
    	xmlRoot.appendChild(xmlElement);    	
    	
    	xmlElement = xmlDoc.createElement("indexReceiveBlockBlacklist");
    	xmlElement.appendChild(xmlDoc.createTextNode(Boolean.toString(this.switchboard.getConfigBool("indexReceiveBlockBlacklist",true))));
    	xmlRoot.appendChild(xmlElement);       	
    	
    	return xmlDoc;
    }
}

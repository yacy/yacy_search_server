// ConfigBasic_p.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
// Created 28.02.2006
//
// $LastChangedDate: 2005-09-13 00:20:37 +0200 (Di, 13 Sep 2005) $
// $LastChangedRevision: 715 $
// $LastChangedBy: borg-0300 $
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

// You must compile this file with
// javac -classpath .:../classes ConfigBasic_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.lang.reflect.Method;
import java.lang.Integer;
import java.util.regex.Pattern;

import de.anomic.data.translator;
import de.anomic.http.httpHeader;
import de.anomic.http.httpd;
import de.anomic.http.httpdFileHandler;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCodings;
import de.anomic.server.serverCore;
import de.anomic.server.serverInstantThread;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.portForwarding.serverPortForwarding;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class ConfigBasic {
    private static final int NEXTSTEP_FINISHED = 0;
    private static final int NEXTSTEP_PWD = 1;
    private static final int NEXTSTEP_PEERNAME = 2;
    private static final int NEXTSTEP_PEERPORT = 3;
    private static final int NEXTSTEP_RECONNECT = 4;
    
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        
        // return variable that accumulates replacements
        ConfigBasic config = new ConfigBasic();
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        String langPath = new File(env.getRootPath(), env.getConfig("langPath", "DATA/LOCALE")).toString();
        String lang = env.getConfig("htLocaleSelection", "default");
        
        int authentication = sb.adminAuthenticated(header);
        if (authentication < 2) {
            // must authenticate
            prop.put("AUTHENTICATE", "admin log-in"); 
            return prop;
        }
        
        // reconfigure port forwarding
        if ((post != null)) config.reinitPortForwarding(post, env);
        
        // starting a peer ping
        boolean doPeerPing = false;
        if ((yacyCore.seedDB.mySeed.isVirgin()) || (yacyCore.seedDB.mySeed.isJunior())) {
            serverInstantThread.oneTimeJob(sb.yc, "peerPing", null, 0);
            doPeerPing = true;
        } 
        
        // scan for Upnp routers
        long begin = System.currentTimeMillis();
        boolean upnpRouterFound = config.findUPnPRouter(3000);
        long end = System.currentTimeMillis();
        
        // if the upnp router scan has taken less than 3 sec, we need to wait
        // a little bit for success of peer ping
        if ((doPeerPing) && ((end - begin) < 3000 )) {
            try {Thread.sleep(3000-(end - begin));} catch (InterruptedException e) {/* */} 
        }
        
        // if a UPnP router is available
        String currentForwarder = env.getConfig("portForwarding.Type", "none");
        boolean forwardingEnabled = env.getConfigBool("portForwarding.Enabled",false);
        boolean otherForwarderEnabled = serverCore.portForwardingEnabled && serverCore.portForwarding != null && !currentForwarder.equalsIgnoreCase("upnp");
        if (otherForwarderEnabled) {
            prop.put("upnp",0); 
        } else {
            prop.put("upnp", upnpRouterFound ? 1 : 0);
        }
        
        // if UPnp is already enabled
        prop.put("upnp_enabled", currentForwarder.equalsIgnoreCase("upnp") && forwardingEnabled ? 1 : 0);
        
        // language settings
        if ((post != null) && (!(post.get("language", "default").equals(lang)))) {
            translator.changeLang(env, langPath, post.get("language", "default") + ".lng");
        }
        
        // password settings
        String user   = (post == null) ? "" : (String) post.get("adminuser", "");
        String pw1    = (post == null) ? "" : (String) post.get("adminpw1", "");
        String pw2    = (post == null) ? "" : (String) post.get("adminpw2", "");
        
        // peer name settings
        String peerName = (post == null) ? env.getConfig("peerName","") : (String) post.get("peername", "");
        
        // port settings
        String port = env.getConfig("port", "8080"); //this allows a low port, but it will only get one, if the user edits the config himself.
		if(post!=null && Integer.parseInt((String)post.get("port"))>1023){
			port = (String)post.get("port", "8080");
		}
        
        // admin password
        if ((user.length() > 0) && (pw1.length() > 3) && (pw1.equals(pw2))) {
            // check passed. set account:
            env.setConfig(httpd.ADMIN_ACCOUNT_B64MD5, serverCodings.encodeMD5Hex(kelondroBase64Order.standardCoder.encodeString(user + ":" + pw1)));
            env.setConfig("adminAccount", "");
            // authenticate immediately
            //prop.put("AUTHENTICATE", "admin log-in"); 
            //return prop;
        }

        // check if peer name already exists
        yacySeed oldSeed = yacyCore.seedDB.lookupByName(peerName);
        if ((oldSeed == null) && (!(env.getConfig("peerName", "").equals(peerName)))) {
            // the name is new
        	boolean nameOK = Pattern.compile("[A-Za-z0-9\\-_]{3,80}").matcher(peerName).matches();
            if (nameOK) env.setConfig("peerName", peerName);
        }
 
        // check port
        boolean reconnect = false;
        if (!env.getConfig("port", port).equals(port)) {
            // validate port
            serverCore theServerCore = (serverCore) env.getThread("10_httpd");
            env.setConfig("port", port);
            
            // redirect the browser to the new port
            reconnect = true;
            
            String host = null;
            if (header.containsKey(httpHeader.HOST)) {
                host = (String)header.get(httpHeader.HOST);
                int idx = host.indexOf(":");
                if (idx != -1) host = host.substring(0,idx);
            } else {
                host = serverCore.publicLocalIP().getHostAddress();
            }
            
            prop.put("reconnect", 1);
            prop.put("reconnect_host", host);
            prop.put("nextStep_host", host);
            prop.put("reconnect_port", port);
            prop.put("nextStep_port", port);            
            prop.put("reconnect_sslSupport", theServerCore.withSSL() ? 1:0);
            prop.put("nextStep_sslSupport", theServerCore.withSSL() ? 1:0);
            
            // force reconnection in 7 seconds
            theServerCore.reconnect(7000);
        } else {
            prop.put("reconnect", 0);
        }
        
        // check if values are proper
        boolean properPW = (env.getConfig("adminAccount", "").length() == 0) && (env.getConfig(httpd.ADMIN_ACCOUNT_B64MD5, "").length() > 0);
        boolean properName = (env.getConfig("peerName","").length() >= 3) && (!(yacySeed.isDefaultPeerName(env.getConfig("peerName",""))));
        boolean properPort = (yacyCore.seedDB.mySeed.isSenior()) || (yacyCore.seedDB.mySeed.isPrincipal());
        
        if ((properPW) && (env.getConfig("defaultFiles", "").startsWith("ConfigBasic.html,"))) {
        	    env.setConfig("defaultFiles", env.getConfig("defaultFiles", "").substring(17));
            httpdFileHandler.initDefaultPath();
        }
        
        prop.put("statusName", (properName) ? 1 : 0);
        prop.put("statusPassword", (properPW) ? 1 : 0);
        prop.put("statusPort", (properPort) ? 1 : 0);
        if (reconnect) {
            prop.put("nextStep", NEXTSTEP_RECONNECT);
        } else if (!properPW) {
            prop.put("nextStep", NEXTSTEP_PWD);
        } else if (!properName) {
            prop.put("nextStep", NEXTSTEP_PEERNAME);
        } else if (!properPort) {
            prop.put("nextStep", NEXTSTEP_PEERPORT);
        } else {
            prop.put("nextStep", NEXTSTEP_FINISHED);
        }
        
        // set default values       
        prop.put("defaultName", env.getConfig("peerName", ""));
        prop.put("defaultUser", "admin");
        prop.put("defaultPort", env.getConfig("port", "8080"));
        lang = env.getConfig("htLocaleSelection", "default"); // re-assign lang, may have changed
        if (lang.equals("default")) {
            prop.put("langDeutsch", 0);
            prop.put("langEnglish", 1);
        } else if (lang.equals("de")) {
            prop.put("langDeutsch", 1);
            prop.put("langEnglish", 0);
        } else {
            prop.put("langDeutsch", 0);
            prop.put("langEnglish", 0);
        }
        return prop;
    }
    
    private boolean findUPnPRouter(int timeout) {
        
        // determine if the upnp port forwarding class is available and load it dynamically        
        Object[] UpnpForwarder = this.getUpnpForwarderClasses();
        serverPortForwarding upnp = (serverPortForwarding) UpnpForwarder[0];
        Method scanForRouter = (Method) UpnpForwarder[1];                
        if ((upnp == null) || (scanForRouter == null)) return false; 
        
        // trying to find a upnp router
        try {
            Object result = scanForRouter.invoke(upnp, new Object[]{new Integer(timeout)});
            if ((result != null)&&(result instanceof Boolean)) {
                return ((Boolean)result).booleanValue();                        
            }
        } catch (Exception e) {/* ignore this error */
        } catch (Error e)     {/* ignore this error */}
        return false;
    }
    
    private Object[] getUpnpForwarderClasses() {
        serverPortForwarding upnp = null;
        Method scanForRouter = null;
        
        try {
            
            // trying to load the upnp forwarder class
            Class forwarderClass = Class.forName("de.anomic.server.portForwarding.upnp.serverPortForwardingUpnp");
            // create a new instance 
            upnp = (serverPortForwarding) forwarderClass.newInstance();
            // trying to get the proper method for router scanning
            scanForRouter = upnp.getClass().getMethod("routerAvailable", new Class[] {int.class});
            
        } catch (Exception e) {/* ignore this error */            
        } catch (Error e)     {/* ignore this error */}   

        return new Object[]{upnp,scanForRouter};
    }
    
    private void reinitPortForwarding(serverObjects post, serverSwitch env) {
        if ((post != null)) {
            try {                
                boolean reinitPortForwarding = false;
                
                if (post.containsKey("enableUpnp")) {
                    // upnp should be enabled
                    env.setConfig("portForwarding.Enabled","true");
                    env.setConfig("portForwarding.Type", "upnp");
                    reinitPortForwarding = true;                      
                } else {
                    String currentForwarder = env.getConfig("portForwarding.Type", "none");
                    boolean otherForwarderEnabled = serverCore.portForwardingEnabled && serverCore.portForwarding != null && !currentForwarder.equalsIgnoreCase("upnp");
                    
                    // if no other forwarder is running we deactivate forwarding
                    // and try to stop an eventually running upnp forwarder
                    if (!otherForwarderEnabled) {
                        env.setConfig("portForwarding.Enabled","false");
                        env.setConfig("portForwarding.Type", "none");
                        reinitPortForwarding = true;
                    }
                }           
                
                if (reinitPortForwarding) {
                    if ((serverCore.portForwardingEnabled) && (serverCore.portForwarding != null)) {
                        // trying to shutdown the current port forwarding channel
                        serverCore.portForwarding.disconnect();                
                    }              
                    
                    // trying to reinitialize the port forwarding
                    serverCore httpd = (serverCore) env.getThread("10_httpd");
                    httpd.initPortForwarding();                      
                }
                
            } catch (Exception e) { /* */ }
        }          
    }
    
}

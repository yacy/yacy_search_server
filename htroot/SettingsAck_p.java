// SettingsAck_p.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
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
// javac -classpath .:../Classes SettingsAck_p.java
// if the shell's current path is HTROOT

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.pool.impl.GenericObjectPool;

import de.anomic.http.httpHeader;
import de.anomic.http.httpRemoteProxyConfig;
import de.anomic.http.httpd;
import de.anomic.http.httpdProxyHandler;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCodings;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.serverThread;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedUploader;

public class SettingsAck_p {
    
    private static boolean nothingChanged;

    /*
    public static HashMap langMap(serverSwitch env) {
	String[] ms = env.getConfig("htLocaleLang", "").split(",");
	HashMap map = new HashMap();
	int p;
	for (int i = 0; i < ms.length; i++) {
	    p = ms[i].indexOf("/");
	    if (p > 0) map.put(ms[i].substring(0, p), ms[i].substring(p + 1));
	}
	return map;
    }
    */
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        serverObjects prop = new serverObjects();
        
        //if (post == null) System.out.println("POST: NULL"); else System.out.println("POST: " + post.toString());
        
        // set values
        String s;
        int pos;
        
        if (post == null) {
            prop.put("info", 1);//no information submitted
            return prop;
        }
        
        // admin password
        if (post.containsKey("adminaccount")) {
            // read and process data
            String user   = (String) post.get("adminuser");
            String pw1    = (String) post.get("adminpw1");
            String pw2    = (String) post.get("adminpw2");
            // do checks
            if ((user == null) || (pw1 == null) || (pw2 == null)) {
                prop.put("info", 1);//error with submitted information
                return prop;
            }
            if (user.length() == 0) {
                prop.put("info", 2);//username must be given
                return prop;
            }
            if (!(pw1.equals(pw2))) {
                prop.put("info", 3);//pw check failed
                return prop;
            }
            // check passed. set account:
            env.setConfig("adminAccountBase64MD5", serverCodings.standardCoder.encodeMD5Hex(serverCodings.standardCoder.encodeBase64String(user + ":" + pw1)));
            env.setConfig("adminAccount", "");
            prop.put("info", 5);//admin account changed
            prop.put("info_user", user);
            return prop;
        }
        
        
        // proxy password
        if (post.containsKey("proxyaccount")) {
            // set new port
            String port = (String) post.get("port");
            env.setConfig("port", port);
            prop.put("info_port", port);
            
            // read and process data
            String filter = (String) post.get("proxyfilter");
			String use_proxyAccounts="";
			if(post.containsKey("use_proxyaccounts")){
				//needed? or set to true by default?
	            use_proxyAccounts = (((String) post.get("use_proxyaccounts")).equals("on") ? "true" : "false" );
			}else{
				use_proxyAccounts = "false";
			}
            // do checks
            if ((filter == null) || (use_proxyAccounts == null)) {
                prop.put("info", 1);//error with submitted information
                return prop;
            }
            /*if (user.length() == 0) {
                prop.put("info", 2);//username must be given
                return prop;
            }*/
            /*if (!(pw1.equals(pw2))) {
                prop.put("info", 3);//pw check failed
                return prop;
            }*/
            if (filter.length() == 0) filter = "*";
            // check passed. set account:
            env.setConfig("proxyClient", filter);
            /*if (pw1.length() == 0) {
                // only ip filter setting without account
                env.setConfig("proxyAccountBase64MD5", "");
                env.setConfig("proxyAccount", "");
                prop.put("info", 6);//proxy account has changed(no pw)
                prop.put("info_filter", filter);
            } else {
                // also paccount setting
                env.setConfig("proxyAccountBase64MD5", serverCodings.standardCoder.encodeMD5Hex(serverCodings.standardCoder.encodeBase64String(user + ":" + pw1)));
                env.setConfig("proxyAccount", "");
                prop.put("info", 7);//proxy account has changed
                prop.put("info_user", user);
                prop.put("info_filter", filter);
            }*/
            env.setConfig("use_proxyAccounts", use_proxyAccounts);//"true" or "false"
			if (use_proxyAccounts.equals("false")){
                prop.put("info", 6);//proxy account has changed(no pw)
                prop.put("info_filter", filter);
			} else {
                prop.put("info", 7);//proxy account has changed
                //prop.put("info_user", user);
                prop.put("info_filter", filter);
			}
            return prop;
        }
        
        // http networking
        if (post.containsKey("httpNetworking")) {
            
            // set transparent proxy flag
            httpdProxyHandler.isTransparentProxy = post.containsKey("isTransparentProxy");
            env.setConfig("isTransparentProxy", httpdProxyHandler.isTransparentProxy ? "true" : "false");
            prop.put("info_isTransparentProxy", httpdProxyHandler.isTransparentProxy ? "on" : "off");            
            
            // setting the keep alive property
            httpd.keepAliveSupport = post.containsKey("connectionKeepAliveSupport");
            env.setConfig("connectionKeepAliveSupport", httpd.keepAliveSupport ? "true" : "false");
            prop.put("info_connectionKeepAliveSupport", httpd.keepAliveSupport ? "on" : "off"); 
            
            prop.put("info", 20);             
            return prop;
        }
        
        // port forwarding configuration
        if (post.containsKey("portForwarding")) {            
            env.setConfig("portForwardingEnabled", post.containsKey("portForwardingEnabled")?"true":"false");
            env.setConfig("portForwardingUseProxy",post.containsKey("portForwardingUseProxy")?"true":"false");
            env.setConfig("portForwardingPort",    (String)post.get("portForwardingPort"));
                        
            env.setConfig("portForwardingHost",    (String)post.get("portForwardingHost"));
            env.setConfig("portForwardingHostPort",(String)post.get("portForwardingHostPort"));
            env.setConfig("portForwardingHostUser",(String)post.get("portForwardingHostUser"));
            env.setConfig("portForwardingHostPwd", (String)post.get("portForwardingHostPwd"));
            
            // trying to reconnect the port forwarding channel
            try {
                serverCore httpd = (serverCore) env.getThread("10_httpd");
                if ((serverCore.portForwardingEnabled) && (serverCore.portForwarding != null)) {
                    // trying to shutdown the current port forwarding channel
                    serverCore.portForwarding.disconnect();                
                }            
                // trying to reinitialize the port forwarding
                httpd.initPortForwarding();
                
                // notifying publishSeed Thread
                serverThread peerPing = env.getThread("30_peerping");
                peerPing.notifyThread();
            } catch (Exception e) {
                prop.put("info", 23); 
                prop.put("info_errormsg",(e.getMessage() == null) ? "unknown" : e.getMessage().replaceAll("\n","<br>"));
                return prop;
            }
            
            prop.put("info", 22); 
            prop.put("info_portForwardingEnabled", post.containsKey("portForwardingEnabled")?"on":"off");  
            prop.put("info_portForwardingUseProxy",post.containsKey("portForwardingUseProxy")?"on":"off");
            prop.put("info_portForwardingPort",    (String)post.get("portForwardingPort"));
            prop.put("info_portForwardingHost",    (String)post.get("portForwardingHost"));
            prop.put("info_portForwardingHostPort",(String)post.get("portForwardingHostPort"));
            prop.put("info_portForwardingHostUser",(String)post.get("portForwardingHostUser"));
            prop.put("info_portForwardingHostPwd", (String)post.get("portForwardingHostPwd"));   
            return prop;
        }
        
        
        // server password
        if (post.containsKey("serveraccount")) {
            // read and process data
            String filter = (String) post.get("serverfilter");
            String user   = (String) post.get("serveruser");
            String pw1    = (String) post.get("serverpw1");
            String pw2    = (String) post.get("serverpw2");
            // do checks
            if (filter == null) {
                //if ((filter == null) || (user == null) || (pw1 == null) || (pw2 == null)) {
                prop.put("info", 1);//error with submitted information
                return prop;
            }
            if (user.length() == 0) {
                prop.put("info", 2);//username must be given
                return prop;
            }
            if (!(pw1.equals(pw2))) {
                prop.put("info", 3);//pw check failed
                return prop;
            }
            if (filter.length() == 0) filter = "*";
            // check passed. set account:
            env.setConfig("serverClient", filter);
            env.setConfig("serverAccountBase64MD5", serverCodings.standardCoder.encodeMD5Hex(serverCodings.standardCoder.encodeBase64String(user + ":" + pw1)));
            env.setConfig("serverAccount", "");
            
            prop.put("info", 8);//server access filter updated
            prop.put("info_user", user);
            prop.put("info_filter", filter);
            return prop;
        }
        
        if (post.containsKey("dispop")) {
            env.setConfig("browserPopUpTrigger", "false");
            prop.put("info", 9);//popup disabled
            return prop;
        }
        
        if (post.containsKey("enpop")) {
            env.setConfig("browserPopUpTrigger", "true");
            prop.put("info", 10);//popup enabled
            return prop;
        }
        
        if (post.containsKey("pmode")) {
            env.setConfig("onlineMode", "2");
            prop.put("info", 11);//permanent online mode
            yacyCore.setOnlineMode(2);
            yacyCore.triggerOnlineAction();
            return prop;
        }
        
        if (post.containsKey("emode")) {
            env.setConfig("onlineMode", "1");
            prop.put("info", 24);//event-based online mode
            yacyCore.setOnlineMode(1);
            return prop;
        }
        
        if (post.containsKey("cmode")) {
            env.setConfig("onlineMode", "0");
            prop.put("info", 25);//cache mode
            yacyCore.setOnlineMode(0);
            return prop;
        }
        
        if (post.containsKey("generalsettings")) {
            /*
            // set peer language
            String peerLang = (String) post.get("peerlang");
            if ((peerLang == null) || (peerLang.equals("en"))) peerLang = "default";
	    HashMap lm = langMap(env);
	    if (!(lm.containsKey(peerLang))) peerLang = "default";
	    env.setConfig("htLocaleSelection", peerLang);
	    prop.put("info_peerLang", (String) lm.get(peerLang));
            */
            
            // check if peer name already exists
            String peerName = (String) post.get("peername");
            String staticIP =  (String)post.get("staticIP");
            env.setConfig("staticIP", staticIP);
            if (staticIP.length() > 0) yacyCore.seedDB.mySeed.put(yacySeed.IP, staticIP);
            yacySeed oldSeed = yacyCore.seedDB.lookupByName(peerName);
            
            if ((oldSeed == null) || (env.getConfig("peerName","").equals(peerName))) {
                // the name is new
                boolean nameOK = (peerName.length() <= 80);
                for (int i = 0; i < peerName.length(); i++) {
                    if ("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_".indexOf(peerName.charAt(i)) < 0) nameOK = false;
                }
                if (!(nameOK)) {
                    // deny change
                    prop.put("info", 17);//peer name is wrong
                } else {
                    
                    // set values
                    env.setConfig("peerName", peerName);
                    prop.put("info", 12);//port or peername changed
                    prop.put("info_peerName", peerName);
                    prop.put("info_staticIP", staticIP);
                }
            } else {
                // deny change
                prop.put("info", 16);//peer name is already used by another peer
            }
            return prop;
        }
        
        if (post.containsKey("proxysettings")) {
            
            /* ====================================================================
             * Reading out the remote proxy settings 
             * ==================================================================== */
            boolean useRemoteProxy = post.containsKey("remoteProxyUse");
            boolean useRemoteProxy4Yacy = post.containsKey("remoteProxyUse4Yacy");
            boolean useRemoteProxy4SSL = post.containsKey("remoteProxyUse4SSL");
            
            String remoteProxyHost = post.get("remoteProxyHost", "");
            String remoteProxyPortStr = post.get("remoteProxyPort", "");
            int remoteProxyPort = 0;
            try {
                remoteProxyPort = Integer.parseInt(remoteProxyPortStr);
            } catch (NumberFormatException e) {
                remoteProxyPort = 3128;
            }
            
            String remoteProxyUser = post.get("remoteProxyUser", "");
            String remoteProxyPwd = post.get("remoteProxyPwd", "");
            
            String remoteProxyNoProxyStr = post.get("remoteProxyNoProxy", "");
            String[] remoteProxyNoProxyPatterns = remoteProxyNoProxyStr.split(",");
            
            /* ====================================================================
             * Storing settings into config file
             * ==================================================================== */
            env.setConfig("remoteProxyHost", remoteProxyHost);
            env.setConfig("remoteProxyPort", Integer.toString(remoteProxyPort));
            env.setConfig("remoteProxyUser", remoteProxyUser);
            env.setConfig("remoteProxyPwd", remoteProxyPwd);
            env.setConfig("remoteProxyNoProxy", remoteProxyNoProxyStr);
            env.setConfig("remoteProxyUse", (useRemoteProxy) ? "true" : "false");
            env.setConfig("remoteProxyUse4Yacy", (useRemoteProxy4Yacy) ? "true" : "false");
            env.setConfig("remoteProxyUse4SSL", (useRemoteProxy4SSL) ? "true" : "false");
            
            /* ====================================================================
             * Enabling settings
             * ==================================================================== */
            plasmaSwitchboard sb = (plasmaSwitchboard)env;
            sb.remoteProxyConfig = httpRemoteProxyConfig.init(sb);            
            
//            httpdProxyHandler.remoteProxyUse = post.get("remoteProxyUse", "").equals("on");
//            httpdProxyHandler.remoteProxyHost = post.get("remoteProxyHost", "");
//            try {
//                httpdProxyHandler.remoteProxyPort = Integer.parseInt((String) post.get("remoteProxyPort", ""));
//            } catch (NumberFormatException e) {
//                httpdProxyHandler.remoteProxyPort = 3128;
//            }
//            httpdProxyHandler.remoteProxyNoProxy = (String) post.get("remoteProxyNoProxy", "");
//            httpdProxyHandler.remoteProxyNoProxyPatterns = httpdProxyHandler.remoteProxyNoProxy.split(",");
//            env.setConfig("remoteProxyHost", httpdProxyHandler.remoteProxyHost);
//            env.setConfig("remoteProxyPort", Integer.toString(httpdProxyHandler.remoteProxyPort));
//            env.setConfig("remoteProxyNoProxy", httpdProxyHandler.remoteProxyNoProxy);
//            env.setConfig("remoteProxyUse", (httpdProxyHandler.remoteProxyUse) ? "true" : "false");
            
            
            prop.put("info", 15); // The remote-proxy setting has been changed
            return prop;
        }
        
        if (post.containsKey("seedUploadRetry")) {
            String error;
            if ((error = ((plasmaSwitchboard)env).yc.saveSeedList(env)) == null) {
                // trying to upload the seed-list file    
                prop.put("info", 13);
                prop.put("info_success",1);
            } else {
                prop.put("info",14);
                prop.put("info_errormsg",error.replaceAll("\n","<br>"));                
                env.setConfig("seedUploadMethod","none");                 
            }
            return prop;
        }
        
        if (post.containsKey("seedSettings")) {
            // getting the currently used uploading method
            String oldSeedUploadMethod = env.getConfig("seedUploadMethod","none");
            String newSeedUploadMethod = (String)post.get("seedUploadMethod");
            String oldSeedURLStr = env.getConfig("seedURL","");
            String newSeedURLStr = (String)post.get("seedURL");
            
            boolean seedUrlChanged = !oldSeedURLStr.equals(newSeedURLStr);
            boolean uploadMethodChanged = !oldSeedUploadMethod.equals(newSeedUploadMethod);
            if (uploadMethodChanged) {
                uploadMethodChanged = yacyCore.changeSeedUploadMethod(newSeedUploadMethod);
            }
            
            if (seedUrlChanged || uploadMethodChanged) {
                env.setConfig("seedUploadMethod", newSeedUploadMethod);
                env.setConfig("seedURL", newSeedURLStr);
                
                // try an upload
                String error;
                if ((error = ((plasmaSwitchboard)env).yc.saveSeedList(env)) == null) {
                    // we have successfully uploaded the seed-list file
                    prop.put("info_seedUploadMethod",newSeedUploadMethod);
                    prop.put("info_seedURL",newSeedURLStr);
                    prop.put("info_success",(newSeedUploadMethod.equalsIgnoreCase("none")?0:1));
                    prop.put("info", 19);
                } else {
                    prop.put("info",14);
                    prop.put("info_errormsg",error.replaceAll("\n","<br>"));                
                    env.setConfig("seedUploadMethod","none");
                }
                return prop;
            }
        }
        
        /* 
         * Loop through the available seed uploaders to see if the 
         * configuration of one of them has changed 
         */
        HashMap uploaders = yacyCore.getSeedUploadMethods();
        Iterator uploaderKeys = uploaders.keySet().iterator();
        while (uploaderKeys.hasNext()) {
            // getting the uploader module name
            String uploaderName = (String) uploaderKeys.next();
            
            
            // determining if the user has reconfigured the settings of this uploader
            if (post.containsKey("seed" + uploaderName + "Settings")) {
                nothingChanged = true;
                yacySeedUploader theUploader = yacyCore.getSeedUploader(uploaderName);
                String[] configOptions = theUploader.getConfigurationOptions();
                if (configOptions != null) {
                    for (int i=0; i<configOptions.length; i++) {
                        String newSettings = post.get(configOptions[i],"");
                        String oldSettings = env.getConfig(configOptions[i],"");
                        nothingChanged &= newSettings.equals(oldSettings); 
                        if (!nothingChanged) {
                            env.setConfig(configOptions[i],newSettings);
                        }
                    }
                }   
                if (!nothingChanged) {
                    // if the seed upload method is equal to the seed uploader whose settings
                    // were changed, we now try to upload the seed list with the new settings
                    if (env.getConfig("seedUploadMethod","none").equalsIgnoreCase(uploaderName)) {
                        String error;
                        if ((error = ((plasmaSwitchboard)env).yc.saveSeedList(env)) == null) {;
                            
                            // we have successfully uploaded the seed file
                            prop.put("info", 13);
                            prop.put("info_success",1);
                        } else {
                            // if uploading failed we print out an error message
                            prop.put("info", 14);
                            prop.put("info_errormsg",error.replaceAll("\n","<br>"));
                            env.setConfig("seedUploadMethod","none");                            
                        }                       
                    } else {
                        prop.put("info", 13);
                        prop.put("info_success",0);
                    }
                } else {
                    prop.put("info", 13);
                    prop.put("info_success",0);
                }
                return prop;
            }            
        }
        
        /*
         * Message forwarding configuration
         */
        if (post.containsKey("msgForwarding")) {
            env.setConfig("msgForwardingEnabled",post.containsKey("msgForwardingEnabled")?"true":"false");
            env.setConfig("msgForwardingCmd",(String) post.get("msgForwardingCmd"));
            env.setConfig("msgForwardingTo",(String) post.get("msgForwardingTo"));
            
            prop.put("info", 21);
            prop.put("info_msgForwardingEnabled", post.containsKey("msgForwardingEnabled") ? "on" : "off");
            prop.put("info_msgForwardingCmd", (String) post.get("msgForwardingCmd"));
            prop.put("info_msgForwardingTo", (String) post.get("msgForwardingTo"));
            
            return prop;
        }
        
        /*
         * Parser configuration
         */
        if (post.containsKey("parserSettings")) {   
            plasmaSwitchboard sb = (plasmaSwitchboard)env;
			post.remove("parserSettings");
            
            String[] enabledMimes = null;
            if (post.containsKey("allParserEnabled")) {
                // enable all available parsers
                enabledMimes = sb.parser.setEnabledParserList(sb.parser.getAvailableParserList().keySet());
            } else {
                // activate all received parsers       
                enabledMimes = sb.parser.setEnabledParserList(post.keySet());
            }
            Arrays.sort(enabledMimes);
            
            StringBuffer enabledMimesTxt = new StringBuffer();
            for (int i=0; i < enabledMimes.length; i++) {
                enabledMimesTxt.append(enabledMimes[i]).append(",");
                prop.put("info_parser_" + i + "_enabledMime",enabledMimes[i]);
            }
            prop.put("info_parser",enabledMimes.length);
            if (enabledMimesTxt.length() > 0) enabledMimesTxt.deleteCharAt(enabledMimesTxt.length()-1);            
            
            env.setConfig("parseableMimeTypes",enabledMimesTxt.toString());
            
            prop.put("info", 18);
            return prop;
        }
        
        
        // nothing made
        prop.put("info", 1);//no information submitted
        return prop;
    }
    
}

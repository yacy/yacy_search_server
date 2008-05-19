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

import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.anomic.http.httpHeader;
import de.anomic.http.httpRemoteProxyConfig;
import de.anomic.http.httpd;
import de.anomic.http.httpdProxyHandler;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCodings;
import de.anomic.server.serverCore;
import de.anomic.server.serverDate;
import de.anomic.server.serverMemory;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedUploader;

public class SettingsAck_p {
    
    private static boolean nothingChanged = false;
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        // return variable that accumulates replacements
        serverObjects prop = new serverObjects();
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        
        // get referer for backlink
        String referer = (String) header.get(httpHeader.REFERER);
        prop.put("referer", (referer == null) ? "Settings_p.html" : referer); 
        
        //if (post == null) System.out.println("POST: NULL"); else System.out.println("POST: " + post.toString());
        
        if (post == null) {
            prop.put("info", "1");//no information submitted
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
                prop.put("info", "1");//error with submitted information
                return prop;
            }
            if (user.length() == 0) {
                prop.put("info", "2");//username must be given
                return prop;
            }
            if (!(pw1.equals(pw2))) {
                prop.put("info", "3");//pw check failed
                return prop;
            }
            // check passed. set account:
            env.setConfig(httpd.ADMIN_ACCOUNT_B64MD5, serverCodings.encodeMD5Hex(kelondroBase64Order.standardCoder.encodeString(user + ":" + pw1)));
            env.setConfig("adminAccount", "");
            prop.put("info", "5");//admin account changed
            prop.putHTML("info_user", user);
            return prop;
        }
        
        
        // proxy password
        if (post.containsKey("proxyaccount")) {
            /* 
             * set new port
             */
            String port = (String) post.get("port");
            prop.putHTML("info_port", port);
            if (!env.getConfig("port", port).equals(port)) {
                // validation port
                serverCore theServerCore = (serverCore) env.getThread("10_httpd");
                try {
                    InetSocketAddress theNewAddress = theServerCore.generateSocketAddress(port);
                    String hostName = theNewAddress.getHostName();
                    prop.put("info_restart", "1");
                    prop.put("info_restart_ip",(hostName.equals("0.0.0.0"))? "localhost" : hostName);
                    prop.put("info_restart_port", theNewAddress.getPort());
                    
                    env.setConfig("port", port);
                    
                    theServerCore.reconnect(5000);                    
                } catch (SocketException e) {
                    prop.put("info", "26");
                    return prop;
                }
            } else {
                prop.put("info_restart", "0");
            }
            
            // read and process data
            String filter = ((String) post.get("proxyfilter")).trim();
			String use_proxyAccounts="";
			if(post.containsKey("use_proxyaccounts")){
				//needed? or set to true by default?
	            use_proxyAccounts = (((String) post.get("use_proxyaccounts")).equals("on") ? "true" : "false" );
			}else{
				use_proxyAccounts = "false";
			}
            // do checks
            if ((filter == null) || (use_proxyAccounts == null)) { 
                prop.put("info", "1");//error with submitted information
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
            else if (!filter.equals("*")){
                // testing proxy filter 
                int patternCount = 0;
                String patternStr = null;
                try {
                    StringTokenizer st = new StringTokenizer(filter,",");                
                    while (st.hasMoreTokens()) {
                        patternCount++;
                        patternStr = st.nextToken();
                        Pattern.compile(patternStr);
                    }
                } catch (PatternSyntaxException e) {
                    prop.put("info", "27");
                    prop.putHTML("info_filter", filter);
                    prop.put("info_nr", patternCount);
                    prop.putHTML("info_error", e.getMessage());
                    prop.putHTML("info_pattern", patternStr);
                    return prop;
                }
            }
            
            // check passed. set account:
            env.setConfig("proxyClient", filter);
            env.setConfig("use_proxyAccounts", use_proxyAccounts);//"true" or "false"
			if (use_proxyAccounts.equals("false")){
                prop.put("info", "6");//proxy account has changed(no pw)
                prop.putHTML("info_filter", filter);
			} else {
                prop.put("info", "7");//proxy account has changed
                //prop.put("info_user", user);
                prop.putHTML("info_filter", filter);
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
            
            // setting via header property
            env.setConfig("proxy.sendViaHeader", post.containsKey("proxy.sendViaHeader")?"true":"false");
            prop.put("info_proxy.sendViaHeader", post.containsKey("proxy.sendViaHeader")? "on" : "off");
            
            // setting X-Forwarded-for header property
            env.setConfig("proxy.sendXForwardedForHeader", post.containsKey("proxy.sendXForwardedForHeader")?"true":"false");
            prop.put("info_proxy.sendXForwardedForHeader", post.containsKey("proxy.sendXForwardedForHeader")? "on" : "off");            
            
            prop.put("info", "20");
            return prop;
        }
        
        // server access
        if (post.containsKey("serveraccount")) {

            // static IP
            String staticIP =  ((String) post.get("staticIP")).trim();
            if (staticIP.startsWith("http://")) {
                if (staticIP.length() > 7) { staticIP = staticIP.substring(7); } else { staticIP = ""; }
            } else if (staticIP.startsWith("https://")) {
                if (staticIP.length() > 8) { staticIP = staticIP.substring(8); } else { staticIP = ""; }
            }
            if (staticIP.indexOf(":") > 0) {
                staticIP = staticIP.substring(0, staticIP.indexOf(":"));
            }
            if (staticIP.length() == 0) {
                serverCore.useStaticIP = false;
            } else {
                serverCore.useStaticIP = true;
            }
            sb.webIndex.seedDB.mySeed().put(yacySeed.IP, staticIP);
            env.setConfig("staticIP", staticIP);

            // server access data
            String filter = ((String) post.get("serverfilter")).trim();
            /*String user   = (String) post.get("serveruser");
            String pw1    = (String) post.get("serverpw1");
            String pw2    = (String) post.get("serverpw2");*/
            // do checks
            if (filter == null) {
                //if ((filter == null) || (user == null) || (pw1 == null) || (pw2 == null)) {
                prop.put("info", "1");//error with submitted information
                return prop;
            }
           /* if (user.length() == 0) {
                prop.put("info", 2);//username must be given
                return prop;
            }
            if (!(pw1.equals(pw2))) {
                prop.put("info", 3);//pw check failed
                return prop;
            }*/
            if (filter.length() == 0) filter = "*";
            else if (!filter.equals("*")){
                // testing proxy filter 
                int patternCount = 0;
                String patternStr = null;
                try {
                    StringTokenizer st = new StringTokenizer(filter,",");
                    while (st.hasMoreTokens()) {
                        patternCount++;
                        patternStr = st.nextToken();
                        Pattern.compile(patternStr);
                    }
                } catch (PatternSyntaxException e) {
                    prop.put("info", "27");
                    prop.putHTML("info_filter", filter);
                    prop.put("info_nr", patternCount);
                    prop.putHTML("info_error", e.getMessage());
                    prop.putHTML("info_pattern", patternStr);
                    return prop;
                }
            }            
            
            // check passed. set account:
            env.setConfig("serverClient", filter);
            //env.setConfig("serverAccountBase64MD5", serverCodings.encodeMD5Hex(kelondroBase64Order.standardCoder.encodeString(user + ":" + pw1)));
            env.setConfig("serverAccount", "");
            
            prop.put("info", "8");//server access filter updated
            //prop.put("info_user", user);
            prop.putHTML("info_filter", filter);
            return prop;
        }

        if (post.containsKey("pmode")) {
            env.setConfig("onlineMode", "2");
            prop.put("info", "11");//permanent online mode
            yacyCore.setOnlineMode(2);
            yacyCore.triggerOnlineAction();
            return prop;
        }
        
        if (post.containsKey("emode")) {
            env.setConfig("onlineMode", "1");
            prop.put("info", "24");//event-based online mode
            yacyCore.setOnlineMode(1);
            return prop;
        }
        
        if (post.containsKey("cmode")) {
            env.setConfig("onlineMode", "0");
            prop.put("info", "25");//cache mode
            yacyCore.setOnlineMode(0);
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
            //String[] remoteProxyNoProxyPatterns = remoteProxyNoProxyStr.split(",");
            
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
            httpdProxyHandler.setRemoteProxyConfig(httpRemoteProxyConfig.init(sb));            
            
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
            
            
            prop.put("info", "15"); // The remote-proxy setting has been changed
            return prop;
        }
        
        if (post.containsKey("seedUploadRetry")) {
            String error;
            if ((error = yacyCore.saveSeedList(sb)) == null) {
                // trying to upload the seed-list file    
                prop.put("info", "13");
                prop.put("info_success", "1");
            } else {
                prop.put("info", "14");
                prop.putHTML("info_errormsg",error.replaceAll("\n","<br>"));
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
                if ((error = yacyCore.saveSeedList(sb)) == null) {
                    // we have successfully uploaded the seed-list file
                    prop.put("info_seedUploadMethod", newSeedUploadMethod);
                    prop.putHTML("info_seedURL",newSeedURLStr);
                    prop.put("info_success", newSeedUploadMethod.equalsIgnoreCase("none") ? "0" : "1");
                    prop.put("info", "19");
                } else {
                    prop.put("info", "14");
                    prop.putHTML("info_errormsg", error.replaceAll("\n","<br>"));
                    env.setConfig("seedUploadMethod","none");
                }
                return prop;
            }
        }
        
        /* 
         * Loop through the available seed uploaders to see if the 
         * configuration of one of them has changed 
         */
        HashMap<String, String> uploaders = yacyCore.getSeedUploadMethods();
        Iterator<String> uploaderKeys = uploaders.keySet().iterator();
        while (uploaderKeys.hasNext()) {
            // getting the uploader module name
            String uploaderName = uploaderKeys.next();
            
            
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
                        if ((error = yacyCore.saveSeedList(sb)) == null) {
                            
                            // we have successfully uploaded the seed file
                            prop.put("info", "13");
                            prop.put("info_success", "1");
                        } else {
                            // if uploading failed we print out an error message
                            prop.put("info", "14");
                            prop.putHTML("info_errormsg",error.replaceAll("\n","<br>"));
                            env.setConfig("seedUploadMethod","none");                            
                        }                       
                    } else {
                        prop.put("info", "13");
                        prop.put("info_success", "0");
                    }
                } else {
                    prop.put("info", "13");
                    prop.put("info_success", "0");
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
            
            prop.put("info", "21");
            prop.put("info_msgForwardingEnabled", post.containsKey("msgForwardingEnabled") ? "on" : "off");
            prop.put("info_msgForwardingCmd", (String) post.get("msgForwardingCmd"));
            prop.putHTML("info_msgForwardingTo", (String) post.get("msgForwardingTo"));
            
            return prop;
        }
        
        /*
         * Parser configuration
         */
        if (post.containsKey("parserSettings")) {
            post.remove("parserSettings");
            
            Set<String> parserModes = plasmaParser.getParserConfigList().keySet();
            HashMap<String, HashSet<String>> newConfigList = new HashMap<String, HashSet<String>>();     
            Iterator<String> parserModeIter = parserModes.iterator();
            while (parserModeIter.hasNext()) {
                String currParserMode = parserModeIter.next();
                newConfigList.put(currParserMode, new HashSet<String>());
            }
            
            // looping through all received settings
            int pos;
            Iterator<String> keyEnum = post.keySet().iterator();
            while (keyEnum.hasNext()) {                
                String key = keyEnum.next();
                if ((pos = key.indexOf(".")) != -1) {
                    String currParserMode = key.substring(0,pos).trim().toUpperCase();
                    String currMimeType = key.substring(pos+1).replaceAll("\n", "");
                    if (parserModes.contains(currParserMode)) {
                        HashSet<String> currEnabledMimeTypes;
                        assert (newConfigList.containsKey(currParserMode)) : "Unexpected Error";
                        currEnabledMimeTypes = newConfigList.get(currParserMode);
                        currEnabledMimeTypes.add(currMimeType);
                    }
                }
            }
            
            int enabledMimesCount = 0;
            StringBuffer currEnabledMimesTxt = new StringBuffer();
            parserModeIter = newConfigList.keySet().iterator();
            while (parserModeIter.hasNext()) {                
                String currParserMode = parserModeIter.next();
                String[] enabledMimes = plasmaParser.setEnabledParserList(currParserMode, newConfigList.get(currParserMode));
                Arrays.sort(enabledMimes);
                
                currEnabledMimesTxt.setLength(0);
                for (int i=0; i < enabledMimes.length; i++) {
                    currEnabledMimesTxt.append(enabledMimes[i]).append(",");
                    prop.put("info_parser_" + enabledMimesCount + "_parserMode",currParserMode);
                    prop.put("info_parser_" + enabledMimesCount + "_enabledMime",enabledMimes[i]);
                    enabledMimesCount++;
                }
                if (currEnabledMimesTxt.length() > 0) currEnabledMimesTxt.deleteCharAt(currEnabledMimesTxt.length()-1);  
                env.setConfig("parseableMimeTypes." + currParserMode,currEnabledMimesTxt.toString());
            }
            prop.put("info_parser",enabledMimesCount);
            prop.put("info", "18");
            return prop;

        }
        
        // Crawler settings
        if (post.containsKey("crawlerSettings")) {
            
            // getting Crawler Timeout
            String timeoutStr = (String) post.get("crawler.clientTimeout");
            if (timeoutStr==null||timeoutStr.length()==0) timeoutStr = "10000";
            
            int crawlerTimeout;
            try {
                crawlerTimeout = Integer.valueOf(timeoutStr).intValue();
                if (crawlerTimeout < 0) crawlerTimeout = 0;
                env.setConfig("crawler.clientTimeout", Integer.toString(crawlerTimeout));
            } catch (NumberFormatException e) {
                prop.put("info", "29");
                prop.put("info_crawler.clientTimeout",post.get("crawler.clientTimeout"));
                return prop;
            }
            
            // getting maximum http file size
            String maxSizeStr = (String) post.get("crawler.http.maxFileSize");
            if (maxSizeStr==null||maxSizeStr.length()==0) timeoutStr = "-1";
            
            long maxHttpSize;
            try {
                maxHttpSize = Integer.valueOf(maxSizeStr).intValue();
                env.setConfig("crawler.http.maxFileSize", Long.toString(maxHttpSize));
            } catch (NumberFormatException e) {
                prop.put("info", "30");
                prop.put("info_crawler.http.maxFileSize",post.get("crawler.http.maxFileSize"));
                return prop;
            }
            
            // getting maximum ftp file size
            maxSizeStr = (String) post.get("crawler.ftp.maxFileSize");
            if (maxSizeStr==null||maxSizeStr.length()==0) timeoutStr = "-1";
            
            long maxFtpSize;
            try {
                maxFtpSize = Integer.valueOf(maxSizeStr).intValue();
                env.setConfig("crawler.ftp.maxFileSize", Long.toString(maxFtpSize));
            } catch (NumberFormatException e) {
                prop.put("info", "31");
                prop.put("info_crawler.ftp.maxFileSize",post.get("crawler.ftp.maxFileSize"));
                return prop;
            }                        
            
            // everything is ok
            prop.put("info_crawler.clientTimeout",(crawlerTimeout==0) ? "0" :serverDate.formatInterval(crawlerTimeout));
            prop.put("info_crawler.http.maxFileSize",(maxHttpSize==-1)? "-1":serverMemory.bytesToString(maxHttpSize));
            prop.put("info_crawler.ftp.maxFileSize", (maxFtpSize==-1) ? "-1":serverMemory.bytesToString(maxFtpSize));
            prop.put("info", "28");
            return prop;
        }
        
        
        // nothing made
        prop.put("info", "1");//no information submitted
        return prop;
    }
    
}

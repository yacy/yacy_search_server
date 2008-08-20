// SettingsAck_p.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
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
    
    public static serverObjects respond(final httpHeader header, final serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        
        // get referer for backlink
        final String referer = header.get(httpHeader.REFERER);
        prop.put("referer", (referer == null) ? "Settings_p.html" : referer); 
        
        //if (post == null) System.out.println("POST: NULL"); else System.out.println("POST: " + post.toString());
        
        if (post == null) {
            prop.put("info", "1");//no information submitted
            return prop;
        }
        
        // admin password
        if (post.containsKey("adminaccount")) {
            // read and process data
            final String user   = post.get("adminuser");
            final String pw1    = post.get("adminpw1");
            final String pw2    = post.get("adminpw2");
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
            final String port = post.get("port");
            prop.putHTML("info_port", port);
            if (!env.getConfig("port", port).equals(port)) {
                // validation port
                final serverCore theServerCore = (serverCore) env.getThread("10_httpd");
                try {
                    final InetSocketAddress theNewAddress = theServerCore.generateSocketAddress(port);
                    final String hostName = theNewAddress.getHostName();
                    prop.put("info_restart", "1");
                    prop.put("info_restart_ip",(hostName.equals("0.0.0.0"))? "localhost" : hostName);
                    prop.put("info_restart_port", theNewAddress.getPort());
                    
                    env.setConfig("port", port);
                    
                    theServerCore.reconnect(5000);                    
                } catch (final SocketException e) {
                    prop.put("info", "26");
                    return prop;
                }
            } else {
                prop.put("info_restart", "0");
            }
            
            // read and process data
            String filter = (post.get("proxyfilter")).trim();
			String use_proxyAccounts="";
			if(post.containsKey("use_proxyaccounts")){
				//needed? or set to true by default?
	            use_proxyAccounts = ((post.get("use_proxyaccounts")).equals("on") ? "true" : "false" );
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
                    final StringTokenizer st = new StringTokenizer(filter,",");                
                    while (st.hasMoreTokens()) {
                        patternCount++;
                        patternStr = st.nextToken();
                        Pattern.compile(patternStr);
                    }
                } catch (final PatternSyntaxException e) {
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
            String staticIP =  (post.get("staticIP")).trim();
            if (staticIP.startsWith("http://")) {
                if (staticIP.length() > 7) { staticIP = staticIP.substring(7); } else { staticIP = ""; }
            } else if (staticIP.startsWith("https://")) {
                if (staticIP.length() > 8) { staticIP = staticIP.substring(8); } else { staticIP = ""; }
            }
            // TODO IPv6 support!
            if (staticIP.indexOf(":") > 0) {
                staticIP = staticIP.substring(0, staticIP.indexOf(":"));
            }
            if (staticIP.length() == 0) {
                serverCore.useStaticIP = false;
            } else {
                serverCore.useStaticIP = true;
            }
            if (yacySeed.isProperIP(staticIP) == null) sb.webIndex.seedDB.mySeed().setIP(staticIP);
            env.setConfig("staticIP", staticIP);

            // server access data
            String filter = (post.get("serverfilter")).trim();
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
                    final StringTokenizer st = new StringTokenizer(filter,",");
                    while (st.hasMoreTokens()) {
                        patternCount++;
                        patternStr = st.nextToken();
                        Pattern.compile(patternStr);
                    }
                } catch (final PatternSyntaxException e) {
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
            final boolean useRemoteProxy = post.containsKey("remoteProxyUse");
            final boolean useRemoteProxy4Yacy = post.containsKey("remoteProxyUse4Yacy");
            final boolean useRemoteProxy4SSL = post.containsKey("remoteProxyUse4SSL");
            
            final String remoteProxyHost = post.get("remoteProxyHost", "");
            final String remoteProxyPortStr = post.get("remoteProxyPort", "");
            int remoteProxyPort = 0;
            try {
                remoteProxyPort = Integer.parseInt(remoteProxyPortStr);
            } catch (final NumberFormatException e) {
                remoteProxyPort = 3128;
            }
            
            final String remoteProxyUser = post.get("remoteProxyUser", "");
            final String remoteProxyPwd = post.get("remoteProxyPwd", "");
            
            final String remoteProxyNoProxyStr = post.get("remoteProxyNoProxy", "");
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
            httpRemoteProxyConfig.init(sb);            
            
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
            final String oldSeedUploadMethod = env.getConfig("seedUploadMethod","none");
            final String newSeedUploadMethod = post.get("seedUploadMethod");
            final String oldSeedURLStr = sb.webIndex.seedDB.mySeed().get(yacySeed.SEEDLIST, "");
            final String newSeedURLStr = post.get("seedURL");
            
            final boolean seedUrlChanged = !oldSeedURLStr.equals(newSeedURLStr);
            boolean uploadMethodChanged = !oldSeedUploadMethod.equals(newSeedUploadMethod);
            if (uploadMethodChanged) {
                uploadMethodChanged = yacyCore.changeSeedUploadMethod(newSeedUploadMethod);
            }
            
            if (seedUrlChanged || uploadMethodChanged) {
                env.setConfig("seedUploadMethod", newSeedUploadMethod);
                sb.webIndex.seedDB.mySeed().put(yacySeed.SEEDLIST, newSeedURLStr);
                
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
        final HashMap<String, String> uploaders = yacyCore.getSeedUploadMethods();
        final Iterator<String> uploaderKeys = uploaders.keySet().iterator();
        while (uploaderKeys.hasNext()) {
            // getting the uploader module name
            final String uploaderName = uploaderKeys.next();
            
            
            // determining if the user has reconfigured the settings of this uploader
            if (post.containsKey("seed" + uploaderName + "Settings")) {
                nothingChanged = true;
                final yacySeedUploader theUploader = yacyCore.getSeedUploader(uploaderName);
                final String[] configOptions = theUploader.getConfigurationOptions();
                if (configOptions != null) {
                    for (int i=0; i<configOptions.length; i++) {
                        final String newSettings = post.get(configOptions[i],"");
                        final String oldSettings = env.getConfig(configOptions[i],"");
                        // bitwise AND with boolean is same as logic AND
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
            env.setConfig("msgForwardingCmd",post.get("msgForwardingCmd"));
            env.setConfig("msgForwardingTo",post.get("msgForwardingTo"));
            
            prop.put("info", "21");
            prop.put("info_msgForwardingEnabled", post.containsKey("msgForwardingEnabled") ? "on" : "off");
            prop.put("info_msgForwardingCmd", post.get("msgForwardingCmd"));
            prop.putHTML("info_msgForwardingTo", post.get("msgForwardingTo"));
            
            return prop;
        }
        
        /*
         * Parser configuration
         */
        if (post.containsKey("parserSettings")) {
            post.remove("parserSettings");
            
            final Set<String> parserModes = plasmaParser.getParserConfigList().keySet();
            final HashMap<String, HashSet<String>> newConfigList = new HashMap<String, HashSet<String>>();     
            Iterator<String> parserModeIter = parserModes.iterator();
            while (parserModeIter.hasNext()) {
                final String currParserMode = parserModeIter.next();
                newConfigList.put(currParserMode, new HashSet<String>());
            }
            
            // looping through all received settings
            int pos;
            final Iterator<String> keyEnum = post.keySet().iterator();
            while (keyEnum.hasNext()) {                
                final String key = keyEnum.next();
                if ((pos = key.indexOf(".")) != -1) {
                    final String currParserMode = key.substring(0,pos).trim().toUpperCase();
                    final String currMimeType = key.substring(pos+1).replaceAll("\n", "");
                    if (parserModes.contains(currParserMode)) {
                        HashSet<String> currEnabledMimeTypes;
                        assert (newConfigList.containsKey(currParserMode)) : "Unexpected Error";
                        currEnabledMimeTypes = newConfigList.get(currParserMode);
                        currEnabledMimeTypes.add(currMimeType);
                    }
                }
            }
            
            int enabledMimesCount = 0;
            final StringBuffer currEnabledMimesTxt = new StringBuffer();
            parserModeIter = newConfigList.keySet().iterator();
            while (parserModeIter.hasNext()) {                
                final String currParserMode = parserModeIter.next();
                final String[] enabledMimes = plasmaParser.setEnabledParserList(currParserMode, newConfigList.get(currParserMode));
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
            String timeoutStr = post.get("crawler.clientTimeout");
            if (timeoutStr==null||timeoutStr.length()==0) timeoutStr = "10000";
            
            int crawlerTimeout;
            try {
                crawlerTimeout = Integer.valueOf(timeoutStr).intValue();
                if (crawlerTimeout < 0) crawlerTimeout = 0;
                env.setConfig("crawler.clientTimeout", Integer.toString(crawlerTimeout));
            } catch (final NumberFormatException e) {
                prop.put("info", "29");
                prop.put("info_crawler.clientTimeout",post.get("crawler.clientTimeout"));
                return prop;
            }
            
            // getting maximum http file size
            String maxSizeStr = post.get("crawler.http.maxFileSize");
            if (maxSizeStr==null||maxSizeStr.length()==0) timeoutStr = "-1";
            
            long maxHttpSize;
            try {
                maxHttpSize = Integer.valueOf(maxSizeStr).intValue();
                if(maxHttpSize < 0) {
                    maxHttpSize = -1;
                }
                env.setConfig("crawler.http.maxFileSize", Long.toString(maxHttpSize));
            } catch (final NumberFormatException e) {
                prop.put("info", "30");
                prop.put("info_crawler.http.maxFileSize",post.get("crawler.http.maxFileSize"));
                return prop;
            }
            
            // getting maximum ftp file size
            maxSizeStr = post.get("crawler.ftp.maxFileSize");
            if (maxSizeStr==null||maxSizeStr.length()==0) timeoutStr = "-1";
            
            long maxFtpSize;
            try {
                maxFtpSize = Integer.valueOf(maxSizeStr).intValue();
                env.setConfig("crawler.ftp.maxFileSize", Long.toString(maxFtpSize));
            } catch (final NumberFormatException e) {
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

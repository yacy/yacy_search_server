// Settings.p.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last change: 02.05.2004
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
// javac -classpath .:../Classes Settings_p.java
// if the shell's current path is HTROOT

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeedUploader;

public final class Settings_p {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        serverObjects prop = new serverObjects();
        
        //if (post == null) System.out.println("POST: NULL"); else System.out.println("POST: " + post.toString());
        
        String page = (post == null) ? "admin" : post.get("page", "admin");
        
        if (page.equals("admin")) {
        	prop.put("settingsTables", "Settings_Admin.inc");
        }
        else if (page.equals("general")) {
            prop.put("settingsTables", "Settings_General.inc");
        }
        else if (page.equals("ProxyAccess")) {
            prop.put("settingsTables", "Settings_ProxyAccess.inc");
        }
        else if (page.equals("http")) {
            prop.put("settingsTables", "Settings_Http.inc");
        }
        else if (page.equals("proxy")) {
            prop.put("settingsTables", "Settings_Proxy.inc");
        }
        else if (page.equals("ServerAccess")) {	
            prop.put("settingsTables", "Settings_ServerAccess.inc");
        }
        else if (page.equals("SystemBehaviour")) {
            prop.put("settingsTables", "Settings_SystemBehaviour.inc");
        }
        else if (page.equals("seed")) {
            prop.put("settingsTables", "Settings_Seed.inc");
        }
        else if (page.equals("messageForwarding")) {
            prop.put("settingsTables", "Settings_MessageForwarding.inc");
        }
        else if (page.equals("portForwarding")) {
            prop.put("settingsTables", "Settings_PortForwarding.inc");
        }
        else if (page.equals("parser")) {
            prop.put("settingsTables", "Settings_Parser.inc");
        }
        else {
        	prop.put("settingsTables", "Settings_Admin.inc");
        }

        prop.put("port", env.getConfig("port", "8080"));               
        
        prop.put("peerName", env.getConfig("peerName", "nameless"));
        prop.put("staticIP", env.getConfig("staticIP", ""));
        String peerLang = env.getConfig("htLocaleSelection", "default");
        if (peerLang.equals("default")) peerLang = "en";
        prop.put("peerLang", peerLang);
        
        // http networking settings
        prop.put("isTransparentProxy", env.getConfig("isTransparentProxy", "false").equals("true") ? 1 : 0); 
        prop.put("connectionKeepAliveSupport", env.getConfig("connectionKeepAliveSupport", "false").equals("true") ? 1 : 0);
        
        // remote port forwarding settings
        prop.put("portForwardingEnabled",env.getConfig("portForwardingEnabled","false").equals("true")? 1 : 0);
        prop.put("portForwardingUseProxy",env.getConfig("portForwardingUseProxy", "false").equals("true")? 1 : 0);
        prop.put("portForwardingPort",env.getConfig("portForwardingPort", ""));
        
        prop.put("portForwardingHost",env.getConfig("portForwardingHost", ""));
        prop.put("portForwardingHostPort",env.getConfig("portForwardingHostPort", ""));
        prop.put("portForwardingHostUser",env.getConfig("portForwardingHostUser", ""));
        prop.put("portForwardingHostPwd",env.getConfig("portForwardingHostPwd", ""));
        
        // set values
        String s;
        int pos;
        
        // admin password
        if (env.getConfig("adminAccountBase64", "").length() == 0) {
            // no password has been specified
            prop.put("adminuser","admin");
        } else {
            s = env.getConfig("adminAccount", "admin:void");
            pos = s.indexOf(":");
            if (pos < 0) {
                prop.put("adminuser","admin");
            } else {
                prop.put("adminuser",s.substring(0, pos));
            }
        }
        
        // remote proxy
        prop.put("remoteProxyUseChecked", env.getConfig("remoteProxyUse", "false").equals("true") ? 1 : 0);
        prop.put("remoteProxyUse4Yacy", env.getConfig("remoteProxyUse4Yacy", "true").equals("true") ? 1 : 0);
        prop.put("remoteProxyUse4SSL", env.getConfig("remoteProxyUse4SSL", "true").equals("true") ? 1 : 0);
        
        prop.put("remoteProxyHost", env.getConfig("remoteProxyHost", ""));
        prop.put("remoteProxyPort", env.getConfig("remoteProxyPort", ""));
        
        prop.put("remoteProxyUser", env.getConfig("remoteProxyUser", ""));
        prop.put("remoteProxyPwd", env.getConfig("remoteProxyPwd", ""));
        
        prop.put("remoteProxyNoProxy", env.getConfig("remoteProxyNoProxy", ""));
        
        // proxy access filter
        prop.put("proxyfilter", env.getConfig("proxyClient", "*"));
        
        // proxy password
        if ( env.getConfig("use_proxyAccounts", "false").equals("false") ) {
            // no password has been specified
            prop.put("use_proxyAccounts", 0); //unchecked
        } else {
            prop.put("use_proxyAccounts", 1); //checked
            /*s = env.getConfig("proxyAccount", "proxy:void");
            pos = s.indexOf(":");
            if (pos < 0) {
                prop.put("proxyuser","proxy");
            } else {
                prop.put("proxyuser",s.substring(0, pos));
            }*/
        }
        
        // server access filter
        prop.put("serverfilter", env.getConfig("serverClient", "*"));
        
        // server password
        if (env.getConfig("serverAccountBase64", "").length() == 0) {
            // no password has been specified
            prop.put("serveruser","server");
        } else {
            s = env.getConfig("serverAccount", "server:void");
            pos = s.indexOf(":");
            if (pos < 0) {
                prop.put("serveruser","server");
            } else {
                prop.put("serveruser",s.substring(0, pos));
            }
        }
        
        // clientIP
        prop.put("clientIP", (String) header.get("CLIENTIP", "<unknown>")); // read an artificial header addendum
        
        /* 
         * seed upload settings
         */
        // available methods
        String enabledUploader = env.getConfig("seedUploadMethod", "none");
        
        // for backward compatiblity ....
        if ((enabledUploader.equalsIgnoreCase("Ftp")) || 
                ((enabledUploader.equals("")) &&
                        (env.getConfig("seedFTPPassword","").length() > 0) &&
                        (env.getConfig("seedFilePath", "").length() > 0))) {
            enabledUploader = "Ftp";
            env.setConfig("seedUploadMethod",enabledUploader);
        }                  
        
        HashMap uploaders = yacyCore.getSeedUploadMethods();
        prop.put("seedUploadMethods", uploaders.size() + 1);
        prop.put("seedUploadMethods_0_name", "none");
        prop.put("seedUploadMethods_0_selected", enabledUploader.equals("none")?1:0);
        prop.put("seedUploadMethods_0_file", "");
        
        int count = 0;
        Iterator uploaderKeys = uploaders.keySet().iterator();
        while (uploaderKeys.hasNext()) {
            count++;
            String uploaderName = (String) uploaderKeys.next();
            prop.put("seedUploadMethods_" +count+ "_name", uploaderName);
            prop.put("seedUploadMethods_" +count+ "_selected", uploaderName.equals(enabledUploader)?1:0);            
            prop.put("seedUploadMethods_" +count+ "_file", "yacy/seedUpload/yacySeedUpload" + uploaderName + ".html");
            
            yacySeedUploader theUploader = yacyCore.getSeedUploader(uploaderName);
            String[] configOptions = theUploader.getConfigurationOptions();
            if (configOptions != null) {
                for (int i=0; i<configOptions.length; i++) {
                    prop.put("seedUploadMethods_" +count+ "_" + configOptions[i], env.getConfig(configOptions[i], ""));
                    // prop.put("seedUpload" + uploaderName,1);
                }
            }
        }
        
        // general settings
        prop.put("seedURL", env.getConfig("seedURL", ""));
        
        /*
         * Message forwarding configuration
         */
        prop.put("msgForwardingEnabled",env.getConfig("msgForwardingEnabled","false").equals("true")? 1 : 0);
        prop.put("msgForwardingCmd",env.getConfig("msgForwardingCmd", ""));
        prop.put("msgForwardingTo",env.getConfig("msgForwardingTo", ""));
        
        /*
         * Parser Configuration
         */
        plasmaSwitchboard sb = (plasmaSwitchboard)env;
        Hashtable enabledParsers = sb.parser.getEnabledParserList();
        Hashtable availableParsers = sb.parser.getAvailableParserList();
        
        // fetching a list of all available mimetypes
        List availableParserKeys = Arrays.asList(availableParsers.keySet().toArray(new String[availableParsers.size()]));
        
        // sort it
        Collections.sort(availableParserKeys);
        
        // loop through the mimeTypes and add it to the properties
        boolean allParsersEnabled = true;
        int parserIdx = 0;
        Iterator availableParserIter = availableParserKeys.iterator();
        while (availableParserIter.hasNext()) {
            String mimeType = (String) availableParserIter.next();
            String parserName = (String) availableParsers.get(mimeType);
            boolean parserIsEnabled = enabledParsers.containsKey(mimeType);
            
            prop.put("parser_" + parserIdx + "_mime", mimeType);
            prop.put("parser_" + parserIdx + "_name", parserName);
            prop.put("parser_" + parserIdx + "_shortname", parserName.substring(parserName.lastIndexOf(".")+1));
            prop.put("parser_" + parserIdx + "_status", parserIsEnabled ? 1:0);
            allParsersEnabled &= parserIsEnabled;
            
            parserIdx++;
        }
        
        prop.put("allParserEnabled",allParsersEnabled ? 1:0);
        prop.put("parser", parserIdx);
        
        // return rewrite properties
        return prop;
    }
    
}

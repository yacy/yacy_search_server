// Settings.p.java
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
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

// You must compile this file with
// javac -classpath .:../Classes Settings_p.java
// if the shell's current path is HTROOT

import java.util.HashMap;
import java.util.Iterator;

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.peers.Network;
import net.yacy.peers.Seed;
import net.yacy.peers.operation.yacySeedUploader;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;


public final class Settings_p {
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;
        
        //if (post == null) System.out.println("POST: NULL"); else System.out.println("POST: " + post.toString());
        
        final String page = (post == null) ? "general" : post.get("page", "general");
        
        if (page.equals("ProxyAccess")) {
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
        else if (page.equals("parser")) {
            prop.put("settingsTables", "Settings_Parser.inc");
        }
        else if (page.equals("crawler")) {
            prop.put("settingsTables", "Settings_Crawler.inc");
        } else {
            prop.put("settingsTables", "");
        }

        prop.put("port", env.getConfig("port", "8090"));               
        
        prop.putHTML("peerName", sb.peers.mySeed().getName());
        prop.putHTML("staticIP", env.getConfig("staticIP", ""));
        String peerLang = env.getConfig("locale.language", "default");
        if (peerLang.equals("default")) peerLang = "en";
        prop.putHTML("peerLang", peerLang);
        
        // http networking settings
        prop.put("isTransparentProxy", env.getConfigBool("isTransparentProxy", false) ? "1" : "0");
        prop.put("proxy.sendViaHeader", env.getConfigBool("proxy.sendViaHeader", false) ? "1" : "0");
        prop.put("proxy.sendXForwardedForHeader", env.getConfigBool("proxy.sendXForwardedForHeader", true) ? "1" : "0");
        
        // remote proxy
        prop.put("remoteProxyUseChecked", env.getConfigBool("remoteProxyUse", false) ? 1 : 0);
        prop.put("remoteProxyUse4Yacy", env.getConfigBool("remoteProxyUse4Yacy", true) ? 1 : 0);
        prop.put("remoteProxyUse4SSL", env.getConfigBool("remoteProxyUse4SSL", true) ? 1 : 0);
        
        prop.putHTML("remoteProxyHost", env.getConfig("remoteProxyHost", ""));
        prop.putHTML("remoteProxyPort", env.getConfig("remoteProxyPort", ""));
        
        prop.putHTML("remoteProxyUser", env.getConfig("remoteProxyUser", ""));
        prop.putHTML("remoteProxyPwd", env.getConfig("remoteProxyPwd", ""));
        
        prop.putHTML("remoteProxyNoProxy", env.getConfig("remoteProxyNoProxy", ""));
        
        // proxy access filter
        prop.putHTML("proxyfilter", env.getConfig("proxyClient", "*"));
        
        // proxy password
        if (!env.getConfigBool("use_proxyAccounts", false)) {
            // no password has been specified
            prop.put("use_proxyAccounts", "0"); //unchecked
        } else {
            prop.put("use_proxyAccounts", "1"); //checked
            /*s = env.getConfig("proxyAccount", "proxy:void");
            pos = s.indexOf(":");
            if (pos < 0) {
                prop.put("proxyuser","proxy");
            } else {
                prop.put("proxyuser",s.substring(0, pos));
            }*/
        }
        
        // server access filter
        prop.putHTML("serverfilter", env.getConfig("serverClient", "*"));
        
        // server password
        prop.put("serveruser","server");
        
        // clientIP
        prop.putXML("clientIP", header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP, "<unknown>")); // read an artificial header addendum
        
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
        
        final HashMap<String, String> uploaders = Network.getSeedUploadMethods();
        prop.put("seedUploadMethods", uploaders.size() + 1);
        prop.put("seedUploadMethods_0_name", "none");
        prop.put("seedUploadMethods_0_selected", enabledUploader.equals("none") ? "1" : "0");
        prop.put("seedUploadMethods_0_file", "");
        
        int count = 0;
        final Iterator<String> uploaderKeys = uploaders.keySet().iterator();
        while (uploaderKeys.hasNext()) {
            count++;
            final String uploaderName = uploaderKeys.next();
            prop.put("seedUploadMethods_" +count+ "_name", uploaderName);
            prop.put("seedUploadMethods_" +count+ "_selected", uploaderName.equals(enabledUploader) ? "1" : "0");            
            prop.put("seedUploadMethods_" +count+ "_file", "Settings_Seed_Upload" + uploaderName + ".inc");
            
            final yacySeedUploader theUploader = Network.getSeedUploader(uploaderName);
            final String[] configOptions = theUploader.getConfigurationOptions();
            if (configOptions != null) {
                for (int i=0; i<configOptions.length; i++) {
                    prop.put(configOptions[i], env.getConfig(configOptions[i], ""));
                    // prop.put("seedUpload" + uploaderName,1);
                }
            }
        }
        
        // general settings
        prop.put("seedURL", sb.peers.mySeed().get(Seed.SEEDLISTURL, ""));
        
        /*
         * Message forwarding configuration
         */
        prop.put("msgForwardingEnabled",env.getConfigBool("msgForwardingEnabled",false) ? "1" : "0");
        prop.putHTML("msgForwardingCmd",env.getConfig("msgForwardingCmd", ""));
        prop.putHTML("msgForwardingTo",env.getConfig("msgForwardingTo", ""));

        // Crawler settings
        prop.putHTML("crawler.clientTimeout",sb.getConfig("crawler.clientTimeout", "10000"));
        prop.putHTML("crawler.http.maxFileSize",sb.getConfig("crawler.http.maxFileSize", "-1"));
        prop.putHTML("crawler.ftp.maxFileSize",sb.getConfig("crawler.ftp.maxFileSize", "-1"));
        prop.putHTML("crawler.smb.maxFileSize",sb.getConfig("crawler.smb.maxFileSize", "-1"));
        prop.putHTML("crawler.file.maxFileSize",sb.getConfig("crawler.file.maxFileSize", "-1"));
        
        prop.put("httpservername",sb.getHttpServer().getVersion());
        
        // return rewrite properties
        return prop;
    }
    
}

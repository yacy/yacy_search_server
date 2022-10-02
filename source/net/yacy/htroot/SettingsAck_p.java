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

package net.yacy.htroot;

import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.TransactionManager;
import net.yacy.http.InetPathAccessHandler;
import net.yacy.kelondro.util.Formatter;
import net.yacy.peers.Network;
import net.yacy.peers.Seed;
import net.yacy.peers.operation.yacySeedUploader;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.SearchEventCache;
import net.yacy.search.snippet.TextSnippet;
import net.yacy.server.serverCore;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class SettingsAck_p {

    private static boolean nothingChanged = false;

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        // set default backlink
        prop.put("needsRestart_referer", "Settings_p.html");
        prop.put("needsRestart", false);
        //if (post == null) System.out.println("POST: NULL"); else System.out.println("POST: " + post.toString());

        if (post == null) {
            prop.put("info", "1");//no information submitted
            return prop;
        }

    	/* Check this is a valid transaction */
    	TransactionManager.checkPostTransaction(header, post);

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
            if (user.isEmpty()) {
                prop.put("info", "2");//username must be given
                return prop;
            }
            if (!(pw1.equals(pw2))) {
                prop.put("info", "3");//pw check failed
                return prop;
            }
            // check passed. set account:
            env.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, sb.encodeDigestAuth(user, pw1));
            env.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, user);
            prop.put("info", "5");//admin account changed
            prop.putHTML("info_user", user);
            return prop;
        }


        // proxy password
        if (post.containsKey("proxyaccount")) {
            // set backlink
            prop.put("needsRestart_referer", "Settings_p.html?page=ProxyAccess");

            /*
             * display port info
             */
            prop.put("info_port", env.getLocalPort());
            prop.put("info_restart", "0");

            // read and process data
            String filter = (post.get("proxyfilter")).trim();
            final boolean useProxyAccounts = post.containsKey("use_proxyaccounts") && post.get("use_proxyaccounts").equals("on");
            // do checks
            if (filter == null) {
                prop.put("info", "1");//error with submitted information
                return prop;
            }

            if (filter.isEmpty()) filter = "*";
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
            env.setConfig("use_proxyAccounts", useProxyAccounts);
            if (!useProxyAccounts){
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

            // set backlink
            prop.put("needsRestart_referer", "Settings_p.html?page=ProxyAccess");

            // set transparent proxy flag
            final boolean isTransparentProxy = post.containsKey("isTransparentProxy");
            env.setConfig(SwitchboardConstants.PROXY_TRANSPARENT_PROXY, isTransparentProxy);
            prop.put("info_isTransparentProxy", isTransparentProxy ? "on" : "off");
            if (isTransparentProxy) prop.put("needsRestart", isTransparentProxy);

            // set proxyAlwaysFresh flag
            final boolean proxyAlwaysFresh = post.containsKey("proxyAlwaysFresh");
            env.setConfig("proxyAlwaysFresh", proxyAlwaysFresh);
            prop.put("info_proxyAlwaysFresh", proxyAlwaysFresh ? "on" : "off");

            // setting via header property
            env.setConfig("proxy.sendViaHeader", post.containsKey("proxy.sendViaHeader"));
            prop.put("info_proxy.sendViaHeader", post.containsKey("proxy.sendViaHeader")? "on" : "off");

            // setting X-Forwarded-for header property
            env.setConfig("proxy.sendXForwardedForHeader", post.containsKey("proxy.sendXForwardedForHeader"));
            prop.put("info_proxy.sendXForwardedForHeader", post.containsKey("proxy.sendXForwardedForHeader")? "on" : "off");

            prop.put("info", "20");
            return prop;
        }

        // server access
        if (post.containsKey("serveraccount")) {
            // set backlink
            prop.put("needsRestart_referer", "Settings_p.html?page=ServerAccess");

        	// fileHost
        	final String fileHost = (post.get("fileHost")).trim();
        	if (fileHost != null && !fileHost.isEmpty() && !fileHost.equals(env.getConfig("fileHost", "localpeer"))) {
        		env.setConfig("fileHost", fileHost);
        	}

            // static IP
            String staticIP =  (post.get("staticIP")).trim();
            if (staticIP.startsWith("http://")) {
                if (staticIP.length() > 7) { staticIP = staticIP.substring(7); } else { staticIP = ""; }
            } else if (staticIP.startsWith("https://")) {
                if (staticIP.length() > 8) { staticIP = staticIP.substring(8); } else { staticIP = ""; }
            }
            final String error = Seed.isProperIP(staticIP) ? null : "ip not proper: " + staticIP;
            if (error == null) {
                serverCore.useStaticIP = true;
                sb.peers.mySeed().setIP(staticIP);
                env.setConfig(SwitchboardConstants.SERVER_STATICIP, staticIP);
            } else {
                serverCore.useStaticIP = false;
                sb.peers.mySeed().setIP("");
                env.setConfig(SwitchboardConstants.SERVER_STATICIP, "");
            }

            // publicPort
            final String publicPort =  (post.get("publicPort")).trim();
            try {
                final Integer pport = Integer.parseInt(publicPort);
                if(pport < 65535 && pport >= 0) {
                    serverCore.usePublicPort = true;
                    sb.peers.mySeed().setPort(pport);
                    env.setConfig(SwitchboardConstants.SERVER_PUBLICPORT, publicPort);
                }
            } catch (final NumberFormatException e) {
                // noop
            }

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
           /* if (user.isEmpty()) {
                prop.put("info", 2);//username must be given
                return prop;
            }
            if (!(pw1.equals(pw2))) {
                prop.put("info", 3);//pw check failed
                return prop;
            }*/
            if (filter.isEmpty()) filter = "*";
            else if (!filter.equals("*")){
                // testing proxy filter
                int patternCount = 0;
                String patternStr = null;
                final StringTokenizer st = new StringTokenizer(filter,",");
                try {
                    while (st.hasMoreTokens()) {
                        patternCount++;
                        patternStr = st.nextToken();
                        InetPathAccessHandler.checkPattern(patternStr);
                    }
                } catch (final IllegalArgumentException e) {
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

            prop.put("info", "8");//server access filter updated
            //prop.put("info_user", user);
            prop.putHTML("info_filter", filter);
            return prop;
        }

        /* Compression settings */
        if (post.containsKey("serverCompression")) {
            // set backlink
            prop.put("needsRestart_referer", "Settings_p.html?page=ServerAccess");

			final boolean oldValue = env.getConfigBool(SwitchboardConstants.SERVER_RESPONSE_COMPRESS_GZIP,
					SwitchboardConstants.SERVER_RESPONSE_COMPRESS_GZIP_DEFAULT);
        	final boolean newValue = post.containsKey(SwitchboardConstants.SERVER_RESPONSE_COMPRESS_GZIP);
            env.setConfig(SwitchboardConstants.SERVER_RESPONSE_COMPRESS_GZIP, newValue);

            prop.put("needsRestart", newValue != oldValue);

            /* server compression settings saved */
            prop.put("info", "37");
        	return prop;
        }

        if (post.containsKey("proxysettings")) {
            // set backlink
            prop.put("needsRestart_referer", "Settings_p.html?page=proxy");

            /* ====================================================================
             * Reading out the remote proxy settings
             * ==================================================================== */
            final boolean useRemoteProxy = post.containsKey("remoteProxyUse");
            final boolean useRemoteProxy4SSL = post.containsKey("remoteProxyUse4SSL");

            final String remoteProxyHost = post.get("remoteProxyHost", "");
            final int remoteProxyPort = post.getInt("remoteProxyPort", 3128);

            final String remoteProxyUser = post.get("remoteProxyUser", "");
            final String remoteProxyPwd = post.get("remoteProxyPwd", "");

            final String remoteProxyNoProxyStr = post.get("remoteProxyNoProxy", "");
            //String[] remoteProxyNoProxyPatterns = CommonPattern.COMMA.split(remoteProxyNoProxyStr);

            /* ====================================================================
             * Storing settings into config file
             * ==================================================================== */
            env.setConfig("remoteProxyHost", remoteProxyHost);
            env.setConfig("remoteProxyPort", Integer.toString(remoteProxyPort));
            env.setConfig("remoteProxyUser", remoteProxyUser);
            env.setConfig("remoteProxyPwd", remoteProxyPwd);
            env.setConfig("remoteProxyNoProxy", remoteProxyNoProxyStr);
            env.setConfig("remoteProxyUse", useRemoteProxy);
            env.setConfig("remoteProxyUse4SSL", useRemoteProxy4SSL);

            /* ====================================================================
             * Enabling settings
             * ==================================================================== */
            sb.initRemoteProxy();

            prop.put("info", "15"); // The remote-proxy setting has been changed
            return prop;
        }

        if (post.containsKey("urlproxySettings")) {
            // set backlink
            prop.put("needsRestart_referer", "Settings_p.html?page=UrlProxyAccess");

            env.setConfig("proxyURL.access", post.get("urlproxyfilter"));
            env.setConfig("proxyURL.rewriteURLs", post.get("urlproxydomains"));
            env.setConfig("proxyURL", "on".equals(post.get("urlproxyenabled")) ? true : false);
            env.setConfig("proxyURL.useforresults", "on".equals(post.get("urlproxyuseforresults")) ? true : false);
            prop.put("info_success", "1");
            prop.put("info", "33");
            return prop;
        }

        if (post.containsKey("seedUploadRetry")) {
            // set backlink
            prop.put("needsRestart_referer", "Settings_p.html?page=seed");

            String error;
            if ((error = Network.saveSeedList(sb)) == null) {
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
            // set backlink
            prop.put("needsRestart_referer", "Settings_p.html?page=seed");

            // get the currently used uploading method
            final String oldSeedUploadMethod = env.getConfig("seedUploadMethod","none");
            final String newSeedUploadMethod = post.get("seedUploadMethod");
            final String oldSeedURLStr = sb.peers.mySeed().get(Seed.SEEDLISTURL, "");
            final String newSeedURLStr = post.get("seedURL");

            final boolean seedUrlChanged = !oldSeedURLStr.equals(newSeedURLStr);
            boolean uploadMethodChanged = !oldSeedUploadMethod.equals(newSeedUploadMethod);
            if (uploadMethodChanged) {
                uploadMethodChanged = Network.changeSeedUploadMethod(newSeedUploadMethod);
            }

            if (seedUrlChanged || uploadMethodChanged) {
                env.setConfig("seedUploadMethod", newSeedUploadMethod);
                sb.peers.mySeed().put(Seed.SEEDLISTURL, newSeedURLStr);
                if (seedUrlChanged) sb.peers.saveMySeed();

                // try an upload
                String error;
                if ((error = Network.saveSeedList(sb)) == null) {
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

        if (post.containsKey("seedFileSettings") || post.containsKey("seedFtpSettings") || post.containsKey("seedScpSettings")) {
            // set backlink
            prop.put("needsRestart_referer", "Settings_p.html?page=seed");
        }

        /*
         * Loop through the available seed uploaders to see if the
         * configuration of one of them has changed
         */
        final HashMap<String, String> uploaders = Network.getSeedUploadMethods();
        final Iterator<String> uploaderKeys = uploaders.keySet().iterator();
        while (uploaderKeys.hasNext()) {
            // get the uploader module name
            final String uploaderName = uploaderKeys.next();


            // determining if the user has reconfigured the settings of this uploader
            if (post.containsKey("seed" + uploaderName + "Settings")) {
                nothingChanged = true;
                final yacySeedUploader theUploader = Network.getSeedUploader(uploaderName);
                final String[] configOptions = theUploader.getConfigurationOptions();
                if (configOptions != null) {
                    for (final String configOption : configOptions) {
                        final String newSettings = post.get(configOption,"");
                        final String oldSettings = env.getConfig(configOption,"");
                        // bitwise AND with boolean is same as logic AND
                        nothingChanged &= newSettings.equals(oldSettings);
                        if (!nothingChanged) {
                            env.setConfig(configOption,newSettings);
                        }
                    }
                }
                if (!nothingChanged) {
                    // if the seed upload method is equal to the seed uploader whose settings
                    // were changed, we now try to upload the seed list with the new settings
                    if (env.getConfig("seedUploadMethod","none").equalsIgnoreCase(uploaderName)) {
                        String error;
                        if ((error = Network.saveSeedList(sb)) == null) {

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
            // set backlink
            prop.put("needsRestart_referer", "Settings_p.html?page=messageForwarding");

            env.setConfig("msgForwardingEnabled", post.containsKey("msgForwardingEnabled"));
            env.setConfig("msgForwardingCmd", post.get("msgForwardingCmd"));
            env.setConfig("msgForwardingTo", post.get("msgForwardingTo"));

            prop.put("info", "21");
            prop.put("info_msgForwardingEnabled", post.containsKey("msgForwardingEnabled") ? "on" : "off");
            prop.putHTML("info_msgForwardingCmd", post.get("msgForwardingCmd"));
            prop.putHTML("info_msgForwardingTo", post.get("msgForwardingTo"));

            return prop;
        }

        // Crawler settings
        if (post.containsKey("crawlerSettings")) {

            // set backlink
            prop.put("needsRestart_referer", "Settings_p.html?page=crawler");

            // get Crawler Timeout
            String timeoutStr = post.get("crawler.clientTimeout");
            if (timeoutStr==null||timeoutStr.length()==0) timeoutStr = "10000";

            int crawlerTimeout;
            try {
                crawlerTimeout = Integer.parseInt(timeoutStr);
                if (crawlerTimeout < 0) crawlerTimeout = 0;
                env.setConfig("crawler.clientTimeout", Integer.toString(crawlerTimeout));
            } catch (final NumberFormatException e) {
                prop.put("info", "29");
                prop.putHTML("info_crawler.clientTimeout",post.get("crawler.clientTimeout"));
                return prop;
            }

            // get maximum http file size
            String maxSizeStr = post.get("crawler.http.maxFileSize");
            if (maxSizeStr==null||maxSizeStr.length()==0) maxSizeStr = "-1";

            long maxHttpSize;
            try {
                maxHttpSize = Integer.parseInt(maxSizeStr);
                if(maxHttpSize < 0) {
                    maxHttpSize = -1;
                }
                env.setConfig("crawler.http.maxFileSize", Long.toString(maxHttpSize));
            } catch (final NumberFormatException e) {
                prop.put("info", "30");
                prop.putHTML("info_crawler.http.maxFileSize",post.get("crawler.http.maxFileSize"));
                return prop;
            }

            // get maximum ftp file size
            maxSizeStr = post.get("crawler.ftp.maxFileSize");
            if (maxSizeStr==null||maxSizeStr.length()==0) maxSizeStr = "-1";

            long maxFtpSize;
            try {
                maxFtpSize = Integer.parseInt(maxSizeStr);
                env.setConfig("crawler.ftp.maxFileSize", Long.toString(maxFtpSize));
            } catch (final NumberFormatException e) {
                prop.put("info", "31");
                prop.putHTML("info_crawler.ftp.maxFileSize",post.get("crawler.ftp.maxFileSize"));
                return prop;
            }

            maxSizeStr = post.get("crawler.smb.maxFileSize");
            if (maxSizeStr==null||maxSizeStr.length()==0) maxSizeStr = "-1";

            long maxSmbSize;
            try {
                maxSmbSize = Integer.parseInt(maxSizeStr);
                env.setConfig("crawler.smb.maxFileSize", Long.toString(maxSmbSize));
            } catch (final NumberFormatException e) {
                prop.put("info", "31");
                prop.putHTML("info_crawler.smb.maxFileSize",post.get("crawler.smb.maxFileSize"));
                return prop;
            }

            maxSizeStr = post.get("crawler.file.maxFileSize");
            if (maxSizeStr==null||maxSizeStr.length()==0) maxSizeStr = "-1";

            long maxFileSize;
            try {
                maxFileSize = Integer.parseInt(maxSizeStr);
                env.setConfig("crawler.file.maxFileSize", Long.toString(maxFileSize));
            } catch (final NumberFormatException e) {
                prop.put("info", "31");
                prop.putHTML("info_crawler.file.maxFileSize",post.get("crawler.file.maxFileSize"));
                return prop;
            }

            // everything is ok
            prop.put("info_crawler.clientTimeout",(crawlerTimeout==0) ? "0" :Formatter.number(crawlerTimeout/1000.0,false)+" sec");
            prop.put("info_crawler.http.maxFileSize",(maxHttpSize==-1)? "-1":Formatter.bytesToString(maxHttpSize));
            prop.put("info_crawler.ftp.maxFileSize", (maxFtpSize==-1) ? "-1":Formatter.bytesToString(maxFtpSize));
            prop.put("info_crawler.smb.maxFileSize", (maxSmbSize==-1) ? "-1":Formatter.bytesToString(maxSmbSize));
            prop.put("info_crawler.file.maxFileSize", (maxFileSize==-1) ? "-1":Formatter.bytesToString(maxFileSize));
            prop.put("info", "28");
            return prop;
        }

        // server port settings
        if (post.containsKey("serverports")) {
            final int port = post.getInt("port", 8090);
            if (port > 0) env.setConfig(SwitchboardConstants.SERVER_PORT, port);
            final int portssl = post.getInt("port.ssl", 8443);
            if (portssl > 0) env.setConfig(SwitchboardConstants.SERVER_SSLPORT, portssl);
            final int portshutdown = post.getInt("port.shutdown", -1);
            env.setConfig(SwitchboardConstants.SERVER_SHUTDOWNPORT, portshutdown);
            prop.put("info_port", port);
            prop.put("info_port.ssl", portssl);
            prop.put("info_port.shutdown", portshutdown);
            prop.put("needsRestart_referer", "Settings_p.html?page=ServerAccess");
            prop.put("info", "36");
            return prop;
        }

        // change https port
        if (post.containsKey("port.ssl")) {

            // set backlink
            prop.put("needsRestart_referer", "Settings_p.html?page=ProxyAccess");

            final int port = post.getInt("port.ssl", 8443);
            if (port > 0 && port != env.getConfigInt(SwitchboardConstants.SERVER_SSLPORT, 8443)) {
                env.setConfig(SwitchboardConstants.SERVER_SSLPORT, port);
            }
            prop.put("info_port.ssl", port);
            prop.put("info", "32");
            return prop;
        }

        // Debug/Analysis settings
        if (post.containsKey("debugAnalysisSettings")) {
            // set backlink
            prop.put("needsRestart_referer", "Settings_p.html?page=debug");

        	boolean tickedCheckbox = post.containsKey("solrBinaryResponse");
            env.setConfig(SwitchboardConstants.REMOTE_SOLR_BINARY_RESPONSE_ENABLED, tickedCheckbox);

            tickedCheckbox = post.containsKey("searchTestLocalDHT");
            env.setConfig(SwitchboardConstants.DEBUG_SEARCH_REMOTE_DHT_TESTLOCAL, tickedCheckbox);

            tickedCheckbox = post.containsKey("searchTestLocalSolr");
            env.setConfig(SwitchboardConstants.DEBUG_SEARCH_REMOTE_SOLR_TESTLOCAL, tickedCheckbox);

            tickedCheckbox = post.containsKey("searchShowRanking");
            env.setConfig(SwitchboardConstants.SEARCH_RESULT_SHOW_RANKING, tickedCheckbox);

            tickedCheckbox = post.containsKey(SwitchboardConstants.DEBUG_SNIPPETS_STATISTICS_ENABLED);
			sb.setConfig(SwitchboardConstants.DEBUG_SNIPPETS_STATISTICS_ENABLED, tickedCheckbox);
			TextSnippet.statistics.setEnabled(tickedCheckbox);

            /* For easier user understanding, the following flags controlling data sources selection
             * are rendered in the UI as checkboxes corresponding to enabled value when ticked */
            tickedCheckbox = post.containsKey("searchLocalDHT");
            env.setConfig(SwitchboardConstants.DEBUG_SEARCH_LOCAL_DHT_OFF, !tickedCheckbox);

            tickedCheckbox = post.containsKey("searchLocalSolr");
            env.setConfig(SwitchboardConstants.DEBUG_SEARCH_LOCAL_SOLR_OFF, !tickedCheckbox);

            tickedCheckbox = post.containsKey("searchRemoteDHT");
            env.setConfig(SwitchboardConstants.DEBUG_SEARCH_REMOTE_DHT_OFF, !tickedCheckbox);

            tickedCheckbox = post.containsKey("searchRemoteSolr");
            env.setConfig(SwitchboardConstants.DEBUG_SEARCH_REMOTE_SOLR_OFF, !tickedCheckbox);

            /* Let's clean up all search events as these settings affect how search is performed and we don't
             * want cached results obtained with the previous settings */
            SearchEventCache.cleanupEvents(true);

            prop.put("info", "34");
            return prop;
        }

        // Referrer Policy settings
        if (post.containsKey("referrerPolicySettings")) {
            // set backlink
            prop.put("needsRestart_referer", "Settings_p.html?page=referrer");

        	final String metaPolicy = post.get("metaPolicy", SwitchboardConstants.REFERRER_META_POLICY_DEFAULT);
            env.setConfig(SwitchboardConstants.REFERRER_META_POLICY, metaPolicy);

        	final boolean tickedCheckbox = post.containsKey("searchResultNoReferrer");
            env.setConfig(SwitchboardConstants.SEARCH_RESULT_NOREFERRER, tickedCheckbox);

            prop.put("info", "35");
            return prop;
        }

        // HTTP client settings
        if (post.containsKey("httpClientSettings")) {
            // set backlink
            prop.put("needsRestart_referer", "Settings_p.html?page=httpClient");

            if(System.getProperty("jsse.enableSNIExtension") == null) {
            	/* Only apply custom SNI extension settings when the JVM system option jsse.enableSNIExtension is not defined */
            	env.setConfig(SwitchboardConstants.HTTP_OUTGOING_GENERAL_TLS_SNI_EXTENSION_ENABLED,
            			post.containsKey(SwitchboardConstants.HTTP_OUTGOING_GENERAL_TLS_SNI_EXTENSION_ENABLED));

            	env.setConfig(SwitchboardConstants.HTTP_OUTGOING_REMOTE_SOLR_TLS_SNI_EXTENSION_ENABLED,
            			post.containsKey(SwitchboardConstants.HTTP_OUTGOING_REMOTE_SOLR_TLS_SNI_EXTENSION_ENABLED));
            }
            sb.initOutgoingConnectionSettings();

            prop.put("info", "38");
            return prop;
        }

        // nothing made
        prop.put("info", "1");//no information submitted
        return prop;
    }

}

// dir.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
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
//
// You must compile this file with
// javac -classpath <application_root>/classes <application_root>/htroot/htdocsdefault/dir.java
// which most probably means to compile this with
// javac -classpath ../../classes dir.java

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import de.anomic.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.net.InetAddress;
import java.net.UnknownHostException;
import de.anomic.http.httpHeader;
import de.anomic.index.indexEntryAttribute;
import de.anomic.index.indexURL;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.plasma.plasmaCondenser;
import de.anomic.plasma.plasmaCrawlLURL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCodings;
import de.anomic.server.serverCore;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverMemory;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.data.userDB;

public class dir {

    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    public static String dateString(Date date) {
        return SimpleFormatter.format(date);
    }

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();

//      System.out.println("###Header="+ header);
//      System.out.println("###post=" + post);
        String action = ((post == null) ? "info" : post.get("action", "info"));

        // variables for this path
//      File   htroot = new File(switchboard.getRootPath(), switchboard.getConfig("htRootPath", "htroot"));
        final File   htroot = new File(switchboard.getRootPath(), switchboard.getConfig("htDocsPath", "DATA/HTDOCS"));
        String path   = (String) header.get("PATH", "/");
        int pos = path.lastIndexOf("/");
        if (pos >= 0) { path = path.substring(0, pos + 1); }
        final File dir = new File(htroot, path);

        // general settings
        prop.put("peername", env.getConfig("peerName", "<nameless>"));
        prop.put("peerdomain", env.getConfig("peerName", "<nameless>").toLowerCase());
        prop.put("peeraddress", yacyCore.seedDB.mySeed.getAddress());
        prop.put("hostname", serverCore.publicIP());
        try{
            prop.put("hostip", InetAddress.getByName(serverCore.publicIP()).getHostAddress());
        }catch(UnknownHostException e){
            prop.put("hostip", "Unknown Host Exception");
        }      
        prop.put("port", serverCore.getPortNr(env.getConfig("port","8080")));

        // generate upload/download authorizations
        final String adminAccountBase64MD5    = switchboard.getConfig("adminAccountBase64MD5", "");
        final String uploadAccountBase64MD5   = switchboard.getConfig("uploadAccountBase64MD5", "");
        final String downloadAccountBase64MD5 = switchboard.getConfig("downloadAccountBase64MD5", "");

        userDB.Entry entry = switchboard.userDB.proxyAuth((String)header.get("Authorization", "xxxxxx"));
        boolean adminAuthorization, downloadAuthorization, uploadAuthorization;
        if(entry == null){
            final String authorizationMD5 = de.anomic.server.serverCodings.encodeMD5Hex(((String) header.get("Authorization", "xxxxxx")).trim().substring(6));
//          if (logoutAccountBase64.equals(authorization))
            adminAuthorization = (adminAccountBase64MD5.length() != 0 && adminAccountBase64MD5.equals(authorizationMD5));
            uploadAuthorization = (adminAuthorization ||(uploadAccountBase64MD5.length() != 0 && uploadAccountBase64MD5.equals(authorizationMD5)));
            downloadAuthorization = (adminAuthorization || uploadAuthorization || downloadAccountBase64MD5.length() == 0 || downloadAccountBase64MD5.equals(authorizationMD5));
        }else{ //userDB
            adminAuthorization=entry.hasAdminRight();
            uploadAuthorization=entry.hasUploadRight();
            downloadAuthorization=entry.hasDownloadRight();
        }

        // do authentitcate processes by triggering the http authenticate method
        if (action.equals("authenticateAdmin") && !adminAuthorization) {
            prop.put("AUTHENTICATE", "admin log-in");
            return prop;
        }
        if (action.equals("authenticateUpload") && !uploadAuthorization) {
            prop.put("AUTHENTICATE", "upload log-in");
            return prop;
        }
        if (action.equals("authenticateDownload") && !downloadAuthorization) {
            prop.put("AUTHENTICATE", "download log-in");
            return prop;
        }

        // work off actions
        if (action.equals("logout")) {
            if (adminAuthorization) {
                prop.put("AUTHENTICATE", "admin log-in");
                return prop;
            } else if (uploadAuthorization) {
                prop.put("AUTHENTICATE", "upload log-in");
                return prop;
            } else if (downloadAuthorization) {
                prop.put("AUTHENTICATE", "download log-in");
                return prop;
            } else {
                action = "";
            }
        } else if (action.equals("downloadPassword") && adminAuthorization) {
            switchboard.setConfig("downloadAccountBase64MD5", (post.get("password", "").length() == 0) ? "" : serverCodings.encodeMD5Hex(kelondroBase64Order.standardCoder.encodeString("download:" + post.get("password", ""))));
        } else if (action.equals("uploadPassword") && adminAuthorization) {
            switchboard.setConfig("uploadAccountBase64MD5", (post.get("password", "").length() == 0) ? "" : serverCodings.encodeMD5Hex(kelondroBase64Order.standardCoder.encodeString("upload:" + post.get("password", ""))));
        } else if (action.equals("upload") && (uploadAuthorization || adminAuthorization)) {
            String filename = new File(post.get("file", "dummy")).getName();
            String description = post.get("description", "");
            pos = filename.lastIndexOf("\\");
            if (pos >= 0) { filename = filename.substring(pos + 1); }
            final File newfile    = new File(dir, filename);
            final File newfilemd5 = new File(dir, filename + ".md5");
            final byte[] binary = (byte[]) post.get("file$file", new byte[0]);
            try {
                serverFileUtils.write(binary, newfile);
                String md5s = serverCodings.encodeMD5Hex(newfile);
                serverFileUtils.write((md5s + "\n" + description).getBytes("UTF-8"), newfilemd5); // generate md5

                // index file info
                if (post.get("indexing", "").equals("on")) {
                    final String urlstring = yacyhURL(yacyCore.seedDB.mySeed, filename, md5s);
                    final String phrase = filename.replace('.', ' ').replace('_', ' ').replace('-', ' ');
                    indexPhrase(switchboard, urlstring, phrase, description);
                }
            } catch (IOException e) {}
        } else if (action.equals("newdir") && (uploadAuthorization || adminAuthorization)) {
            final String newdirname = post.get("directory", "EmptyDir");
            if (newdirname != null && newdirname.length() > 0) {
                final File newdir = new File(dir, newdirname);
                newdir.mkdir();
            }
        } else if (action.equals("delete") && adminAuthorization) {
            String filename = post.get("file", "foo");
            final File file = new File(dir, filename);
            if (file.exists()) {
                final File filemd5 = new File(dir, post.get("file", "foo") + ".md5");
                // read md5 and phrase
                String md5s = "";
                String description = "";
                if (filemd5.exists()) try {
                    md5s = new String(serverFileUtils.read(filemd5));
                    pos = md5s.indexOf('\n');
                    if (pos >= 0) {
                        description = md5s.substring(pos + 1);
                        md5s = md5s.substring(0, pos);
                    }
                } catch (IOException e) {}
                // delete file(s)
                if (file.isDirectory()) {
                    final String[] content = file.list();
                    for (int i = 0; i < content.length; i++) (new File(file, content[i])).delete();
                    file.delete();
                } else if (file.isFile()) {
                    file.delete();
                    if (filemd5.exists()) filemd5.delete();
                }
                // delete index
                final String urlstring = yacyhURL(yacyCore.seedDB.mySeed, filename, md5s);
                final String phrase = filename.replace('.', ' ').replace('_', ' ').replace('-', ' ');
                deletePhrase(switchboard, urlstring, phrase, description);
            }
        }

        // if authorized, generate directory tree listing
        if ((adminAuthorization) || (uploadAuthorization) || (downloadAuthorization)) {
            // generate dir listing
            final String[] list = dir.list();
            File f, fmd5;
            String md5s, description;
            Date d;
            // tree += "<span class=\"tt\">path&nbsp;=&nbsp;" + path + "</span><br><br>";
            if (list != null) {
                int filecount = 0, fileIdx = 0;
                prop.put("path", path);
                
                boolean dark = false;
                for (int i = 0; i < list.length; i++) {
                    if (!((list[i].startsWith("dir.")) || (list[i].endsWith(".md5")))) {
                        
                        prop.put("dirlist_" + fileIdx + "_dark" , dark?1:0);
                        
                        dark = !dark;
                        filecount++;
                        f = new File(dir, list[i]);
                        fmd5 = new File(dir, list[i] + ".md5");
                        try {
                            if (fmd5.exists()) {
                                md5s = new String(serverFileUtils.read(fmd5));
                                pos = md5s.indexOf('\n');
                               if (pos >= 0) {
                                   description = md5s.substring(pos + 1);
                                   md5s = md5s.substring(0, pos);
                               } else {
                                   description = "";
                               }                
                            } else {
                                // generate md5 on-the-fly
                                md5s = serverCodings.encodeMD5Hex(f);
                                description = "";
                                serverFileUtils.write((md5s + "\n" + description).getBytes("UTF-8"), fmd5);
                            }
                        } catch (IOException e) {
                            md5s = "";
                            description = "";
                        }
                        d = new Date(f.lastModified());
                        if (f.isDirectory()) {
                            prop.put("dirlist_" + fileIdx + "_dir" , 1);                            
                            prop.put("dirlist_" + fileIdx + "_dir_date" , dateString(d));
                            prop.put("dirlist_" + fileIdx + "_dir_name" , list[i]);                            
                        } else {
                            prop.put("dirlist_" + fileIdx + "_dir" , 0);
                            prop.put("dirlist_" + fileIdx + "_dir_date" , dateString(d));
                            prop.put("dirlist_" + fileIdx + "_dir_name" , list[i]); 
                            prop.put("dirlist_" + fileIdx + "_dir_size" , serverMemory.bytesToString(f.length()).replaceAll(" ", "&nbsp;"));
                            prop.put("dirlist_" + fileIdx + "_dir_yacyhURL",yacyhURL(yacyCore.seedDB.mySeed, f.getName(), md5s));
                            prop.put("dirlist_" + fileIdx + "_dir_md5s",md5s);
                            
                            boolean showImage = (description.length() == 0) && (list[i].endsWith(".jpg") || list[i].endsWith(".gif") || list[i].endsWith(".png"));
                            prop.put("dirlist_" + fileIdx + "_dir_descriptionMode",showImage?0:1);
                            if (showImage) {
                                prop.put("dirlist_" + fileIdx + "_dir_descriptionMode_image",list[i]);
                            } else {
                                prop.put("dirlist_" + fileIdx + "_dir_descriptionMode_text",description);
                            }                            
                        }
                        
                        prop.put("dirlist_" + fileIdx + "_adminAuthorization",adminAuthorization?1:0);
                        if (adminAuthorization) {
                            prop.put("dirlist_" + fileIdx + "_adminAuthorization_name",list[i]);
                        }                        
                        
//                      if (adminAuthorization) tree += "</form> "; else tree += "<br>";
                        fileIdx++;
                    }
                }
                
                prop.put("dirlist",filecount);
                prop.put("emptydir", filecount == 0 ? 1:0);
            }
            prop.put("authenticated",1);
        } else {
            prop.put("authenticated",0);
        }
        

        String ident = "";
        String account = "";
        String service = "";
        String info = "";
        String logout = "";
        if (adminAuthorization) {
            ident = "Administrator";
            account = "<table border=\"0\" cellpadding=\"0\" cellspacing=\"2\" width=\"100%\">" +
                      "<tr class=\"TableCellDark\"><td class=\"small\">upload:</td><td><form action=\"#\" method=\"post\" enctype=\"multipart/form-data\">" +
                      "<input type=\"hidden\" name=\"action\" value=\"uploadPassword\">" + 
                      "<input type=\"password\" name=\"password\" size=\"12\">&nbsp;" +
                      "<input type=\"submit\" value=\"Set Password\" class=\"small\">" +
                      "</form></td></tr>" +
                      "<tr class=\"TableCellLight\"><td class=\"small\">download:</td><td><form action=\"#\" method=\"post\" enctype=\"multipart/form-data\">" +
                      "<input type=\"hidden\" name=\"action\" value=\"downloadPassword\">" + 
                      "<input type=\"password\" name=\"password\" size=\"12\">&nbsp;" +
                      "<input type=\"submit\" value=\"Set Password\" class=\"small\">" +
                      "</form></td></tr>" +
                      "</table>";
            logout = "<form action=\"#\" method=\"post\" enctype=\"multipart/form-data\">" +
                     "<input type=\"hidden\" name=\"action\" value=\"logout\">" + 
                     "<input type=\"submit\" value=\"Log-Out Administrator\" class=\"small\">&nbsp;(enter&nbsp;empty&nbsp;account)" +
                     "</form>";
            service = "<table border=\"0\" cellpadding=\"0\" cellspacing=\"2\" width=\"100%\">" +
                      "<tr class=\"TableCellDark\">" +
                      "<td class=\"small\">New Directory:</td>" +
                      "<td><form action=\"#\" method=\"post\" enctype=\"multipart/form-data\">" +
                      "<input type=\"hidden\" name=\"action\" value=\"newdir\">" + 
                      "<input type=\"text\" name=\"directory\" size=\"10\" class=\"small\">&nbsp;" +
                      "<input type=\"submit\" value=\"Create\" class=\"small\">" +
                      "</form></td></tr>" +
                      "<tr class=\"TableCellLight\">" +
                      "<td class=\"small\">File Upload:</td>" +
                      "<td class=\"small\"><form action=\"#\" method=\"post\" enctype=\"multipart/form-data\">" +
                      "Resource&nbsp;=&nbsp;<input type=\"hidden\" name=\"action\" value=\"upload\">" + 
                      "<input type=\"file\" name=\"file\" size=\"10\" class=\"small\"><br>" +
                      "Description&nbsp;=&nbsp;<input type=\"text\" name=\"description\" size=\"30\" class=\"small\"><br>" +
                      "Indexing&nbsp;:&nbsp;<input type=\"checkbox\" name=\"indexing\" checked><br>" +
                      "<input type=\"submit\" value=\"Transfer\" class=\"small\">" +
                      "</form></td></tr>" +
                      "</table>";
            info = "Admin and download accounts are necessary to grant their services to clients; " +
                   "no password is required for the download-account unless you set one. " +
                   "Files uploaded and indexed here have a special index entry 'yacyshare'; " +
                   "if you want to find files that are indexed in any share zone, add the word 'yacyshare' to the search words.";
        } else if (uploadAuthorization) {
            ident = "Uploader";
            account = "<form action=\"#\" method=\"post\" enctype=\"multipart/form-data\">" +
                      "<input type=\"hidden\" name=\"action\" value=\"authenticateAdmin\">" +
                      "<input type=\"submit\" value=\"Log-In as Administrator\" class=\"small\">" +
                      "</form>";
            if (uploadAccountBase64MD5.length() == 0) {
                logout = "";
            } else {
                logout = "<form action=\"#\" method=\"post\" enctype=\"multipart/form-data\">" +
                         "<input type=\"hidden\" name=\"action\" value=\"logout\">" + 
                         "<input type=\"submit\" value=\"Log-Out 'upload'\" class=\"small\">&nbsp;(enter&nbsp;empty&nbsp;account)" +
                         "</form>";
            }
            service = "<table border=\"0\" cellpadding=\"0\" cellspacing=\"2\" width=\"100%\">" +
                      "<tr class=\"TableCellDark\">" +
                      "<td class=\"small\">New Directory:</td>" +
                      "<td class=\"small\"><form action=\"#\" method=\"post\" enctype=\"multipart/form-data\">" +
                      "<input type=\"hidden\" name=\"action\" value=\"newdir\">" + 
                      "<input type=\"text\" name=\"directory\" size=\"10\" class=\"small\">&nbsp;" +
                      "<input type=\"submit\" value=\"Create\" class=\"small\">" +
                      "</form></td></tr>" +
                      "<tr class=\"TableCellLight\">" +
                      "<td class=\"small\">File Upload:</td>" +
                      "<td class=\"small\"><form action=\"#\" method=\"post\" enctype=\"multipart/form-data\">" +
                      "Resource&nbsp;=&nbsp;<input type=\"hidden\" name=\"action\" value=\"upload\">" + 
                      "<input type=\"file\" name=\"file\" size=\"10\" class=\"small\"><br>" +
                      "Description&nbsp;=&nbsp;<input type=\"text\" name=\"description\" size=\"30\" class=\"small\"><br>" +
                      "Indexing&nbsp;:&nbsp;<input type=\"checkbox\" name=\"indexing\"><br>" +
                      "<input type=\"submit\" value=\"Transfer\" class=\"small\">" +
                      "</form></td></tr>" +
                      "</table>";
            info = "Uploaders are not granted to delete files or directories. If you want to do this, log-in as admin.";
        } else if (downloadAuthorization) {
            ident = "Downloader"; 
            account = "<form action=\"#\" method=\"post\" enctype=\"multipart/form-data\">" +
                      "<input type=\"hidden\" name=\"action\" value=\"authenticateAdmin\" class=\"small\">" +
                      "<input type=\"submit\" value=\"Log-In as Administrator\" class=\"small\">" +
                      "</form> " +
                      "<form action=\"#\" method=\"post\" enctype=\"multipart/form-data\">" +
                      "<input type=\"hidden\" name=\"action\" value=\"authenticateUpload\" class=\"small\">" +
                      "<input type=\"submit\" value=\"Log-In as user 'upload'\" class=\"small\">" +
                      "</form>";
            if (downloadAccountBase64MD5.length() == 0) {
                logout = "";
            } else {
                logout = "<form action=\"#\" method=\"post\" enctype=\"multipart/form-data\">" +
                         "<input type=\"hidden\" name=\"action\" value=\"logout\">" + 
                         "<input type=\"submit\" value=\"Log-Out 'download'\" class=\"small\">&nbsp;(enter&nbsp;empty&nbsp;account)" +
                         "</form>";
                service = "You are granted to view directory listings and do downloads in this directory.<br>" +
                          "If you want to upload, please log in as user 'upload'";
                info = "Download is granted even if no download account has been defined. " +
                       "If you are an administrator and you wish to block non-authorized downloades, please log in as user 'admin' " +
                       "and set a download password.";
            }
        } else {
            ident = "not authorized";
            account = "<form action=\"#\" method=\"post\" enctype=\"multipart/form-data\">" +
                      "<input type=\"hidden\" name=\"action\" value=\"authenticateAdmin\">" +
                      "<input type=\"submit\" value=\"Log-In as Administrator\" class=\"small\">" +
                      "</form> " +
                      "<form action=\"#\" method=\"post\" enctype=\"multipart/form-data\">" +
                      "<input type=\"hidden\" name=\"action\" value=\"authenticateUpload\">" +
                      "<input type=\"submit\" value=\"Log-In as user 'upload'\" class=\"small\">" +
                      "</form> " +
                      "<form action=\"#\" method=\"post\" enctype=\"multipart/form-data\">" +
                      "<input type=\"hidden\" name=\"action\" value=\"authenticateDownload\">" +
                      "<input type=\"submit\" value=\"Log-In as user 'download'\" class=\"small\">" +
                      "</form>";
            logout = "";
            service = "No service available.";
            info = "You must log-in to upload or download.";
        }
        
        prop.put("ident", ident);
        prop.put("account", account);
        prop.put("service", service);
        prop.put("info", info);
        prop.put("logout", logout);
//      return rewrite properties
        return prop;
    }

    private static String formatLong(long l, int length) {
        String r = "" + l;
        for (int i = r.length(); i < length; i++) { r = "&nbsp;" + r; }
        return r;
    }

    // rDNS services:
    // http://www.xdr2.net/reverse_DNS_lookup.asp
    // http://remote.12dt.com/rns/
    // http://bl.reynolds.net.au/search/
    // http://www.declude.com/Articles.asp?ID=97
    // http://www.dnsstuff.com/

    // listlist: http://www.aspnetimap.com/help/welcome/dnsbl.html

    public static String yacyhURL(yacySeed seed, String filename, String md5) {
        return "http://share." + seed.getHexHash() + ".yacyh/" + filename + "?md5=" + md5;
    }

    public static void indexPhrase(plasmaSwitchboard switchboard, String urlstring, String phrase, String descr) {
        try {
            final URL url = new URL(urlstring);
            final plasmaCondenser condenser = new plasmaCondenser(new ByteArrayInputStream(("yacyshare. " + phrase + ". " + descr).getBytes()));
            final plasmaCrawlLURL.Entry newEntry = switchboard.urlPool.loadedURL.newEntry(
                url, "YaCyShare: " + descr, new Date(), new Date(),
                "AAAAAAAAAAAA", /*referrer*/
                0, /*copycount*/
                false, /*localneed*/
                condenser.RESULT_WORD_ENTROPHY,
                "**", /*language*/
                indexEntryAttribute.DT_SHARE, /*doctype*/
                phrase.length(), /*size*/
                condenser.RESULT_NUMB_WORDS
            );
            newEntry.store();
            switchboard.urlPool.loadedURL.stackEntry(
                    newEntry,
                    "____________", /*initiator*/
                    yacyCore.seedDB.mySeed.hash, /*executor*/
                    5 /*process case*/
                );
            
            final String urlHash = newEntry.hash();
            /*final int words =*/ switchboard.wordIndex.addPageIndex(url, urlHash, new Date(), phrase.length() + descr.length() + 13, null, condenser, "**", indexEntryAttribute.DT_SHARE, 0, 0);
        } catch (IOException e) {}
    }

    public static void deletePhrase(plasmaSwitchboard switchboard, String urlstring, String phrase, String descr) {
        try {
            final String urlhash = indexURL.urlHash(new URL(urlstring));
            final Iterator words = plasmaCondenser.getWords(("yacyshare " + phrase + " " + descr).getBytes("UTF-8"));
            Map.Entry entry;
            while (words.hasNext()) {
                entry = (Map.Entry) words.next();
                switchboard.wordIndex.removeEntry(indexEntryAttribute.word2hash((String) entry.getKey()), urlhash, true);
            }
            switchboard.urlPool.loadedURL.remove(urlhash);
        } catch (Exception e) {
            serverLog.logSevere("DIR", "INTERNAL ERROR in dir.deletePhrase", e);
        }
    }

}

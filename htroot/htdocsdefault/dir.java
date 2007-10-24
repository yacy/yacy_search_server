// dir.java 
// -----------------------
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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;

import de.anomic.data.userDB;
import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.http.httpd;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.plasma.plasmaCondenser;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCodings;
import de.anomic.server.serverCore;
import de.anomic.server.serverDomains;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverMemory;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.dirlistComparator;
import de.anomic.tools.md5DirFileFilter;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;

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
        prop.putHTML("peername", env.getConfig("peerName", "<nameless>"));
        prop.putHTML("peerdomain", env.getConfig("peerName", "<nameless>").toLowerCase());
        prop.putHTML("peeraddress", yacyCore.seedDB.mySeed().getPublicAddress());
        prop.put("hostname", serverDomains.myPublicIP());
        try{
            prop.put("hostip", InetAddress.getByName(serverDomains.myPublicIP()));
        }catch(UnknownHostException e){
            prop.put("hostip", "Unknown Host Exception");
        }      
        prop.put("port", serverCore.getPortNr(env.getConfig("port","8080")));

        // generate upload/download authorizations
        final String adminAccountBase64MD5    = switchboard.getConfig(httpd.ADMIN_ACCOUNT_B64MD5, "");
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
            adminAuthorization=entry.hasRight(userDB.Entry.ADMIN_RIGHT);
            uploadAuthorization=entry.hasRight(userDB.Entry.UPLOAD_RIGHT);
            downloadAuthorization=entry.hasRight(userDB.Entry.DOWNLOAD_RIGHT);
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
                byte[] md5 = serverCodings.encodeMD5Raw(newfile);
                String md5s = serverCodings.encodeHex(md5);
                serverFileUtils.write((md5s + "\n" + description).getBytes("UTF-8"), newfilemd5); // generate md5

                // index file info
                if (post.get("indexing", "").equals("on")) {
                    final String urlstring = yacyhURL(yacyCore.seedDB.mySeed(), filename, md5s);
                    final String phrase = filename.replace('.', ' ').replace('_', ' ').replace('-', ' ');
                    indexPhrase(switchboard, urlstring, phrase, description, md5);
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
                final String urlstring = yacyhURL(yacyCore.seedDB.mySeed(), filename, md5s);
                final String phrase = filename.replace('.', ' ').replace('_', ' ').replace('-', ' ');
                deletePhrase(switchboard, urlstring, phrase, description);
            }
        }

        // if authorized, generate directory tree listing
        if ((adminAuthorization) || (uploadAuthorization) || (downloadAuthorization)) {
            // generate dir listing
            md5DirFileFilter fileFilter = new md5DirFileFilter();
            final File[] list = dir.listFiles(fileFilter);
            
            // sorting the dir list
            dirlistComparator comparator = new dirlistComparator();
            Arrays.sort(list,comparator);
            
            String md5s, description;
            // tree += "<span class=\"tt\">path&nbsp;=&nbsp;" + path + "</span><br><br>";
            if (list != null) {
                int filecount = 0, fileIdx = 0;
                prop.putHTML("path", path);
                
                boolean dark = false;
                for (int i = 0; i < list.length; i++) {

                    filecount++;
                    File f = list[i];
                    String fileName = f.getName();

                    // changing table row color
                    prop.put("dirlist_" + fileIdx + "_dark" , dark ? "1" : "0");
                    dark = !dark;                        


                    // reading the content of the md5 file that belongs
                    // to the file
                    File fmd5 = new File(dir, fileName + ".md5");
                    try {
                        if (fmd5.exists()) {
                            md5s = new String(serverFileUtils.read(fmd5));
                            pos = md5s.indexOf('\n');
                            if (pos >= 0) {
                                // the second line contains an optional description
                                description = md5s.substring(pos + 1);
                                // the first line contains the md5 sum of the file
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

                    // last modification date if the entry
                    prop.put("dirlist_" + fileIdx + "_dir_date" , dateString(new Date(f.lastModified())));
                    prop.put("dirlist_" + fileIdx + "_dir_rfc822date" , httpc.dateString(new Date(f.lastModified())));
                    prop.put("dirlist_" + fileIdx + "_dir_timestamp" , Long.toString(f.lastModified()));
                    // the entry name
                    prop.putHTML("dirlist_" + fileIdx + "_dir_name" , fileName);                     

                    if (f.isDirectory()) {
                        // the entry is a directory
                        prop.put("dirlist_" + fileIdx + "_dir" , "1");
                        prop.putHTML("dirlist_" + fileIdx + "_dir_URL","http://" + yacyCore.seedDB.mySeed().getPublicAddress() + path + fileName + "/");
                    } else {
                        // determine if we should display the description string or a preview image
                        boolean showImage = /* (description.length() == 0) && */ (fileName.endsWith(".jpg") || fileName.endsWith(".gif") || fileName.endsWith(".png") || fileName.endsWith(".ico") || fileName.endsWith(".bmp"));

                        // the entry is a file
                        prop.put("dirlist_" + fileIdx + "_dir" , "0");
                        // the file size
                        prop.put("dirlist_" + fileIdx + "_dir_size" , serverMemory.bytesToString(f.length()));
                        prop.put("dirlist_" + fileIdx + "_dir_sizeBytes" , f.length());
                        // the unique url
                        prop.putHTML("dirlist_" + fileIdx + "_dir_yacyhURL",yacyhURL(yacyCore.seedDB.mySeed(), fileName, md5s));  
                        prop.putHTML("dirlist_" + fileIdx + "_dir_URL","http://" + yacyCore.seedDB.mySeed().getPublicAddress() + path + fileName);
                        // the md5 sum of the file
                        prop.put("dirlist_" + fileIdx + "_dir_md5s",md5s);
                        // description mode: 0...image preview, 1...description text 
                        prop.put("dirlist_" + fileIdx + "_dir_descriptionMode",showImage?0:1);
                        if (showImage) {
                            prop.putHTML("dirlist_" + fileIdx + "_dir_descriptionMode_image",fileName);
                        }
                        // always set the description tag (needed by rss and xml)
                        prop.putHTML("dirlist_" + fileIdx + "_dir_descriptionMode_text",description);                                                   
                    }

                    prop.put("dirlist_" + fileIdx + "_adminAuthorization",adminAuthorization ? "1" : "0");
                    prop.putHTML("dirlist_" + fileIdx + "_adminAuthorization_name",fileName);

                    fileIdx++;
                }

                prop.put("dirlist",filecount);
                prop.put("emptydir", filecount == 0 ? "1" : "0");
            }
            prop.put("authenticated", "1");
        } else {
            prop.put("authenticated", "0");
        }
        
        if (adminAuthorization) {
            prop.put("ident", "0");
            prop.put("logout", "0");
            prop.put("account", "0");
            prop.put("service", "0");
            prop.put("info", "0");
        } else if (uploadAuthorization) {
            prop.put("ident", "1");
            prop.put("logout", "1");
            prop.put("account", "1");
            prop.put("service", "0");
            prop.put("info", "1");
        } else if (downloadAuthorization) {
            prop.put("ident", "2"); 
            prop.put("logout", "2");
            prop.put("account", "2");
            prop.put("service", "1");
            prop.put("info", "2");

        } else {
            prop.put("ident", "3");
            prop.put("logout", "3");
            prop.put("account", "3");
            prop.put("service", "2");
            prop.put("info", "3");
        }
        
        // return rewrite properties
        return prop;
    }

    public static String yacyhURL(yacySeed seed, String filename, String md5) {
        return "http://share." + seed.getHexHash() + ".yacyh/" + filename + "?md5=" + md5;
    }

    public static void indexPhrase(plasmaSwitchboard switchboard, String urlstring, String phrase, String descr, byte[] md5) {
        try {
            final yacyURL url = new yacyURL(urlstring, null);
            final plasmaCondenser condenser = new plasmaCondenser(new ByteArrayInputStream(("yacyshare. " + phrase + ". " + descr).getBytes()), "UTF-8");
            final indexURLEntry newEntry = new indexURLEntry(
                url,
                "YaCyShare: " + descr,
                yacyCore.seedDB.mySeed().getName(),
                "", // tags
                "", // ETag
                new Date(), // modification
                new Date(), // loadtime
                new Date(), // freshtime
                "AAAAAAAAAAAA", // referrer
                md5, // md5
                phrase.length(), // size
                condenser.RESULT_NUMB_WORDS, // word count
                plasmaHTCache.DT_SHARE, // doctype
                new kelondroBitfield(4),
                "**", // language
                0,0,0,0,0,0
            );
            switchboard.wordIndex.loadedURL.store(newEntry);
            switchboard.wordIndex.loadedURL.stack(
                    newEntry,
                    "____________", /*initiator*/
                    yacyCore.seedDB.mySeed().hash, /*executor*/
                    5 /*process case*/
                );
            
            /*final int words =*/ switchboard.wordIndex.addPageIndex(url, new Date(), phrase.length() + descr.length() + 13, null, condenser, "**", plasmaHTCache.DT_SHARE, 0, 0);
        } catch (IOException e) {}
    }

    public static void deletePhrase(plasmaSwitchboard switchboard, String urlstring, String phrase, String descr) {
        try {
            final String urlhash = (new yacyURL(urlstring, null)).hash();
            final Iterator words = plasmaCondenser.getWords(("yacyshare " + phrase + " " + descr).getBytes("UTF-8"), "UTF-8").keySet().iterator();
            String word;
            while (words.hasNext()) {
                word = (String) words.next();
                switchboard.wordIndex.removeEntry(plasmaCondenser.word2hash(word), urlhash);
            }
            switchboard.wordIndex.loadedURL.remove(urlhash);
        } catch (Exception e) {
            serverLog.logSevere("DIR", "INTERNAL ERROR in dir.deletePhrase", e);
        }
    }

}

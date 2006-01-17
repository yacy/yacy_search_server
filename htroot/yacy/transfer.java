// transfer.java 
// -----------------------
// part of YaCy caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// created 07.11.2005
//
// $LastChangedDate: 2005-10-17 17:46:12 +0200 (Mon, 17 Oct 2005) $
// $LastChangedRevision: 947 $
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

import java.io.File;
import java.io.IOException;

import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaRankingDistribution;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.serverCodings;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public final class transfer {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        if (post == null || env == null) return null;
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();

        
        String process   = post.get("process", "");  // permission or store
        //String key       = post.get("key", "");      // a transmission key from the client
        String otherpeer = post.get("iam", "");      // identification of the client (a peer-hash)
        String purpose   = post.get("purpose", "");  // declares how the file shall be treated
        String filename  = post.get("filename", ""); // a name of a file without path
        //long   filesize  = Long.parseLong((String) post.get("filesize", "")); // the size of the file
        
        yacySeed otherseed = yacyCore.seedDB.get(otherpeer);
        if (otherseed == null) {
            // reject unknown peers
            // this does not appear fair, but anonymous senders are dangerous
            prop.put("process", 0);
            prop.put("response", "denied");
            prop.put("process_access", "");
            prop.put("process_address", "");
            prop.put("process_protocol", "");
            prop.put("process_path", "");
            prop.put("process_maxsize", "0");
            sb.getLog().logFine("RankingTransmission: rejected unknown peer '" + otherpeer + "'");
            return prop;
        }
        
        String otherpeerName = otherseed.hash + ":" + otherseed.getName();
        
        if (process.equals("permission")) {
            prop.put("process", 0);
            if (purpose.equals("crcon")) {
                // consolidation of cr files
                //System.out.println("yacy/transfer:post=" + post.toString());
                //String cansendprotocol = (String) post.get("can-send-protocol", "http");
                String access = kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(otherpeer + ":" + filename)) + ":" + kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw("" + System.currentTimeMillis()));
                prop.put("response", "ok");
                prop.put("process_access", access);
                prop.put("process_address", yacyCore.seedDB.mySeed.getAddress());
                prop.put("process_protocol", "http");
                prop.put("process_path", "");  // currently empty; the store process will find a path
                prop.put("process_maxsize", "-1"); // if response is too big we return the size of the file
                sb.rankingPermissions.put(serverCodings.encodeMD5Hex(kelondroBase64Order.standardCoder.encodeString(access)), filename);
                sb.getLog().logFine("RankingTransmission: granted peer " + otherpeerName + " to send CR file " + filename);
            }
            return prop;
        }

        if (process.equals("store")) {
            prop.put("process", 1);
            if (purpose.equals("crcon")) {
                byte[] filebytes = (byte[]) post.get("filename$file");
                String accesscode = post.get("access", "");   // one-time authentication
                String md5 = post.get("md5", "");   // one-time authentication
                //java.util.HashMap perm = sb.rankingPermissions;
                //System.out.println("PERMISSIONDEBUG: accesscode=" + accesscode + ", permissions=" + perm.toString());
                String grantedFile = (String) sb.rankingPermissions.get(accesscode);
                prop.put("process_tt", "");
                if ((grantedFile == null) || (!(grantedFile.equals(filename)))) {
                    // fraud-access of this interface
                    prop.put("response", "denied");
                    sb.getLog().logFine("RankingTransmission: denied " + otherpeerName + " to send CR file " + filename + ": wrong access code");
                } else {
                    sb.rankingPermissions.remove(accesscode); // not needed any more
                    File path = new File(sb.rankingPath, plasmaRankingDistribution.CR_OTHER);
                    path.mkdirs();
                    File file = new File(path, filename);
                    try {
                        if (file.getCanonicalPath().toString().startsWith(path.getCanonicalPath().toString())){
                            serverFileUtils.write(filebytes, file);
                            String md5t = serverCodings.encodeMD5Hex(file);
                            if (md5t.equals(md5)) {
                                prop.put("response", "ok");
                                sb.getLog().logFine("RankingTransmission: received from peer " + otherpeerName + " CR file " + filename);
                            } else {
                                prop.put("response", "transfer failure");
                                sb.getLog().logFine("RankingTransmission: transfer failunre from peer " + otherpeerName + " for CR file " + filename);
                            }
                        }else{
                            //exploit?
                            prop.put("response", "io error");
                            return prop;
                        }
                    } catch (IOException e) {
                        prop.put("response", "io error");
                    }
                }
            }
            return prop;
        }
        
        // wrong access
        prop.put("process", 0);
        prop.put("response", "denied");
        prop.put("process_access", "");
        prop.put("process_address", "");
        prop.put("process_protocol", "");
        prop.put("process_path", "");
        prop.put("process_maxsize", "0");
        sb.getLog().logFine("RankingTransmission: rejected unknown process " + process + ":" + purpose + " from peer " + otherpeerName);
        return prop;
    }


}

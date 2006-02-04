// migration.java
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.yacy.net
// Frankfurt, Germany, 2004, 2005
//
// this file is contributed by Alexander Schier
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

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;
import de.anomic.server.logging.serverLog;

public class migration {
    //SVN constants
    public static final int USE_WORK_DIR=1389; //wiki & messages in DATA/WORK
    public static void main(String[] args) {

    }
    public static void migrate(plasmaSwitchboard sb){
        presetPasswords(sb);
        migrateSwitchConfigSettings(sb);
        migrateWorkFiles(sb);
    }

    public static void migrateWorkFiles(plasmaSwitchboard sb){
        File file=new File(sb.getRootPath(), "DATA/SETTINGS/wiki.db");
        File file2;
        if (file.exists()) {
            serverLog.logInfo("MIGRATION", "Migrating wiki.db to "+ sb.workPath);
            sb.wikiDB.close();
            file2 = new File(sb.workPath, "wiki.db");
            try {
                serverFileUtils.copy(file, file2);
                file.delete();
            } catch (IOException e) {
            }
            
            file = new File(sb.getRootPath(), "DATA/SETTINGS/wiki-bkp.db");
            if (file.exists()) {
                serverLog.logInfo("MIGRATION", "Migrating wiki-bkp.db to "+ sb.workPath);
                file2 = new File(sb.workPath, "wiki-bkp.db");
                try {
                    serverFileUtils.copy(file, file2);
                    file.delete();
                } catch (IOException e) {}        
            }
            sb.initWiki((int) sb.getConfigLong("ramCacheWiki", 1024) / 1024);
        }
        
        
        file=new File(sb.getRootPath(), "DATA/SETTINGS/message.db");
        if(file.exists()){
            serverLog.logInfo("MIGRATION", "Migrating message.db to "+ sb.workPath);
            sb.messageDB.close();
            file2=new File(sb.workPath, "message.db");
            try {
                serverFileUtils.copy(file, file2);
                file.delete();
            } catch (IOException e) {}
            sb.initMessages((int) sb.getConfigLong("ramCacheMessage", 1024) / 1024);
        }
    }

    public static void presetPasswords(plasmaSwitchboard sb) {
        // set preset accounts/passwords
        String acc;
        if ((acc = sb.getConfig("serverAccount", "")).length() > 0) {
            sb.setConfig("serverAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(kelondroBase64Order.standardCoder.encodeString(acc)));
            sb.setConfig("serverAccount", "");
        }
        if ((acc = sb.getConfig("adminAccount", "")).length() > 0) {
            sb.setConfig("adminAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(kelondroBase64Order.standardCoder.encodeString(acc)));
            sb.setConfig("adminAccount", "");
        }
    
        // fix unsafe old passwords
        if ((acc = sb.getConfig("proxyAccountBase64", "")).length() > 0) {
            sb.setConfig("proxyAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(acc));
            sb.setConfig("proxyAccountBase64", "");
        }
        if ((acc = sb.getConfig("serverAccountBase64", "")).length() > 0) {
            sb.setConfig("serverAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(acc));
            sb.setConfig("serverAccountBase64", "");
        }
        if ((acc = sb.getConfig("adminAccountBase64", "")).length() > 0) {
            sb.setConfig("adminAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(acc));
            sb.setConfig("adminAccountBase64", "");
        }
        if ((acc = sb.getConfig("uploadAccountBase64", "")).length() > 0) {
            sb.setConfig("uploadAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(acc));
            sb.setConfig("uploadAccountBase64", "");
        }
        if ((acc = sb.getConfig("downloadAccountBase64", "")).length() > 0) {
            sb.setConfig("downloadAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(acc));
            sb.setConfig("downloadAccountBase64", "");
        }
    }

    public static void migrateSwitchConfigSettings(plasmaSwitchboard sb) {
        String value = "";
        if ((value = sb.getConfig("parseableMimeTypes","")).length() > 0) {
            sb.setConfig("parseableMimeTypes.CRAWLER", value);
            sb.setConfig("parseableMimeTypes.PROXY", value);
            sb.setConfig("parseableMimeTypes.URLREDIRECTOR", value);
            sb.setConfig("parseableMimeTypes.ICAP", value);
        }
        //Locales in DATA, because DATA must be writable, htroot not.
        if(sb.getConfig("htLocalePath", "htroot/locale").equals("htroot/locale")){
        	sb.setConfig("htLocalePath", "DATA/HTDOCS/locale");
        }
    }

}

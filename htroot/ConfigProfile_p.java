// EditProfile_p.java
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
// This File is contributed by Alexander Schier
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
// javac -classpath .:../classes EditProfile_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;

public class ConfigProfile_p {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        //listManager.switchboard = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        final Properties profile = new Properties();
        FileInputStream fileIn = null;
        try {
            fileIn = new FileInputStream(new File("DATA/SETTINGS/profile.txt"));
            profile.load(fileIn);
        } catch(IOException e) {
        } finally {
            if (fileIn != null) try { fileIn.close(); } catch (Exception e) {}
        }

        if (post != null && post.containsKey("set")) {
            profile.setProperty("name", (String)post.get("name"));
            profile.setProperty("nickname", (String)post.get("nickname"));
            profile.setProperty("homepage", (String)post.get("homepage"));
            profile.setProperty("email", (String)post.get("email"));

            profile.setProperty("icq", (String)post.get("icq"));
            profile.setProperty("jabber", (String)post.get("jabber"));
            profile.setProperty("yahoo", (String)post.get("yahoo"));
            profile.setProperty("msn", (String)post.get("msn"));
            profile.setProperty("skype", (String)post.get("skype"));

            profile.setProperty("comment", (String)post.get("comment"));


            prop.put("name", profile.getProperty("name", ""));
            prop.put("nickname", profile.getProperty("nickname", ""));
            prop.put("homepage", profile.getProperty("homepage", ""));
            prop.put("email", profile.getProperty("email", ""));

            prop.put("icq", profile.getProperty("icq", ""));
            prop.put("jabber", profile.getProperty("jabber", ""));
            prop.put("yahoo", profile.getProperty("yahoo", ""));
            prop.put("msn", profile.getProperty("msn", ""));
            prop.put("skype", profile.getProperty("skype", ""));

            prop.put("comment", profile.getProperty("comment", ""));

            // write new values
            FileOutputStream fileOut = null;
            try {
                fileOut = new FileOutputStream(new File("DATA/SETTINGS/profile.txt"));
                profile.store(fileOut , null );

                // generate a news message
                Properties news = profile;
                news.remove("comment");
                yacyCore.newsPool.publishMyNews(yacyNewsRecord.newRecord(yacyNewsPool.CATEGORY_PROFILE_UPDATE, news));
                //yacyCore.newsPool.publishMyNews(new yacyNewsRecord(yacyNewsRecord.CATEGORY_PROFILE_UPDATE, profile));
            } catch(IOException e) {
            } finally {
                if (fileOut != null) try { fileOut.close(); } catch (Exception e) {}
            }
        }

        else{
            prop.put("name", profile.getProperty("name", ""));
            prop.put("nickname", profile.getProperty("nickname", ""));
            prop.put("homepage", profile.getProperty("homepage", ""));
            prop.put("email", profile.getProperty("email", ""));

            prop.put("icq", profile.getProperty("icq", ""));
            prop.put("jabber", profile.getProperty("jabber", ""));
            prop.put("yahoo", profile.getProperty("yahoo", ""));
            prop.put("msn", profile.getProperty("msn", ""));
            prop.put("skype", profile.getProperty("skype", ""));

            prop.put("comment", profile.getProperty("comment", ""));
        }

        return prop;
    }

}
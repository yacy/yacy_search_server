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

// You must compile this file with
// javac -classpath .:../classes EditProfile_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;

public class ConfigProfile_p {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
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
            profile.setProperty("name", post.get("name"));
            profile.setProperty("nickname", post.get("nickname"));
            profile.setProperty("homepage", post.get("homepage"));
            profile.setProperty("email", post.get("email"));

            profile.setProperty("icq", post.get("icq"));
            profile.setProperty("jabber", post.get("jabber"));
            profile.setProperty("yahoo", post.get("yahoo"));
            profile.setProperty("msn", post.get("msn"));
            profile.setProperty("skype", post.get("skype"));

            profile.setProperty("comment", post.get("comment"));


            prop.putHTML("name", profile.getProperty("name", ""));
            prop.putHTML("nickname", profile.getProperty("nickname", ""));
            prop.put("homepage", profile.getProperty("homepage", ""));
            prop.put("email", profile.getProperty("email", ""));

            prop.put("icq", profile.getProperty("icq", ""));
            prop.put("jabber", profile.getProperty("jabber", ""));
            prop.put("yahoo", profile.getProperty("yahoo", ""));
            prop.put("msn", profile.getProperty("msn", ""));
            prop.put("skype", profile.getProperty("skype", ""));

            prop.putHTML("comment", profile.getProperty("comment", ""));

            // write new values
            FileOutputStream fileOut = null;
            try {
                fileOut = new FileOutputStream(new File("DATA/SETTINGS/profile.txt"));
                profile.store(fileOut , null );

                // generate a news message
                Properties news = profile;
                news.remove("comment");
                sb.webIndex.newsPool.publishMyNews(yacyNewsRecord.newRecord(sb.webIndex.seedDB.mySeed(), yacyNewsPool.CATEGORY_PROFILE_UPDATE, news));
                //yacyCore.newsPool.publishMyNews(new yacyNewsRecord(yacyNewsRecord.CATEGORY_PROFILE_UPDATE, profile));
            } catch(IOException e) {
            } finally {
                if (fileOut != null) try { fileOut.close(); } catch (Exception e) {}
            }
        }

        else{
            prop.putHTML("name", profile.getProperty("name", ""));
            prop.putHTML("nickname", profile.getProperty("nickname", ""));
            prop.put("homepage", profile.getProperty("homepage", ""));
            prop.put("email", profile.getProperty("email", ""));

            prop.put("icq", profile.getProperty("icq", ""));
            prop.put("jabber", profile.getProperty("jabber", ""));
            prop.put("yahoo", profile.getProperty("yahoo", ""));
            prop.put("msn", profile.getProperty("msn", ""));
            prop.put("skype", profile.getProperty("skype", ""));

            prop.putHTML("comment", profile.getProperty("comment", ""));
        }

        return prop;
    }

}
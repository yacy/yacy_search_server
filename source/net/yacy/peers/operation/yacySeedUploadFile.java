//yacySeedUploadFile.java
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file ist contributed by Martin Thelian
//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.peers.operation;

import java.io.File;

import net.yacy.server.serverSwitch;

import com.google.common.io.Files;


public class yacySeedUploadFile implements yacySeedUploader {

    public static final String CONFIG_FILE_PATH = "seedFilePath";

    @Override
    public String uploadSeedFile(final serverSwitch sb, final File seedFile) throws Exception {

        String seedFilePath = "";
        try {
            seedFilePath = sb.getConfig(CONFIG_FILE_PATH,"");
            if (seedFilePath.isEmpty()) throw new Exception("Path to seed file is not configured properly");

            final File publicSeedFile = new File(seedFilePath);
            Files.copy(seedFile,publicSeedFile);

            return "Seed-List file stored successfully";
        } catch (final Exception e) {
            throw new Exception("Unable to store the seed-list file into the filesystem using path '" + seedFilePath + "'. " + e.getMessage());
        }
    }

    @Override
    public String[] getConfigurationOptions() {
        return new String[]{CONFIG_FILE_PATH};
        }

}

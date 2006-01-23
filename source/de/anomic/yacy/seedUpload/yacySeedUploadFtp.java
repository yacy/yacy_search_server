//yacySeedUploadFtp.java 
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@anomic.de
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
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.


package de.anomic.yacy.seedUpload;

import java.io.File;

import de.anomic.net.ftpc;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacySeedUploader;

public class yacySeedUploadFtp implements yacySeedUploader {

    public static final String CONFIG_FTP_SERVER = "seedFTPServer";
    public static final String CONFIG_FTP_ACCOUNT = "seedFTPAccount";
    public static final String CONFIG_FTP_PASSWORD = "seedFTPPassword";
    public static final String CONFIG_FTP_PATH = "seedFTPPath";
    
    public String uploadSeedFile (serverSwitch sb, yacySeedDB seedDB, File seedFile) throws Exception {
        try {        
            if (sb == null) throw new NullPointerException("Reference to serverSwitch must not be null.");
            if (seedDB == null) throw new NullPointerException("Reference to seedDB must not be null.");
            if ((seedFile == null)||(!seedFile.exists())) throw new Exception("Seed file does not exist.");
            if (!seedFile.isFile()) throw new Exception("Seed file is not a file.");
            if (!seedFile.canRead()) throw new Exception("Seed file is not readable.");
            
            String  seedFTPServer   = sb.getConfig(CONFIG_FTP_SERVER,null);
            String  seedFTPAccount  = sb.getConfig(CONFIG_FTP_ACCOUNT,null);
            String  seedFTPPassword = sb.getConfig(CONFIG_FTP_PASSWORD,null);
            File    seedFTPPath     = new File(sb.getConfig(CONFIG_FTP_PATH,null));       
        
            if ((seedFTPServer != null) && (seedFTPAccount != null) && (seedFTPPassword != null) && (seedFTPPath != null)) {
                String log = ftpc.put(seedFTPServer, seedFile, seedFTPPath.getParent(), seedFTPPath.getName(), seedFTPAccount, seedFTPPassword);
                return log;
            } 
            throw new Exception ("Seed upload settings not configured properly. password-len=" +
                    seedFTPPassword.length() + ", filePath=" +
                    seedFTPPath);
        } catch (Exception e) {
            throw e;
        }
    }

    public String[] getConfigurationOptions() {
        return new String[] {CONFIG_FTP_SERVER,CONFIG_FTP_ACCOUNT,CONFIG_FTP_PASSWORD,CONFIG_FTP_PATH};
    }

    public String[] getLibxDependencies() {
        return new String[]{};
    }

}
